package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.repository.MermaRepository

data class MermaUiState(
    val isLoading: Boolean = false,
    val mermas: List<MermaPendiente> = emptyList(),
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getMermasPendientes(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, mermas = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun solicitar(productoId: String, productoNombre: String, cantidad: Double, motivo: String, almacenId: String, solicitadoPor: String, solicitadoPorNombre: String) {
        viewModelScope.launch {
            repository.crearMerma(androidIdActual, productoId, productoNombre, cantidad, motivo, almacenId, solicitadoPor, solicitadoPorNombre)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Merma solicitada"); cargarPendientes(androidIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun resolver(mermaId: String, estado: String, aprobadoPor: String) {
        viewModelScope.launch {
            repository.resolverMerma(androidIdActual, mermaId, estado, aprobadoPor)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = if (estado == "aprobada") "Merma aprobada" else "Merma rechazada"); cargarPendientes(androidIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearMensaje() { _uiState.value = _uiState.value.copy(mensaje = null) }
}
