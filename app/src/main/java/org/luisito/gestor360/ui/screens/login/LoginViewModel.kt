package org.luisito.gestor360.ui.screens.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.UserEntity
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.data.repository.LoginResult
import org.luisito.gestor360.data.repository.UserRepository

class LoginViewModel(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = authRepository.login(username, password)

            when (result) {
                is LoginResult.Success -> {
                    // Obtener usuario de Supabase
                    val user = userRepository.getUserByAuthId(result.userId)
                    if (user != null) {
                        // Guardar en Room
                        val db = AppDatabase.getInstance(getApplication())
                        db.userDao().insertUser(
                            UserEntity(
                                id = user.id.toString(),
                                authId = user.auth_id,
                                username = user.username,
                                nombre = user.nombre,
                                rol = user.rol,
                                almacenId = user.almacen_id,
                                activo = user.activo
                            )
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                userId = user.id.toString(),
                                userRol = user.rol,
                                username = user.username,
                                nombre = user.nombre ?: user.username
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Usuario no encontrado en la base de datos"
                            )
                        }
                    }
                }
                is LoginResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
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
    val userId: String = "",
    val userRol: String = "",
    val username: String = "",
    val nombre: String = "",
    val error: String? = null
)
