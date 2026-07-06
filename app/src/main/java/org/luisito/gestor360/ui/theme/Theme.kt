package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AzulCorporativo = Color(0xFF1E3A8A)
private val AzulSecundario = Color(0xFF2563EB)
private val AzulClaro = Color(0xFFEAF2FF)
private val Gris900 = Color(0xFF111827)
private val Gris800 = Color(0xFF1F2937)
private val Gris700 = Color(0xFF374151)
private val Gris500 = Color(0xFF6B7280)
private val Gris200 = Color(0xFFE5E7EB)
private val Gris100 = Color(0xFFF3F4F6)
private val Gris50 = Color(0xFFF9FAFB)
private val Blanco = Color(0xFFFFFFFF)
private val VerdeExito = Color(0xFF16A34A)
private val RojoError = Color(0xFFDC2626)

private val LightColorScheme = lightColorScheme(
    primary = AzulCorporativo,
    onPrimary = Blanco,
    primaryContainer = AzulClaro,
    onPrimaryContainer = AzulCorporativo,
    secondary = Gris700,
    onSecondary = Blanco,
    tertiary = AzulSecundario,
    onTertiary = Blanco,
    background = Gris50,
    onBackground = Gris900,
    surface = Blanco,
    onSurface = Gris900,
    surfaceVariant = Gris100,
    onSurfaceVariant = Gris700,
    error = RojoError,
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
