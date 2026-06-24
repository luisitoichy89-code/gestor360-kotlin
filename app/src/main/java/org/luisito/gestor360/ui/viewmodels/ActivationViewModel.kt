package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.License
import org.luisito.gestor360.domain.repository.ILicenseRepository
import org.luisito.gestor360.domain.result.Result

class ActivationViewModel(
    private val licenseRepo: ILicenseRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _license = MutableStateFlow<License?>(null)
    val license: StateFlow<License?> = _license
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _isActivated = MutableStateFlow(false)
    val isActivated: StateFlow<Boolean> = _isActivated
    
    fun verifyLicense(androidId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = licenseRepo.verifyLicense(androidId)) {
                is Result.Success -> {
                    _license.value = result.data
                    _isActivated.value = result.data.isActive
                }
                is Result.Error -> {
                    _error.value = result.message
                    _isActivated.value = false
                }
            }
            _isLoading.value = false
        }
    }
    
    fun activateLicense(androidId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = licenseRepo.activateLicense(androidId)) {
                is Result.Success -> {
                    _isActivated.value = true
                    // Recargar para obtener la licencia creada
                    verifyLicense(androidId)
                }
                is Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
