package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

// Tema de Gestor360°: fondo claro u oscuro según elección del usuario,
// texto siempre negro/blanco (nunca gris puro) y dos acentos con función
// fija — azul para acciones, rojo para cancelar/eliminar. Nada de blanco
// ni verde como color de texto o de marca en el tema claro.
private val LightColorScheme = lightColorScheme(
    primary = Azul,
    onPrimary = TextBlack,
    primaryContainer = AzulClaro,
    onPrimaryContainer = TextBlack,

    secondary = AzulOscuro,
    onSecondary = TextBlack,
    secondaryContainer = AzulClaro,
    onSecondaryContainer = TextBlack,

    tertiary = AzulOscuro,
    onTertiary = TextBlack,
    tertiaryContainer = AzulClaro,
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

private val DarkColorScheme = darkColorScheme(
    primary = Azul,
    onPrimary = TextWhite,
    primaryContainer = AzulClaroDark,
    onPrimaryContainer = TextWhite,

    secondary = AzulOscuro,
    onSecondary = TextWhite,
    secondaryContainer = AzulClaroDark,
    onSecondaryContainer = TextWhite,

    tertiary = AzulOscuro,
    onTertiary = TextWhite,
    tertiaryContainer = AzulClaroDark,
    onTertiaryContainer = TextWhite,

    error = Rojo,
    onError = TextWhite,
    errorContainer = RojoClaroDark,
    onErrorContainer = TextWhite,

    background = BackgroundDark,
    onBackground = TextWhite,

    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextGrayDark,

    outline = OutlineDark
)

/**
 * `darkTheme` se le pasa desde afuera (MainActivity, leyendo
 * ThemeManager.observarTemaOscuro) en vez de usar isSystemInDarkTheme(),
 * porque la elección la hace el usuario a mano con el botón ☀️/🌙 de la
 * barra superior, no el sistema operativo.
 *
 * Nota: las sombras neomórficas (neuShadow, ver Neumorphism.kt) siguen
 * usando NeuLuz/NeuSombra fijos aunque el tema sea oscuro — el relieve se
 * nota menos en modo oscuro, pero fondo/superficies/texto sí cambian
 * correctamente. Si se quiere el relieve neomórfico completo en oscuro,
 * hay que pasar NeuLuzDark/NeuSombraDark a los `neuShadow(...)` de las
 * pantallas, que ya están definidos en Color.kt.
 */
@Composable
fun Gestor360Theme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content
    )
}
