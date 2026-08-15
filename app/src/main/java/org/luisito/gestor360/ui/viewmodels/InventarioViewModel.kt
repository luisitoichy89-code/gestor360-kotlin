package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.data.models.TurnoInfo
import org.luisito.gestor360.utils.SessionManager
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.data.repository.InventarioRepository
import org.luisito.gestor360.data.repository.TurnoRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

data class InventarioUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingTurnos: Boolean = false,
    val dia: InventarioDia? = null,
    val error: String? = null,
    val turnosDelDia: List<TurnoInfo> = emptyList(),
    val turnosSeleccionadosIds: Set<Long> = emptySet(),
    val turnoHistoricoId: Long? = null,
    val pasosCierre: List<CierrePaso> = emptyList(),
    val cierreExitoso: Boolean = false,
    val verificacionTerminada: Boolean = false,
    val hayErrores: Boolean = false,
    val pendientesCierre: List<String> = emptyList()
)

enum class EstadoPaso { PENDIENTE, EN_PROGRESO, COMPLETADO, ERROR }

data class CierrePaso(
    val nombre: String,
    val estado: EstadoPaso = EstadoPaso.PENDIENTE,
    val detalle: String? = null
)

private val NOMBRES_PASOS_CIERRE = listOf(
    "Revisando órdenes de cola",
    "Analizando ventas",
    "Revisando devoluciones",
    "Revisando mermas",
    "Verificando aprobaciones pendientes"
)

