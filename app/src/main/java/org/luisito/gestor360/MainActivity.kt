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
    var isLicensed by remember { mutableStateOf(true) } // Temporal

    val loginViewModel: LoginViewModel = viewModel()
    val loginState by loginViewModel.uiState.collectAsState()

    LaunchedEffect(loginState.isLoggedIn) {
        if (loginState.isLoggedIn) {
            // Guardar sesión en DataStore aquí después
        }
    }

    if (!isLicensed) {
        ActivationScreen(
            onLicenseValid = { isLicensed = true }
        )
    } else if (!loginState.isLoggedIn) {
        LoginScreen(
            onLoginSuccess = {
                loginViewModel.login("test", "password")
            },
            isLoading = loginState.isLoading,
            error = loginState.error
        )
    } else {
        DashboardScreen(
            onLogout = {
                loginViewModel.resetState()
            }
        )
    }
}
