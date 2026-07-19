package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.data.models.TurnoInfo
import org.luisito.gestor360.data.repository.InventarioRepository
import java.time.LocalDate
import org.luisito.gestor360.ui.util.mensajeAmigable

data class InventarioUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingTurnos: Boolean = false,
    val fecha: LocalDate = LocalDate.now(),
    val dia: InventarioDia? = null,
    val error: String? = null,
    // Turnos que hubo en `fecha` (solo se pueblan para el admin al elegir una
    // fecha pasada; ver InventarioRepository.getTurnosDelDia).
    val turnosDelDia: List<TurnoInfo> = emptyList(),
    // Turnos marcados con ✓. Vacío = ver todos.
    val turnosSeleccionadosIds: Set<Long> = emptySet()
) {
    val esHoy: Boolean get() = fecha == LocalDate.now()
}

class InventarioViewModel(
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""
    private var esAdminActual = false

    fun cargar(androidId: String, esAdmin: Boolean = false) {
        androidIdActual = androidId
        esAdminActual = esAdmin
        cargarFecha(_uiState.value.fecha)
    }

    fun seleccionarFecha(fecha: LocalDate) {
        _uiState.value = _uiState.value.copy(fecha = fecha, turnosDelDia = emptyList(), turnosSeleccionadosIds = emptySet())
        cargarFecha(fecha)
    }

    /** Marca/desmarca un turno con ✓. Cada cambio vuelve a pedir el día filtrado por lo seleccionado. */
    fun toggleTurnoSeleccionado(turnoId: Long) {
        val actuales = _uiState.value.turnosSeleccionadosIds
        val nuevos = if (turnoId in actuales) actuales - turnoId else actuales + turnoId
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = nuevos)
        cargarFecha(_uiState.value.fecha, turnoIds = nuevos.toList())
    }

    /** "Seleccionar todos" -> marca todos los turnos del día y quita el filtro (se ve el día completo). */
    fun seleccionarTodosLosTurnos() {
        val todos = _uiState.value.turnosDelDia.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = todos)
        cargarFecha(_uiState.value.fecha, turnoIds = emptyList())
    }

    private fun cargarFecha(fecha: LocalDate, turnoIds: List<Long> = emptyList()) {
        if (androidIdActual.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getInventarioDia(
                androidIdActual, fecha,
                forzarRefresh = false,
                turnoIds = turnoIds.ifEmpty { null },
                onActualizadoDesdeServidor = { actualizado ->
                    if (_uiState.value.fecha == fecha) {
                        _uiState.value = _uiState.value.copy(dia = actualizado)
                    }
                }
            )
                .onSuccess { dia -> _uiState.value = _uiState.value.copy(isLoading = false, dia = dia) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudo cargar el inventario del día")) }

            // Solo tiene sentido pedir la lista de turnos del día una vez, al
            // entrar a una fecha pasada, y solo para el admin.
            if (esAdminActual && fecha != LocalDate.now() && _uiState.value.turnosDelDia.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingTurnos = true)
                repository.getTurnosDelDia(androidIdActual, fecha)
                    .onSuccess { turnos -> _uiState.value = _uiState.value.copy(turnosDelDia = turnos, isLoadingTurnos = false) }
                    .onFailure { _uiState.value = _uiState.value.copy(isLoadingTurnos = false) }
            }
        }
    }

    fun refrescar() {
        cargarFecha(_uiState.value.fecha, turnoIds = _uiState.value.turnosSeleccionadosIds.toList())
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
