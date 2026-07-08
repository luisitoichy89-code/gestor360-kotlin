package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.repository.TarjetaRepository

data class TarjetaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val tarjetas: List<Tarjeta> = emptyList(),
    val error: String? = null
)

class TarjetaViewModel(
    private val repository: TarjetaRepository = TarjetaRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TarjetaUiState())
    val uiState: StateFlow<TarjetaUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.limpiarCache()
            repository.getTarjetas(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, tarjetas = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual)
    }

    fun crear(banco: String, numero: String, titular: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.crearTarjeta(androidIdActual, banco, numero, titular)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun editar(id: Long, banco: String, numero: String, titular: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.editarTarjeta(androidIdActual, id, banco, numero, titular)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun toggleActivo(tarjeta: Tarjeta) {
        viewModelScope.launch {
            repository.setActivo(androidIdActual, tarjeta.id, !tarjeta.activo).onSuccess { refrescar() }
        }
    }

    fun eliminar(id: Long) {
        viewModelScope.launch {
            repository.eliminarTarjeta(androidIdActual, id).onSuccess { refrescar() }
        }
    }
}
