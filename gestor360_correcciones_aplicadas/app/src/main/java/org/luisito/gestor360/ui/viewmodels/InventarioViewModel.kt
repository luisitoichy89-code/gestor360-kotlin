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
    val turnosDelDia: List<TurnoInfo> = emptyList(),
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

    fun toggleTurnoSeleccionado(turnoId: Long) {
        val actuales = _uiState.value.turnosSeleccionadosIds
        val nuevos = if (turnoId in actuales) actuales - turnoId else actuales + turnoId
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = nuevos)
        cargarFecha(_uiState.value.fecha, turnoIds = nuevos.toList())
    }

    fun seleccionarTodosLosTurnos() {
        val todos = _uiState.value.turnosDelDia.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = todos)
        cargarFecha(_uiState.value.fecha, turnoIds = emptyList())
    }

    private fun cargarFecha(fecha: LocalDate, turnoIds: List<Long> = emptyList(), forzarRefresh: Boolean = false) {
        if (androidIdActual.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getInventarioDia(
                androidIdActual, fecha,
                forzarRefresh = forzarRefresh,
                turnoIds = turnoIds.ifEmpty { null },
                onActualizadoDesdeServidor = { actualizado ->
                    if (_uiState.value.fecha == fecha) {
                        _uiState.value = _uiState.value.copy(dia = actualizado)
                    }
                }
            )
                .onSuccess { dia -> _uiState.value = _uiState.value.copy(isLoading = false, dia = dia) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudo cargar el inventario del día")) }

            if (esAdminActual && fecha != LocalDate.now() && _uiState.value.turnosDelDia.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingTurnos = true)
                repository.getTurnosDelDia(androidIdActual, fecha)
                    .onSuccess { turnos -> _uiState.value = _uiState.value.copy(turnosDelDia = turnos, isLoadingTurnos = false) }
                    .onFailure { _uiState.value = _uiState.value.copy(isLoadingTurnos = false) }
            }
        }
    }

    fun refrescar() {
        // forzarRefresh = true: antes este botón siempre mandaba false (ver
        // auditoría, Causa raíz C) y con caché existente + online no
        // disparaba ninguna espera visible ni traía nada nuevo de forma
        // perceptible. Con esto, InventarioRepository.getInventarioDia()
        // espera de verdad la respuesta del servidor (mostrando isLoading
        // mientras tanto) y, sin conexión, fusiona la última caché con las
        // ventas locales aún no sincronizadas en vez de fallar o devolver la
        // caché a secas.
        cargarFecha(_uiState.value.fecha, turnoIds = _uiState.value.turnosSeleccionadosIds.toList(), forzarRefresh = true)
    }

    fun cerrarTurno(cierreContado: Double) {
        val turno = _uiState.value.dia?.turno ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.cerrarTurno(androidIdActual, turno.id, cierreContado)
                .onSuccess { nuevoTurnoId ->
                    // NO se fija turnosSeleccionadosIds acá (antes sí, ver
                    // auditoría Causa raíz A / recomendación 4): ese campo es
                    // solo para filtrar turnos de días pasados (se renderiza
                    // gateado por !uiState.esHoy en InventarioScreen, y
                    // cerrarTurno() solo puede llamarse con la fecha activa
                    // en hoy). Dejarlo pegado en el turno recién cerrado hacía
                    // que CUALQUIER refresco posterior de hoy — incluso ya
                    // offline, mucho después de este cierre — reenviara ese
                    // filtro y cayera directo a la rama turnoIds de
                    // getInventarioDia() en vez de usar caché/Room.
                    // turnoIds acá SÍ se pasa, pero solo como parámetro de
                    // esta llamada puntual: refrescarDesdeServidor() ya sabe
                    // tratar "hoy + turnoIds explícito" como equivalente a
                    // "el turno activo" (ver InventarioRepository).
                    cargarFecha(_uiState.value.fecha, turnoIds = listOf(nuevoTurnoId))
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo cerrar el turno")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
