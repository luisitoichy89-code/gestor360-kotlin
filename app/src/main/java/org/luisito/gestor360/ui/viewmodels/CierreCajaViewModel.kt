package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.data.repository.TurnoRepository

data class CierreCajaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val turnoActivo: Turno? = null,
    val turnoRecienCerrado: Turno? = null,
    val productosVendidos: List<Pair<String, Double>> = emptyList(),
    val totalEfectivo: Double = 0.0,
    val totalTransferencia: Double = 0.0,
    val totalMixto: Double = 0.0,
    val error: String? = null
)

class CierreCajaViewModel(
    private val turnoRepository: TurnoRepository = TurnoRepository(),
    private val saleRepository: SaleRepository = SaleRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(CierreCajaUiState())
    val uiState: StateFlow<CierreCajaUiState> = _uiState.asStateFlow()
    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            turnoRepository.getTurnoAbierto(androidId)
                .onSuccess { turno ->
                    _uiState.value = _uiState.value.copy(turnoActivo = turno)
                    if (turno != null) cargarVentasDelTurno(turno)
                    else _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() { if (androidIdActual.isNotBlank()) cargar(androidIdActual) }

    fun abrirTurno(apertura: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            turnoRepository.abrirTurno(androidIdActual, apertura)
                .onSuccess { cargar(androidIdActual) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun cerrarTurno(cierre: Double) {
        val turno = _uiState.value.turnoActivo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            turnoRepository.cerrarTurno(androidIdActual, turno.id!!, cierre)
                .onSuccess { _uiState.value = _uiState.value.copy(turnoRecienCerrado = it, turnoActivo = null, isSaving = false) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message, isSaving = false) }
        }
    }

    fun limpiarTurnoRecienCerrado() { _uiState.value = _uiState.value.copy(turnoRecienCerrado = null) }

    private fun cargarVentasDelTurno(turno: Turno) {
        viewModelScope.launch {
            saleRepository.getSales(androidIdActual)
                .onSuccess { ventas -> procesar(ventas, turno) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    private fun procesar(ventas: List<org.luisito.gestor360.data.models.Sale>, turno: Turno) {
        val delTurno = ventas.filter { it.usuario_id.toString() == turno.usuario_id.toString() && (it.created_at ?: "") >= (turno.created_at ?: "") }
        val productos = delTurno
            .groupBy { it.producto_id }
            .map { (_, filas) -> ("Producto #${filas.first().producto_id}" to filas.sumOf { it.cantidad }) }
            .sortedByDescending { it.second }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            productosVendidos = productos,
            totalEfectivo = delTurno.filter { it.metodo == "cash" }.sumOf { it.total },
            totalTransferencia = delTurno.filter { it.metodo == "transfer" }.sumOf { it.total },
            totalMixto = delTurno.filter { it.metodo == "mixed" }.sumOf { it.total }
        )
    }
}
