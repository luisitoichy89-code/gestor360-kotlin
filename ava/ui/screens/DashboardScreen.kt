package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.ui.theme.Azul
import org.luisito.gestor360.ui.theme.Morado
import org.luisito.gestor360.ui.theme.TarjetaCarpeta

private data class SeccionDashboard(val titulo: String, val icono: ImageVector, val ruta: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userRol: String = "",
    username: String = "",
    androidId: String = "",
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val esAdmin = userRol == "admin"

    val secciones = buildList {
        add(SeccionDashboard("Ventas", Icons.Default.PointOfSale, "ventas", Azul))
        add(SeccionDashboard("Productos", Icons.Default.Inventory2, "productos", Morado))
        add(SeccionDashboard("Inventario", Icons.Default.ReceiptLong, "inventario", Azul))
        if (esAdmin) {
            add(SeccionDashboard("Tarjetas", Icons.Default.CreditCard, "tarjetas", Morado))
            add(SeccionDashboard("Aprobaciones", Icons.Default.FactCheck, "aprobaciones", Azul))
            add(SeccionDashboard("Devolución", Icons.Default.AssignmentReturn, "devolucion", Morado))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (secciones.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay secciones disponibles")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(secciones) { seccion ->
                    TarjetaCarpeta(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(22.dp),
                        elevation = 3.dp,
                        accentColor = seccion.color,
                        accentThickness = 9.dp,
                        onClick = { onNavigate(seccion.ruta) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(seccion.color.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(seccion.icono, null, modifier = Modifier.size(26.dp), tint = seccion.color)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                seccion.titulo,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
