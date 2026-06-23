package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.screens.DashboardScreen
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.screens.login.LoginScreen
import org.luisito.gestor360.ui.screens.login.LoginViewModel
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.utils.DataStoreManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Gestor360App(
                dataStoreManager = DataStoreManager(this)
            )
        }
    }
}

@Composable
fun Gestor360App(
    dataStoreManager: DataStoreManager
) {
    // Estado temporal para pruebas
    var isLicensed by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isCheckingSession by remember { mutableStateOf(true) }

    val loginViewModel: LoginViewModel = viewModel()
    val loginState by loginViewModel.uiState.collectAsState()

    // Verificar si ya hay sesión guardada
    LaunchedEffect(Unit) {
        // Por ahora, siempre falso para forzar login
        isCheckingSession = false
    }

    if (isCheckingSession) {
        // Pantalla de carga
    } else if (!isLicensed) {
        ActivationScreen(
            onLicenseValid = { isLicensed = true }
        )
    } else if (!isLoggedIn && !loginState.isLoggedIn) {
        LoginScreen(
            onLoginSuccess = {
                loginViewModel.login("test", "test")
            },
            isLoading = loginState.isLoading,
            error = loginState.error
        )
    } else {
        DashboardScreen(
            onLogout = {
                isLoggedIn = false
                loginViewModel.resetState()
            }
        )
    }
}
