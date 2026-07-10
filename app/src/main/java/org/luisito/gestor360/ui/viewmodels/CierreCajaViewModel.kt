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
    val turnoActivo: Turno? = null,
    val ventasDelDia: List<Pair<Sale, String>> = emptyList(),
    val efectivoEsperado: Double = 0.0,
    val totalVendido: Double = 0.0,
    val totalEfectivoVentas: Double = 0.0,
    val totalTransferenciaVentas: Double = 0.0,
    val totalMixtoEfectivo: Double = 0.0,
    val totalMixtoTransferencia: Double = 0.0,
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
            try {
                val turno = turnoRepository.obtenerTurnoActivo(androidId).getOrNull()
                val ventas = saleRepository.getSalesConNombre(androidId).getOrDefault(emptyList())

                var efectivo = 0.0
                var transferencia = 0.0
                var mixtoEfectivo = 0.0
                var mixtoTransferencia = 0.0

                ventas.forEach { (venta, _) ->
                    when (venta.metodo) {
                        "cash" -> efectivo += venta.efectivo
                        "transfer" -> transferencia += venta.transferencia
                        "mixed" -> {
                            mixtoEfectivo += venta.efectivo
                            mixtoTransferencia += venta.transferencia
                        }
                        "transfer_visual" -> transferencia += venta.transferencia
                        "mixed_visual" -> {
                            mixtoEfectivo += venta.efectivo
                            mixtoTransferencia += venta.transferencia
                        }
                    }
                }

                val totalEfectivo = efectivo + mixtoEfectivo
                val totalTransferencia = transferencia + mixtoTransferencia
                val totalVendido = totalEfectivo + totalTransferencia
                val efectivoEsperado = (turno?.apertura ?: 0.0) + totalEfectivo

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    turnoActivo = turno,
                    ventasDelDia = ventas,
                    efectivoEsperado = efectivoEsperado,
                    totalVendido = totalVendido,
                    totalEfectivoVentas = efectivo,
                    totalTransferenciaVentas = transferencia,
                    totalMixtoEfectivo = mixtoEfectivo,
                    totalMixtoTransferencia = mixtoTransferencia
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // El turno se abre automáticamente con la primera acción del día (fn_asegurar_turno_abierto en SQL)
    fun cerrarTurno(cierre: Double) {
        val turno = _uiState.value.turnoActivo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            turnoRepository.cerrarTurno(androidIdActual, turno.id, cierre)
                .onSuccess { cargar(androidIdActual) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isSaving = false, error = e.message) }
        }
    }

    fun anularVenta(ventaId: String) {
        viewModelScope.launch {
            saleRepository.anularVenta(androidIdActual, ventaId)
                .onSuccess { cargar(androidIdActual) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }
}