class InventarioViewModel(
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""
    private var esVistaPersonalActual = false
    private val session = SessionManager(AppContextHolder.context)
    private val esAdminActual: Boolean get() = session.getRol() == "admin"
    private val turnoRepository = TurnoRepository()

    fun cargar(androidId: String, esVistaPersonal: Boolean = false) {
        androidIdActual = androidId
        esVistaPersonalActual = esVistaPersonal
        _uiState.value = _uiState.value.copy(turnoHistoricoId = null)
        cargarInventario()
    }

    fun cargarTurnoHistorico(androidId: String, turnoId: Long) {
        androidIdActual = androidId
        esVistaPersonalActual = false
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, error = null, turnoHistoricoId = turnoId
            )
            repository.getInventarioDia(
                androidId,
                turnoIds = listOf(turnoId),
                soloLectura = true
            )
                .onSuccess { dia -> _uiState.value = _uiState.value.copy(isLoading = false, dia = dia) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.mensajeAmigable("No se pudo cargar ese turno")
                    )
                }
        }
    }

    fun toggleTurnoSeleccionado(turnoId: Long) {
        val actuales = _uiState.value.turnosSeleccionadosIds
        val nuevos = if (turnoId in actuales) actuales - turnoId else actuales + turnoId
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = nuevos)
        cargarInventario(turnoIds = nuevos.toList())
    }

    fun toggleVendedorSeleccionado(turnoIds: List<Long>) {
        if (turnoIds.isEmpty()) return
        val actuales = _uiState.value.turnosSeleccionadosIds
        val nuevos = if (actuales.containsAll(turnoIds)) actuales - turnoIds.toSet() else actuales + turnoIds.toSet()
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = nuevos)
        cargarInventario(turnoIds = nuevos.toList())
    }

    fun seleccionarTodosLosTurnos() {
        val todos = _uiState.value.turnosDelDia.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(turnosSeleccionadosIds = todos)
        cargarInventario(turnoIds = emptyList())
    }

    private fun cargarInventario(turnoIds: List<Long> = emptyList(), forzarRefresh: Boolean = false) {
        if (androidIdActual.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getInventarioDia(
                androidIdActual,
                forzarRefresh = forzarRefresh,
                turnoIds = turnoIds.ifEmpty { null },
                onActualizadoDesdeServidor = { actualizado ->
                    _uiState.value = _uiState.value.copy(dia = actualizado)
                }
            )
                .onSuccess { dia -> _uiState.value = _uiState.value.copy(isLoading = false, dia = dia) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudo cargar el inventario")) }

            if (esAdminActual && !esVistaPersonalActual && _uiState.value.turnosDelDia.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingTurnos = true)
                repository.getTurnosDelDia(androidIdActual)
                    .onSuccess { turnos -> _uiState.value = _uiState.value.copy(turnosDelDia = turnos, isLoadingTurnos = false) }
                    .onFailure { _uiState.value = _uiState.value.copy(isLoadingTurnos = false) }
            }
        }
    }

    fun refrescar() {
        val historicoId = _uiState.value.turnoHistoricoId
        if (historicoId != null) {
            cargarTurnoHistorico(androidIdActual, historicoId)
        } else {
            cargarInventario(turnoIds = _uiState.value.turnosSeleccionadosIds.toList(), forzarRefresh = true)
        }
    }

    fun cerrarTurno(cierreContado: Double) {
        val dia = _uiState.value.dia
        if (dia == null || dia.ventas.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "No se puede cerrar un turno sin al menos una venta (efectivo, transferencia o mixto)"
            )
            return
        }
        val turnoId = dia.turno?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.cerrarTurno(androidIdActual, turnoId, cierreContado)
                .onSuccess { diaEnCero ->
                    _uiState.value = _uiState.value.copy(
                        dia = diaEnCero,
                        isSaving = false,
                        cierreExitoso = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.mensajeAmigable("No se pudo cerrar el turno")
                    )
                }
        }
    }

    fun verificarCierre() {
        if (androidIdActual.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pasosCierre = NOMBRES_PASOS_CIERRE.map { CierrePaso(nombre = it) },
                verificacionTerminada = false,
                pendientesCierre = emptyList()
            )
            val pendientes = mutableListOf<String>()

            actualizarPaso(0, EstadoPaso.EN_PROGRESO)
            delay(2000)
            repository.contarColaPendiente(androidIdActual)
                .onSuccess { p ->
                    actualizarPaso(0, EstadoPaso.COMPLETADO, detalle = "$p pendiente${if (p == 1) "" else "s"}")
                    if (p > 0) pendientes.add("$p órden${if (p == 1) "" else "es"} en cola de sincronización")
                }
                .onFailure { e ->
                    actualizarPaso(0, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudo revisar la cola"))
                    pendientes.add("No se pudo revisar la cola de sincronización")
                }

            actualizarPaso(1, EstadoPaso.EN_PROGRESO)
            delay(2000)
            repository.contarVentasSinTurno()
                .onSuccess { h ->
                    val estado = if (h > 0) EstadoPaso.ERROR else EstadoPaso.COMPLETADO
                    actualizarPaso(1, estado, detalle = "$h venta${if (h == 1) "" else "s"} sin turno")
                    if (h > 0) pendientes.add("$h venta${if (h == 1) "" else "s"} sin turno asignado")
                }
                .onFailure { e ->
                    actualizarPaso(1, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudo analizar las ventas"))
                    pendientes.add("No se pudo analizar las ventas")
                }

            actualizarPaso(2, EstadoPaso.EN_PROGRESO)
            delay(2000)
            repository.contarDevolucionesPendientes()
                .onSuccess { d ->
                    val estado = if (d > 0) EstadoPaso.ERROR else EstadoPaso.COMPLETADO
                    actualizarPaso(2, estado, detalle = "$d devolución${if (d == 1) "" else "es"} pendiente${if (d == 1) "" else "s"}")
                    if (d > 0) pendientes.add("$d devolución${if (d == 1) "" else "es"} pendiente${if (d == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(2, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudieron revisar las devoluciones"))
                    pendientes.add("No se pudieron revisar las devoluciones")
                }

            actualizarPaso(3, EstadoPaso.EN_PROGRESO)
            delay(2000)
            repository.contarMermasPendientes()
                .onSuccess { m ->
                    val estado = if (m > 0) EstadoPaso.ERROR else EstadoPaso.COMPLETADO
                    actualizarPaso(3, estado, detalle = "$m merma${if (m == 1) "" else "s"} pendiente${if (m == 1) "" else "s"}")
                    if (m > 0) pendientes.add("$m merma${if (m == 1) "" else "s"} pendiente${if (m == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(3, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudieron revisar las mermas"))
                    pendientes.add("No se pudieron revisar las mermas")
                }

            actualizarPaso(4, EstadoPaso.EN_PROGRESO)
            delay(2000)
            repository.haySolicitudesPendientes()
                .onSuccess { a ->
                    val estado = if (a > 0) EstadoPaso.ERROR else EstadoPaso.COMPLETADO
                    actualizarPaso(4, estado, detalle = "$a aprobación${if (a == 1) "" else "es"} pendiente${if (a == 1) "" else "s"}")
                    if (a > 0) pendientes.add("$a aprobación${if (a == 1) "" else "es"} de stock pendiente${if (a == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(4, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudieron revisar las aprobaciones"))
                    pendientes.add("No se pudieron revisar las aprobaciones")
                }

            val hayErrores = pendientes.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                verificacionTerminada = true,
                hayErrores = hayErrores,
                pendientesCierre = pendientes
            )
        }
    }

    private fun actualizarPaso(indice: Int, estado: EstadoPaso, detalle: String? = null) {
        val actuales = _uiState.value.pasosCierre.toMutableList()
        if (indice !in actuales.indices) return
        actuales[indice] = actuales[indice].copy(estado = estado, detalle = detalle)
        _uiState.value = _uiState.value.copy(pasosCierre = actuales)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
