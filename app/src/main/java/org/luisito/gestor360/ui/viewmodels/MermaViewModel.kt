package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.data.repository.ProductRepository

data class MermaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val pendientes: List<MermaPendiente> = emptyList(),
    val error: String? = null,
    val mensaje: String? = null
)

class MermaViewModel(
    private val repository: MermaRepository = MermaRepository(),
    private val productRepository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MermaUiState())
    val uiState: StateFlow<MermaUiState> = _uiState.asStateFlow()

    private var clienteIdActual: String = ""

    fun cargarPendientes(clienteId: String) {
        clienteIdActual = clienteId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPendientes(clienteId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, pendientes = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() {
        if (clienteIdActual.isNotBlank()) cargarPendientes(clienteIdActual)
    }

    fun solicitar(
        productoId: Long,
        productoNombre: String,
        cantidad: Double,
        motivo: String,
        almacenId: String,
        clienteId: String,
        solicitadoPor: Long,
        solicitadoPorNombre: String,
        onListo: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.solicitar(productoId, productoNombre, cantidad, motivo, almacenId, clienteId, solicitadoPor, solicitadoPorNombre)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(mensaje = "Merma enviada para aprobación del admin")
                    onListo()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun aprobar(merma: MermaPendiente, aprobadoPor: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val stockActual = productRepository.getProducts(merma.almacen_id).getOrNull()
                ?.firstOrNull { it.id == merma.producto_id }?.stock ?: 0.0
            repository.aprobar(merma, stockActual, aprobadoPor)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun rechazar(merma: MermaPendiente, aprobadoPor: Long) {
        viewModelScope.launch {
            repository.rechazar(merma, aprobadoPor).onSuccess { refrescar() }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMensaje() {
        _uiState.value = _uiState.value.copy(mensaje = null)
    }
}
