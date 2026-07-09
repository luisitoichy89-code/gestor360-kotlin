package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
                    val actual = _uiState.value.localSeleccionado
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        locales = lista,
                        localSeleccionado = actual ?: lista.firstOrNull { it.activo } ?: lista.firstOrNull()
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun seleccionar(local: Local) {
        _uiState.value = _uiState.value.copy(localSeleccionado = local)
        val sm = SessionManager(AppContextHolder.context)
        sm.updateLocalId(local.id)
    }
}
