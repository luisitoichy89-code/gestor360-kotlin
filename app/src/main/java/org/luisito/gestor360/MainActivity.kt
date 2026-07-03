package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.screens.AprobacionesScreen
import org.luisito.gestor360.ui.screens.DashboardScreen
import org.luisito.gestor360.ui.screens.PinLoginScreen
import org.luisito.gestor360.ui.screens.ProductosScreen
import org.luisito.gestor360.ui.screens.TarjetasScreen
import org.luisito.gestor360.ui.screens.VentasScreen
import org.luisito.gestor360.ui.screens.VerificarDispositivoScreen
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
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

/**
 * Pantallas internas una vez logueado. "Home" es tu DashboardScreen existente;
 * las demás son las nuevas pantallas de POS.
 */
private sealed class PantallaInterna {
    object Home : PantallaInterna()
    object Ventas : PantallaInterna()
    object Productos : PantallaInterna()
    object Tarjetas : PantallaInterna()
    object Aprobaciones : PantallaInterna()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gestor360App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val accesoViewModel: AccesoViewModel = viewModel()

    var isLoading by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var usuarioParaPin by remember { mutableStateOf<User?>(null) }
    var pantalla by remember { mutableStateOf<PantallaInterna>(PantallaInterna.Home) }
    var mostrarMenu by remember { mutableStateOf(false) }

    // Sesión ya guardada de una apertura anterior: entra directo, sin volver a pedir PIN.
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

    when {
        isLoading -> {
            // Pantalla de carga (puedes reemplazar por un splash si prefieres)
        }

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
            val almacenId = sessionManager.getAlmacenId()
            val clienteId = sessionManager.getClienteId()
            val usuarioId = sessionManager.getUserId()
            val esAdmin = rol == "admin"

            when (pantalla) {
                is PantallaInterna.Home -> DashboardScreen(
                    userRol = rol,
                    username = sessionManager.getNombre().ifEmpty { sessionManager.getUsername() },
                    onMenuClick = { mostrarMenu = true },
                    onLogout = { cerrarSesion() }
                )
                is PantallaInterna.Ventas -> VentasScreen(
                    almacenId = almacenId,
                    clienteId = clienteId,
                    usuarioId = usuarioId,
                    onBack = { pantalla = PantallaInterna.Home }
                )
                is PantallaInterna.Productos -> ProductosScreen(
                    almacenId = almacenId,
                    clienteId = clienteId,
                    usuarioId = usuarioId,
                    usuarioNombre = sessionManager.getNombre().ifEmpty { sessionManager.getUsername() },
                    rol = rol,
                    onBack = { pantalla = PantallaInterna.Home }
                )
                is PantallaInterna.Tarjetas -> if (esAdmin) {
                    TarjetasScreen(clienteId = clienteId, onBack = { pantalla = PantallaInterna.Home })
                } else {
                    LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                }
                is PantallaInterna.Aprobaciones -> if (esAdmin) {
                    AprobacionesScreen(
                        clienteId = clienteId,
                        adminUsuarioId = usuarioId,
                        onBack = { pantalla = PantallaInterna.Home }
                    )
                } else {
                    LaunchedEffect(Unit) { pantalla = PantallaInterna.Home }
                }
            }

            if (mostrarMenu) {
                ModalBottomSheet(onDismissRequest = { mostrarMenu = false }) {
                    ListItem(
                        headlineContent = { Text("Ventas") },
                        leadingContent = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            pantalla = PantallaInterna.Ventas
                            mostrarMenu = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Productos") },
                        leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            pantalla = PantallaInterna.Productos
                            mostrarMenu = false
                        }
                    )
                    if (esAdmin) {
                        ListItem(
                            headlineContent = { Text("Tarjetas") },
                            leadingContent = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                pantalla = PantallaInterna.Tarjetas
                                mostrarMenu = false
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Aprobaciones de merma") },
                            leadingContent = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                pantalla = PantallaInterna.Aprobaciones
                                mostrarMenu = false
                            }
                        )
                    }
                    Divider()
                    ListItem(
                        headlineContent = { Text("Cerrar sesión") },
                        leadingContent = { Icon(Icons.Default.Logout, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            mostrarMenu = false
                            cerrarSesion()
                        }
                    )
                }
            }
        }
    }
}
