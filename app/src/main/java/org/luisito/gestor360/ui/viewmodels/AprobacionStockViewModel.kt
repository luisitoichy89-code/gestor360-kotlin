package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.AprobacionStock
import org.luisito.gestor360.data.repository.AprobacionStockRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

data class AprobacionStockUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
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
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar las aprobaciones pendientes")) }
        }
    }

    fun solicitarProducto(androidId: String, nombre: String, precio: Double, cantidad: Double) {
        viewModelScope.launch {
            repository.solicitarProducto(androidId, nombre, precio, cantidad)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Producto enviado a aprobación"); cargar(androidId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo enviar la solicitud")) }
        }
    }

    fun solicitarAumento(androidId: String, productoId: String, productoNombre: String, cantidad: Double) {
        viewModelScope.launch {
            repository.solicitarAumento(androidId, productoId, productoNombre, cantidad)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Aumento enviado a aprobación"); cargar(androidId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo enviar la solicitud")) }
        }
    }

    fun resolver(id: String, estado: String, aprobadoPor: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.resolver(androidIdActual, id, estado, aprobadoPor)
                .onSuccess { cargar(androidIdActual) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo resolver la solicitud")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun solicitarAnularVenta(androidId: String, ventaId: String, ventaTotal: Double) {
        viewModelScope.launch {
            repository.solicitarAnularVenta(androidId, ventaId, ventaTotal)
                .onSuccess { _uiState.value = _uiState.value.copy(mensaje = "Anulación enviada a aprobación"); cargar(androidId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo enviar la anulación")) }
        }
    }

    fun clearMensaje() { _uiState.value = _uiState.value.copy(mensaje = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
