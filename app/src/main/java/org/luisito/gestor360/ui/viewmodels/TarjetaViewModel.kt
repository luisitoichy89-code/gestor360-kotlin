package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.repository.TarjetaRepository

data class TarjetaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val tarjetas: List<Tarjeta> = emptyList(),
    val error: String? = null
)

class TarjetaViewModel(
    private val repository: TarjetaRepository = TarjetaRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(TarjetaUiState())
    val uiState: StateFlow<TarjetaUiState> = _uiState.asStateFlow()
    private var androidIdActual: String = ""
    private var almacenIdActual: String = ""

    fun cargar(androidId: String, almacenId: String) {
        androidIdActual = androidId
        almacenIdActual = almacenId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getTarjetas(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, tarjetas = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun crear(banco: String, numero: String, titular: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            repository.crearTarjeta(androidIdActual, banco, numero, titular, almacenIdActual)
                .onSuccess { cargar(androidIdActual, almacenIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
