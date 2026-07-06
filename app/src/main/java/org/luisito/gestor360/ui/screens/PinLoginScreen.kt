package org.luisito.gestor360.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel

@Composable
fun PinLoginScreen(usuario: User, onLoginExitoso: (User) -> Unit, onCambiarDispositivo: () -> Unit, viewModel: AccesoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var intentos by remember { mutableStateOf(0) }
    var bloqueado by remember { mutableStateOf(false) }
    val error = uiState.pinError
    val verificando = uiState.verificando
    val maxIntentos = 3
    val shake = animateFloatAsState(targetValue = if (error != null) 1f else 0f, label = "shake")
    val canSubmit = pin.length in 4..6 && !verificando && !bloqueado

    LaunchedEffect(intentos) { if (intentos >= maxIntentos) { bloqueado = true; delay(2500); intentos = 0; bloqueado = false } }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).scale(1f - (shake.value * 0.02f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(20.dp), color = if (bloqueado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Lock, null, Modifier.padding(18.dp), tint = if (bloqueado) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(16.dp))
            Text(usuario.nombre?.takeIf { it.isNotBlank() } ?: usuario.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (usuario.rol == "admin") "Acceso Administrativo" else "Acceso Operativo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(bloqueado) { Text("Demasiados intentos. Intenta nuevamente en unos segundos.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pin, { if (!bloqueado && it.length <= 6) { pin = it.filter { c -> c.isDigit() }; viewModel.limpiarPinError() } }, label = { Text("PIN de seguridad") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword), isError = error != null, supportingText = { AnimatedVisibility(error != null) { Text(error ?: "", color = MaterialTheme.colorScheme.error) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(18.dp))
            Button(onClick = { val ok = viewModel.validarPin(pin); if (ok) { intentos = 0; onLoginExitoso(usuario) } else { intentos++ } }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { if (verificando) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Verificando acceso...") } else if (bloqueado) Text("Bloqueado temporalmente") else Text("Acceder al sistema") }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onCambiarDispositivo, enabled = !bloqueado) { Text("No soy este usuario / cambiar dispositivo") }
            if (intentos > 0 && !bloqueado) { Spacer(Modifier.height(8.dp)); Text("Intentos fallidos: $intentos / $maxIntentos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}
