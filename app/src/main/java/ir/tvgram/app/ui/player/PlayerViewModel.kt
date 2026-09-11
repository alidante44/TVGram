package ir.tvgram.app.ui.player

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.tvgram.app.playback.PlaybackQueue
import ir.tvgram.app.playback.PlayerFactory
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val item: TgMediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** 0..100 of the underlying Telegram file, so the viewer sees it arriving. */
    val downloadPercent: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
    val seekStepMs: Long = 10_000,
    val fullscreen: Boolean = false,
    /** Degrees, for footage shot sideways on a phone. */
    val rotation: Int = 0,
    /** Set while the viewer is dragging the seek bar but has not committed. */
    val scrubPositionMs: Long? = null,
    val photo: ImageBitmap? = null,
    val positionInCategory: Pair<Int, Int> = 0 to 0,
) {
    val isPhoto: Boolean get() = item?.kind == MediaKind.PHOTO
    val isVideo: Boolean get() = item?.kind == MediaKind.VIDEO || item?.kind == MediaKind.ANIMATION
    val canSeek: Boolean get() = !isPhoto && durationMs > 0

    /** What the seek bar should show: the drag position while scrubbing. */
    val displayedPositionMs: Long get() = scrubPositionMs ?: positionMs
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerFactory: PlayerFactory,
    private val queue: PlaybackQueue,
    private val client: TelegramClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var settings: AppSettings = AppSettings()
    private var progressJob: Job? = null
    private var downloadJob: Job? = null
    private var photoJob: Job? = null

    var player: ExoPlayer? = null
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING,
                durationMs = player?.duration?.takeIf { it > 0 } ?: _state.value.durationMs,
            )
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.errorCodeName, isBuffering = false)
        }
    }

    /**
     * Opens whatever the queue currently points at.
     *
     * This is called every time the overlay appears rather than once in `init`:
     * the view model is scoped to the activity, so without it a second opening
     * would show whatever the first one left behind.
     */
    fun open() {
        viewModelScope.launch {
            settings = settingsRepository.settings.first()
            val exoPlayer = player ?: playerFactory.createPlayer(context, settings).also {
                it.addListener(listener)
                player = it
            }
            exoPlayer.playWhenReady = true
            _state.value = PlayerUiState(seekStepMs = settings.seekStepSeconds * 1000L)
            loadCurrent()
            trackProgress()
        }
    }

    /**
     * Stops and unloads. Without this, leaving the overlay left the audio
     * playing — the view model outlives the screen that shows it.
     */
    fun close() {
        progressJob?.cancel()
        downloadJob?.cancel()
        photoJob?.cancel()
        player?.run {
            stop()
            clearMediaItems()
        }
        _state.value = PlayerUiState()
    }

    private fun loadCurrent() {
        val item = queue.current ?: return
        val exoPlayer = player ?: return

        photoJob?.cancel()
        downloadJob?.cancel()

        _state.value = _state.value.copy(
            item = item,
            error = null,
            positionMs = 0,
            scrubPositionMs = null,
            rotation = 0,
            photo = null,
            durationMs = if (item.durationSeconds > 0) item.durationSeconds * 1000L else 0,
            hasNext = queue.hasNext(),
            hasPrevious = queue.hasPrevious(),
            downloadPercent = 0,
            positionInCategory = queue.positionInCategory(),
        )

        if (item.kind == MediaKind.PHOTO) {
            // A still has nothing to play; make sure whatever was playing stops.
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            loadPhoto(item)
        } else {
            exoPlayer.setMediaItem(playerFactory.mediaItem(item))
            exoPlayer.prepare()
            exoPlayer.play()
        }
        trackDownload(item)
    }

    private fun loadPhoto(item: TgMediaItem) {
        _state.value = _state.value.copy(
            photo = decodePreview(item.minithumbnail),
            isBuffering = true,
        )
        photoJob = viewModelScope.launch {
            val result = runCatching {
                val path = client.downloadFully(item.fileId)
                withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            }
            _state.value = _state.value.copy(
                photo = result.getOrNull() ?: _state.value.photo,
                isBuffering = false,
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    /** The embedded blur shows instantly while the full photo downloads. */
    private fun decodePreview(bytes: ByteArray?): ImageBitmap? = bytes?.let {
        runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
    }

    /** Mirrors the file's download progress so the bar shows what is buffered. */
    private fun trackDownload(item: TgMediaItem) {
        downloadJob = viewModelScope.launch {
            runCatching {
                client.fileUpdates(item.fileId).collect { file ->
                    val total = file.bestKnownSize
                    if (total > 0) {
                        val percent = (file.downloadedSize * 100 / total).toInt().coerceIn(0, 100)
                        _state.value = _state.value.copy(downloadPercent = percent)
                    }
                }
            }
        }
    }

    private fun trackProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                player?.let { exoPlayer ->
                    if (_state.value.scrubPositionMs == null) {
                        _state.value = _state.value.copy(
                            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                            durationMs = exoPlayer.duration.takeIf { it > 0 }
                                ?: _state.value.durationMs,
                        )
                    }
                }
                delay(500)
            }
        }
    }

    // --- controls ---------------------------------------------------------

    fun togglePlayPause() {
        val exoPlayer = player ?: return
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekBy(deltaMs: Long) {
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0)
        exoPlayer.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    /**
     * Moves the seek bar without seeking yet, so the viewer can aim at a point
     * in a long film without the player chasing every keypress.
     */
    fun scrubBy(deltaMs: Long) {
        val state = _state.value
        if (state.durationMs <= 0) return
        val from = state.scrubPositionMs ?: state.positionMs
        _state.value = state.copy(
            scrubPositionMs = (from + deltaMs).coerceIn(0, state.durationMs),
        )
    }

    /**
     * Commits the scrub. Playback resumes from there and, because the data
     * source asks Telegram to download from the byte the player wants, so does
     * the download — no waiting for the parts that were skipped.
     */
    fun commitScrub() {
        val target = _state.value.scrubPositionMs ?: return
        player?.seekTo(target)
        _state.value = _state.value.copy(scrubPositionMs = null, positionMs = target)
    }

    fun cancelScrub() {
        _state.value = _state.value.copy(scrubPositionMs = null)
    }

    fun next() {
        if (queue.moveToNext()) loadCurrent()
    }

    fun previous() {
        if (queue.moveToPrevious()) loadCurrent()
    }

    fun toggleFullscreen() {
        _state.value = _state.value.copy(fullscreen = !_state.value.fullscreen)
    }

    fun exitFullscreen(): Boolean {
        if (!_state.value.fullscreen) return false
        _state.value = _state.value.copy(fullscreen = false)
        return true
    }

    /** Quarter turns, for video shot sideways on a phone. */
    fun rotate() {
        _state.value = _state.value.copy(rotation = (_state.value.rotation + 90) % 360)
    }

    private fun onEnded() {
        if (settings.autoplayNext && queue.moveToNext()) {
            loadCurrent()
        } else {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        downloadJob?.cancel()
        photoJob?.cancel()
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        super.onCleared()
    }
}
