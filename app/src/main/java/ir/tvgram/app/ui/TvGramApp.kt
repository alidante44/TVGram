package ir.tvgram.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import ir.tvgram.app.ui.lock.LockScreen
import ir.tvgram.app.ui.login.CredentialsScreen
import ir.tvgram.app.ui.login.LoginScreen
import ir.tvgram.app.ui.media.MediaScreen
import ir.tvgram.app.ui.player.MediaPlayerOverlay
import ir.tvgram.app.ui.settings.SettingsScreen
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.telegram.model.AuthState
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.TgMediaItem

@Composable
fun TvGramApp(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()

    // Deliberately not rememberSaveable: if the activity is recreated the
    // passcode is asked for again, which is the whole point of it.
    var unlocked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvGramColors.Background),
    ) {
        when {
            isLocked == null -> CenteredMessage(text = stringResource(R.string.login_connecting))

            // The lock comes before the account, not after it.
            isLocked == true && !unlocked -> LockScreen(onUnlocked = { unlocked = true })

            else -> when (val state = authState) {
                is AuthState.NeedsCredentials -> CredentialsScreen(onSave = viewModel::saveCredentials)

                is AuthState.Initializing ->
                    CenteredMessage(text = stringResource(R.string.login_connecting))

                is AuthState.Ready -> MainShell(
                    railSide = settings.railSide,
                    connectionState = connectionState,
                )

                else -> LoginScreen(state = state)
            }
        }
    }
}

@Composable
private fun MainShell(railSide: RailSide, connectionState: ConnectionState) {
    var destination by rememberSaveable { mutableStateOf(Destination.MEDIA) }
    // Films, songs and photos all open the same player window, so there is one
    // place to learn rather than three.
    var playerOpen by remember { mutableStateOf(false) }

    val openMedia: (TgMediaItem) -> Unit = { playerOpen = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            val railFirst = railSide.leadsRow(LocalLayoutDirection.current)

            if (railFirst) {
                NavigationRail(selected = destination, onSelect = { destination = it })
            }

            Box(modifier = Modifier.weight(1f)) {
                when (destination) {
                    Destination.MEDIA -> MediaScreen(onOpen = openMedia)

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

        if (playerOpen) {
            MediaPlayerOverlay(onClose = { playerOpen = false })
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
