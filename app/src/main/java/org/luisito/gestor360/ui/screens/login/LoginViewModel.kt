package org.luisito.gestor360.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.data.repository.LoginResult
import org.luisito.gestor360.utils.DataStoreManager

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            dataStoreManager.getSession().collect { session ->
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
            dataStoreManager.clearSession()
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
    val error: String? = null
)

sealed class NavigationEvent {
    data class NavigateToDashboard(
        val userId: String,
        val userRol: String,
        val username: String,
        val nombre: String
    ) : NavigationEvent()
}
