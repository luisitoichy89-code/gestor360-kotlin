package org.luisito.gestor360.ui.screens

import androidx.compose.runtime.Composable

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
        mostrarBotonVentasRealizadas = false,
        esVistaPersonal = true
    )
}
