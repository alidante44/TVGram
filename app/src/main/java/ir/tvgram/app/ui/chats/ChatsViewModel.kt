package ir.tvgram.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.playback.PlaybackQueue
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.feed.MessageFeed
import ir.tvgram.telegram.feed.MessageFeedState
import ir.tvgram.telegram.model.BuiltInFolder
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgLiveStream
import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatsUiState(
    val chats: List<TgChat> = emptyList(),
    val selectedChat: TgChat? = null,
    val isLoadingChats: Boolean = true,
    /** What the viewer typed into the chat search box. */
    val query: String = "",
    val results: List<TgChat> = emptyList(),
    /** Set while the open chat is broadcasting. */
    val liveStream: TgLiveStream? = null,
) {
    /** Search results stand in for the chat list while a query is active. */
    val visibleChats: List<TgChat> get() = if (query.isBlank()) chats else results
}

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val client: TelegramClient,
    private val settingsRepository: SettingsRepository,
    private val queue: PlaybackQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    private val feed = MutableStateFlow<MessageFeed?>(null)
    private var searchJob: Job? = null
    private var liveStreamJob: Job? = null

    val messages: StateFlow<MessageFeedState> = feed
        .flatMapLatest { it?.state ?: flowOf(MessageFeedState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageFeedState())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        viewModelScope.launch {
            val stored = settingsRepository.settings.first()
            // The reader always starts from the full chat list: it is a reader,
            // not a second source picker.
            val folder = client.folders().firstOrNull { it.builtIn == BuiltInFolder.ALL }
                ?: TgFolder.forBuiltIn(BuiltInFolder.ALL)
            val chats = runCatching { client.chats(folder) }.getOrElse { emptyList() }
            _state.value = ChatsUiState(chats = chats, isLoadingChats = false)

            chats.firstOrNull { it.id == stored.lastChatId }?.let(::selectChat)
        }
    }

    /**
     * Filters the loaded chats straight away so typing feels immediate, then
     * asks Telegram as well — the account usually has far more chats than the
     * list has loaded.
     */
    fun search(query: String) {
        searchJob?.cancel()
        val local = if (query.isBlank()) {
            emptyList()
        } else {
            _state.value.chats.filter { it.title.contains(query, ignoreCase = true) }
        }
        _state.value = _state.value.copy(query = query, results = local)
        if (query.isBlank()) return

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val remote = runCatching { client.searchChats(query) }.getOrElse { emptyList() }
            _state.value = _state.value.copy(results = (local + remote).distinctBy { it.id })
        }
    }

    fun selectChat(chat: TgChat) {
        if (_state.value.selectedChat?.id == chat.id) return
        _state.value = _state.value.copy(selectedChat = chat, liveStream = null)
        feed.value = MessageFeed(client, viewModelScope, chat.id).also { it.loadMore() }
        watchLiveStream(chat.id)
    }

    /**
     * Keeps the live banner truthful: asked once when the chat opens, then again
     * whenever Telegram says this chat's video chat changed, so the banner
     * appears when a broadcast starts and goes away when it ends.
     */
    private fun watchLiveStream(chatId: Long) {
        liveStreamJob?.cancel()
        liveStreamJob = viewModelScope.launch {
            refreshLiveStream(chatId)
            client.videoChatUpdates
                .filter { it == chatId }
                .collect { refreshLiveStream(chatId) }
        }
    }

    private suspend fun refreshLiveStream(chatId: Long) {
        val stream = runCatching { client.liveStream(chatId) }.getOrNull()
        if (_state.value.selectedChat?.id == chatId) {
            _state.value = _state.value.copy(liveStream = stream)
        }
    }

    fun loadMore() {
        feed.value?.loadMore()
    }

    fun retry() {
        feed.value?.retry()
    }

    /** Opening media from a message reuses the player, with the chat as queue. */
    fun openMedia(item: TgMediaItem) {
        val playable = messages.value.messages.mapNotNull { it.media }
        queue.submit(playable.ifEmpty { listOf(item) }, item)
    }

    private companion object {
        /** Long enough that typing on a remote does not fire a search per letter. */
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
