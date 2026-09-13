package ir.tvgram.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens

/**
 * Everything interactive in this app is built from these few primitives rather
 * than from tv-material's components: D-pad focus is the only input, so what
 * matters is a consistent focus ring, a consistent scale-up, and nothing that
 * assumes a pointer.
 */

/** Icons are drawn as tinted images — no Material `Icon` dependency needed. */
@Composable
fun TvIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = TvGramColors.OnBackground,
    size: Dp = 24.dp,
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * A focusable surface: grows slightly and gains a bright outline when the D-pad
 * lands on it, which is the only reliable "you are here" cue on a TV.
 */
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(TvGramDimens.CardCorner),
    focusScale: Float = 1.06f,
    background: Color = TvGramColors.Surface,
    focusedBackground: Color = TvGramColors.SurfaceElevated,
    onFocusChanged: (Boolean) -> Unit = {},
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        label = "focus-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .background(if (focused) focusedBackground else background, shape)
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 0.dp,
                brush = SolidColor(
                    when {
                        focused -> TvGramColors.Focus
                        selected -> TvGramColors.Accent
                        else -> Color.Transparent
                    },
                ),
                shape = shape,
            ),
        content = { content(focused) },
    )
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        focusScale = 1.04f,
        background = TvGramColors.SurfaceElevated,
        focusedBackground = TvGramColors.Accent,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val contentColor = if (focused) TvGramColors.Background else TvGramColors.OnBackground
            leadingIcon?.let { TvIcon(it, null, tint = contentColor, size = 20.dp) }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) contentColor else TvGramColors.OnBackgroundMuted,
            )
        }
    }
}

/**
 * Text entry on a TV: focusing the field opens the platform's on-screen
 * keyboard, so the field itself only has to show state clearly.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(TvGramColors.SurfaceElevated, RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 3.dp else 1.dp,
                brush = SolidColor(if (focused) TvGramColors.Focus else TvGramColors.AccentMuted),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = TvGramColors.OnBackgroundMuted,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TvGramColors.OnBackground),
            cursorBrush = SolidColor(TvGramColors.Accent),
            singleLine = true,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
            ),
        )
    }
}

@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = TvGramColors.OnBackgroundMuted,
            )
            if (actionLabel != null && onAction != null) {
                TvButton(text = actionLabel, onClick = onAction, modifier = Modifier.wrapContentSize())
            }
        }
    }
}

/** A slim indeterminate bar; motion reads faster than a spinner across a room. */
@Composable
fun LoadingBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val position by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Restart),
        label = "loading-position",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(TvGramColors.SurfaceElevated),
    ) {
        val width = maxWidth
        Box(
            modifier = Modifier
                .offset(x = width * position)
                .width(width * 0.3f)
                .height(3.dp)
                .background(TvGramColors.Accent),
        )
    }
}

/**
 * A switch that looks like a switch.
 *
 * It replaced an "ON"/"OFF" label, which read as a value rather than something
 * you could change — the complaint being that settings rows gave no sign they
 * were controls at all. The knob slides, so a press has a visible result even
 * before the setting is written.
 */
@Composable
fun TvSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switch-knob",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .background(
                if (checked) TvGramColors.Accent else TvGramColors.SurfaceElevated,
                RoundedCornerShape(14.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(24.dp)
                .background(
                    if (checked) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                    RoundedCornerShape(12.dp),
                ),
        )
    }
}
