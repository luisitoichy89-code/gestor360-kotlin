package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

// Tema único de Gestor360°: fondo claro, texto siempre negro, y dos acentos
// con función fija — naranja para acciones, rojo para cancelar/eliminar.
// Nada de blanco ni verde como color de texto o de marca.
private val LightColorScheme = lightColorScheme(
    primary = Naranja,
    onPrimary = TextBlack,
    primaryContainer = NaranjaClaro,
    onPrimaryContainer = TextBlack,

    secondary = NaranjaOscuro,
    onSecondary = TextBlack,
    secondaryContainer = NaranjaClaro,
    onSecondaryContainer = TextBlack,

    tertiary = NaranjaOscuro,
    onTertiary = TextBlack,
    tertiaryContainer = NaranjaClaro,
    onTertiaryContainer = TextBlack,

    error = Rojo,
    onError = TextBlack,
    errorContainer = RojoClaro,
    onErrorContainer = TextBlack,

    background = BackgroundLight,
    onBackground = TextBlack,

    surface = SurfaceWhite,
    onSurface = TextBlack,
    surfaceVariant = SurfaceVariantGray,
    onSurfaceVariant = TextGray,

    outline = Outline
)

@Composable
fun Gestor360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
