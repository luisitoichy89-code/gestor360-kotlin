package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.data.repository.TurnoRepository

data class CierreCajaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val turnoActivo: Turno? = null,
    val productosVendidos: List<Pair<String, Double>> = emptyList(),
    val totalEfectivo: Double = 0.0,
    val totalTransferencia: Double = 0.0,
    val totalMixto: Double = 0.0,
    val turnoRecienCerrado: Turno? = null
) {
    val totalGeneral: Double get() = totalEfectivo + totalTransferencia + totalMixto
}

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
            turnoRepository.obtenerTurnoActivo(androidId)
                .onSuccess { turno ->
                    _uiState.value = _uiState.value.copy(turnoActivo = turno)
                    if (turno != null) cargarVentasDelTurno(turno) else _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    private fun cargarVentasDelTurno(turno: Turno) {
        viewModelScope.launch {
            saleRepository.getSales(androidIdActual)
                .onSuccess { ventas -> procesar(ventas, turno) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    private fun procesar(ventas: List<Sale>, turno: Turno) {
        val delTurno = ventas.filter {
            it.usuario_id == turno.usuario_id && (it.created_at ?: "") >= (turno.created_at ?: "")
        }
        val productos = delTurno.groupBy { it.producto_nombre ?: "Producto #${it.producto_id}" }.map { (nombre, filas) -> nombre to filas.sumOf { it.cantidad } }.sortedByDescending { it.second }
            .groupBy { it.producto_nombre }
            .map { (nombre, filas) -> nombre to filas.sumOf { it.cantidad } }
            .sortedByDescending { it.second }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            productosVendidos = productos,
            totalEfectivo = delTurno.filter { it.metodo == "cash" }.sumOf { it.total },
            totalTransferencia = delTurno.filter { it.metodo == "transfer" }.sumOf { it.total },
            totalMixto = delTurno.filter { it.metodo == "mixed" }.sumOf { it.total }
        )
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual)
    }

    fun abrirTurno(apertura: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            turnoRepository.abrirTurno(androidIdActual, apertura)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun cerrarTurno(cierreContado: Double) {
        val turno = _uiState.value.turnoActivo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            turnoRepository.cerrarTurno(androidIdActual, turno.id, cierreContado)
                .onSuccess {
                    val esperado = turno.apertura + _uiState.value.totalEfectivo
                    val diferencia = cierreContado - esperado
                    _uiState.value = _uiState.value.copy(
                        turnoRecienCerrado = turno.copy(cierre = cierreContado, diferencia = diferencia)
                    )
                    refrescar()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun limpiarTurnoRecienCerrado() {
        _uiState.value = _uiState.value.copy(turnoRecienCerrado = null)
    }
}
