package org.luisito.gestor360.ui.screens.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.LicenseRepository
import org.luisito.gestor360.data.repository.LicenseStatus
import org.luisito.gestor360.data.SupabaseClient

class ActivationViewModel : ViewModel() {
    private val licenseRepository = LicenseRepository(SupabaseClient.instance)
    
    private val _uiState = MutableStateFlow<LicenseStatus?>(null)
    val uiState: StateFlow<LicenseStatus?> = _uiState
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    fun verifyLicense(androidId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = licenseRepository.verifyLicense(androidId)
            _uiState.value = result
            _isLoading.value = false
        }
    }
}
