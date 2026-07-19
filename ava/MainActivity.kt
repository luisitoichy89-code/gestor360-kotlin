package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import org.luisito.gestor360.ui.components.FeedbackBar
import org.luisito.gestor360.ui.components.FeedbackViewModel
import org.luisito.gestor360.ui.components.LocalFeedback
import org.luisito.gestor360.ui.components.FeedbackTipo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.FotoRepository
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.data.models.Local
import org.luisito.gestor360.data.repository.DeviceVerificationRepository
import org.luisito.gestor360.data.repository.VerificacionEnCalienteResultado
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.ui.components.AvatarUsuario
import org.luisito.gestor360.ui.components.BotonTema
import org.luisito.gestor360.ui.components.SyncStatusBar
import org.luisito.gestor360.ui.components.VerificarActualizacion
import org.luisito.gestor360.ui.screens.*
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.theme.NeuCard
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.ui.viewmodels.LocalSeleccionViewModel
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.DeviceIdManager
import org.luisito.gestor360.utils.SessionManager
import org.luisito.gestor360.utils.ThemeManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.init(applicationContext)
        SyncWorker.programarPeriodico(applicationContext)
        setContent { Gestor360App() }
    }
}

private sealed class PantallaInterna {
    object Home : PantallaInterna()
    object Ventas : PantallaInterna()
    object Carrito : PantallaInterna()
    object Productos : PantallaInterna()
    object Tarjetas : PantallaInterna()
    object Aprobaciones : PantallaInterna()
    object Inventario : PantallaInterna()
    object Devolucion : PantallaInterna()
    object Conflictos : PantallaInterna()
    object MisVentas : PantallaInterna()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gestor360App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val temaOscuro by ThemeManager.observarTemaOscuro(context).collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Gestor360Theme(darkTheme = temaOscuro) {
        Gestor360AppContenido(
            temaOscuro = temaOscuro,
            onCambiarTema = { scope.launch { ThemeManager.alternarTema(context) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Gestor360AppContenido(temaOscuro: Boolean, onCambiarTema: () -> Unit) {
    var mostrarSplash by remember { mutableStateOf(true) }
    if (mostrarSplash) {
        SplashScreen(onFinished = { mostrarSplash = false })
        return
    }

    VerificarActualizacion()

    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val fotoRepository = remember { FotoRepository(AppDatabase.obtener(context).userDao()) }
    val deviceVerificationRepository = remember { DeviceVerificationRepository() }
    val scope = rememberCoroutineScope()
    val accesoViewModel: AccesoViewModel = viewModel()
    val localSeleccionViewModel: LocalSeleccionViewModel = viewModel()
    var isLoading by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var usuarioParaPin by remember { mutableStateOf<User?>(null) }
    var fotoUsuarioPin by remember { mutableStateOf<ByteArray?>(null) }
    var fotoUsuarioLogueado by remember { mutableStateOf<ByteArray?>(null) }
    var pantalla by remember { mutableStateOf<PantallaInterna>(PantallaInterna.Home) }
    var mostrarConfirmarSalir by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var localRecienCambiado by remember { mutableStateOf<Local?>(null) }

    LaunchedEffect(usuarioParaPin) {
        fotoUsuarioPin = usuarioParaPin?.let { it.android_id?.let { id -> fotoRepository.obtenerFoto(id) } }
    }

    LaunchedEffect(isLoggedIn) {
        fotoUsuarioLogueado = if (isLoggedIn) fotoRepository.obtenerFoto(sessionManager.getAndroidId()) else null
    }

    fun onFotoDashboardSeleccionada(bytes: ByteArray) {
        fotoUsuarioLogueado = bytes
        scope.launch { fotoRepository.guardarFoto(sessionManager.getAndroidId(), bytes) }
    }

    LaunchedEffect(localRecienCambiado) {
        localRecienCambiado?.let { local ->
            snackbarHostState.showSnackbar("Cambiado a: ${local.nombre}")
        }
    }

    LaunchedEffect(Unit) {
        if (sessionManager.haySesionRevocadaPersistida()) {
            sessionManager.clear()
            sessionManager.limpiarSesionRevocada()
        }

        isLoggedIn = sessionManager.isLoggedIn()
        if (!isLoggedIn) {
            val androidId = DeviceIdManager.getFormattedDeviceId(context)
            val cacheado = deviceVerificationRepository.intentarAccesoCacheado(androidId)

            if (cacheado != null && NetworkMonitor.hayInternet(context)) {
                when (val resultado = deviceVerificationRepository.verificarEnCaliente(androidId)) {
                    is VerificacionEnCalienteResultado.Bloqueado -> {
                        accesoViewModel.mostrarBloqueoPorRevision(resultado.mensaje)
                        usuarioParaPin = null
                    }
                    else -> {
                        usuarioParaPin = cacheado
                        accesoViewModel.establecerUsuarioCacheado(cacheado)
                    }
                }
            } else {
                usuarioParaPin = cacheado
                cacheado?.let { accesoViewModel.establecerUsuarioCacheado(it) }
            }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        NetworkMonitor.observar(context).collect { hayInternet ->
            if (hayInternet && sessionManager.isLoggedIn()) {
                SyncWorker.sincronizarAhora(context)
            }
        }
    }

    fun cerrarSesion() {
        sessionManager.clear()
        accesoViewModel.reiniciar()
        usuarioParaPin = null
        isLoggedIn = false
        pantalla = PantallaInterna.Home
    }

    LaunchedEffect(Unit) {
        SessionManager.sesionRevocada.collect { revocada ->
            if (revocada && sessionManager.isLoggedIn()) {
                sessionManager.limpiarSesionRevocada()
                cerrarSesion()
            }
        }
    }

    if (isLoggedIn) {
        BackHandler(enabled = true) {
            if (pantalla != PantallaInterna.Home) pantalla = PantallaInterna.Home
            else mostrarConfirmarSalir = true
        }
    }

    if (mostrarConfirmarSalir) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarSalir = false },
            title = { Text("¿Salir de Gestor360?") },
            text = { Text("Vas a cerrar la aplicación.") },
            confirmButton = { TextButton(onClick = { (context as? ComponentActivity)?.finish() }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { mostrarConfirmarSalir = false }) { Text("Cancelar") } }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {}
            !isLoggedIn && usuarioParaPin == null -> VerificarDispositivoScreen(
                onDispositivoAutorizado = { usuario -> usuarioParaPin = usuario },
                viewModel = accesoViewModel
            )
            !isLoggedIn && usuarioParaPin != null -> PinLoginScreen(
                usuario = usuarioParaPin!!,
                onLoginExitoso = { usuario ->
                    sessionManager.saveSession(userId = usuario.id, username = usuario.username, rol = usuario.rol, localId = usuario.local_id, clienteId = usuario.cliente_id, androidId = usuario.android_id ?: "", nombre = usuario.nombre)
                    isLoggedIn = true
                },
                onCambiarDispositivo = { usuarioParaPin = null; accesoViewModel.reiniciar(); sessionManager.limpiarLicenciaVerificada() },
                viewModel = accesoViewModel,
                fotoBytes = fotoUsuarioPin
            )
            else -> {
                val rol = sessionManager.getRol()
                val androidId = sessionManager.getAndroidId()
                val esAdmin = rol == "admin"

                AnimatedContent(
                    targetState = pantalla,
                    label = "navegacion_principal",
                    transitionSpec = { fadeIn(animationSpec = androidx.compose.animation.core.tween(350)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) }
                ) { pantallaActual ->
                    when (pantallaActual) {
                        is PantallaInterna.Home -> Column(modifier = Modifier.statusBarsPadding()) {
                            SyncStatusBar(androidId = androidId, onVerConflictos = { pantalla = PantallaInterna.Conflictos })
                            InicioTopBar(
                                username = sessionManager.getNombre().ifEmpty { sessionManager.getUsername() },
                                esAdmin = esAdmin,
                                androidId = androidId,
                                localSeleccionViewModel = localSeleccionViewModel,
                                onLocalCambiado = { local -> localRecienCambiado = local },
                                onLogout = { cerrarSesion() },
                                fotoUsuario = fotoUsuarioLogueado,
                                onFotoSeleccionada = { bytes -> onFotoDashboardSeleccionada(bytes) },
                                onFotoError = {
                                    scope.launch { snackbarHostState.showSnackbar("No se pudo cargar esa foto. Probá con otra imagen.") }
                                },
                                temaOscuro = temaOscuro,
                                onCambiarTema = onCambiarTema
                            )
                            DashboardScreen(
                                userRol = rol,
                                username = sessionManager.getNombre().ifEmpty { sessionManager.getUsername() },
                                androidId = androidId,
                                onNavigate = { ruta ->
                                    pantalla = when (ruta) {
                                        "ventas" -> PantallaInterna.Ventas
                                        "misventas" -> PantallaInterna.MisVentas
                                        "productos" -> PantallaInterna.Productos
                                        "inventario" -> PantallaInterna.Inventario
                                        "tarjetas" -> if (esAdmin) PantallaInterna.Tarjetas else PantallaInterna.Home
                                        "aprobaciones" -> if (esAdmin) PantallaInterna.Aprobaciones else PantallaInterna.Home
                                        "devolucion" -> if (esAdmin) PantallaInterna.Devolucion else PantallaInterna.Home
                                        else -> PantallaInterna.Home
                                    }
                                },
                                onLogout = { cerrarSesion() }
                            )
                        }
                        is PantallaInterna.Ventas -> VentasScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home }, onIrACarrito = { pantalla = PantallaInterna.Carrito })
                        is PantallaInterna.Carrito -> CarritoScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Ventas }, onVentaConfirmada = { pantalla = PantallaInterna.Ventas })
                        is PantallaInterna.Productos -> ProductosScreen(androidId = androidId, rol = rol, onBack = { pantalla = PantallaInterna.Home })
                        is PantallaInterna.Inventario -> InventarioScreen(androidId = androidId, rol = rol, onBack = { pantalla = PantallaInterna.Home }, onVerVentasRealizadas = { pantalla = PantallaInterna.MisVentas })
                        is PantallaInterna.Tarjetas -> if (esAdmin) TarjetasScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home }) else LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                        is PantallaInterna.Aprobaciones -> if (esAdmin) AprobacionesScreen(androidId = androidId, rol = rol, onBack = { pantalla = PantallaInterna.Home }) else LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                        is PantallaInterna.Devolucion -> if (esAdmin) DevolucionScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home }) else LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                        is PantallaInterna.Conflictos -> ConflictosScreen(onBack = { pantalla = PantallaInterna.Home })
                        is PantallaInterna.MisVentas -> MisVentasScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home })
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        val feedbackVM = remember { FeedbackViewModel() }
        CompositionLocalProvider(LocalFeedback provides feedbackVM) {
            val feedbackState by feedbackVM.state.collectAsState()
            FeedbackBar(
                mensaje = feedbackState.mensaje,
                tipo = feedbackState.tipo,
                onDismiss = { feedbackVM.limpiar() }
            )
        }
    }
}

