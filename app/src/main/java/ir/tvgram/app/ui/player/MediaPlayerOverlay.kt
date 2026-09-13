package ir.tvgram.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.TelegramImage
import ir.tvgram.app.ui.common.TvIcon
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.util.Format
import kotlinx.coroutines.delay

/**
 * One overlay for everything playable: a film, a song and a photo all open the
 * same window, so the controls are always in the same place.
 *
 * It lives in a [Dialog] rather than a Box in the main layout because a dialog
 * owns its own window, and that is what keeps the D-pad inside it — otherwise
 * focus wanders into the grid behind and the viewer ends up scrolling the wrong
 * thing.
 */
@Composable
fun MediaPlayerOverlay(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Opening on appear and closing on disappear is what stops the audio from
    // carrying on after the viewer leaves, and what makes the next thing they
    // pick actually be the thing that plays.
    DisposableEffect(Unit) {
        viewModel.open()
        onDispose { viewModel.close() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        PlayerWindow(state = state, viewModel = viewModel, onClose = onClose)
    }
}

@Composable
private fun PlayerWindow(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
) {
    val surfaceFocus = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { runCatching { surfaceFocus.requestFocus() } }

    // Controls fade out once the viewer settles in to watch, and any key press
    // brings them back.
    //
    // Fullscreen hides them whatever is open, including a photo: a still has no
    // "playing" state to wait for, so the old rule left the bar sitting on top
    // of the picture and fullscreen did nothing for photos.
    LaunchedEffect(lastInteraction, state.isPlaying, state.isPhoto, state.fullscreen) {
        controlsVisible = true
        val autoHide = state.fullscreen || (state.isPlaying && !state.isPhoto)
        if (autoHide) {
            val timeout = if (state.isPhoto) PHOTO_CONTROLS_TIMEOUT_MS else CONTROLS_TIMEOUT_MS
            delay(timeout)
            if (System.currentTimeMillis() - lastInteraction >= timeout) {
                controlsVisible = false
            }
        }
    }

    fun touched() {
        lastInteraction = System.currentTimeMillis()
    }

    fun leave() {
        if (!viewModel.exitFullscreen()) onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.fullscreen) Color.Black else TvGramColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (state.fullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth(0.82f)
                            .fillMaxHeight(0.86f)
                    },
                )
                .background(
                    Color.Black,
                    if (state.fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp),
                )
                .clip(if (state.fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp)),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(surfaceFocus)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        touched()
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                leave(); true
                            }

                            Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                                if (!state.isPhoto) viewModel.togglePlayPause()
                                true
                            }

                            // Left and right nudge playback; the seek bar below
                            // is where a longer jump is aimed.
                            Key.DirectionLeft, Key.MediaRewind -> {
                                if (state.isPhoto) viewModel.previous()
                                else viewModel.seekBy(-state.seekStepMs)
                                true
                            }

                            Key.DirectionRight, Key.MediaFastForward -> {
                                if (state.isPhoto) viewModel.next()
                                else viewModel.seekBy(state.seekStepMs)
                                true
                            }

                            // Down reveals the controls and hands focus over;
                            // it no longer changes what is playing.
                            Key.DirectionDown -> {
                                controlsVisible = true
                                false
                            }

                            Key.DirectionUp -> {
                                controlsVisible = true; true
                            }

                            Key.MediaNext -> {
                                viewModel.next(); true
                            }

                            Key.MediaPrevious -> {
                                viewModel.previous(); true
                            }

                            else -> false
                        }
                    },
            ) {
                MediaSurface(state = state, viewModel = viewModel)

                if (state.isBuffering) {
                    Text(
                        text = stringResource(R.string.player_buffering),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvGramColors.OnBackground,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }

                state.error?.let { error ->
                    Text(
                        text = stringResource(R.string.player_error) + "\n" + error,
                        style = MaterialTheme.typography.titleMedium,
                        color = TvGramColors.Danger,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(TvGramColors.Scrim, RoundedCornerShape(12.dp))
                            .padding(24.dp),
                    )
                }
            }

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                ControlBar(
                    state = state,
                    viewModel = viewModel,
                    onClose = onClose,
                    onInteract = { touched() },
                )
            }
        }
    }
}

