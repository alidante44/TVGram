package ir.tvgram.app.playback

import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the player is currently working through.
 *
 * The grid hands over the whole visible list rather than a single item, so
 * "play next" continues through the chat instead of stopping after one file —
 * and the player screen needs no navigation arguments.
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

    /** Moves to the next playable item, skipping photos and plain documents. */
    fun moveToNext(): Boolean = moveBy(1)

    fun moveToPrevious(): Boolean = moveBy(-1)

    private fun moveBy(step: Int): Boolean {
        val items = _items.value
        var candidate = _index.value + step
        while (candidate in items.indices) {
            if (items[candidate].kind.isPlayable) {
                _index.value = candidate
                return true
            }
            candidate += step
        }
        return false
    }

    fun hasNext(): Boolean = peek(1) != null

    fun hasPrevious(): Boolean = peek(-1) != null

    private fun peek(step: Int): TgMediaItem? {
        val items = _items.value
        var candidate = _index.value + step
        while (candidate in items.indices) {
            if (items[candidate].kind.isPlayable) return items[candidate]
            candidate += step
        }
        return null
    }
}
