package com.dockstandby.ui.standby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp

/**
 * Text whose characters roll (slide up + fade) individually when they change, like a flip clock.
 * [colorAt] can tint single characters (e.g. the Minimal clock's orange colon). A [brush] is
 * painted across the whole row afterwards, so gradients stay continuous across digits.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    colorAt: (index: Int, ch: Char) -> Color? = { _, _ -> null },
) {
    val rowModifier = if (brush == null) modifier else modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush, blendMode = BlendMode.SrcIn)
        }
    // Letter spacing only applies between characters inside one Text, so apply it between the
    // per-character Texts instead (negative values pull digits together like the mockup).
    val gap = with(LocalDensity.current) { style.letterSpacing.toDp() }
    Row(rowModifier.clipToBounds(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        text.forEachIndexed { i, ch ->
            // Keyed by position so only the digits that change animate.
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically(tween(450)) { it / 2 } + fadeIn(tween(450))) togetherWith
                        (slideOutVertically(tween(450)) { -it / 2 } + fadeOut(tween(300)))
                },
                label = "digit$i",
            ) { c ->
                Text(
                    c.toString(),
                    style = style.copy(color = colorAt(i, c) ?: if (brush != null) Color.White else style.color, letterSpacing = 0.sp),
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}
