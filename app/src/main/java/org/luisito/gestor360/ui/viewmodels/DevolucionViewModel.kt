package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Devolucion
import org.luisito.gestor360.data.repository.DevolucionRepository

data class DevolucionUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val pendientes: List<Devolucion> = emptyList(),
    val mensaje: String? = null,
    val error: String? = null
)

class DevolucionViewModel(
    private val repository: DevolucionRepository = DevolucionRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevolucionUiState())
    val uiState: StateFlow<DevolucionUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPendientes(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, pendientes = it) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() { if (androidIdActual.isNotBlank()) cargar(androidIdActual) }

    fun solicitar(androidId: String, productoId: Long, productoNombre: String, cantidad: Double, metodo: String, motivo: String, onListo: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.solicitar(androidId, productoId, productoNombre, cantidad, metodo, motivo)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Devolución enviada a aprobación"); onListo() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message ?: "No se pudo enviar la devolución") }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun aprobar(id: Long, destino: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.resolver(androidIdActual, id, "aprobada", destino)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun rechazar(id: Long) {
        viewModelScope.launch {
            repository.resolver(androidIdActual, id, "rechazada").onSuccess { refrescar() }
        }
    }

    fun clearMensaje() { _uiState.value = _uiState.value.copy(mensaje = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
