package ir.tvgram.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.telegram.model.TgLiveStream

/**
 * The live broadcast, full screen.
 *
 * A live stream has no length and no position, so it gets none of the ordinary
 * player's controls — there is nothing to seek to. Close is the whole interface.
 */
@Composable
fun LiveOverlay(
    stream: TgLiveStream,
    onClose: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(stream.groupCallId) {
        viewModel.open(stream)
        onDispose { viewModel.close() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
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

            when {
                state.failure != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(48.dp),
                ) {
                    Text(
                        text = stringResource(state.failure!!.messageRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvGramColors.OnBackground,
                    )
                    TvButton(text = stringResource(R.string.action_close), onClick = onClose)
                }

                state.isJoining || state.segmentsPlayed == 0 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.live_connecting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvGramColors.OnBackground,
                    )
                    LoadingBar(modifier = Modifier.fillMaxWidth(0.4f))
                }
            }
        }
    }
}

private fun LiveFailure.messageRes(): Int = when (this) {
    LiveFailure.JOIN_REFUSED -> R.string.live_join_refused
    LiveFailure.NO_CHANNELS -> R.string.live_no_channels
    LiveFailure.NO_SEGMENTS -> R.string.live_no_segments
}
