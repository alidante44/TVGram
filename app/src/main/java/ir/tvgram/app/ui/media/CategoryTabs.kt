package ir.tvgram.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.telegram.model.MediaCategory

/** The five buckets across the top: All, Videos, Photos, Audio, Other. */
@Composable
fun CategoryTabs(
    selected: MediaCategory,
    onSelect: (MediaCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CATEGORY_ORDER.forEach { category ->
            val isSelected = category == selected
            FocusableSurface(
                onClick = { onSelect(category) },
                selected = isSelected,
                shape = RoundedCornerShape(8.dp),
                focusScale = 1.03f,
                background = if (isSelected) TvGramColors.AccentMuted else Color.Transparent,
                focusedBackground = TvGramColors.Accent,
            ) { focused ->
                Box(modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(category.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            focused -> TvGramColors.Background
                            isSelected -> TvGramColors.OnBackground
                            else -> TvGramColors.OnBackgroundMuted
                        },
                    )
                }
            }
        }
    }
}

/** Order shown on screen: everything first, then most-watched types. */
val CATEGORY_ORDER: List<MediaCategory> = listOf(
    MediaCategory.ALL,
    MediaCategory.VIDEO,
    MediaCategory.PHOTO,
    MediaCategory.AUDIO,
    MediaCategory.OTHER,
)

fun MediaCategory.labelRes(): Int = when (this) {
    MediaCategory.ALL -> R.string.category_all
    MediaCategory.VIDEO -> R.string.category_video
    MediaCategory.PHOTO -> R.string.category_photo
    MediaCategory.AUDIO -> R.string.category_audio
    MediaCategory.OTHER -> R.string.category_other
}
