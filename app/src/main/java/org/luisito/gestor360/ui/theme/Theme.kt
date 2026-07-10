package org.luisito.gestor360.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Verde clarito: fondo general más suave, relleno (tarjetas/botones) un
// escalón más fuerte para que se distinga del fondo sin perder legibilidad.
private val VerdeFondo = Color(0xFFEAF7EC)
private val VerdeRelleno = Color(0xFFBFE6C4)
private val VerdeBoton = Color(0xFFA3DBA9)
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
    primary = VerdeBoton,
    onPrimary = Gris900,
    primaryContainer = VerdeRelleno,
    onPrimaryContainer = Gris900,
    secondary = Gris700,
    onSecondary = Blanco,
    tertiary = VerdeBoton,
    onTertiary = Gris900,
    background = VerdeFondo,
    onBackground = Gris900,
    surface = Blanco,
    onSurface = Gris900,
    surfaceVariant = VerdeRelleno,
    onSurfaceVariant = Gris900,
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
