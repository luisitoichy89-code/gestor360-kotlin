package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AzulAccion,
    onPrimary = SurfaceWhite,
    primaryContainer = AzulClaro,
    onPrimaryContainer = AzulOscuro,

    secondary = AzulOscuro,
    onSecondary = SurfaceWhite,
    secondaryContainer = AzulClaro,
    onSecondaryContainer = AzulOscuro,

    tertiary = AzulOscuro,
    onTertiary = SurfaceWhite,
    tertiaryContainer = AzulClaro,
    onTertiaryContainer = AzulOscuro,

    background = BackgroundLight,
    onBackground = TextBlack,

    surface = SurfaceWhite,
    onSurface = TextBlack,
    surfaceVariant = SurfaceVariantGray,
    onSurfaceVariant = TextBlack,

    outline = BorderGray,
    outlineVariant = BorderGray,

    error = Rojo,
    onError = SurfaceWhite,
    errorContainer = RojoClaro,
    onErrorContainer = RojoOscuro
)

@Composable
fun Gestor360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
