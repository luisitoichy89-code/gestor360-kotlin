package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.repository.LicenciaRepository

@Composable
fun InicioScreen(
    licenciaRepository: LicenciaRepository,
    onLicenciaValida: () -> Unit,
    onLicenciaInvalida: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var mensaje by remember { mutableStateOf("Verificando licencia...") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val resultado = licenciaRepository.verificarLicencia()
            isLoading = false
            if (resultado.valida) {
                onLicenciaValida()
            } else {
                mensaje = resultado.mensaje
                onLicenciaInvalida(resultado.mensaje)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = mensaje,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
