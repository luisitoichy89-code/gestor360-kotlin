package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.data.repository.InventarioRepository
import java.time.LocalDate
import org.luisito.gestor360.ui.util.mensajeAmigable

data class InventarioUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val fecha: LocalDate = LocalDate.now(),
    val dia: InventarioDia? = null,
    val turnoRecienCerrado: InventarioDia? = null,
    val error: String? = null,
    val verSoloTurnoActual: Boolean = true,
    val turnosDelDia: List<TurnoResumen> = emptyList(),
    val turnoSeleccionadoId: Long? = null
) {
    val esHoy: Boolean get() = fecha == LocalDate.now()
}

data class TurnoResumen(
    val id: Long,
    val apertura: String,
    val cierre: String?
)

class InventarioViewModel(
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        cargarFecha(_uiState.value.fecha)
    }

    fun seleccionarFecha(fecha: LocalDate) {
        _uiState.value = _uiState.value.copy(fecha = fecha, turnoSeleccionadoId = null)
        cargarFecha(fecha)
    }

    fun toggleVerTurnoActual() {
        _uiState.value = _uiState.value.copy(
            verSoloTurnoActual = !_uiState.value.verSoloTurnoActual,
            turnoSeleccionadoId = null
        )
    }

    fun seleccionarTurno(turnoId: Long) {
        _uiState.value = _uiState.value.copy(turnoSeleccionadoId = turnoId, verSoloTurnoActual = false)
    }

    private fun cargarFecha(fecha: LocalDate) {
        if (androidIdActual.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getInventarioDia(
                androidIdActual, fecha,
                forzarRefresh = false,
                onActualizadoDesdeServidor = { actualizado ->
                    if (_uiState.value.fecha == fecha) {
                        _uiState.value = _uiState.value.copy(dia = actualizado)
                    }
                }
            )
                .onSuccess { dia ->
                    val turnos = extraerTurnos(dia)
                    _uiState.value = _uiState.value.copy(isLoading = false, dia = dia, turnosDelDia = turnos)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudo cargar el inventario del día")) }
        }
    }

    private fun extraerTurnos(dia: InventarioDia): List<TurnoResumen> {
        val turnos = mutableListOf<TurnoResumen>()
        val turnoActual = dia.turno
        if (turnoActual != null) {
            turnos.add(TurnoResumen(turnoActual.id, turnoActual.created_at ?: "", turnoActual.cierre?.toString()))
        }
        return turnos
    }

    fun refrescar() {
        cargarFecha(_uiState.value.fecha)
    }

    fun cerrarTurno(cierreContado: Double) {
        val turno = _uiState.value.dia?.turno ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.cerrarTurno(androidIdActual, turno.id, cierreContado)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo cerrar el turno")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
