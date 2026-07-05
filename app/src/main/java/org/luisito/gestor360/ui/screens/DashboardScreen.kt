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

private data class SeccionDashboard(val titulo: String, val icono: ImageVector, val ruta: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userRol: String = "",
    username: String = "",
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val esAdmin = userRol == "admin"
    val secciones = buildList {
        add(SeccionDashboard("Ventas", Icons.Default.PointOfSale, "ventas"))
        add(SeccionDashboard("Productos", Icons.Default.Inventory2, "productos"))
        add(SeccionDashboard("Cierre de Caja", Icons.Default.ReceiptLong, "cierrecaja"))
        if (esAdmin) {
            add(SeccionDashboard("Tarjetas", Icons.Default.CreditCard, "tarjetas"))
            add(SeccionDashboard("Aprobaciones", Icons.Default.FactCheck, "aprobaciones"))
        add(SeccionDashboard("@soporte", Icons.Default.HeadsetMic, "soporte"))
            add(SeccionDashboard("Trazas", Icons.Default.History, "trazas"))
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
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigate(seccion.ruta) }
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
                }
            }
        }
    }
}
