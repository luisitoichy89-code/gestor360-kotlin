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
import org.luisito.gestor360.ui.screens.CodeLoginScreen
import org.luisito.gestor360.ui.screens.CreatePasswordScreen
import org.luisito.gestor360.ui.screens.DashboardScreen
import org.luisito.gestor360.ui.screens.PendingApprovalScreen
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.viewmodels.CodeLoginViewModel
import org.luisito.gestor360.utils.DeviceIdManager

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
    var isLicensed by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var showCreatePassword by remember { mutableStateOf(false) }
    var showPendingApproval by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf("") }
    var currentUserId by remember { mutableStateOf(0) }

    val codeLoginViewModel: CodeLoginViewModel = viewModel(
        factory = CodeLoginViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
    )
    val uiState by codeLoginViewModel.uiState.collectAsState()

    // Manejar flujo de login
    LaunchedEffect(uiState.isCodeValid) {
        if (uiState.isCodeValid && uiState.userId != null) {
            // Verificar si ya tiene contraseña
            // Por ahora, asumimos que no tiene y mostramos crear contraseña
            currentUsername = uiState.username
            currentUserId = uiState.userId ?: 0
            showCreatePassword = true
        }
    }

    LaunchedEffect(uiState.passwordCreated) {
        if (uiState.passwordCreated) {
            showCreatePassword = false
            showPendingApproval = true
        }
    }

    if (!isLicensed) {
        ActivationScreen(
            onLicenseValid = { isLicensed = true }
        )
    } else if (showPendingApproval) {
        PendingApprovalScreen(
            username = currentUsername
        )
    } else if (showCreatePassword) {
        CreatePasswordScreen(
            username = currentUsername,
            userId = currentUserId,
            onPasswordCreated = {
                codeLoginViewModel.createPassword(currentUserId, "password") // TODO: Obtener password del estado
            },
            isLoading = uiState.isLoading,
            error = uiState.error
        )
    } else if (!isLoggedIn) {
        CodeLoginScreen(
            onCodeValid = { username, _ ->
                codeLoginViewModel.validateCode(username, "") // TODO: Obtener código del estado
            },
            isLoading = uiState.isLoading,
            error = uiState.error
        )
    } else {
        DashboardScreen(
            userRol = "admin",
            username = "Usuario",
            onMenuClick = {},
            onLogout = { isLoggedIn = false }
        )
    }
}
