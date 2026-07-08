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
    val ventasDelTurno: List<Sale> = emptyList(),
    val productosVendidos: List<Pair<String, Double>> = emptyList(),
    val totalEfectivo: Double = 0.0,
    val totalTransferencia: Double = 0.0,
    val totalMixto: Double = 0.0,
    // Desglose real de lo cobrado dentro de las ventas con método "mixed":
    // antes se mostraba el total mixto en un solo bloque y se perdía cuánto
    // de eso fue efectivo y cuánto transferencia (dato que Sale ya trae).
    val totalMixtoEfectivo: Double = 0.0,
    val totalMixtoTransferencia: Double = 0.0,
    val turnoRecienCerrado: Turno? = null
) {
    val totalGeneral: Double get() = totalEfectivo + totalTransferencia + totalMixto
    // Efectivo real que debe cuadrar en caja: el de ventas 100% efectivo +
    // la parte en efectivo de las ventas mixtas.
    val efectivoEnCaja: Double get() = totalEfectivo + totalMixtoEfectivo
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
            // getSalesConNombre ya hace el join en memoria contra productos y
            // resuelve el nombre real (antes se usaba getSales() y se mostraba
            // "Producto #<id>" porque la tabla ventas no guarda el nombre).
            saleRepository.getSalesConNombre(androidIdActual)
                .onSuccess { ventasConNombre -> procesar(ventasConNombre, turno) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    private fun procesar(ventasConNombre: List<Pair<Sale, String>>, turno: Turno) {
        val delTurno = ventasConNombre.filter { (venta, _) -> venta.usuario_id.toString() == turno.usuario_id.toString() && (venta.created_at ?: "") >= (turno.created_at ?: "") }
        val productos = delTurno.groupBy { (venta, nombre) -> nombre }
            .map { (nombre, filas) -> nombre to filas.sumOf { it.first.cantidad } }
            .sortedByDescending { it.second }
        val ventas = delTurno.map { it.first }
        val mixtas = ventas.filter { it.metodo == "mixed" }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            ventasDelTurno = ventas,
            productosVendidos = productos,
            totalEfectivo = ventas.filter { it.metodo == "cash" }.sumOf { it.total },
            totalTransferencia = ventas.filter { it.metodo == "transfer" }.sumOf { it.total },
            totalMixto = mixtas.sumOf { it.total },
            // Dentro de "mixed", Sale.efectivo/Sale.transferencia ya traen cuánto
            // de esa venta fue en cada método (se calculan al registrar la venta).
            totalMixtoEfectivo = mixtas.sumOf { it.efectivo },
            totalMixtoTransferencia = mixtas.sumOf { it.transferencia }
        )
    }

    fun refrescar() { if (androidIdActual.isNotBlank()) cargar(androidIdActual) }

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
                    // Antes solo sumaba totalEfectivo (ventas 100% cash) e ignoraba la
                    // parte en efectivo de las ventas con método "mixed", así que el
                    // "esperado" quedaba subestimado cada vez que hubo un pago mixto.
                    val esperado = turno.apertura + _uiState.value.efectivoEnCaja
                    val diferencia = cierreContado - esperado
                    _uiState.value = _uiState.value.copy(turnoRecienCerrado = turno.copy(cierre = cierreContado, diferencia = diferencia))
                    refrescar()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun anularVenta(ventaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            saleRepository.anularVenta(androidIdActual, ventaId)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun limpiarTurnoRecienCerrado() { _uiState.value = _uiState.value.copy(turnoRecienCerrado = null) }
}
