package org.luisito.gestor360.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.components.rememberFotoBitmap
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.ui.theme.NeuButton
import org.luisito.gestor360.ui.theme.neuShadow

/**
 * Pantalla de acceso: mismo look & feel de tarjeta clara, centrada y
 * redondeada usado como referencia de diseño — fondo claro, texto negro,
 * campo de PIN con visibilidad alternable y botón de acción en naranja.
 *
 * El bloqueo por intentos fallidos vive en el ViewModel (persistido en
 * disco, cifrado, ver PinRateLimiter): sobrevive a cerrar la app o rotar la
 * pantalla, a diferencia de un simple `remember` local.
 */
@Composable
fun PinLoginScreen(
    usuario: User,
    onLoginExitoso: (User) -> Unit,
    onCambiarDispositivo: () -> Unit,
    viewModel: AccesoViewModel = viewModel(),
    fotoBytes: ByteArray? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var validando by remember { mutableStateOf(false) }
    var segundosRestantes by remember { mutableStateOf(uiState.pinBloqueadoSegundos) }
    val error = uiState.pinError
    val verificando = uiState.verificando
    val bloqueado = uiState.pinBloqueado && segundosRestantes > 0
    val shake = animateFloatAsState(targetValue = if (error != null) 1f else 0f, label = "shake")
    val canSubmit = pin.length in 4..6 && !verificando && !bloqueado && !validando

    LaunchedEffect(uiState.pinBloqueadoSegundos) { segundosRestantes = uiState.pinBloqueadoSegundos }
    LaunchedEffect(bloqueado) {
        while (segundosRestantes > 0) { delay(1000); segundosRestantes-- }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .scale(1f - (shake.value * 0.02f))
                // Antes: elevation 8dp + blur 18dp -> sombra muy marcada en
                // claro. Ahora bien suave, casi imperceptible, como en la
                // referencia (apenas se nota que la tarjeta "flota").
                .neuShadow(shape = RoundedCornerShape(28.dp), elevation = 3.dp, blur = 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                org.luisito.gestor360.ui.components.ContactoSoporteWhatsApp()
                Spacer(Modifier.height(8.dp))
                val fotoUsuario = rememberFotoBitmap(fotoBytes)
                Surface(
                    shape = CircleShape,
                    color = if (bloqueado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(72.dp)
                        .neuShadow(shape = CircleShape, elevation = 2.dp, blur = 10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (fotoUsuario != null) {
                            // Misma foto que se elige/guarda desde el dashboard: solo local,
                            // nunca se sube a Supabase Storage.
                            Image(
                                bitmap = fotoUsuario,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.Lock,
                                null,
                                modifier = Modifier.padding(20.dp),
                                tint = if (bloqueado) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
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
                            "Demasiados intentos. Intenta nuevamente en ${segundosRestantes}s.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Campo plano tipo "pill", relleno gris suave y sin borde —
                // como en la referencia — en vez del neuShadow "hundido" tan
                // marcado que tenía antes.
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
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Sombra casi plana, solo para separarla levemente
                        // del fondo (nada de efecto "hundido").
                        .neuShadow(shape = RoundedCornerShape(28.dp), elevation = 1.dp, blur = 10.dp),
                    shape = RoundedCornerShape(28.dp)
                )
                Spacer(Modifier.height(20.dp))

                NeuButton(
                    onClick = {
                        validando = true
                        viewModel.validarPin(pin) { ok ->
                            validando = false
                            if (ok) onLoginExitoso(usuario) else pin = ""
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = 1.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (verificando || validando) {
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
            }
        }
    }
}
