package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.luisito.gestor360.ui.components.Gestor360Drawer
import org.luisito.gestor360.ui.screens.DashboardScreen
import org.luisito.gestor360.ui.screens.InventoryScreen
import org.luisito.gestor360.ui.screens.MermaScreen
import org.luisito.gestor360.ui.screens.ProductsScreen
import org.luisito.gestor360.ui.screens.SalesScreen
import org.luisito.gestor360.ui.screens.SyncScreen
import org.luisito.gestor360.ui.screens.TracesScreen
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.screens.login.LoginScreen
import org.luisito.gestor360.ui.screens.login.LoginViewModel
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.utils.SyncManager
import org.luisito.gestor360.utils.SyncResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Gestor360App()
        }
    }
}

@Composable
fun Gestor360App() {
    var isLicensed by remember { mutableStateOf(true) }
    var selectedItem by remember { mutableStateOf("dashboard") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isSyncing by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<String?>(null) }

    val loginViewModel: LoginViewModel = viewModel()
    val loginState by loginViewModel.uiState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val syncManager = remember { SyncManager(context) }

    if (!isLicensed) {
        ActivationScreen(
            onLicenseValid = { isLicensed = true }
        )
    } else if (!loginState.isLoggedIn) {
        LoginScreen(
            onLoginSuccess = {
                loginViewModel.login("admin", "Gestor_360TuMadre1989@")
            },
            isLoading = loginState.isLoading,
            error = loginState.error
        )
    } else {
        Gestor360Drawer(
            drawerState = drawerState,
            selectedItem = selectedItem,
            onItemClick = { item ->
                selectedItem = item
                when (item) {
                    "logout" -> loginViewModel.resetState()
                }
            }
        ) {
            when (selectedItem) {
                "ventas" -> SalesScreen(
                    almacenId = "1",
                    usuarioId = 1,
                    onBack = { selectedItem = "dashboard" }
                )
                "productos" -> ProductsScreen(
                    almacenId = "1",
                    onBack = { selectedItem = "dashboard" }
                )
                "inventario" -> InventoryScreen(
                    almacenId = "1",
                    onBack = { selectedItem = "dashboard" }
                )
                "mermas" -> MermaScreen(
                    onBack = { selectedItem = "dashboard" }
                )
                "trazas" -> TracesScreen(
                    onBack = { selectedItem = "dashboard" }
                )
                "sync" -> SyncScreen(
                    onBack = { selectedItem = "dashboard" },
                    onSync = {
                        CoroutineScope(Dispatchers.Main).launch {
                            isSyncing = true
                            syncResult = null
                            val result = syncManager.syncAll("1")
                            isSyncing = false
                            syncResult = when (result) {
                                is SyncResult.Success -> "✅ Sincronización exitosa"
                                is SyncResult.Error -> "❌ Error: ${result.message}"
                            }
                        }
                    },
                    isLoading = isSyncing,
                    result = syncResult
                )
                else -> DashboardScreen(
                    userRol = loginState.userRol,
                    username = loginState.username,
                    onMenuClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            drawerState.open()
                        }
                    },
                    onLogout = {
                        loginViewModel.resetState()
                    }
                )
            }
        }
    }
}
