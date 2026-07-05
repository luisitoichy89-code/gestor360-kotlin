package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Naranja = Color(0xFFFF6600)
private val NaranjaOscuro = Color(0xFFCC5500)
private val Blanco = Color(0xFFFFFFFF)
private val GrisClaro = Color(0xFFF5F5F5)
private val GrisOscuro = Color(0xFF333333)

private val LightColorScheme = lightColorScheme(
    primary = Naranja,
    onPrimary = Blanco,
    primaryContainer = Naranja.copy(alpha = 0.15f),
    onPrimaryContainer = NaranjaOscuro,
    secondary = NaranjaOscuro,
    onSecondary = Blanco,
    background = GrisClaro,
    onBackground = GrisOscuro,
    surface = Blanco,
    onSurface = GrisOscuro,
    surfaceVariant = GrisClaro,
    onSurfaceVariant = GrisOscuro.copy(alpha = 0.7f),
    error = Color(0xFFD32F2F),
    onError = Blanco
)

@Composable
fun Gestor360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
