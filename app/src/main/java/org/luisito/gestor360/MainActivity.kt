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
import org.luisito.gestor360.ui.screens.LoginScreen
import org.luisito.gestor360.ui.screens.PendingApprovalScreen
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.viewmodels.CodeLoginViewModel
import org.luisito.gestor360.ui.viewmodels.CodeLoginViewModelFactory
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
    var isFirstLogin by remember { mutableStateOf(true) }
    var showCreatePassword by remember { mutableStateOf(false) }
    var showPendingApproval by remember { mutableStateOf(false) }
    var showNormalLogin by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf("") }
    var currentUserId by remember { mutableStateOf(0) }

    val codeLoginViewModel: CodeLoginViewModel = viewModel(
        factory = CodeLoginViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
    )
    val uiState by codeLoginViewModel.uiState.collectAsState()

    // Manejar flujo de primer login
    LaunchedEffect(uiState.isCodeValid) {
        if (uiState.isCodeValid && uiState.userId != null) {
            currentUsername = uiState.username
            currentUserId = uiState.userId ?: 0
            showCreatePassword = true
            isFirstLogin = false
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
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        
        CreatePasswordScreen(
            username = currentUsername,
            userId = currentUserId,
            onPasswordCreated = {
                codeLoginViewModel.createPassword(currentUserId, password)
            },
            isLoading = uiState.isLoading,
            error = uiState.error
        )
    } else if (!isLoggedIn && !showNormalLogin) {
        // Mostrar pantalla de login con código solo si es primer login y no tiene contraseña
        CodeLoginScreen(
            onCodeValid = { username, code ->
                codeLoginViewModel.validateCode(username, code)
            },
            isLoading = uiState.isLoading,
            error = uiState.error
        )
    } else if (!isLoggedIn && showNormalLogin) {
        LoginScreen(
            onLoginSuccess = {
                // Verificar Android ID y rol
                isLoggedIn = true
            },
            onRecovery = {
                // TODO: Implementar recuperación
            },
            isLoading = false,
            error = null
        )
    } else {
        DashboardScreen(
            userRol = "admin",
            username = currentUsername.ifEmpty { "Usuario" },
            onMenuClick = {},
            onLogout = {
                isLoggedIn = false
                showNormalLogin = true
            }
        )
    }
}
