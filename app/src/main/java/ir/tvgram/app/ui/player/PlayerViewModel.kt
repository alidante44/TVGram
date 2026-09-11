package ir.tvgram.app.ui.player

import android.content.Context
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
import ir.tvgram.telegram.model.TgMediaItem
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val item: TgMediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** 0..100 of the underlying Telegram file, so the viewer sees it arriving. */
    val downloadPercent: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
    val seekStepMs: Long = 10_000,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerFactory: PlayerFactory,
    private val queue: PlaybackQueue,
    private val client: TelegramClient,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var settings: AppSettings = AppSettings()
    private var progressJob: Job? = null
    private var downloadJob: Job? = null

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

    init {
        viewModelScope.launch {
            settings = settingsRepository.settings.first()
            val exoPlayer = playerFactory.createPlayer(context, settings).apply {
                addListener(listener)
                playWhenReady = true
            }
            player = exoPlayer
            _state.value = _state.value.copy(seekStepMs = settings.seekStepSeconds * 1000L)
            openCurrent()
            trackProgress()
        }
    }

    private fun openCurrent() {
        val item = queue.current ?: return
        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(playerFactory.mediaItem(item))
        exoPlayer.prepare()
        _state.value = _state.value.copy(
            item = item,
            error = null,
            positionMs = 0,
            durationMs = if (item.durationSeconds > 0) item.durationSeconds * 1000L else 0,
            hasNext = queue.hasNext(),
            hasPrevious = queue.hasPrevious(),
            downloadPercent = 0,
        )
        trackDownload(item)
    }

    /** Mirrors the file's download progress so the bar shows what is buffered. */
    private fun trackDownload(item: TgMediaItem) {
        downloadJob?.cancel()
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
                    _state.value = _state.value.copy(
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                        durationMs = exoPlayer.duration.takeIf { it > 0 } ?: _state.value.durationMs,
                    )
                }
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        val exoPlayer = player ?: return
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekBy(deltaMs: Long) {
        val exoPlayer = player ?: return
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0)
        val duration = exoPlayer.duration
        exoPlayer.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun next() {
        if (queue.moveToNext()) openCurrent()
    }

    fun previous() {
        if (queue.moveToPrevious()) openCurrent()
    }

    private fun onEnded() {
        if (settings.autoplayNext && queue.moveToNext()) {
            openCurrent()
        } else {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        downloadJob?.cancel()
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        super.onCleared()
    }
}
