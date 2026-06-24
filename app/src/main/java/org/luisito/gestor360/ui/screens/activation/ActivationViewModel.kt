package org.luisito.gestor360.ui.screens.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.LicenseStatus
import org.luisito.gestor360.data.repository.LicenseRepository

class ActivationViewModel(
    private val licenseRepository: LicenseRepository = LicenseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    fun checkLicense(deviceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = licenseRepository.checkLicense(deviceId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    licenseStatus = result
                )
            }
        }
    }
}

data class ActivationUiState(
    val deviceId: String = "",
    val isLoading: Boolean = false,
    val licenseStatus: LicenseStatus = LicenseStatus.Pending
)
