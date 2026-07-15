package org.luisito.gestor360.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.R
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.utils.DeviceIdManager
import org.luisito.gestor360.ui.theme.NeuButton
import org.luisito.gestor360.ui.theme.NeuCard
import org.luisito.gestor360.ui.theme.NeuOutlinedButton
import org.luisito.gestor360.ui.theme.neuShadow

@Composable
fun VerificarDispositivoScreen(onDispositivoAutorizado: (User) -> Unit, viewModel: AccesoViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val androidId = remember { DeviceIdManager.getFormattedDeviceId(context) }
    var permisosOk by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permisosOk = permissions.values.all { it }
        if (!permisosOk) {
            Toast.makeText(context, "Permisos necesarios para recibir SMS y almacenamiento", Toast.LENGTH_LONG).show()
        }
    }

    val permisosRequeridos = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    fun solicitarPermisos() {
        val faltan = permisosRequeridos.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltan) {
            permissionLauncher.launch(permisosRequeridos)
        } else {
            permisosOk = true
        }
    }

    LaunchedEffect(uiState.usuarioVerificado) {
        if (uiState.usuarioVerificado != null) solicitarPermisos()
    }

    // Antes esto vivía dentro del mismo LaunchedEffect que llamaba a solicitarPermisos():
    // se comprobaba "permisosOk" en la misma pasada, pero solicitarPermisos() solo LANZA
    // el diálogo del sistema (async) y retorna al instante — la primera vez que un usuario
    // instala la app, el diálogo de permisos SMS aún no tiene respuesta cuando se hacía esa
    // comprobación, así que onDispositivoAutorizado(user) nunca se llamaba y, como la key del
    // efecto (usuarioVerificado) no cambiaba, tampoco se reintentaba después de conceder el
    // permiso. El usuario podía terminar navegando sin RECEIVE_SMS/READ_SMS realmente
    // concedidos, y sin ese permiso Android nunca entrega el SMS_RECEIVED al receiver — por
    // eso "el receiver no recibe nada" aunque el manifest esté bien.
    // Separarlo en su propio LaunchedEffect con "permisosOk" como key hace que SÍ se
    // re-ejecute en cuanto el callback del permissionLauncher actualiza ese estado.
    LaunchedEffect(uiState.usuarioVerificado, permisosOk) {
        val user = uiState.usuarioVerificado ?: return@LaunchedEffect
        if (permisosOk) onDispositivoAutorizado(user)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.gestor360_logo_bandera), "Gestor360", Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("Gestor360", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Verificación de dispositivo requerida", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            NeuCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Identificador del dispositivo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer { Text(androidId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(12.dp))
                    NeuOutlinedButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", androidId)); Toast.makeText(context, "ID copiado al portapapeles", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copiar identificador") }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (uiState.verificando) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Verificando dispositivo...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else { NeuButton(onClick = { viewModel.verificarDispositivo(androidId) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Verificar dispositivo") } }
            uiState.mensajeError?.let { error -> Spacer(Modifier.height(16.dp)); Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().neuShadow(shape = RoundedCornerShape(12.dp), elevation = 3.dp, blur = 6.dp)) { Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) } }
            // Si el usuario niega el permiso de SMS (o lo hace "no volver a preguntar"),
            // no lo dejamos bloqueado sin poder entrar: el checkout ya tiene el botón
            // "Confirmé visual" en el overlay de pago como respaldo manual para ese caso.
            if (uiState.usuarioVerificado != null && !permisosOk) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { uiState.usuarioVerificado?.let(onDispositivoAutorizado) }) {
                    Text("Continuar sin confirmación automática por SMS")
                }
            }
        }
    }
}
