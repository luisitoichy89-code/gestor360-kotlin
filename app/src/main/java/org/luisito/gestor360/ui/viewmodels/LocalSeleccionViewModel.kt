package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.models.Local
import org.luisito.gestor360.data.repository.LocalRepository
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import org.luisito.gestor360.ui.util.mensajeAmigable

data class LocalSeleccionUiState(
    val isLoading: Boolean = false,
    val locales: List<Local> = emptyList(),
    val localSeleccionado: Local? = null,
    val error: String? = null
)

/**
 * Dueño de la selección de "local activo". Es la ÚNICA vía por la que el
 * local_id activo cambia: al elegir, lo persiste en SessionManager (fuente de
 * verdad que leen TODOS los repositorios para armar p_local_id) y limpia el
 * caché Room, porque el caché de Producto/Venta/Tarjeta/Merma/Turno queda
 * filtrado por local_id y mezclar datos de dos locales en la misma tabla
 * local sería exactamente el mismo bug que estamos arreglando, solo que
 * offline.
 */
class LocalSeleccionViewModel(
    private val repository: LocalRepository = LocalRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalSeleccionUiState())
    val uiState: StateFlow<LocalSeleccionUiState> = _uiState.asStateFlow()

    fun cargar(androidId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getLocales(androidId)
                .onSuccess { lista ->
                    val sm = SessionManager(AppContextHolder.context)
                    val idActivo = sm.getLocalId()
                    val actual = lista.firstOrNull { it.id == idActivo }
                        ?: lista.firstOrNull { it.activo }
                        ?: lista.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        locales = lista,
                        localSeleccionado = actual
                    )
                    // Si el dispositivo no tenía local_id guardado todavía (primer login
                    // de un admin con varios locales), se fija el default recién resuelto.
                    if (idActivo == null && actual != null) {
                        sm.setLocalId(actual.id)
                    }
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar los locales")) }
        }
    }

    fun seleccionar(local: Local) {
        _uiState.value = _uiState.value.copy(localSeleccionado = local)
        val context = AppContextHolder.context
        val sm = SessionManager(context)
        sm.setLocalId(local.id)

        viewModelScope.launch {
            // El caché local está filtrado por local_id: al cambiar de local activo,
            // se limpia para no mezclar (ni mostrar por un instante) datos del local anterior.
            val db = AppDatabase.obtener(context)
            db.productoDao().limpiar()
            db.tarjetaDao().limpiar()
            db.mermaDao().limpiarTodas()
            db.turnoDao().limpiarTodos()
            db.ventaDao().limpiarSincronizadas()

            if (NetworkMonitor.hayInternet(context)) SyncWorker.sincronizarAhora(context)

            // Registro informativo en el servidor (auditoría / soporte), no es la fuente
            // de verdad — si falla o el dispositivo está offline, el cambio de local ya
            // quedó aplicado igual porque vive en SessionManager.
            try {
                SupabaseClientProvider.client.postgrest
                    .from("local_seleccion_context")
                    .upsert(buildJsonObject {
                        put("android_id", sm.getAndroidId())
                        put("local_id", local.id)
                    })
            } catch (_: Exception) { }
        }
    }
}
