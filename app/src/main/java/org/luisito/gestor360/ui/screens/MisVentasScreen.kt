package org.luisito.gestor360.ui.screens

import androidx.compose.runtime.Composable

/**
 * "Mis Ventas" es la vista del vendedor sobre su propio inventario/turno:
 * reutiliza InventarioScreen, que ya restringe el selector de vendedores
 * y el cierre de turno a rol admin (ver esAdmin en InventarioScreen) y,
 * en el camino offline, ahora filtra correctamente por usuario cuando el
 * rol no es admin (ver InventarioRepository). Así evitamos duplicar toda
 * esa lógica en una segunda pantalla.
 */
@Composable
fun MisVentasScreen(
    androidId: String,
    onBack: () -> Unit,
    onVerHistorialTurnos: () -> Unit = {}
) {
    InventarioScreen(
        androidId = androidId,
        onBack = onBack,
        onVerHistorialTurnos = onVerHistorialTurnos,
        titulo = "Mis Ventas",
        mostrarBotonVentasRealizadas = false
    )
}
