package org.luisito.gestor360.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.CodeRepository
import org.luisito.gestor360.utils.DeviceIdManager

class CodeLoginViewModel(
    private val context: Context,
    private val codeRepository: CodeRepository = CodeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeLoginUiState())
    val uiState: StateFlow<CodeLoginUiState> = _uiState.asStateFlow()

    fun validateCode(username: String, code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = codeRepository.validateCode(username, code)

            if (result.isValid && result.userId != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCodeValid = true,
                        userId = result.userId,
                        username = result.username ?: username,
                        rol = result.rol
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message ?: "Código inválido"
                    )
                }
            }
        }
    }

    fun createPassword(userId: Int, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // TODO: Actualizar contraseña en Supabase Auth
                val deviceId = DeviceIdManager.getDeviceId(context)
                val saved = codeRepository.saveDeviceId(userId, deviceId)

                if (saved) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            passwordCreated = true,
                            isDeviceSaved = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Error al guardar el dispositivo"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al crear contraseña"
                    )
                }
            }
        }
    }

    fun resetState() {
        _uiState.update { CodeLoginUiState() }
    }
}

data class CodeLoginUiState(
    val isLoading: Boolean = false,
    val isCodeValid: Boolean = false,
    val passwordCreated: Boolean = false,
    val isDeviceSaved: Boolean = false,
    val userId: Int? = null,
    val username: String = "",
    val rol: String? = null,
    val error: String? = null
)
