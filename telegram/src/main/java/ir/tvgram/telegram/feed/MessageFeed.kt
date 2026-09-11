package ir.tvgram.telegram.feed

import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.TgMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MessageFeedState(
    val messages: List<TgMessage> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
) {
    val isInitialLoad: Boolean get() = isLoading && messages.isEmpty()
    val isEmpty: Boolean get() = !isLoading && messages.isEmpty() && error == null
}

/** Chat history, newest first, paged backwards as the reader scrolls. */
class MessageFeed(
    private val client: TelegramClient,
    private val scope: CoroutineScope,
    val chatId: Long,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val _state = MutableStateFlow(MessageFeedState())
    val state: StateFlow<MessageFeedState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val seen = HashSet<Long>()
    private var fromMessageId = 0L
    private var job: Job? = null

    fun loadMore() {
        if (job?.isActive == true || _state.value.endReached) return
        job = scope.launch {
            mutex.withLock {
                _state.value = _state.value.copy(isLoading = true, error = null)
                try {
                    val page = client.messagePage(chatId, fromMessageId, pageSize)
                    fromMessageId = page.nextFromMessageId
                    val fresh = page.messages.filter { seen.add(it.id) }
                    _state.value = _state.value.let {
                        it.copy(
                            messages = it.messages + fresh,
                            isLoading = false,
                            endReached = !page.hasMore,
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: e::class.java.simpleName,
                    )
                }
            }
        }
    }

    fun retry() {
        _state.value = _state.value.copy(error = null)
        loadMore()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 40
    }
}
