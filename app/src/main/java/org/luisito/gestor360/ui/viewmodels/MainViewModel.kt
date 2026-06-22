package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.model.User
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.data.repository.LicenciaRepository

class MainViewModel(
    private val authRepository: AuthRepository,
    private val licenciaRepository: LicenciaRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.login(username, password)
            _isLoading.value = false
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Error de autenticación"
            }
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
