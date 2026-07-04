package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel

/**
 * Segundo paso: con el dispositivo ya verificado, el usuario solo ingresa su PIN
 * (4 a 6 dígitos) para entrar. No hay usuario/contraseña de Supabase Auth aquí.
 */
@Composable
fun PinLoginScreen(
    usuario: User,
    onLoginExitoso: (User) -> Unit,
    onCambiarDispositivo: () -> Unit,
    viewModel: AccesoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(usuario.nombre?.takeIf { it.isNotBlank() } ?: usuario.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (usuario.rol == "admin") "Administrador" else "Vendedor", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 6) {
                    pin = it.filter { c -> c.isDigit() }
                    viewModel.limpiarPinError()
                }
            },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = uiState.pinError != null,
            supportingText = { uiState.pinError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (viewModel.validarPin(pin)) onLoginExitoso(usuario) },
            enabled = pin.length in 4..6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onCambiarDispositivo) {
            Text("No soy yo / cambiar dispositivo")
        }
    }
}
