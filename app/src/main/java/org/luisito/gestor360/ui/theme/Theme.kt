package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GrisOscuro = Color(0xFF1A1A1A)
private val GrisMedio = Color(0xFF333333)
private val GrisClaro = Color(0xFFF5F5F5)
private val Blanco = Color(0xFFFFFFFF)
private val Acento = Color(0xFFE53935)

private val LightColorScheme = lightColorScheme(
    primary = GrisOscuro,
    onPrimary = Blanco,
    primaryContainer = GrisClaro,
    onPrimaryContainer = GrisOscuro,
    secondary = GrisMedio,
    onSecondary = Blanco,
    background = Blanco,
    onBackground = GrisOscuro,
    surface = Blanco,
    onSurface = GrisOscuro,
    surfaceVariant = GrisClaro,
    onSurfaceVariant = GrisMedio,
    error = Color(0xFFD32F2F),
    onError = Blanco,
    tertiary = Acento,
    onTertiary = Blanco
)

@Composable
fun Gestor360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
