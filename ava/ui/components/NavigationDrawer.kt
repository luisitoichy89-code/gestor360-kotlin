package org.luisito.gestor360.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun Gestor360Drawer(
    drawerState: androidx.compose.material3.DrawerState,
    selectedItem: String,
    onItemClick: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Gestor360°",
                    modifier = Modifier.padding(16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
                Divider()
                Column(modifier = Modifier.fillMaxSize()) {
                    NavigationDrawerItem(
                        label = { Text("📊 Dashboard") },
                        selected = selectedItem == "dashboard",
                        onClick = { scope.launch { drawerState.close(); onItemClick("dashboard") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("🛒 Vender") },
                        selected = selectedItem == "ventas",
                        onClick = { scope.launch { drawerState.close(); onItemClick("ventas") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("📦 Productos") },
                        selected = selectedItem == "productos",
                        onClick = { scope.launch { drawerState.close(); onItemClick("productos") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("📦 Mi Inventario") },
                        selected = selectedItem == "inventario",
                        onClick = { scope.launch { drawerState.close(); onItemClick("inventario") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("⚠️ Mermas") },
                        selected = selectedItem == "mermas",
                        onClick = { scope.launch { drawerState.close(); onItemClick("mermas") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("📋 Trazas") },
                        selected = selectedItem == "trazas",
                        onClick = { scope.launch { drawerState.close(); onItemClick("trazas") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("🔄 Sincronizar") },
                        selected = selectedItem == "sync",
                        onClick = { scope.launch { drawerState.close(); onItemClick("sync") } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Divider()
                    NavigationDrawerItem(
                        label = { Text("🚪 Cerrar Sesión") },
                        selected = false,
                        onClick = { scope.launch { drawerState.close(); onItemClick("logout") } },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        },
        content = content
    )
}
