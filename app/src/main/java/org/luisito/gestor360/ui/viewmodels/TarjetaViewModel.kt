package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.data.repository.TarjetaRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

data class TarjetaUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val tarjetas: List<TarjetaEntity> = emptyList(),
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
            repository.getTarjetas(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, tarjetas = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar las tarjetas")) }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual)
    }

    fun crear(nombre: String, tipo: String, numeroCuenta: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.createTarjeta(androidIdActual, nombre, tipo, numeroCuenta)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo guardar la tarjeta")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun editar(id: String, nombre: String, tipo: String, numeroCuenta: String, activo: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.updateTarjeta(androidIdActual, id, nombre, tipo, numeroCuenta, activo)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo actualizar la tarjeta")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun toggleActivo(tarjeta: TarjetaEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.cambiarActivo(androidIdActual, tarjeta, !tarjeta.activo)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo actualizar la tarjeta")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.deleteTarjeta(androidIdActual, id)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo eliminar la tarjeta")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
