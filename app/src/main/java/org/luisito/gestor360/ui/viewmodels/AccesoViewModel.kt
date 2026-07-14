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
import org.luisito.gestor360.security.PinRateLimiter
import org.luisito.gestor360.utils.AppContextHolder

data class AccesoUiState(
    val verificando: Boolean = false,
    val usuarioVerificado: User? = null,
    val mensajeError: String? = null,
    val pinError: String? = null,
    val pinBloqueado: Boolean = false,
    val pinBloqueadoSegundos: Long = 0L
)

class AccesoViewModel(
    private val repository: DeviceVerificationRepository = DeviceVerificationRepository(),
    private val rateLimiter: PinRateLimiter = PinRateLimiter(AppContextHolder.context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccesoUiState())
    val uiState: StateFlow<AccesoUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun verificarDispositivo(androidId: String) {
        androidIdActual = androidId
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
                is VerificacionResultado.SinConexionPrimerInicio ->
                    _uiState.value = _uiState.value.copy(verificando = false, mensajeError = "No hay conexión y este dispositivo nunca se verificó antes. Conéctate a internet al menos una vez para activar el acceso offline.")
                is VerificacionResultado.Error ->
                    _uiState.value = _uiState.value.copy(
                        verificando = false,
                        mensajeError = resultado.mensaje.takeIf { it.isNotBlank() && it.length < 100 }
                            ?: "No se pudo verificar el dispositivo. Verifica tu conexión e intenta de nuevo."
                    )
            }
        }
    }

    /**
     * Valida el PIN contra el hash cacheado (nunca contra texto plano) y
     * aplica rate limiting persistente por usuario: si ya está bloqueado por
     * demasiados intentos fallidos, ni siquiera se llega a comparar el PIN.
     */
    fun validarPin(pin: String, onResultado: (Boolean) -> Unit) {
        val usuario = _uiState.value.usuarioVerificado
        val key = usuario?.android_id ?: androidIdActual
        if (usuario == null || key.isBlank()) { onResultado(false); return }

        val estadoPrevio = rateLimiter.estadoActual(key)
        if (estadoPrevio.bloqueado) {
            _uiState.value = _uiState.value.copy(
                pinBloqueado = true, pinBloqueadoSegundos = estadoPrevio.segundosRestantes,
                pinError = "Demasiados intentos. Espera ${estadoPrevio.segundosRestantes}s."
            )
            onResultado(false)
            return
        }

        viewModelScope.launch {
            val correcto = repository.validarPinLocal(key, pin)
            if (correcto) {
                rateLimiter.registrarExito(key)
                _uiState.value = _uiState.value.copy(pinError = null, pinBloqueado = false, pinBloqueadoSegundos = 0L)
            } else {
                val estado = rateLimiter.registrarFallo(key)
                _uiState.value = _uiState.value.copy(
                    pinError = if (estado.bloqueado) "Demasiados intentos. Espera ${estado.segundosRestantes}s." else "El PIN no es correcto. Intenta de nuevo.",
                    pinBloqueado = estado.bloqueado,
                    pinBloqueadoSegundos = estado.segundosRestantes
                )
            }
            onResultado(correcto)
        }
    }

    fun limpiarPinError() {
        _uiState.value = _uiState.value.copy(pinError = null)
    }

    fun reiniciar() {
        _uiState.value = AccesoUiState()
    }
}
