package org.luisito.gestor360.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // TODO: Conectar con Supabase Auth
            // Por ahora, cualquier credencial funciona
            kotlinx.coroutines.delay(1000) // Simular carga

            if (username.isNotEmpty() && password.isNotEmpty()) {
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Usuario y contraseña son obligatorios"
                    )
                }
            }
        }
    }

    fun resetState() {
        _uiState.update { LoginUiState() }
    }
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)
