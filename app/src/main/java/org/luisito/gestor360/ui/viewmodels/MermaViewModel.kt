package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.entities.MermaEntity
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

data class MermaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val pendientes: List<MermaEntity> = emptyList(),
    val error: String? = null,
    val mensaje: String? = null
)

class MermaViewModel(
    private val repository: MermaRepository = MermaRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MermaUiState())
    val uiState: StateFlow<MermaUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun cargarPendientes(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPendientes(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, pendientes = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar las mermas pendientes")) }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargarPendientes(androidIdActual)
    }

    fun solicitar(androidId: String, productoId: String, productoNombre: String, cantidad: Double, motivo: String, onListo: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.crear(androidId, productoId, productoNombre, cantidad, motivo)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(mensaje = "Merma enviada para aprobación del admin")
                    onListo()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo registrar la merma")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun aprobar(merma: MermaEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.aprobar(androidIdActual, merma.id)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo aprobar la merma")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun rechazar(merma: MermaEntity) {
        viewModelScope.launch {
            repository.rechazar(androidIdActual, merma.id).onSuccess { refrescar() }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearMensaje() { _uiState.value = _uiState.value.copy(mensaje = null) }
}
