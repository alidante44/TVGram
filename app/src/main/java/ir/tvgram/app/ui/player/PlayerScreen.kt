package ir.tvgram.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.TelegramImage
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.app.util.Format
import ir.tvgram.telegram.model.MediaKind

/**
 * Fullscreen playback with D-pad controls.
 *
 * There is no pointer here, so the remote's keys *are* the interface: centre
 * toggles play, left/right seek, up/down step through the queue, BACK exits.
 * The overlay fades itself out a few seconds after the last key press.
 */
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Any key press wakes the overlay; it hides itself again once the viewer
    // settles down to watch.
    LaunchedEffect(lastInteraction, state.isPlaying) {
        controlsVisible = true
        if (state.isPlaying) {
            kotlinx.coroutines.delay(CONTROLS_TIMEOUT_MS)
            if (System.currentTimeMillis() - lastInteraction >= CONTROLS_TIMEOUT_MS) {
                controlsVisible = false
            }
        }
    }

    fun interacted() {
        lastInteraction = System.currentTimeMillis()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                interacted()
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onClose(); true
                    }

                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                        viewModel.togglePlayPause(); true
                    }

                    Key.DirectionLeft, Key.MediaRewind -> {
                        viewModel.seekBy(-state.seekStepMs); true
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        viewModel.seekBy(state.seekStepMs); true
                    }

                    Key.DirectionUp, Key.MediaPrevious -> {
                        viewModel.previous(); true
                    }

                    Key.DirectionDown, Key.MediaNext -> {
                        viewModel.next(); true
                    }

                    else -> false
                }
            },
    ) {
        val item = state.item

        if (item != null && (item.kind == MediaKind.VIDEO || item.kind == MediaKind.ANIMATION)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view -> view.player = viewModel.player },
            )
        } else if (item != null) {
            // Audio has nothing to show, so give it the album art large and
            // centred rather than a black rectangle.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TelegramImage(
                    fileId = item.thumbnailFileId,
                    minithumbnail = item.minithumbnail,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxHeight(0.55f)
                        .padding(24.dp),
                )
            }
            // Audio needs no surface at all — ExoPlayer plays it without a view.
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlsOverlay(state = state)
        }

        state.error?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(TvGramColors.Scrim, RoundedCornerShape(12.dp))
                    .padding(32.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_error) + "\n" + error,
                    style = MaterialTheme.typography.titleMedium,
                    color = TvGramColors.Danger,
                )
            }
        }
    }
}

@Composable
private fun ControlsOverlay(state: PlayerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(
                horizontal = TvGramDimens.ScreenPaddingHorizontal,
                vertical = TvGramDimens.ScreenPaddingVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.item?.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = TvGramColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.item?.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackgroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ProgressBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            downloadPercent = state.downloadPercent,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                text = "${Format.duration((state.positionMs / 1000).toInt())} / " +
                    Format.duration((state.durationMs / 1000).toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackground,
            )
            if (state.isBuffering) {
                Text(
                    text = stringResource(R.string.player_buffering),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvGramColors.OnBackgroundMuted,
                )
            }
            if (state.downloadPercent in 1..99) {
                Text(
                    text = stringResource(R.string.player_downloading, state.downloadPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvGramColors.OnBackgroundMuted,
                )
            }
        }
    }
}

/** Two bars: what has been downloaded behind, where playback is in front. */
@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, downloadPercent: Int) {
    val played = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = (downloadPercent / 100f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(TvGramColors.SurfaceElevated, RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(buffered)
                .height(6.dp)
                .background(TvGramColors.AccentMuted, RoundedCornerShape(3.dp)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(played)
                .height(6.dp)
                .background(TvGramColors.Accent, RoundedCornerShape(3.dp)),
        )
    }
}

private const val CONTROLS_TIMEOUT_MS = 4_000L
