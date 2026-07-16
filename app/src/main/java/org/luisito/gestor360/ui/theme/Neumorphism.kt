package org.luisito.gestor360.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Versión ligera del neomorfismo. Sombras a la mitad de intensidad,
 * bordes más suaves. Login usa intensidad aún menor.
 */

fun Modifier.neuShadow(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 3.dp,
    blur: Dp = 8.dp,
    pressed: Boolean = false,
    lightColor: Color = NeuLuz.copy(alpha = 0.5f),
    darkColor: Color = NeuSombra.copy(alpha = 0.5f),
    lightIntensity: Float = 1f
): Modifier = this.drawBehind {
    val elevationPx = elevation.toPx() * lightIntensity * if (pressed) -1f else 1f
    val blurPx = blur.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    drawIntoCanvas { canvas ->
        val darkPaint = Paint().apply {
            color = darkColor.copy(alpha = darkColor.alpha * lightIntensity)
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(elevationPx, elevationPx)
        canvas.drawPath(path, darkPaint)
        canvas.restore()

        val lightPaint = Paint().apply {
            color = lightColor.copy(alpha = lightColor.alpha * lightIntensity)
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(-elevationPx, -elevationPx)
        canvas.drawPath(path, lightPaint)
        canvas.restore()
    }
}

fun Modifier.neuShadowLogin(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 2.dp,
    blur: Dp = 6.dp,
    pressed: Boolean = false
): Modifier = this.neuShadow(
    shape = shape,
    elevation = elevation,
    blur = blur,
    pressed = pressed,
    lightColor = NeuLuz.copy(alpha = 0.25f),
    darkColor = NeuSombra.copy(alpha = 0.25f),
    lightIntensity = 0.5f
)

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    elevation: Dp = 3.dp,
    pressed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .neuShadow(shape = shape, elevation = elevation, pressed = pressed)
            .clip(shape)
            .background(containerColor)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Column(content = content)
    }
}

@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    elevation: Dp = 3.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = modifier
            .neuShadow(shape = shape, elevation = elevation)
            .clip(shape)
            .background(containerColor.copy(alpha = alpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

@Composable
fun NeuOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    elevation: Dp = 3.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = modifier
            .neuShadow(shape = shape, elevation = elevation, pressed = true)
            .clip(shape)
            .background(containerColor.copy(alpha = alpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}
