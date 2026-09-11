package ir.tvgram.telegram.feed

import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.MediaCategory
import ir.tvgram.telegram.model.TgMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MediaFeedState(
    val items: List<TgMediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
) {
    val isInitialLoad: Boolean get() = isLoading && items.isEmpty()
    val isEmpty: Boolean get() = !isLoading && items.isEmpty() && error == null
}

/**
 * Incrementally loads a chat's media, newest first, appending to [state] as
 * each page lands so the grid fills in front of the viewer instead of waiting
 * for everything.
 *
 * [MediaCategory.ALL] is the interesting case: Telegram has no "any media"
 * search filter, so the feed runs one cursor per concrete category and merges
 * them by date. An item is only emitted once it is known to be newer than the
 * head of every other cursor, which keeps the merged order correct across
 * page boundaries.
 */
class MediaFeed(
    private val client: TelegramClient,
    private val scope: CoroutineScope,
    val chatId: Long,
    val category: MediaCategory,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    /** Narrows the feed to media matching this text; empty means everything. */
    private val query: String = "",
) {
    private val _state = MutableStateFlow(MediaFeedState())
    val state: StateFlow<MediaFeedState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val cursors: List<Cursor> = when (category) {
        MediaCategory.ALL -> MERGED_CATEGORIES.map(::Cursor)
        else -> listOf(Cursor(category))
    }
    private val seen = HashSet<Long>()
    private var job: Job? = null

    /** Loads the next page. Repeated calls while a load is in flight are ignored. */
    fun loadMore() {
        if (job?.isActive == true) return
        val snapshot = _state.value
        if (snapshot.endReached) return
        job = scope.launch {
            mutex.withLock {
                _state.value = _state.value.copy(isLoading = true, error = null)
                try {
                    val fresh = nextBatch()
                    _state.value = _state.value.let { current ->
                        current.copy(
                            items = current.items + fresh,
                            isLoading = false,
                            endReached = cursors.all { it.exhausted && it.buffer.isEmpty() },
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

    /** Discards everything loaded so far and starts again from the newest message. */
    fun refresh() {
        job?.cancel()
        job = null
        cursors.forEach { it.reset() }
        seen.clear()
        _state.value = MediaFeedState()
        loadMore()
    }

    fun retry() {
        _state.value = _state.value.copy(error = null)
        loadMore()
    }

    private suspend fun nextBatch(): List<TgMediaItem> {
        // Refill every cursor that could still contribute, so the merge below
        // always compares real heads rather than "not fetched yet".
        for (cursor in cursors) {
            if (cursor.buffer.isEmpty() && !cursor.exhausted) cursor.fetch()
        }

        val batch = ArrayList<TgMediaItem>(pageSize)
        while (batch.size < pageSize) {
            val next = cursors
                .mapNotNull { c -> c.buffer.firstOrNull()?.let { c to it } }
                .maxByOrNull { (_, item) -> item.sortKey }
                ?: break

            val (cursor, item) = next
            cursor.buffer.removeAt(0)
            if (seen.add(item.messageId)) batch += item

            if (cursor.buffer.isEmpty() && !cursor.exhausted) {
                cursor.fetch()
                // A refill that yields nothing means this cursor is done; the
                // loop simply stops considering it.
            }
        }
        return batch
    }

    private inner class Cursor(val category: MediaCategory) {
        val buffer = ArrayList<TgMediaItem>()
        var fromMessageId = 0L
        var exhausted = false

        fun reset() {
            buffer.clear()
            fromMessageId = 0L
            exhausted = false
        }

        suspend fun fetch() {
            val page = client.mediaPage(chatId, category, fromMessageId, pageSize, query)
            buffer += page.items
            fromMessageId = page.nextFromMessageId
            if (!page.hasMore || page.items.isEmpty()) exhausted = true
        }
    }

    private val TgMediaItem.sortKey: Long
        // Date alone is too coarse — an album posted in one second must keep a
        // stable order, and message ids increase monotonically within a chat.
        get() = date.toLong() * 1_000_000L + (messageId shr 20).coerceAtMost(999_999L)

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 40

        /** The concrete categories that make up [MediaCategory.ALL]. */
        val MERGED_CATEGORIES: List<MediaCategory> = listOf(
            MediaCategory.VIDEO,
            MediaCategory.PHOTO,
            MediaCategory.AUDIO,
            MediaCategory.OTHER,
        )
    }
}
