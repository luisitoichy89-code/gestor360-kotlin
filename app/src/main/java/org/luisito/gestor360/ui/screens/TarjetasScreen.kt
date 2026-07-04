package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetasScreen(
    androidId: String,
    almacenId: String,
    onBack: () -> Unit,
    viewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarFormulario by remember { mutableStateOf(false) }

    LaunchedEffect(androidId, almacenId) { viewModel.cargar(androidId, almacenId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tarjetas") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { mostrarFormulario = true }) { Icon(Icons.Default.Add, "Nueva") } }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                uiState.tarjetas.isEmpty() -> Text("No hay tarjetas")
                else -> LazyColumn { items(uiState.tarjetas) { t -> Text("${t.banco} ····${t.numero.takeLast(4)}") } }
            }
        }
    }

    if (mostrarFormulario) {
        var banco by remember { mutableStateOf("") }; var numero by remember { mutableStateOf("") }; var titular by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { mostrarFormulario = false }, title = { Text("Nueva tarjeta") },
            text = {
                Column {
                    OutlinedTextField(banco, { banco = it }, label = { Text("Banco") })
                    OutlinedTextField(numero, { numero = it }, label = { Text("Número") })
                    OutlinedTextField(titular, { titular = it }, label = { Text("Titular") })
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.crear(banco, numero, titular); mostrarFormulario = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { mostrarFormulario = false }) { Text("Cancelar") } }
        )
    }
}
