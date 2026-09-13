package ir.tvgram.app.ui.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.TgProxy

/**
 * Proxies on their own screen rather than buried in settings.
 *
 * A proxy is the thing you reach for when nothing is loading, and hunting for
 * it three screens deep is exactly the wrong moment to be scrolling. The switch
 * at the top turns the current one off and on in one press.
 */
@Composable
fun ProxyScreen(
    modifier: Modifier = Modifier,
    viewModel: ProxyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    var link by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = TvGramDimens.ScreenPaddingHorizontal,
            vertical = TvGramDimens.ScreenPaddingVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "status") {
            StatusCard(active = state.active, connection = connection)
        }

        if (state.isBusy) {
            item(key = "busy") { LoadingBar() }
        }

        item(key = "link") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.proxy_paste_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = TvGramColors.OnBackgroundMuted,
                )
                TvTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        viewModel.dismissBadLink()
                    },
                    placeholder = "tg://proxy?server=…&port=…&secret=…",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.badLink) {
                    Text(
                        text = stringResource(R.string.proxy_link_bad),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvGramColors.Danger,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = stringResource(R.string.proxy_connect),
                        enabled = link.isNotBlank(),
                        onClick = {
                            viewModel.addFromLink(link)
                            link = ""
                        },
                    )
                    TvButton(
                        text = stringResource(R.string.settings_proxy_disable),
                        enabled = state.active != null,
                        onClick = viewModel::disable,
                    )
                }
            }
        }

        if (state.proxies.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.proxy_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvGramColors.OnBackgroundMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        items(state.proxies, key = { it.id }) { proxy ->
            ProxyRow(
                proxy = proxy,
                onSelect = { viewModel.enable(proxy.id) },
                onRemove = { viewModel.remove(proxy.id) },
            )
        }
    }
}

/** Says in one line whether traffic is going through a proxy and whether it works. */
@Composable
private fun StatusCard(active: TgProxy?, connection: ConnectionState) {
    val working = connection == ConnectionState.READY || connection == ConnectionState.UPDATING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvGramColors.SurfaceElevated, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = active?.label ?: stringResource(R.string.settings_proxy_none),
            style = MaterialTheme.typography.titleLarge,
            color = TvGramColors.OnBackground,
        )
        Text(
            text = stringResource(
                when {
                    active == null && working -> R.string.proxy_status_direct
                    working -> R.string.proxy_status_working
                    else -> R.string.proxy_status_failing
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (working) TvGramColors.OnBackgroundMuted else TvGramColors.Danger,
        )
    }
}

@Composable
private fun ProxyRow(proxy: TgProxy, onSelect: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusableSurface(
            onClick = onSelect,
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(10.dp),
            focusScale = 1f,
            background = if (proxy.isEnabled) TvGramColors.AccentMuted else TvGramColors.Surface,
            focusedBackground = TvGramColors.Accent,
        ) { focused ->
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = proxy.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) TvGramColors.Background else TvGramColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FocusableSurface(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            focusScale = 1f,
            background = TvGramColors.Surface,
            focusedBackground = TvGramColors.Danger,
        ) { focused ->
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_proxy_remove),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
