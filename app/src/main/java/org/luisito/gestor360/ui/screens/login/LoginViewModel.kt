package org.luisito.gestor360.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.data.repository.LoginResult
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.utils.DataStoreManager

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager,
    private val supabase: SupabaseClient = SupabaseClientProvider.client
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _sessionStatus = MutableStateFlow<SessionStatus?>(null)
    val sessionStatus: StateFlow<SessionStatus?> = _sessionStatus.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        checkSession()
        listenSessionStatus()
    }

    private fun listenSessionStatus() {
        viewModelScope.launch {
            supabase.auth.sessionStatus.collect { status: SessionStatus ->
                _sessionStatus.value = status
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _uiState.value = _uiState.value.copy(
                            isOffline = false,
                            error = null
                        )
                    }
                    is SessionStatus.RefreshFailure -> {
                        _uiState.value = _uiState.value.copy(
                            isOffline = true,
                            error = "⚠️ Sin conexión. Los datos se sincronizarán cuando vuelvas a internet."
                        )
                    }
                    is SessionStatus.Initializing -> {
                        _uiState.value = _uiState.value.copy(loading = true)
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _uiState.value = _uiState.value.copy(
                            loading = false,
                            isOffline = false
                        )
                    }
                    else -> {
                        // Otros estados
                    }
                }
            }
        }
    }

    private fun checkSession() {
        viewModelScope.launch {
            val session = dataStoreManager.getSession().first()
            if (session != null) {
                _navigationEvent.value = NavigationEvent.NavigateToDashboard(
                    userId = session.userId,
                    userRol = session.userRol,
                    username = session.username,
                    nombre = session.nombre
                )
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Usuario y contraseña son obligatorios")
            return
        }

        _uiState.value = _uiState.value.copy(loading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.login(username, password)
            when (result) {
                is LoginResult.Success -> {
                    dataStoreManager.saveSession(
                        userId = result.userId,
                        userRol = result.userRol,
                        username = result.username,
                        nombre = result.nombre
                    )
                    _uiState.value = _uiState.value.copy(loading = false)
                    _navigationEvent.value = NavigationEvent.NavigateToDashboard(
                        userId = result.userId,
                        userRol = result.userRol,
                        username = result.username,
                        nombre = result.nombre
                    )
                }
                is LoginResult.Error -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
            } catch (e: Exception) {
                // Si falla, igual limpiamos la sesión local
            }
            dataStoreManager.clearSession()
            _navigationEvent.value = NavigationEvent.NavigateToLogin
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearNavigation() {
        _navigationEvent.value = null
    }

    companion object {
        fun provideFactory(authRepository: AuthRepository, dataStoreManager: DataStoreManager): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LoginViewModel(authRepository, dataStoreManager) as T
                }
            }
        }
    }
}

data class LoginUiState(
    val loading: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null
)

sealed class NavigationEvent {
    data class NavigateToDashboard(
        val userId: String,
        val userRol: String,
        val username: String,
        val nombre: String
    ) : NavigationEvent()
    object NavigateToLogin : NavigationEvent()
}
