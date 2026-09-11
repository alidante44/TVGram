package ir.tvgram.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.TelegramImage
import ir.tvgram.app.ui.common.TvIcon
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.util.Format
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgMediaItem

/**
 * One tile in the grid. The thumbnail carries the information; the text under
 * it is deliberately small so a wall of tiles still reads as pictures.
 */
@Composable
fun MediaCard(
    item: TgMediaItem,
    persianDates: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        background = TvGramColors.Surface,
        focusedBackground = TvGramColors.SurfaceElevated,
    ) { focused ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                TelegramImage(
                    fileId = item.thumbnailFileId,
                    minithumbnail = item.minithumbnail,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )

                // Audio and documents rarely have a thumbnail, so give them a
                // recognisable glyph instead of an empty rectangle.
                if (item.thumbnailFileId == null && item.minithumbnail == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TvIcon(
                            imageVector = item.kind.glyph(),
                            contentDescription = null,
                            tint = TvGramColors.OnBackgroundMuted,
                            size = 44.dp,
                        )
                    }
                }

                if (focused && item.kind.isPlayable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvIcon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.player_play),
                            tint = TvGramColors.Focus,
                            size = 48.dp,
                        )
                    }
                }

                val badge = when {
                    item.durationSeconds > 0 -> Format.duration(item.durationSeconds)
                    item.fileSize > 0 -> Format.fileSize(item.fileSize)
                    else -> ""
                }
                if (badge.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = TvGramColors.OnBackground,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.media_unnamed) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvGramColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Format.date(item.date, persianDates),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvGramColors.OnBackgroundMuted,
                        maxLines = 1,
                    )
                    item.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = TvGramColors.OnBackgroundMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun MediaKind.glyph() = when (this) {
    MediaKind.VIDEO, MediaKind.ANIMATION -> Icons.Filled.PlayArrow
    MediaKind.PHOTO -> Icons.Filled.Image
    MediaKind.AUDIO -> Icons.Filled.MusicNote
    MediaKind.VOICE -> Icons.Filled.GraphicEq
    MediaKind.DOCUMENT -> Icons.Filled.Description
}
