package ir.tvgram.app.ui.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens

/**
 * Fullscreen photo viewing. Left and right walk through the photos of the same
 * chat, so browsing an album is one key held down rather than a trip back to
 * the grid for every picture.
 */
@Composable
fun PhotoViewerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var captionVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onClose(); true
                    }

                    Key.DirectionLeft -> {
                        viewModel.previous(); true
                    }

                    Key.DirectionRight -> {
                        viewModel.next(); true
                    }

                    Key.DirectionCenter, Key.Enter -> {
                        captionVisible = !captionVisible; true
                    }

                    else -> false
                }
            },
    ) {
        state.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = state.item?.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (state.isLoading) {
            LoadingBar(modifier = Modifier.align(Alignment.TopCenter))
        }

        state.error?.let { error ->
            Text(
                text = stringResource(R.string.error_generic, error),
                style = MaterialTheme.typography.titleMedium,
                color = TvGramColors.Danger,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (captionVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(
                        horizontal = TvGramDimens.ScreenPaddingHorizontal,
                        vertical = 20.dp,
                    ),
            ) {
                Text(
                    text = state.item?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TvGramColors.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.counter,
                    style = MaterialTheme.typography.labelSmall,
                    color = TvGramColors.OnBackgroundMuted,
                )
            }
        }
    }
}
