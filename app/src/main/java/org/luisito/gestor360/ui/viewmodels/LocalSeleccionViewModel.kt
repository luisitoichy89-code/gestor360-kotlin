package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Local
import org.luisito.gestor360.data.repository.LocalRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import org.luisito.gestor360.ui.util.mensajeAmigable

data class LocalSeleccionUiState(
    val isLoading: Boolean = false,
    val locales: List<Local> = emptyList(),
    val localSeleccionado: Local? = null,
    val error: String? = null
)

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
                    if (idActivo == null && actual != null) {
                        sm.setLocalId(actual.id)
                    }
                    // Precargar caché de todos los locales en segundo plano
                    if (NetworkMonitor.hayInternet(AppContextHolder.context)) {
                        precargarDatosLocales(androidId, lista)
                    }
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar los locales")) }
        }
    }

    private fun precargarDatosLocales(androidId: String, locales: List<Local>) {
        val context = AppContextHolder.context
        val productRepo = ProductRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            for (local in locales) {
                try {
                    productRepo.precargarLocal(androidId, local.id)
                } catch (_: Exception) { }
            }
        }
    }

    fun seleccionar(local: Local) {
        _uiState.value = _uiState.value.copy(localSeleccionado = local)
        val sm = SessionManager(AppContextHolder.context)
        sm.setLocalId(local.id)

        viewModelScope.launch {
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
