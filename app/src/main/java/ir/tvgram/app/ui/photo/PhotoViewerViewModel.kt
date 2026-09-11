package ir.tvgram.app.ui.photo

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.playback.PlaybackQueue
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoViewerState(
    val item: TgMediaItem? = null,
    val bitmap: ImageBitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val counter: String = "",
)

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val client: TelegramClient,
    private val queue: PlaybackQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoViewerState())
    val state: StateFlow<PhotoViewerState> = _state.asStateFlow()

    /** Only the photos of the current queue — arrow keys walk this list. */
    private val photos: List<TgMediaItem> = queue.items.value.filter { it.kind == MediaKind.PHOTO }
    private var index: Int = photos.indexOfFirst { it.uid == queue.current?.uid }.coerceAtLeast(0)
    private var loadJob: Job? = null

    init {
        show(index)
    }

    fun next() {
        if (index + 1 < photos.size) show(index + 1)
    }

    fun previous() {
        if (index - 1 >= 0) show(index - 1)
    }

    private fun show(target: Int) {
        val item = photos.getOrNull(target) ?: return
        index = target
        _state.value = PhotoViewerState(
            item = item,
            bitmap = decode(item.minithumbnail),
            isLoading = true,
            counter = "${target + 1} / ${photos.size}",
        )

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = runCatching {
                val path = client.downloadFully(item.fileId)
                withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            }
            _state.value = _state.value.copy(
                bitmap = result.getOrNull() ?: _state.value.bitmap,
                isLoading = false,
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    /** The embedded blur shows instantly while the full photo downloads. */
    private fun decode(bytes: ByteArray?): ImageBitmap? = bytes?.let {
        runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
    }
}
