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

    // Evita que un doble-tap en "Acceder" dispare dos validaciones de PIN en
    // paralelo: sin esto, dos taps casi simultáneos pueden pasar los dos la
    // revisión de rateLimiter.estadoActual() ANTES de que el primero llegue
    // a registrarse (ver validarPin), colándose intentos de más frente al
    // límite de PinRateLimiter.
    private var validandoPin = false

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
     * Usado por MainActivity para la ruta de acceso CACHEADO
     * (DeviceVerificationRepository.intentarAccesoCacheado): a diferencia de
     * verificarDispositivo(), esa ruta nunca toca la red ni pasa por este
     * ViewModel, así que usuarioVerificado se quedaba en null — y sin él,
     * validarPin() rechazaba cualquier PIN, incluso el correcto, porque ni
     * llegaba a compararlo (ver el guard `usuario == null` ahí abajo). Esto
     * registra el usuario ya resuelto localmente para que validarPin() tenga
     * con qué comparar, sin repetir ninguna verificación online.
     */
    fun establecerUsuarioCacheado(usuario: User) {
        androidIdActual = usuario.android_id ?: androidIdActual
        _uiState.value = _uiState.value.copy(
            verificando = false,
            usuarioVerificado = usuario,
            mensajeError = null
        )
    }

    /**
     * Valida el PIN contra el hash cacheado (nunca contra texto plano) y
     * aplica rate limiting persistente por usuario: si ya está bloqueado por
     * demasiados intentos fallidos, ni siquiera se llega a comparar el PIN.
     */
    fun validarPin(pin: String, onResultado: (Boolean) -> Unit) {
        // Tap repetido mientras ya hay una validación en curso: se ignora en
        // vez de arrancar una segunda corrutina en paralelo (ver comentario
        // en la declaración de validandoPin). La corrutina en curso es la
        // que manda; ella sola termina de resolver validando/pin en la UI.
        if (validandoPin) return

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

        validandoPin = true
        viewModelScope.launch {
            try {
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
            } finally {
                validandoPin = false
            }
        }
    }

    /**
     * Usado por MainActivity cuando el acceso cacheado (offline-first) pasó
     * la revisión "en caliente" (ver DeviceVerificationRepository.
     * verificarEnCaliente()) y resultó Bloqueado: no se llegó a mostrar el
     * PIN, así que no hay `verificarDispositivo()` de por medio. Esto solo
     * pinta el mensaje de error en VerificarDispositivoScreen y deja el
     * botón "Verificar dispositivo" visible para reintentar manualmente.
     */
    fun mostrarBloqueoPorRevision(mensaje: String) {
        _uiState.value = AccesoUiState(mensajeError = mensaje)
    }

    fun limpiarPinError() {
        _uiState.value = _uiState.value.copy(pinError = null)
    }

    fun reiniciar() {
        _uiState.value = AccesoUiState()
    }
}
