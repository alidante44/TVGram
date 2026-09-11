package ir.tvgram.app.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.playback.PlaybackQueue
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.feed.MediaFeed
import ir.tvgram.telegram.feed.MediaFeedState
import ir.tvgram.telegram.model.MediaCategory
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

data class SourceState(
    val folders: List<TgFolder> = emptyList(),
    val selectedFolder: TgFolder? = null,
    val chats: List<TgChat> = emptyList(),
    val selectedChat: TgChat? = null,
    val isLoadingChats: Boolean = false,
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val client: TelegramClient,
    private val settingsRepository: SettingsRepository,
    private val playbackQueue: PlaybackQueue,
) : ViewModel() {

    private val _source = MutableStateFlow(SourceState())
    val source: StateFlow<SourceState> = _source.asStateFlow()

    private val _category = MutableStateFlow(MediaCategory.ALL)
    val category: StateFlow<MediaCategory> = _category.asStateFlow()

    private val feed = MutableStateFlow<MediaFeed?>(null)

    val feedState: StateFlow<MediaFeedState> = feed
        .flatMapLatest { it?.state ?: flowOf(MediaFeedState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaFeedState())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        viewModelScope.launch {
            val stored = settingsRepository.settings.first()
            loadFolders(stored)
        }
    }

    private suspend fun loadFolders(stored: AppSettings) {
        val folders = runCatching { client.folders() }.getOrElse { emptyList() }
        _source.value = _source.value.copy(folders = folders)

        val initial = folders.firstOrNull { it.id == stored.defaultFolderId } ?: folders.firstOrNull()
        if (initial != null) openFolder(initial, resumeChatId = stored.lastChatId)
    }

    fun selectFolder(folder: TgFolder) = openFolder(folder, resumeChatId = 0L)

    private fun openFolder(folder: TgFolder, resumeChatId: Long) {
        _source.value = _source.value.copy(
            selectedFolder = folder,
            chats = emptyList(),
            isLoadingChats = true,
        )
        viewModelScope.launch {
            val chats = runCatching { client.chats(folder) }.getOrElse { emptyList() }
            _source.value = _source.value.copy(chats = chats, isLoadingChats = false)

            // Re-open whatever the viewer was last watching, so coming back to
            // the app does not dump them on an empty screen.
            if (_source.value.selectedChat == null && resumeChatId != 0L) {
                chats.firstOrNull { it.id == resumeChatId }?.let(::selectChat)
            }
        }
    }

    fun selectChat(chat: TgChat) {
        _source.value = _source.value.copy(selectedChat = chat)
        viewModelScope.launch {
            settingsRepository.update { it.copy(lastChatId = chat.id) }
        }
        restartFeed()
    }

    fun selectCategory(category: MediaCategory) {
        if (_category.value == category) return
        _category.value = category
        restartFeed()
    }

    fun loadMore() {
        feed.value?.loadMore()
    }

    fun retry() {
        feed.value?.retry()
    }

    fun refresh() {
        feed.value?.refresh()
    }

    /** Hands the whole loaded list to the player so "next" keeps going. */
    fun openItem(item: TgMediaItem) {
        playbackQueue.submit(feedState.value.items, item)
    }

    private fun restartFeed() {
        val chat = _source.value.selectedChat ?: return
        feed.value = MediaFeed(
            client = client,
            scope = viewModelScope,
            chatId = chat.id,
            category = _category.value,
        ).also { it.loadMore() }
    }
}
