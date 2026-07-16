package org.luisito.gestor360.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta Gestor360 — fondo claro tipo tarjeta, texto negro, y dos colores de
// acento con función fija en toda la app: azul para acciones, rojo para
// cancelar/eliminar. No se usa blanco ni verde como color de texto.
val BackgroundLight = Color(0xFFF5F5F7)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceVariantGray = Color(0xFFEFEFF1)

val TextBlack = Color(0xFF1A1A1A)
val TextGray = Color(0xFF5F5F5F)
val BorderGray = Color(0xFFDADCE0)
val Outline = Color(0xFF9AA0A6)

val Azul = Color(0xFF4A7FB5)
val AzulOscuro = Color(0xFF2C5680)
val AzulClaro = Color(0xFFCFE0F0)

val Rojo = Color(0xFFD32F2F)
val RojoOscuro = Color(0xFFB71C1C)
val RojoClaro = Color(0xFFFFCDD2)

// Acento morado: SOLO decorativo (línea/borde en L de las tarjetas del
// dashboard). No reemplaza a Azul/Rojo como color funcional.
val Morado = Color(0xFF7C5CD6)
val MoradoOscuro = Color(0xFF5B3FB0)
val MoradoClaro = Color(0xFFEDE7FB)

// Sombras del efecto neomórfico (soft UI): misma base BackgroundLight,
// una sombra clara (arriba-izquierda) y una oscura (abajo-derecha) para dar
// sensación de relieve o hundimiento sin cambiar la paleta ni el tema.
// En modo CLARO el contraste fondo/sombra es alto (fondo casi blanco), así
// que la sombra debe ser gris neutro y muy transparente o se ve "tallada".
val NeuLuz = Color(0xFFFFFFFF)
val NeuSombra = Color(0xFFB9BEC7)

// ============================================================
// Variante oscura — mismos roles de color (azul = acciones, rojo =
// cancelar/eliminar), invertidos para fondo oscuro y texto claro.
// ============================================================
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceVariantDark = Color(0xFF2A2A2D)

val TextWhite = Color(0xFFF2F2F2)
val TextGrayDark = Color(0xFFAEAEB2)
val OutlineDark = Color(0xFF6E6E73)

val AzulClaroDark = Color(0xFF25384A)
val RojoClaroDark = Color(0xFF4A2323)

val NeuLuzDark = Color(0xFF2E2E31)
val NeuSombraDark = Color(0xFF000000)
