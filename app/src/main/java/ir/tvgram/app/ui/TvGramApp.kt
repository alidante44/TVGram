package ir.tvgram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tvgram.app.R
import ir.tvgram.app.settings.RailSide
import ir.tvgram.app.ui.chats.ChatsScreen
import ir.tvgram.app.ui.common.CenteredMessage
import ir.tvgram.app.ui.login.CredentialsScreen
import ir.tvgram.app.ui.login.LoginScreen
import ir.tvgram.app.ui.media.MediaScreen
import ir.tvgram.app.ui.photo.PhotoViewerScreen
import ir.tvgram.app.ui.player.PlayerScreen
import ir.tvgram.app.ui.settings.SettingsScreen
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.telegram.model.AuthState
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgMediaItem

/** Fullscreen surfaces that take over the whole screen when open. */
private sealed interface Overlay {
    data object None : Overlay
    data object Player : Overlay
    data object Photo : Overlay
}

@Composable
fun TvGramApp(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvGramColors.Background),
    ) {
        when (val state = authState) {
            is AuthState.NeedsCredentials -> CredentialsScreen(onSave = viewModel::saveCredentials)

            is AuthState.Initializing -> CenteredMessage(text = stringResource(R.string.login_connecting))

            is AuthState.Ready -> MainShell(
                railSide = settings.railSide,
                connectionState = connectionState,
            )

            else -> LoginScreen(state = state)
        }
    }
}

@Composable
private fun MainShell(railSide: RailSide, connectionState: ConnectionState) {
    var destination by rememberSaveable { mutableStateOf(Destination.MEDIA) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }

    val openMedia: (TgMediaItem) -> Unit = { item ->
        overlay = if (item.kind == MediaKind.PHOTO) Overlay.Photo else Overlay.Player
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            val railFirst = railSide.leadsRow(LocalLayoutDirection.current)

            if (railFirst) {
                NavigationRail(selected = destination, onSelect = { destination = it })
            }

            Box(modifier = Modifier.weight(1f)) {
                when (destination) {
                    Destination.MEDIA -> MediaScreen(
                        onPlay = openMedia,
                        onShowPhoto = openMedia,
                    )

                    Destination.CHATS -> ChatsScreen(onOpenMedia = openMedia)
                    Destination.SETTINGS -> SettingsScreen()
                }
            }

            if (!railFirst) {
                NavigationRail(selected = destination, onSelect = { destination = it })
            }
        }

        // A TV box on a flaky Wi-Fi link silently stops loading; say so rather
        // than leaving the viewer looking at a grid that never fills.
        ConnectionBanner(
            state = connectionState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        when (overlay) {
            Overlay.Player -> PlayerScreen(onClose = { overlay = Overlay.None })
            Overlay.Photo -> PhotoViewerScreen(onClose = { overlay = Overlay.None })
            Overlay.None -> Unit
        }
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, modifier: Modifier = Modifier) {
    val message = when (state) {
        ConnectionState.WAITING_FOR_NETWORK -> stringResource(R.string.error_no_connection)
        ConnectionState.CONNECTING, ConnectionState.CONNECTING_TO_PROXY ->
            stringResource(R.string.state_connecting)

        ConnectionState.UPDATING -> stringResource(R.string.state_updating)
        ConnectionState.READY -> null
    } ?: return

    Box(
        modifier = modifier
            .padding(12.dp)
            .background(TvGramColors.SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = TvGramColors.OnBackgroundMuted,
        )
    }
}

/**
 * "Left" means the physical left edge, whichever way the text runs — a Persian
 * UI still puts the rail where the user asked for it.
 */
private fun RailSide.leadsRow(direction: LayoutDirection): Boolean = when (this) {
    RailSide.START -> true
    RailSide.LEFT -> direction == LayoutDirection.Ltr
    RailSide.RIGHT -> direction == LayoutDirection.Rtl
}
