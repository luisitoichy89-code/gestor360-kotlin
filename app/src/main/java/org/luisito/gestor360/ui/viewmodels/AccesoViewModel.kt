package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.data.repository.DeviceVerificationRepository
import org.luisito.gestor360.data.repository.VerificacionResultado

data class AccesoUiState(
    val verificando: Boolean = false,
    val usuarioVerificado: User? = null,
    val mensajeError: String? = null,
    val pinError: String? = null
)

class AccesoViewModel(
    private val repository: DeviceVerificationRepository = DeviceVerificationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccesoUiState())
    val uiState: StateFlow<AccesoUiState> = _uiState.asStateFlow()

    fun verificarDispositivo(androidId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(verificando = true, mensajeError = null)
            when (val resultado = repository.verificar(androidId)) {
                is VerificacionResultado.Autorizado ->
                    _uiState.value = _uiState.value.copy(verificando = false, usuarioVerificado = resultado.usuario)
                is VerificacionResultado.DispositivoNoAutorizado ->
                    _uiState.value = _uiState.value.copy(verificando = false, mensajeError = "Este dispositivo no está autorizado. Pide al admin que registre tu Android ID.")
                is VerificacionResultado.UsuarioInactivo ->
                    _uiState.value = _uiState.value.copy(verificando = false, mensajeError = "Tu usuario está desactivado. Contacta al admin del negocio.")
                is VerificacionResultado.LicenciaInactiva ->
                    _uiState.value = _uiState.value.copy(verificando = false, mensajeError = "La licencia del negocio no está activa. Contacta al admin.")
                is VerificacionResultado.LicenciaVencida ->
                    _uiState.value = _uiState.value.copy(verificando = false, mensajeError = "La licencia del negocio venció hace ${resultado.diasVencida} días. Debe renovarse para continuar.")
                is VerificacionResultado.Error ->
                    _uiState.value = _uiState.value.copy(verificando = false, mensajeError = resultado.mensaje)
            }
        }
    }

    fun validarPin(pin: String): Boolean {
        val usuario = _uiState.value.usuarioVerificado ?: return false
        val correcto = repository.validarPin(usuario, pin)
        if (!correcto) _uiState.value = _uiState.value.copy(pinError = "PIN incorrecto")
        return correcto
    }

    fun limpiarPinError() {
        _uiState.value = _uiState.value.copy(pinError = null)
    }

    fun reiniciar() {
        _uiState.value = AccesoUiState()
    }
}
