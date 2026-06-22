package org.luisito.gestor360.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Composable
fun AppDrawer(
    rol: String,
    onItemClick: (String) -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet {
        // Encabezado
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Gestor360°",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider()

        // Opciones según rol
        val menuItems = if (rol == "admin") {
            listOf(
                MenuItem("🛒 Vender", "ventas"),
                MenuItem("📦 Productos", "productos"),
                MenuItem("📥 Recibir Mercancía", "recepcion"),
                MenuItem("⚠️ Mermas", "mermas"),
                MenuItem("👥 Mi Equipo", "equipo"),
                MenuItem("📊 Reportes", "reportes"),
                MenuItem("📋 Trazas", "trazas"),
                MenuItem("💬 Chat", "chat"),
                MenuItem("🔄 Sincronizar", "sync")
            )
        } else {
            listOf(
                MenuItem("🛒 Vender", "ventas"),
                MenuItem("📦 Mi Inventario", "inventario"),
                MenuItem("⚠️ Reportar Merma", "merma"),
                MenuItem("📊 Mi Resumen", "resumen"),
                MenuItem("💬 Chat", "chat")
            )
        }

        menuItems.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label) },
                selected = false,
                onClick = { onItemClick(item.route) }
            )
        }

        HorizontalDivider()

        NavigationDrawerItem(
            label = { Text("Cerrar Sesión") },
            selected = false,
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
            onClick = onLogout
        )
    }
}

data class MenuItem(
    val label: String,
    val route: String
)
