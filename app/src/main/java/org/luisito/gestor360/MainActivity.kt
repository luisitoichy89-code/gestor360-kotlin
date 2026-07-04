package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.screens.*
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.utils.DeviceIdManager
import org.luisito.gestor360.utils.SessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        val deviceId = DeviceIdManager.getDeviceId(this)
        val savedUser = sessionManager.getUser()

        setContent {
            Gestor360Theme {
                var pantalla by remember { mutableStateOf(if (savedUser != null) "home" else "verificar") }
                var usuario by remember { mutableStateOf(savedUser) }

                when (pantalla) {
                    "verificar" -> VerificarDispositivoScreen(
                        onVerificado = { user ->
                            usuario = user
                            sessionManager.saveUser(user)
                            pantalla = "pin"
                        }
                    )
                    "pin" -> usuario?.let { user ->
                        PinLoginScreen(
                            usuario = user,
                            onPinCorrecto = { pantalla = "home" },
                            onCancelar = { pantalla = "verificar" }
                        )
                    }
                    "home" -> usuario?.let { user ->
                        DashboardScreen(
                            usuario = user,
                            deviceId = deviceId,
                            onNavigate = { pantalla = it },
                            onLogout = { sessionManager.clear(); usuario = null; pantalla = "verificar" }
                        )
                    }
                    "productos" -> usuario?.let { user ->
                        ProductosScreen(
                            androidId = deviceId,
                            almacenId = user.almacen_id ?: "1",
                            onBack = { pantalla = "home" }
                        )
                    }
                    "ventas" -> usuario?.let { user ->
                        VentasScreen(
                            androidId = deviceId,
                            almacenId = user.almacen_id ?: "1",
                            usuarioId = user.id.toString(),
                            onBack = { pantalla = "home" }
                        )
                    }
                    "tarjetas" -> usuario?.let { user ->
                        TarjetasScreen(
                            androidId = deviceId,
                            almacenId = user.almacen_id ?: "1",
                            onBack = { pantalla = "home" }
                        )
                    }
                    "aprobaciones" -> usuario?.let { user ->
                        AprobacionesScreen(
                            androidId = deviceId,
                            usuarioId = user.id.toString(),
                            onBack = { pantalla = "home" }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    usuario: User,
    deviceId: String,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestor360°") },
                navigationIcon = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Salir") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Bienvenido, ${usuario.nombre ?: usuario.username}", style = MaterialTheme.typography.headlineSmall)
            Text("Local: ${usuario.almacen_id}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { onNavigate("productos") }, modifier = Modifier.fillMaxWidth()) { Text("📦 Productos") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onNavigate("ventas") }, modifier = Modifier.fillMaxWidth()) { Text("🛒 Ventas") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onNavigate("tarjetas") }, modifier = Modifier.fillMaxWidth()) { Text("💳 Tarjetas") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onNavigate("aprobaciones") }, modifier = Modifier.fillMaxWidth()) { Text("✅ Aprobaciones") }
        }
    }
}