@Composable
private fun MediaSurface(state: PlayerUiState, viewModel: PlayerViewModel) {
    val item = state.item
    val rotated = Modifier
        .fillMaxSize()
        .graphicsLayer {
            rotationZ = state.rotation.toFloat()
            // A quarter turn swaps width and height; scaling back keeps the
            // picture inside the window instead of cropping it.
            if (state.rotation % 180 != 0 && size.height > 0f) {
                val ratio = size.width / size.height
                scaleX = 1f / ratio
                scaleY = ratio
            }
        }

    when {
        item == null -> Unit

        state.isPhoto -> state.photo?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = item.title,
                modifier = rotated,
                contentScale = ContentScale.Fit,
            )
        }

        state.isVideo -> AndroidView(
            modifier = rotated,
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = viewModel.player },
        )

        // Audio has nothing to show, so the cover art gets the space.
        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TelegramImage(
                fileId = item.thumbnailFileId,
                minithumbnail = item.minithumbnail,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun ControlBar(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onInteract: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF0000000))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.item?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TvGramColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val (position, total) = state.positionInCategory
                val subtitle = listOfNotNull(
                    state.item?.subtitle?.takeIf { it.isNotBlank() },
                    if (total > 1) "$position / $total" else null,
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TvGramColors.OnBackgroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.downloadPercent in 1..99) {
                Text(
                    text = stringResource(R.string.player_downloading, state.downloadPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = TvGramColors.OnBackgroundMuted,
                )
            }
        }

        if (state.canSeek) {
            SeekBar(state = state, viewModel = viewModel, onInteract = onInteract)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.isPhoto) {
                ControlButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = stringResource(
                        if (state.isPlaying) R.string.player_pause else R.string.player_play,
                    ),
                    onClick = { onInteract(); viewModel.togglePlayPause() },
                )
            }

            ControlButton(
                icon = Icons.Filled.SkipPrevious,
                label = stringResource(R.string.player_previous),
                enabled = state.hasPrevious,
                onClick = { onInteract(); viewModel.previous() },
            )
            ControlButton(
                icon = Icons.Filled.SkipNext,
                label = stringResource(R.string.player_next),
                enabled = state.hasNext,
                onClick = { onInteract(); viewModel.next() },
            )
            ControlButton(
                icon = Icons.Filled.Rotate90DegreesCw,
                label = stringResource(R.string.player_rotate),
                onClick = { onInteract(); viewModel.rotate() },
            )
            ControlButton(
                icon = if (state.fullscreen) {
                    Icons.Filled.FullscreenExit
                } else {
                    Icons.Filled.Fullscreen
                },
                label = stringResource(
                    if (state.fullscreen) {
                        R.string.player_exit_fullscreen
                    } else {
                        R.string.player_fullscreen
                    },
                ),
                onClick = { onInteract(); viewModel.toggleFullscreen() },
            )
            ControlButton(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.player_close),
                onClick = onClose,
            )
        }
    }
}

/**
 * A seek bar aimed with the D-pad: left and right move a marker, and the centre
 * key commits it. Playback — and the download behind it — then continues from
 * that point rather than from wherever it had got to.
 */
@Composable
private fun SeekBar(state: PlayerUiState, viewModel: PlayerViewModel, onInteract: () -> Unit) {
    val scrubbing = state.scrubPositionMs != null
    val progress = if (state.durationMs > 0) {
        (state.displayedPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val buffered = (state.downloadPercent / 100f).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusableSurface(
            onClick = {
                onInteract()
                if (scrubbing) viewModel.commitScrub() else viewModel.scrubBy(0)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            focusScale = 1f,
            background = Color.Transparent,
            focusedBackground = Color.Transparent,
        ) { focused ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val step = if (state.durationMs > LONG_MEDIA_MS) {
                            state.durationMs / 100
                        } else {
                            state.seekStepMs
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onInteract(); viewModel.scrubBy(-step); true
                            }

                            Key.DirectionRight -> {
                                onInteract(); viewModel.scrubBy(step); true
                            }

                            Key.Back, Key.Escape -> {
                                if (scrubbing) {
                                    viewModel.cancelScrub(); true
                                } else {
                                    false
                                }
                            }

                            else -> false
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (focused || scrubbing) 10.dp else 6.dp)
                        .background(TvGramColors.SurfaceElevated, RoundedCornerShape(5.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(buffered)
                            .height(if (focused || scrubbing) 10.dp else 6.dp)
                            .background(TvGramColors.AccentMuted, RoundedCornerShape(5.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(if (focused || scrubbing) 10.dp else 6.dp)
                            .background(
                                if (scrubbing) TvGramColors.Focus else TvGramColors.Accent,
                                RoundedCornerShape(5.dp),
                            ),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "${Format.duration((state.displayedPositionMs / 1000).toInt())} / " +
                    Format.duration((state.durationMs / 1000).toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (scrubbing) TvGramColors.Focus else TvGramColors.OnBackground,
            )
            if (scrubbing) {
                Text(
                    text = stringResource(R.string.player_seek_confirm),
                    style = MaterialTheme.typography.labelSmall,
                    color = TvGramColors.OnBackgroundMuted,
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        focusScale = 1.08f,
        background = TvGramColors.SurfaceElevated,
        focusedBackground = TvGramColors.Accent,
    ) { focused ->
        Box(modifier = Modifier.padding(12.dp)) {
            TvIcon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    focused -> TvGramColors.Background
                    enabled -> TvGramColors.OnBackground
                    else -> TvGramColors.OnBackgroundMuted
                },
                size = 26.dp,
            )
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 4_000L

/** A photo is looked at rather than watched, so its bar goes sooner. */
private const val PHOTO_CONTROLS_TIMEOUT_MS = 1_500L

/** Past this length a percentage step aims better than a fixed number of seconds. */
private const val LONG_MEDIA_MS = 20 * 60 * 1000L
