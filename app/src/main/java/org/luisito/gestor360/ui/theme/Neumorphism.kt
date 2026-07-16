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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sombra neomórfica. Antes usaba SIEMPRE los mismos colores fijos
 * (blanco .4 / azul-marino .25) sin importar el tema — eso "por casualidad"
 * se veía bien en oscuro (el marino a .25 casi no contrasta sobre un fondo
 * casi negro) pero en claro el mismo marino sobre un fondo casi blanco
 * quedaba como un relieve tallado y muy marcado.
 *
 * Ahora, si no se pasan colores explícitos, se eligen según
 * MaterialTheme.colorScheme (detectando claro/oscuro por luminancia del
 * background) para que el claro use gris neutro muy suave y el oscuro
 * mantenga el look actual (que ya estaba bien).
 */
@Composable
fun Modifier.neuShadow(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 2.dp,
    blur: Dp = 6.dp,
    pressed: Boolean = false,
    lightColor: Color? = null,
    darkColor: Color? = null
): Modifier {
    val esClaro = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val resolvedLight = lightColor ?: if (esClaro) NeuLuz.copy(alpha = 0.55f) else NeuLuzDark.copy(alpha = 0.35f)
    val resolvedDark = darkColor ?: if (esClaro) NeuSombra.copy(alpha = 0.22f) else NeuSombraDark.copy(alpha = 0.3f)
    // En claro reducimos elevación y aumentamos un poco el blur para que la
    // sombra se difumine en vez de marcarse como un borde duro.
    val elevAjustada = if (esClaro) elevation * 0.6f else elevation
    val blurAjustado = if (esClaro) blur * 1.3f else blur

    return this.then(
        Modifier.drawBehind {
            val elevationPx = elevAjustada.toPx() * if (pressed) -1f else 1f
            val blurPx = blurAjustado.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            drawIntoCanvas { canvas ->
                val darkPaint = Paint().apply {
                    color = resolvedDark
                    asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.save()
                canvas.translate(elevationPx, elevationPx)
                canvas.drawPath(path, darkPaint)
                canvas.restore()

                val lightPaint = Paint().apply {
                    color = resolvedLight
                    asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.save()
                canvas.translate(-elevationPx, -elevationPx)
                canvas.drawPath(path, lightPaint)
                canvas.restore()
            }
        }
    )
}

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    elevation: Dp = 2.dp,
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
    elevation: Dp = 2.dp,
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

/**
 * Tarjeta moderna de dashboard: fondo suave con sombra neomórfica ligera y
 * un pequeño indicador de color (chip redondeado) en la esquina superior
 * derecha — reemplaza al borde grueso en L de la versión anterior, que se
 * veía recargado. El color de acento sigue siendo configurable (Morado por
 * defecto) y es puramente decorativo.
 */
@Composable
fun TarjetaCarpeta(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    accentColor: Color = Morado,
    accentThickness: Dp = 6.dp,
    elevation: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .neuShadow(shape = shape, elevation = elevation)
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
        // Chip de color en la esquina, sutil, en vez del borde en L.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .size(accentThickness)
                .clip(CircleShape)
                .background(accentColor)
        )
        Column(content = content)
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
    elevation: Dp = 2.dp,
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
