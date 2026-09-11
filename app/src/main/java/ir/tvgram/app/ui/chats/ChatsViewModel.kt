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
import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatsUiState(
    val chats: List<TgChat> = emptyList(),
    val selectedChat: TgChat? = null,
    val isLoadingChats: Boolean = true,
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val client: TelegramClient,
    private val settingsRepository: SettingsRepository,
    private val queue: PlaybackQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    private val feed = MutableStateFlow<MessageFeed?>(null)

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
                ?: TgFolder.builtIn(BuiltInFolder.ALL)
            val chats = runCatching { client.chats(folder) }.getOrElse { emptyList() }
            _state.value = ChatsUiState(chats = chats, isLoadingChats = false)

            chats.firstOrNull { it.id == stored.lastChatId }?.let(::selectChat)
        }
    }

    fun selectChat(chat: TgChat) {
        if (_state.value.selectedChat?.id == chat.id) return
        _state.value = _state.value.copy(selectedChat = chat)
        feed.value = MessageFeed(client, viewModelScope, chat.id).also { it.loadMore() }
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
}
