package org.luisito.gestor360.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel

/**
 * Pantalla de acceso: mismo look & feel de tarjeta clara, centrada y
 * redondeada usado como referencia de diseño — fondo claro, texto negro,
 * campo de PIN con visibilidad alternable y botón de acción en naranja.
 */
@Composable
fun PinLoginScreen(usuario: User, onLoginExitoso: (User) -> Unit, onCambiarDispositivo: () -> Unit, viewModel: AccesoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var intentos by remember { mutableStateOf(0) }
    var bloqueado by remember { mutableStateOf(false) }
    val error = uiState.pinError
    val verificando = uiState.verificando
    val maxIntentos = 3
    val shake = animateFloatAsState(targetValue = if (error != null) 1f else 0f, label = "shake")
    val canSubmit = pin.length in 4..6 && !verificando && !bloqueado

    LaunchedEffect(intentos) { if (intentos >= maxIntentos) { bloqueado = true; delay(2500); intentos = 0; bloqueado = false } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .scale(1f - (shake.value * 0.02f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (bloqueado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        modifier = Modifier.padding(18.dp),
                        tint = if (bloqueado) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    usuario.nombre?.takeIf { it.isNotBlank() } ?: usuario.username,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (usuario.rol == "admin") "Acceso Administrativo" else "Acceso Operativo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(28.dp))

                AnimatedVisibility(bloqueado) {
                    Column {
                        Text(
                            "Demasiados intentos. Intenta nuevamente en unos segundos.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (!bloqueado && it.length <= 6) { pin = it.filter { c -> c.isDigit() }; viewModel.limpiarPinError() } },
                    label = { Text("PIN de seguridad") },
                    singleLine = true,
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = { AnimatedVisibility(error != null) { Text(error ?: "", color = MaterialTheme.colorScheme.error) } },
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pinVisible) "Ocultar PIN" else "Mostrar PIN",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { val ok = viewModel.validarPin(pin); if (ok) { intentos = 0; onLoginExitoso(usuario) } else { intentos++ } },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (verificando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Verificando acceso...")
                    } else if (bloqueado) {
                        Text("Bloqueado temporalmente")
                    } else {
                        Text("Acceder al sistema", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))

                TextButton(
                    onClick = onCambiarDispositivo,
                    enabled = !bloqueado,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("No soy este usuario / cambiar dispositivo")
                }

                if (intentos > 0 && !bloqueado) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Intentos fallidos: $intentos / $maxIntentos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
