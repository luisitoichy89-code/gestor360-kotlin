package org.luisito.gestor360.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GestorColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = SurfaceWhite,
    background = BackgroundLight,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    error = ErrorRed,
    onError = SurfaceWhite,
    outline = BorderGray,
)

@Composable
fun Gestor360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GestorColorScheme,
        content = content
    )
}
