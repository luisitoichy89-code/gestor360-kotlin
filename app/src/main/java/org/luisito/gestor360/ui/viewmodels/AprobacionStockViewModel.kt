package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.AprobacionStock
import org.luisito.gestor360.data.repository.AprobacionStockRepository

data class AprobacionStockUiState(
    val isLoading: Boolean = false,
    val pendientes: List<AprobacionStock> = emptyList(),
    val mensaje: String? = null,
    val error: String? = null
)

class AprobacionStockViewModel(
    private val repository: AprobacionStockRepository = AprobacionStockRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(AprobacionStockUiState())
    val uiState: StateFlow<AprobacionStockUiState> = _uiState.asStateFlow()
    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPendientes(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, pendientes = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun solicitarProducto(nombre: String, precio: Double, cantidad: Double) {
        viewModelScope.launch {
            repository.solicitarProducto(androidIdActual, nombre, precio, cantidad)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Producto enviado a aprobación"); cargar(androidIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun solicitarAumento(productoId: Long, cantidad: Double) {
        viewModelScope.launch {
            repository.solicitarAumento(androidIdActual, productoId, cantidad)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Aumento enviado a aprobación"); cargar(androidIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun resolver(id: Long, estado: String, aprobadoPor: Long) {
        viewModelScope.launch {
            repository.resolver(androidIdActual, id, estado, aprobadoPor)
                .onSuccess { cargar(androidIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun solicitarAnularVenta(androidId: String, ventaId: String, ventaTotal: Double) {
        viewModelScope.launch {
            repository.solicitarAnularVenta(androidId, ventaId, ventaTotal)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Anulación enviada a aprobación"); cargar(androidId) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }
    fun clearMensaje() { _uiState.value = _uiState.value.copy(mensaje = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
