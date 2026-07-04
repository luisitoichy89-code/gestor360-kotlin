package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Turno
import org.luisito.gestor360.data.repository.TurnoRepository

data class TurnoUiState(
    val isLoading: Boolean = false,
    val turnoAbierto: Turno? = null,
    val historial: List<Turno> = emptyList(),
    val resumenCierre: Turno? = null,
    val error: String? = null,
    val mensaje: String? = null
)

class TurnoViewModel(
    private val repository: TurnoRepository = TurnoRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(TurnoUiState())
    val uiState: StateFlow<TurnoUiState> = _uiState.asStateFlow()
    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getTurnoAbierto(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, turnoAbierto = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            repository.getHistorialTurnos(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(historial = it) }
        }
    }

    fun abrirTurno(efectivoInicial: Double, usuarioId: Long, almacenId: String) {
        viewModelScope.launch {
            repository.abrirTurno(androidIdActual, efectivoInicial, usuarioId, almacenId)
                .onSuccess { cargar(androidIdActual); _uiState.value = _uiState.value.copy(mensaje = "Turno abierto") }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun cerrarTurno(turnoId: Long) {
        viewModelScope.launch {
            repository.cerrarTurno(androidIdActual, turnoId)
                .onSuccess { _uiState.value = _uiState.value.copy(resumenCierre = it, turnoAbierto = null, mensaje = "Turno cerrado"); cargar(androidIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun clearMensaje() { _uiState.value = _uiState.value.copy(mensaje = null) }
    fun clearResumen() { _uiState.value = _uiState.value.copy(resumenCierre = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
