package org.luisito.gestor360.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch

@Composable
fun ScaffoldWithDrawer(
    rol: String,
    title: String,
    onItemClick: (String) -> Unit,
    onLogout: () -> Unit,
    content: @Composable (() -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                rol = rol,
                onItemClick = { route ->
                    scope.launch { drawerState.close() }
                    onItemClick(route)
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = title,
                    onMenuClick = {
                        scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() }
                    }
                )
            }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}
