package org.luisito.gestor360.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.utils.DeviceIdManager

@Composable
fun VerificarDispositivoScreen(onDispositivoAutorizado: (User) -> Unit, viewModel: AccesoViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val androidId = remember { DeviceIdManager.getFormattedDeviceId(context) }

    LaunchedEffect(uiState.usuarioVerificado) { uiState.usuarioVerificado?.let { onDispositivoAutorizado(it) } }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) { Image(painterResource(R.drawable.logo_splash), "Gestor360", Modifier.size(80.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Gestor360", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Verificación de dispositivo requerida", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Identificador del dispositivo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer { Text(androidId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", androidId)); Toast.makeText(context, "ID copiado al portapapeles", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copiar identificador") }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (uiState.verificando) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Verificando dispositivo...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else { Button(onClick = { viewModel.verificarDispositivo(androidId) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Verificar dispositivo") } }
            uiState.mensajeError?.let { error -> Spacer(Modifier.height(16.dp)); Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
