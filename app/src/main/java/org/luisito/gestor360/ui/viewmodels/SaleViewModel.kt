package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.ClienteInfo
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.models.SaleItem
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

data class SaleUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val ventas: List<Sale> = emptyList(),
    val ventaConfirmada: Boolean = false,
    val error: String? = null
)

class SaleViewModel(
    private val repository: SaleRepository = SaleRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getSales(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, ventas = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar los productos")) }
        }
    }

    fun vender(items: List<SaleItem>, metodo: String, efectivo: Double, transferencia: Double, cliente: ClienteInfo?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.guardarVenta(androidIdActual, items, metodo, efectivo, transferencia, cliente)
                .onSuccess { _uiState.value = _uiState.value.copy(isSaving = false, ventaConfirmada = true) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isSaving = false, error = e.mensajeAmigable("No se pudo registrar la venta")) }
        }
    }

    fun anularVenta(ventaId: String) {
        viewModelScope.launch {
            repository.anularVenta(androidIdActual, ventaId)
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo anular la venta")) }
        }
    }

    fun limpiarVentaConfirmada() { _uiState.value = _uiState.value.copy(ventaConfirmada = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
