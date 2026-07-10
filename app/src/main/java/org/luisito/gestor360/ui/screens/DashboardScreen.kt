package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.data.repository.TicketRepository

private data class SeccionDashboard(val titulo: String, val icono: ImageVector, val ruta: String)

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
    var mensajesSinLeer by remember { mutableStateOf(0L) }

    // Se refresca cada vez que entras al Dashboard (ej. al volver de Soporte
    // después de marcar los mensajes como leídos).
    LaunchedEffect(androidId) {
        if (esAdmin && androidId.isNotBlank()) {
            TicketRepository().contarNoLeidos(androidId).onSuccess { mensajesSinLeer = it }
        }
    }

    val secciones = buildList {
        add(SeccionDashboard("Ventas", Icons.Default.PointOfSale, "ventas"))
        add(SeccionDashboard("Productos", Icons.Default.Inventory2, "productos"))
        add(SeccionDashboard("Inventario", Icons.Default.ReceiptLong, "inventario"))
        if (esAdmin) {
            add(SeccionDashboard("Tarjetas", Icons.Default.CreditCard, "tarjetas"))
            add(SeccionDashboard("Aprobaciones", Icons.Default.FactCheck, "aprobaciones"))
            add(SeccionDashboard("@soporte", Icons.Default.HeadsetMic, "soporte"))
            add(SeccionDashboard("Devolución", Icons.Default.AssignmentReturn, "devolucion"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestor360") },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, "Cerrar sesión", tint = Color.White)
                    }
                },
                actions = {
                    Text("👤 $username", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
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
                    Box {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (seccion.ruta == "soporte") mensajesSinLeer = 0L // se marcan leídos al abrir la pantalla
                                onNavigate(seccion.ruta)
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(seccion.icono, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(seccion.titulo, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (seccion.ruta == "soporte" && mensajesSinLeer > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            ) {
                                Text(if (mensajesSinLeer > 99) "99+" else mensajesSinLeer.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
