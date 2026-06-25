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
import org.luisito.gestor360.utils.SessionManager

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
    var isLoading by remember { mutableStateOf(true) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    val codeLoginViewModel: CodeLoginViewModel = viewModel(
        factory = CodeLoginViewModelFactory(context)
    )
    val uiState by codeLoginViewModel.uiState.collectAsState()

    // Verificar sesión al iniciar
    LaunchedEffect(Unit) {
        if (sessionManager.isLoggedIn()) {
            val (userId, username, rol) = codeLoginViewModel.getSessionData()
            currentUserId = userId
            currentUsername = username
            isLoggedIn = true
            isFirstLogin = false
        }
        isLoading = false
    }

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

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            isLoggedIn = true
            currentUsername = uiState.username
            currentUserId = uiState.userId ?: 0
        }
    }

    if (isLoading) {
        // Pantalla de carga
    } else if (!isLicensed) {
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
    } else if (!isLoggedIn && isFirstLogin) {
        CodeLoginScreen(
            onCodeValid = { username, code ->
                codeLoginViewModel.validateCode(username, code)
            },
            isLoading = uiState.isLoading,
            error = uiState.error
        )
    } else if (!isLoggedIn && !isFirstLogin) {
        LoginScreen(
            onLoginSuccess = {
                // Obtener username y password del estado
                // TODO: Conectar con ViewModel
                isLoggedIn = true
            },
            onRecovery = {
                // TODO: Implementar recuperación
            },
            isLoading = uiState.isLoading,
            error = uiState.error
        )
    } else {
        DashboardScreen(
            userRol = sessionManager.getRol(),
            username = currentUsername.ifEmpty { sessionManager.getUsername() },
            onMenuClick = {},
            onLogout = {
                codeLoginViewModel.logout()
                isLoggedIn = false
                isFirstLogin = true
                currentUsername = ""
                currentUserId = 0
            }
        )
    }
}
