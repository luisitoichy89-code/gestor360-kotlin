package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.screens.AprobacionesScreen
import org.luisito.gestor360.ui.screens.CierreCajaScreen
import org.luisito.gestor360.ui.screens.DashboardScreen
import org.luisito.gestor360.ui.screens.PinLoginScreen
import org.luisito.gestor360.ui.screens.ProductosScreen
import org.luisito.gestor360.ui.screens.TarjetasScreen
import org.luisito.gestor360.ui.screens.TrazasScreen
import org.luisito.gestor360.ui.screens.VentasScreen
import org.luisito.gestor360.ui.screens.VerificarDispositivoScreen
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.ui.viewmodels.LocalSeleccionViewModel
import org.luisito.gestor360.utils.SessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gestor360Theme {
                Gestor360App()
            }
        }
    }
}

private sealed class PantallaInterna {
    object Home : PantallaInterna()
    object Ventas : PantallaInterna()
    object Productos : PantallaInterna()
    object Tarjetas : PantallaInterna()
    object Aprobaciones : PantallaInterna()
    object CierreCaja : PantallaInterna()
    object Trazas : PantallaInterna()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gestor360App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val accesoViewModel: AccesoViewModel = viewModel()
    val localSeleccionViewModel: LocalSeleccionViewModel = viewModel()

    var isLoading by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var usuarioParaPin by remember { mutableStateOf<User?>(null) }
    var pantalla by remember { mutableStateOf<PantallaInterna>(PantallaInterna.Home) }
    var mostrarMenu by remember { mutableStateOf(false) }
    var mostrarConfirmarSalir by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoggedIn = sessionManager.isLoggedIn()
        isLoading = false
    }

    fun cerrarSesion() {
        sessionManager.clear()
        accesoViewModel.reiniciar()
        usuarioParaPin = null
        isLoggedIn = false
        pantalla = PantallaInterna.Home
    }

    if (isLoggedIn) {
        BackHandler(enabled = true) {
            if (pantalla != PantallaInterna.Home) {
                pantalla = PantallaInterna.Home
            } else {
                mostrarConfirmarSalir = true
            }
        }
    }

    if (mostrarConfirmarSalir) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarSalir = false },
            title = { Text("¿Salir de Gestor360?") },
            text = { Text("Vas a cerrar la aplicación.") },
            confirmButton = {
                TextButton(onClick = { (context as? ComponentActivity)?.finish() }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarSalir = false }) { Text("Cancelar") }
            }
        )
    }

    when {
        isLoading -> { /* Pantalla de carga */ }

        !isLoggedIn && usuarioParaPin == null -> {
            VerificarDispositivoScreen(
                onDispositivoAutorizado = { usuario -> usuarioParaPin = usuario },
                viewModel = accesoViewModel
            )
        }

        !isLoggedIn && usuarioParaPin != null -> {
            PinLoginScreen(
                usuario = usuarioParaPin!!,
                onLoginExitoso = { usuario ->
                    sessionManager.saveSession(
                        userId = usuario.id,
                        username = usuario.username,
                        rol = usuario.rol,
                        almacenId = usuario.almacen_id ?: "1",
                        clienteId = usuario.cliente_id,
                        androidId = usuario.android_id ?: "",
                        nombre = usuario.nombre
                    )
                    isLoggedIn = true
                },
                onCambiarDispositivo = {
                    usuarioParaPin = null
                    accesoViewModel.reiniciar()
                },
                viewModel = accesoViewModel
            )
        }

        else -> {
            val rol = sessionManager.getRol()
            val androidId = sessionManager.getAndroidId()
            val esAdmin = rol == "admin"

            when (pantalla) {
                is PantallaInterna.Home -> Column {
                    if (esAdmin) {
                        SelectorDeLocalBar(androidId = androidId, viewModel = localSeleccionViewModel)
                    }
                    DashboardScreen(
                        userRol = rol,
                        username = sessionManager.getNombre().ifEmpty { sessionManager.getUsername() },
                        onMenuClick = { mostrarMenu = true },
                        onLogout = { cerrarSesion() }
                    )
                }
                is PantallaInterna.Ventas -> VentasScreen(
                    androidId = androidId,
                    onBack = { pantalla = PantallaInterna.Home }
                )
                is PantallaInterna.Productos -> ProductosScreen(
                    androidId = androidId,
                    rol = rol,
                    onBack = { pantalla = PantallaInterna.Home }
                )
                is PantallaInterna.CierreCaja -> CierreCajaScreen(
                    androidId = androidId,
                    onBack = { pantalla = PantallaInterna.Home }
                )
                is PantallaInterna.Tarjetas -> if (esAdmin) {
                    TarjetasScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home })
                } else {
                    LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                }
                is PantallaInterna.Aprobaciones -> if (esAdmin) {
                    AprobacionesScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home })
                } else {
                    LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                }
                is PantallaInterna.Trazas -> if (esAdmin) {
                    TrazasScreen(androidId = androidId, onBack = { pantalla = PantallaInterna.Home })
                } else {
                    LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                }
            }

            if (mostrarMenu) {
                AlertDialog(
                    onDismissRequest = { mostrarMenu = false },
                    title = { Text("Menú") },
                    text = {
                        Column {
                            OpcionMenu(Icons.Default.PointOfSale, "Ventas") { pantalla = PantallaInterna.Ventas; mostrarMenu = false }
                            OpcionMenu(Icons.Default.Inventory2, "Productos") { pantalla = PantallaInterna.Productos; mostrarMenu = false }
                            OpcionMenu(Icons.Default.ReceiptLong, "Cierre de caja") { pantalla = PantallaInterna.CierreCaja; mostrarMenu = false }
                            if (esAdmin) {
                                OpcionMenu(Icons.Default.CreditCard, "Tarjetas") { pantalla = PantallaInterna.Tarjetas; mostrarMenu = false }
                                OpcionMenu(Icons.Default.FactCheck, "Aprobaciones de merma") { pantalla = PantallaInterna.Aprobaciones; mostrarMenu = false }
                                OpcionMenu(Icons.Default.History, "Historial de actividad") { pantalla = PantallaInterna.Trazas; mostrarMenu = false }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { mostrarMenu = false; cerrarSesion() }) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Cerrar sesión")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarMenu = false }) { Text("Cerrar") }
                    }
                )
            }
        }
    }
}

@Composable
private fun OpcionMenu(icono: ImageVector, texto: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable(onClick = onClick)
    ) {
        Icon(icono, contentDescription = null)
        Text(texto, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * Barra informativa sobre el Dashboard: qué local tiene seleccionado el admin.
 * Por ahora es solo para orientarse (no filtra productos/ventas todavía — ver
 * nota en get_locales.sql sobre por qué).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDeLocalBar(androidId: String, viewModel: LocalSeleccionViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var menuAbierto by remember { mutableStateOf(false) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    if (uiState.locales.size <= 1) return // Sin nada que elegir, no mostrar la barra.

    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                uiState.localSeleccionado?.nombre ?: "Selecciona un local",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { menuAbierto = true }) { Text("Cambiar") }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    uiState.locales.forEach { local ->
                        DropdownMenuItem(
                            text = { Text(local.nombre) },
                            onClick = { viewModel.seleccionar(local); menuAbierto = false }
                        )
                    }
                }
            }
        }
    }
}
