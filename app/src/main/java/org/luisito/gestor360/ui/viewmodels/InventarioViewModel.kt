package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.luisito.gestor360.BuildConfig
import org.luisito.gestor360.data.models.InventarioDia
import org.luisito.gestor360.data.models.TurnoInfo
import org.luisito.gestor360.utils.SessionManager
import org.luisito.gestor360.utils.AppContextHolder
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
    val turnosSeleccionadosIds: Set<Long> = emptySet(),
    val pasosCierre: List<CierrePaso> = emptyList()
) {
    val esHoy: Boolean get() = fecha == LocalDate.now()
}

enum class EstadoPaso { PENDIENTE, EN_PROGRESO, COMPLETADO, ERROR }

data class CierrePaso(
    val nombre: String,
    val estado: EstadoPaso = EstadoPaso.PENDIENTE,
    val detalle: String? = null
)

private val NOMBRES_PASOS_CIERRE = listOf(
    "Revisando órdenes de cola",
    "Analizando ventas",
    "Verificando stock",
    "Revisando devoluciones",
    "Revisando mermas",
    "Verificando aprobaciones pendientes"
)

private const val URL_VALIDAR_STOCK = "https://duspeazziwxptcrignju.supabase.co/functions/v1/validar-punto5"

@Serializable
private data class ValidarStockRequest(
    @SerialName("local_id") val localId: Long,
    @SerialName("turno_id") val turnoId: Long
)

class InventarioViewModel(
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""
    private val session = SessionManager(AppContextHolder.context)
    private val esAdminActual: Boolean get() = session.getRol() == "admin"
    private val httpClient by lazy { HttpClient(Android) }

    fun cargar(androidId: String) {
        androidIdActual = androidId
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

    fun toggleVendedorSeleccionado(turnoIds: List<Long>) {
        if (turnoIds.isEmpty()) return
        val actuales = _uiState.value.turnosSeleccionadosIds
        val nuevos = if (actuales.containsAll(turnoIds)) actuales - turnoIds.toSet() else actuales + turnoIds.toSet()
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
        val esAdmin = esAdminActual
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getInventarioDia(
                androidIdActual, fecha,
                forzarRefresh = forzarRefresh,
                turnoIds = turnoIds.ifEmpty { null },
                vendedorId = if (esAdmin) null else session.getUserId(),
                onActualizadoDesdeServidor = { actualizado ->
                    if (_uiState.value.fecha == fecha) {
                        _uiState.value = _uiState.value.copy(dia = actualizado)
                    }
                }
            )
                .onSuccess { dia -> _uiState.value = _uiState.value.copy(isLoading = false, dia = dia) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudo cargar el inventario del día")) }

            if (esAdmin && _uiState.value.turnosDelDia.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingTurnos = true)
                repository.getTurnosDelDia(androidIdActual, fecha)
                    .onSuccess { turnos -> _uiState.value = _uiState.value.copy(turnosDelDia = turnos, isLoadingTurnos = false) }
                    .onFailure { _uiState.value = _uiState.value.copy(isLoadingTurnos = false) }
            }
        }
    }

    fun refrescar() {
        cargarFecha(_uiState.value.fecha, turnoIds = _uiState.value.turnosSeleccionadosIds.toList(), forzarRefresh = true)
    }

    fun cerrarTurno(cierreContado: Double) {
        val turno = _uiState.value.dia?.turno ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.cerrarTurno(androidIdActual, turno.id, cierreContado)
                .onSuccess { diaEnCero ->
                    _uiState.value = _uiState.value.copy(dia = diaEnCero)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo cerrar el turno")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun verificarCierre() {
        if (androidIdActual.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pasosCierre = NOMBRES_PASOS_CIERRE.map { CierrePaso(nombre = it) }
            )

            actualizarPaso(0, EstadoPaso.EN_PROGRESO)
            repository.contarColaPendiente(androidIdActual)
                .onSuccess { pendientes ->
                    actualizarPaso(0, EstadoPaso.COMPLETADO, detalle = "$pendientes pendiente${if (pendientes == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(0, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudo revisar la cola"))
                }

            actualizarPaso(1, EstadoPaso.EN_PROGRESO)
            repository.contarVentasSinTurno()
                .onSuccess { huerfanas ->
                    actualizarPaso(1, EstadoPaso.COMPLETADO, detalle = "$huerfanas venta${if (huerfanas == 1) "" else "s"} sin turno")
                }
                .onFailure { e ->
                    actualizarPaso(1, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudo analizar las ventas"))
                }

            actualizarPaso(2, EstadoPaso.EN_PROGRESO)
            val turno = _uiState.value.dia?.turno
            if (turno == null) {
                actualizarPaso(2, EstadoPaso.ERROR, detalle = "No hay turno cargado")
            } else {
                try {
                    val respuesta = httpClient.post(URL_VALIDAR_STOCK) {
                        header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(ValidarStockRequest(localId = turno.localId, turnoId = turno.id)))
                    }
                    if (respuesta.status.isSuccess()) {
                        actualizarPaso(2, EstadoPaso.COMPLETADO, detalle = respuesta.bodyAsText())
                    } else {
                        actualizarPaso(2, EstadoPaso.ERROR, detalle = "Error ${respuesta.status.value}")
                    }
                } catch (e: Exception) {
                    actualizarPaso(2, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudo verificar el stock"))
                }
            }

            actualizarPaso(3, EstadoPaso.EN_PROGRESO)
            repository.contarDevolucionesPendientes()
                .onSuccess { pendientes ->
                    actualizarPaso(3, EstadoPaso.COMPLETADO, detalle = "$pendientes devolución${if (pendientes == 1) "" else "es"} pendiente${if (pendientes == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(3, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudieron revisar las devoluciones"))
                }

            actualizarPaso(4, EstadoPaso.EN_PROGRESO)
            repository.contarMermasPendientes()
                .onSuccess { pendientes ->
                    actualizarPaso(4, EstadoPaso.COMPLETADO, detalle = "$pendientes merma${if (pendientes == 1) "" else "s"} pendiente${if (pendientes == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(4, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudieron revisar las mermas"))
                }

            actualizarPaso(5, EstadoPaso.EN_PROGRESO)
            repository.haySolicitudesPendientes()
                .onSuccess { pendientes ->
                    actualizarPaso(5, EstadoPaso.COMPLETADO, detalle = "$pendientes aprobación${if (pendientes == 1) "" else "es"} pendiente${if (pendientes == 1) "" else "s"}")
                }
                .onFailure { e ->
                    actualizarPaso(5, EstadoPaso.ERROR, detalle = e.mensajeAmigable("No se pudieron revisar las aprobaciones"))
                }
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
        httpClient.close()
    }
}
