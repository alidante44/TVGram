package ir.tvgram.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import ir.tvgram.app.ui.common.TvButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ir.tvgram.app.ui.proxy.ProxyScreen
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
    val proxyLink by viewModel.proxyLink.collectAsStateWithLifecycle()

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
                    proxyLink = proxyLink,
                )

                else -> LoginScreen(state = state)
            }
        }
    }
}

@Composable
private fun MainShell(
    railSide: RailSide,
    connectionState: ConnectionState,
    proxyLink: String?,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.MEDIA) }
    // Films, songs and photos all open the same player window, so there is one
    // place to learn rather than three.
    var playerOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    val openMedia: (TgMediaItem) -> Unit = { playerOpen = true }

    // A proxy link opened from outside should land somewhere the viewer can see
    // whether it worked, so it brings the proxy screen up by itself.
    LaunchedEffect(proxyLink) {
        if (proxyLink != null) destination = Destination.PROXY
    }

    // Back steps home first, and only asks about leaving once there is nowhere
    // left to step back to — a remote's back key is easy to press by accident,
    // and dropping out of the app mid-film is a poor way to find that out.
    BackHandler(enabled = !playerOpen && !confirmExit) {
        if (destination != Destination.MEDIA) destination = Destination.MEDIA else confirmExit = true
    }

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
                    Destination.PROXY -> ProxyScreen()
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

        if (confirmExit) {
            ExitDialog(onDismiss = { confirmExit = false })
        }
    }
}

/** The one thing standing between a stray back press and a closed app. */
@Composable
private fun ExitDialog(onDismiss: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(TvGramColors.Surface, RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.exit_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                color = TvGramColors.OnBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = stringResource(R.string.exit_confirm_stay),
                    onClick = onDismiss,
                )
                TvButton(
                    text = stringResource(R.string.exit_confirm_leave),
                    onClick = { activity?.finish() },
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
