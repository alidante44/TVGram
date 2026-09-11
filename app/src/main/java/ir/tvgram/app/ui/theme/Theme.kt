package ir.tvgram.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/**
 * Colours are hand-picked for a 10-foot, mostly-dark screen: a near-black
 * ground so letterboxed video blends in, and a single saturated accent that
 * survives the aggressive contrast processing TVs apply.
 */
object TvGramColors {
    val Background = Color(0xFF07090E)
    val Surface = Color(0xFF11151F)
    val SurfaceElevated = Color(0xFF1A2030)
    val Accent = Color(0xFF3FA9F5)
    val AccentMuted = Color(0xFF1E4C74)
    val OnBackground = Color(0xFFECEFF4)
    val OnBackgroundMuted = Color(0xFF98A2B3)
    val Focus = Color(0xFFFFFFFF)
    val Danger = Color(0xFFF2555A)
    val Scrim = Color(0xCC05070B)
}

/** Overscan-safe insets. TV panels routinely crop the outer few percent. */
object TvGramDimens {
    val ScreenPaddingHorizontal = 48.dp
    val ScreenPaddingVertical = 27.dp
    val RailCollapsedWidth = 88.dp
    val RailExpandedWidth = 260.dp
    val CardCorner = 12.dp
    val GridSpacing = 16.dp
}

private val TvTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp),
)

private val TvColorScheme = darkColorScheme(
    primary = TvGramColors.Accent,
    onPrimary = TvGramColors.Background,
    secondary = TvGramColors.AccentMuted,
    background = TvGramColors.Background,
    onBackground = TvGramColors.OnBackground,
    surface = TvGramColors.Surface,
    onSurface = TvGramColors.OnBackground,
    surfaceVariant = TvGramColors.SurfaceElevated,
    error = TvGramColors.Danger,
)

@Composable
fun TvGramTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        typography = TvTypography,
        content = content,
    )
}
