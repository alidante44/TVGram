package ir.tvgram.app.ui.live

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.tvgram.app.playback.PlayerFactory
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.TgLiveStream
import ir.tvgram.telegram.model.TgStreamChannel
import java.io.File
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

data class LiveUiState(
    val stream: TgLiveStream? = null,
    val isJoining: Boolean = false,
    val isPlaying: Boolean = false,
    /** Set when the stream cannot be watched, with the reason as a string key. */
    val failure: LiveFailure? = null,
    val segmentsPlayed: Int = 0,
)

enum class LiveFailure { JOIN_REFUSED, NO_CHANNELS, NO_SEGMENTS }

/**
 * Watches a channel's live broadcast.
 *
 * Telegram does not serve a live stream as a file or a playlist URL. It serves
 * numbered segments to participants of the call: join, ask which tracks exist,
 * then pull one segment at a time and hand each to the player as it arrives.
 * Video segments are MPEG-4, which is why they can be queued straight onto
 * ExoPlayer rather than demuxed by hand.
 *
 * Joining needs a WebRTC payload that Telegram's own clients get from tgcalls,
 * a native component TDLib does not ship. A listener's payload is simple enough
 * to send without it — no camera, no microphone, just a synchronisation source
 * — and that is what this does. If the server refuses it, the screen says so
 * plainly instead of spinning.
 */
@HiltViewModel
class LiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: TelegramClient,
    private val playerFactory: PlayerFactory,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var pumpJob: Job? = null
    private var joinedCallId: Int = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }
    }

    fun open(stream: TgLiveStream) {
        viewModelScope.launch {
            _state.value = LiveUiState(stream = stream, isJoining = true)

            val settings = settingsRepository.settings.first()
            val exoPlayer = player ?: playerFactory.createPlayer(context, settings).also {
                it.addListener(listener)
                player = it
            }
            exoPlayer.playWhenReady = true

            if (!client.joinLiveStream(stream.groupCallId)) {
                _state.value = _state.value.copy(isJoining = false, failure = LiveFailure.JOIN_REFUSED)
                return@launch
            }
            joinedCallId = stream.groupCallId

            val channel = client.liveStreamChannels(stream.groupCallId)
                // The highest channel id is the video track when there is one;
                // an audio-only broadcast leaves just the one to pick.
                .maxByOrNull { it.channelId }
            if (channel == null) {
                _state.value = _state.value.copy(isJoining = false, failure = LiveFailure.NO_CHANNELS)
                return@launch
            }

            _state.value = _state.value.copy(isJoining = false)
            pump(stream.groupCallId, channel, exoPlayer)
        }
    }

    /**
     * Pulls segments and queues them.
     *
     * It starts a little behind the newest segment the server admits to having:
     * the very latest is often still being written, and asking for it returns
     * nothing. Queueing rather than replacing is what makes playback continuous.
     */
    private fun pump(callId: Int, channel: TgStreamChannel, exoPlayer: ExoPlayer) {
        pumpJob?.cancel()
        pumpJob = viewModelScope.launch {
            val step = channel.segmentDurationMs
            var offset = channel.timeOffsetMs - step * SEGMENTS_BEHIND
            var queued = 0
            var emptyRuns = 0

            while (isActive) {
                val bytes = runCatching {
                    client.liveStreamSegment(callId, offset, channel.scale, channel.channelId)
                }.getOrNull()

                if (bytes == null || bytes.isEmpty()) {
                    emptyRuns++
                    if (emptyRuns >= MAX_EMPTY_RUNS) {
                        if (queued == 0) {
                            _state.value = _state.value.copy(failure = LiveFailure.NO_SEGMENTS)
                            return@launch
                        }
                        // The broadcast caught up with us; wait for it to move on.
                        delay(step)
                        emptyRuns = 0
                    } else {
                        delay(step / 2)
                    }
                    continue
                }

                emptyRuns = 0
                val file = writeSegment(bytes, queued)
                exoPlayer.addMediaItem(MediaItem.fromUri(file.toUri()))
                if (queued == 0) exoPlayer.prepare()
                queued++
                offset += step
                _state.value = _state.value.copy(segmentsPlayed = queued)

                // Stay a couple of segments ahead of the player, no further, so
                // the stream keeps its place rather than drifting into the past.
                while (isActive && exoPlayer.mediaItemCount - exoPlayer.currentMediaItemIndex > BUFFER_SEGMENTS) {
                    delay(step / 2)
                }
            }
        }
    }

    private suspend fun writeSegment(bytes: ByteArray, index: Int): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, SEGMENT_DIRECTORY).apply { mkdirs() }
            File(directory, "segment-${index % SEGMENT_RING}.mp4").apply { writeBytes(bytes) }
        }

    fun close() {
        pumpJob?.cancel()
        pumpJob = null
        player?.run {
            stop()
            clearMediaItems()
        }
        val callId = joinedCallId
        joinedCallId = 0
        if (callId != 0) {
            viewModelScope.launch { runCatching { client.leaveLiveStream(callId) } }
        }
        _state.value = LiveUiState()
    }

    override fun onCleared() {
        pumpJob?.cancel()
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        super.onCleared()
    }

    private companion object {
        /** How far behind the newest offset to start, so the segment is complete. */
        const val SEGMENTS_BEHIND = 3

        /** How many segments to keep queued ahead of the player. */
        const val BUFFER_SEGMENTS = 4

        const val MAX_EMPTY_RUNS = 4
        const val SEGMENT_DIRECTORY = "live-segments"

        /** Segment files are reused in a ring so the cache cannot grow forever. */
        const val SEGMENT_RING = 32
    }
}

private fun File.toUri(): android.net.Uri = android.net.Uri.fromFile(this)
