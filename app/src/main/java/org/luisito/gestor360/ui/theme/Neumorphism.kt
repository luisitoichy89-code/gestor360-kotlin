package org.luisito.gestor360.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asFrameworkPaint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.Role

/**
 * Estilo visual neomórfico (soft UI) para Gestor360°.
 *
 * No cambia paleta, tipografía ni lógica: solo agrega una doble sombra
 * (una clara arriba-izquierda, una oscura abajo-derecha) sobre el mismo
 * fondo, para que tarjetas y botones parezcan tallados/elevados del fondo
 * en lugar de usar bordes o Material elevation plano.
 *
 * `pressed = true` invierte las sombras para dar un efecto hundido
 * (usado en campos de texto o estados presionados).
 */
fun Modifier.neuShadow(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 6.dp,
    blur: Dp = 12.dp,
    pressed: Boolean = false,
    lightColor: Color = NeuLuz,
    darkColor: Color = NeuSombra
): Modifier = this.drawBehind {
    val elevationPx = elevation.toPx() * if (pressed) -1f else 1f
    val blurPx = blur.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }

    drawIntoCanvas { canvas ->
        val darkPaint = Paint().apply {
            color = darkColor
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(elevationPx, elevationPx)
        canvas.drawPath(path, darkPaint)
        canvas.restore()

        val lightPaint = Paint().apply {
            color = lightColor
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(-elevationPx, -elevationPx)
        canvas.drawPath(path, lightPaint)
        canvas.restore()
    }
}

/** Reemplazo directo de `Card` / `ElevatedCard`: misma forma de uso, look neomórfico. */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    elevation: Dp = 6.dp,
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

/** Reemplazo directo de `Button`: pastilla elevada con sombra doble en vez de Material elevation. */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    elevation: Dp = 6.dp,
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
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

/** Reemplazo directo de `OutlinedButton`: mismo tamaño/uso, pero hundido (pressed) en vez de con borde. */
@Composable
fun NeuOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    elevation: Dp = 4.dp,
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
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}
