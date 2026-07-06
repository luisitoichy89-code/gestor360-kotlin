package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class SeccionDashboard(
    val titulo: String,
    val icono: ImageVector,
    val ruta: String
)

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gestor360", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión", tint = Color.White) } },
                actions = { Text(text = "👤 $username", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (secciones.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay secciones disponibles", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(secciones) { seccion ->
                    ElevatedCard(
                        onClick = { onNavigate(seccion.ruta) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = seccion.icono, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = seccion.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
