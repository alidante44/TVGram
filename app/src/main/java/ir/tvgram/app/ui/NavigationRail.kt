package ir.tvgram.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.TvIcon
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens

/** The three places the app can be, top to bottom, as asked for. */
enum class Destination(val route: String) {
    MEDIA("media"),
    CHATS("chats"),
    SETTINGS("settings"),
}

/**
 * A collapsed icon rail that expands to labels while it holds focus — the
 * pattern every TV user already knows, and the only one that does not steal
 * width from the grid when it is not in use.
 */
@Composable
fun NavigationRail(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) TvGramDimens.RailExpandedWidth else TvGramDimens.RailCollapsedWidth,
        label = "rail-width",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(TvGramColors.Surface)
            .padding(vertical = TvGramDimens.ScreenPaddingVertical, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Destination.entries.forEach { destination ->
            RailItem(
                destination = destination,
                selected = destination == selected,
                expanded = expanded,
                onFocusChanged = { focused -> if (focused) expanded = true },
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: Destination,
    selected: Boolean,
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        shape = RoundedCornerShape(10.dp),
        focusScale = 1f,
        background = if (selected) TvGramColors.SurfaceElevated else Color.Transparent,
        focusedBackground = TvGramColors.Accent,
        onFocusChanged = onFocusChanged,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val contentColor = when {
                focused -> TvGramColors.Background
                selected -> TvGramColors.OnBackground
                else -> TvGramColors.OnBackgroundMuted
            }
            TvIcon(destination.icon(), stringResource(destination.labelRes()), tint = contentColor, size = 28.dp)
            if (expanded) {
                Text(
                    text = stringResource(destination.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Destination.icon(): ImageVector = when (this) {
    Destination.MEDIA -> Icons.Filled.VideoLibrary
    Destination.CHATS -> Icons.AutoMirrored.Filled.Chat
    Destination.SETTINGS -> Icons.Filled.Settings
}

fun Destination.labelRes(): Int = when (this) {
    Destination.MEDIA -> R.string.nav_media
    Destination.CHATS -> R.string.nav_chats
    Destination.SETTINGS -> R.string.nav_settings
}
