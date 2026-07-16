package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

data class MermaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val pendientes: List<MermaPendiente> = emptyList(),
    val error: String? = null
)

class MermaViewModel(
    private val repository: MermaRepository = MermaRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MermaUiState())
    val uiState: StateFlow<MermaUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPendientes(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, pendientes = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar las mermas pendientes")) }
        }
    }

    fun refrescar() { if (androidIdActual.isNotBlank()) cargar(androidIdActual) }

    fun solicitar(productoId: Long, productoNombre: String, cantidad: Double, motivo: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.solicitar(androidIdActual, productoId, productoNombre, cantidad, motivo)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo enviar la solicitud de merma")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun resolver(id: Long, estado: String, resueltoPor: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.resolver(androidIdActual, id, estado, resueltoPor)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo resolver la merma")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
