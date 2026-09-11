package ir.tvgram.app.playback

import ir.tvgram.telegram.model.MediaCategory
import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the player is working through.
 *
 * The grid hands over the whole visible list rather than a single item, so
 * "next" continues through the chat. It stays within one category, though:
 * jumping from a film to a photo to a song because they happened to be adjacent
 * in the grid is not what "next" means to someone watching a film.
 */
@Singleton
class PlaybackQueue @Inject constructor() {

    private val _items = MutableStateFlow<List<TgMediaItem>>(emptyList())
    val items: StateFlow<List<TgMediaItem>> = _items.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    val current: TgMediaItem?
        get() = _items.value.getOrNull(_index.value)

    fun submit(items: List<TgMediaItem>, startAt: TgMediaItem) {
        _items.value = items
        _index.value = items.indexOfFirst { it.uid == startAt.uid }.coerceAtLeast(0)
    }

    /** Steps to the next item of the same category; false when there is none. */
    fun moveToNext(): Boolean = moveBy(1)

    fun moveToPrevious(): Boolean = moveBy(-1)

    fun hasNext(): Boolean = peek(1) != null

    fun hasPrevious(): Boolean = peek(-1) != null

    private fun moveBy(step: Int): Boolean {
        val target = indexOfNeighbour(step) ?: return false
        _index.value = target
        return true
    }

    private fun peek(step: Int): TgMediaItem? =
        indexOfNeighbour(step)?.let { _items.value.getOrNull(it) }

    /**
     * Photos sit next to films in a chat's timeline, so a plain +1 would change
     * what kind of thing is on screen. Neighbours are looked for within the
     * current item's category instead.
     */
    private fun indexOfNeighbour(step: Int): Int? {
        val items = _items.value
        val category = current?.category ?: return null
        var candidate = _index.value + step
        while (candidate in items.indices) {
            if (items[candidate].category == category) return candidate
            candidate += step
        }
        return null
    }

    /** How far through the run of same-category items the current one is. */
    fun positionInCategory(): Pair<Int, Int> {
        val category = current?.category ?: return 0 to 0
        val sameKind = _items.value.filter { it.category == category }
        val position = sameKind.indexOfFirst { it.uid == current?.uid }
        return (position + 1) to sameKind.size
    }

    fun categoryOfCurrent(): MediaCategory? = current?.category
}
