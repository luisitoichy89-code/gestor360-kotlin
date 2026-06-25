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
import org.luisito.gestor360.utils.SessionManager

class CodeLoginViewModel(
    private val context: Context,
    private val codeRepository: CodeRepository = CodeRepository()
) : ViewModel() {

    private val sessionManager = SessionManager(context)

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
                val username = _uiState.value.username
                val authCreated = codeRepository.createAuthUser(username, password)

                if (!authCreated) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Error al crear usuario en el sistema de autenticación"
                        )
                    }
                    return@launch
                }

                val deviceId = DeviceIdManager.getDeviceId(context)
                val saved = codeRepository.saveDeviceId(userId, deviceId)

                if (saved) {
                    // Guardar sesión local
                    sessionManager.saveSession(userId, username, _uiState.value.rol ?: "seller")
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

    fun loginWithPassword(username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 1. Autenticar en Supabase Auth
                val authSuccess = codeRepository.loginWithPassword(username, password)

                if (!authSuccess) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Credenciales incorrectas"
                        )
                    }
                    return@launch
                }

                // 2. Obtener userId
                val userId = codeRepository.getUserId(username)
                if (userId == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Usuario no encontrado en el sistema"
                        )
                    }
                    return@launch
                }

                // 3. Verificar Android ID
                val deviceId = DeviceIdManager.getDeviceId(context)
                val deviceVerified = codeRepository.verifyDevice(userId, deviceId)

                if (!deviceVerified) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Dispositivo no autorizado. Contacta con tu administrador."
                        )
                    }
                    return@launch
                }

                // 4. Guardar sesión
                val usernameSaved = _uiState.value.username.ifEmpty { username }
                sessionManager.saveSession(userId, usernameSaved, _uiState.value.rol ?: "seller")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userId = userId,
                        username = usernameSaved
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al iniciar sesión"
                    )
                }
            }
        }
    }

    fun checkSession(): Boolean {
        return sessionManager.isLoggedIn()
    }

    fun getSessionData(): Triple<Int, String, String> {
        return Triple(
            sessionManager.getUserId(),
            sessionManager.getUsername(),
            sessionManager.getRol()
        )
    }

    fun logout() {
        sessionManager.clear()
        _uiState.update { CodeLoginUiState() }
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
    val isLoggedIn: Boolean = false,
    val userId: Int? = null,
    val username: String = "",
    val rol: String? = null,
    val error: String? = null
)

    fun requestRecovery(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val userExists = codeRepository.checkIfUserExists(username)
            if (!userExists) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Usuario no encontrado"
                    )
                }
                return@launch
            }

            val success = codeRepository.requestPasswordRecovery(username)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    recoverySent = success,
                    error = if (!success) "Error al enviar la solicitud de recuperación" else null
                )
            }
        }
    }

    fun clearRecoveryState() {
        _uiState.update { it.copy(recoverySent = false) }
    }