@Composable
private fun InicioTopBar(
    username: String,
    esAdmin: Boolean,
    androidId: String,
    localSeleccionViewModel: LocalSeleccionViewModel,
    onLocalCambiado: (Local) -> Unit,
    onLogout: () -> Unit,
    fotoUsuario: ByteArray?,
    onFotoSeleccionada: (ByteArray) -> Unit,
    onFotoError: () -> Unit,
    temaOscuro: Boolean,
    onCambiarTema: () -> Unit
) {
    NeuCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (fotoUsuario == null) {
                    Text("Añadir foto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                AvatarUsuario(fotoBytes = fotoUsuario, size = 40.dp, editable = true, onFotoSeleccionada = onFotoSeleccionada, onError = onFotoError)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(username, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(modifier = Modifier.width(10.dp))
            BotonTema(temaOscuro = temaOscuro, onClick = onCambiarTema)
            if (esAdmin) {
                Spacer(modifier = Modifier.width(12.dp))
                SelectorDeLocalInline(androidId = androidId, viewModel = localSeleccionViewModel, onLocalCambiado = onLocalCambiado, modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SelectorDeLocalInline(androidId: String, viewModel: LocalSeleccionViewModel, onLocalCambiado: (Local) -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var menuAbierto by remember { mutableStateOf(false) }
    var localAConfirmar by remember { mutableStateOf<Local?>(null) }
    LaunchedEffect(androidId) { viewModel.cargar(androidId) }
    if (uiState.locales.size > 1) {
        Box(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { menuAbierto = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(uiState.localSeleccionado?.nombre ?: "Selecciona un local", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                uiState.locales.forEach { local ->
                    DropdownMenuItem(text = { Text(local.nombre) }, onClick = { localAConfirmar = local; menuAbierto = false })
                }
            }
        }
    }
    if (localAConfirmar != null) {
        AlertDialog(
            onDismissRequest = { localAConfirmar = null },
            title = { Text("Cambiar de local") },
            text = { Text("¿Cambiar a ${localAConfirmar!!.nombre}?") },
            confirmButton = { TextButton(onClick = { val local = localAConfirmar!!; viewModel.seleccionar(local); onLocalCambiado(local); localAConfirmar = null }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { localAConfirmar = null }) { Text("Cancelar") } }
        )
    }
}
