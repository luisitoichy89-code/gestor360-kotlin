package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.TurnoInfo
import org.luisito.gestor360.data.repository.InventarioRepository

data class HistorialTurnosUiState(
    val isLoading: Boolean = false,
    val turnos: List<TurnoInfo> = emptyList(),
    val error: String? = null
)

class HistorialTurnosViewModel : ViewModel() {
    private val repository = InventarioRepository()
    private val _uiState = MutableStateFlow(HistorialTurnosUiState())
    val uiState: StateFlow<HistorialTurnosUiState> = _uiState.asStateFlow()

    fun cargar(androidId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getTurnosDelDia(androidId)
                .onSuccess { turnos ->
                    _uiState.value = _uiState.value.copy(isLoading = false, turnos = turnos)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }
}
