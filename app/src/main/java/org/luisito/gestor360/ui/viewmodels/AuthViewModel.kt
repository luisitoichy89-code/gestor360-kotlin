package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.domain.repository.IAuthRepository
import org.luisito.gestor360.domain.result.Result

class AuthViewModel(
    private val authRepo: IAuthRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _userRole = MutableStateFlow<String?>(null)
    val userRole: StateFlow<String?> = _userRole
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = authRepo.login(email, password)) {
                is Result.Success -> {
                    _isAuthenticated.value = true
                    getUserRole()
                }
                is Result.Error -> {
                    _error.value = result.message
                    _isAuthenticated.value = false
                }
            }
            _isLoading.value = false
        }
    }
    
    fun getUserRole() {
        viewModelScope.launch {
            when (val result = authRepo.getCurrentUserRole()) {
                is Result.Success -> {
                    _userRole.value = result.data
                }
                is Result.Error -> {
                    _error.value = result.message
                }
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
            _isAuthenticated.value = false
            _userRole.value = null
        }
    }
    
    fun checkAuth() {
        _isAuthenticated.value = authRepo.isAuthenticated()
        if (_isAuthenticated.value) {
            getUserRole()
        }
    }
}
