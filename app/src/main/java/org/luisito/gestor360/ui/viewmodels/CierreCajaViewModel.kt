package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.repository.SaleRepository
import java.time.LocalDate

data class CierreCajaUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val fecha: String = LocalDate.now().toString(),
    val productosVendidos: List<Pair<String, Double>> = emptyList(),
    val totalEfectivo: Double = 0.0,
    val totalTransferencia: Double = 0.0,
    val totalMixto: Double = 0.0
) {
    val totalGeneral: Double get() = totalEfectivo + totalTransferencia + totalMixto
}

class CierreCajaViewModel(
    private val repository: SaleRepository = SaleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CierreCajaUiState())
    val uiState: StateFlow<CierreCajaUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun cargar(androidId: String, fecha: String = LocalDate.now().toString()) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, fecha = fecha)
            repository.getSales(androidId)
                .onSuccess { ventas -> procesar(ventas, fecha) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual, _uiState.value.fecha)
    }

    private fun procesar(ventas: List<Sale>, fecha: String) {
        val delDia = ventas.filter { (it.created_at ?: "").take(10) == fecha }

        val productos = delDia
            .groupBy { it.producto_nombre }
            .map { (nombre, filas) -> nombre to filas.sumOf { it.cantidad } }
            .sortedByDescending { it.second }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            productosVendidos = productos,
            totalEfectivo = delDia.filter { it.metodo == "cash" }.sumOf { it.total },
            totalTransferencia = delDia.filter { it.metodo == "transfer" }.sumOf { it.total },
            totalMixto = delDia.filter { it.metodo == "mixed" }.sumOf { it.total }
        )
    }
}
