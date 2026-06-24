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
import org.luisito.gestor360.ui.screens.ProductsScreen
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.screens.login.LoginScreen
import org.luisito.gestor360.ui.screens.login.LoginViewModel
import org.luisito.gestor360.ui.theme.Gestor360Theme

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

    val loginViewModel: LoginViewModel = viewModel()
    val loginState by loginViewModel.uiState.collectAsState()

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
                    "productos" -> {
                        // Navegar a productos
                    }
                }
            }
        ) {
            when (selectedItem) {
                "productos" -> ProductsScreen(
                    almacenId = "1",
                    onBack = { selectedItem = "dashboard" }
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
