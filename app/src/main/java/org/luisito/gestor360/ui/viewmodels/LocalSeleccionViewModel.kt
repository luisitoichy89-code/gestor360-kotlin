package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.models.Local
import org.luisito.gestor360.data.repository.LocalRepository
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

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
                    val context = AppContextHolder.context
                    val session = SessionManager(context)

                    // Restaurar la selección guardada en sesión, o elegir la primera activa
                    val savedLocalId = session.getLocalId()
                    val actual = _uiState.value.localSeleccionado
                        ?: lista.firstOrNull { it.id == savedLocalId }
                        ?: lista.firstOrNull { it.activo }
                        ?: lista.firstOrNull()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        locales = lista,
                        localSeleccionado = actual
                    )

                    // Aseguramos que la sesión esté actualizada con el local activo
                    actual?.let { session.setLocalId(it.id) }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    /**
     * El admin cambió de local.
     * 1. Actualiza la sesión → ProductRepository filtrará por el nuevo local_id.
     * 2. Limpia el caché de productos → la próxima lectura trae del servidor.
     * Las pantallas de productos/ventas deben observar localSeleccionado y
     * recargar cuando cambie (LaunchedEffect con localId como key).
     */
    fun seleccionar(local: Local) {
        _uiState.value = _uiState.value.copy(localSeleccionado = local)
        val context = AppContextHolder.context
        SessionManager(context).setLocalId(local.id)
        viewModelScope.launch {
            // Limpiar caché de productos para forzar recarga filtrada por nuevo local
            AppDatabase.obtener(context).productoDao().limpiar()
        }
    }
}
