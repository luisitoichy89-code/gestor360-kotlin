package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.utils.DeviceIdManager

/**
 * Primera pantalla que ve cualquier usuario (admin o vendedor) al abrir la app:
 * su Android ID y un botón "Verificar". Si el dispositivo está autorizado y la
 * licencia del negocio está vigente, pasa a la pantalla de PIN.
 */
@Composable
fun VerificarDispositivoScreen(
    onDispositivoAutorizado: (User) -> Unit,
    viewModel: AccesoViewModel = viewModel()
) {
    val context = LocalContext.current
    val androidId = remember { DeviceIdManager.getFormattedDeviceId(context) }
    val androidIdCrudo = remember { DeviceIdManager.getDeviceId(context) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.usuarioVerificado) {
        uiState.usuarioVerificado?.let { onDispositivoAutorizado(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Gestor360°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Image(painter = painterResource(id = R.drawable.ic_logo), contentDescription = "Gestor360", modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Verifica este dispositivo para continuar", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Android ID de este dispositivo", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainerCompat(androidId)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.verificando) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.verificarDispositivo(androidIdCrudo) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verificar")
            }
        }

        uiState.mensajeError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SelectionContainerCompat(texto: String) {
    // SelectionContainer permite copiar el Android ID fácilmente para pasárselo al admin.
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
