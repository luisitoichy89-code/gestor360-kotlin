package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.ui.components.ConfirmarEliminarDialog
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoChip
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetasScreen(
    androidId: String,
    onBack: (() -> Unit)? = null,
    viewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var tarjetaEnEdicion by remember { mutableStateOf<Tarjeta?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var tarjetaAEliminar by remember { mutableStateOf<Tarjeta?>(null) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tarjetas") },
                navigationIcon = { if (onBack != null) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } } },
                actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, contentDescription = "Refrescar") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { tarjetaEnEdicion = null; mostrarFormulario = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva tarjeta") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Estas cuentas aparecen como opciones al cobrar por transferencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.tarjetas.isEmpty() -> EstadoVacio("Aún no has agregado ninguna cuenta")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.tarjetas, key = { it.id }) { tarjeta ->
                        TarjetaCard(tarjeta = tarjeta, onEditar = { tarjetaEnEdicion = tarjeta; mostrarFormulario = true }, onToggleActivo = { viewModel.toggleActivo(tarjeta) }, onEliminar = { tarjetaAEliminar = tarjeta })
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (mostrarFormulario) {
        TarjetaFormDialog(tarjeta = tarjetaEnEdicion, isSaving = uiState.isSaving, onDismiss = { mostrarFormulario = false }, onGuardar = { banco, numero, titular ->
            val existente = tarjetaEnEdicion
            if (existente == null) viewModel.crear(banco, numero, titular)
            else viewModel.editar(existente.id, banco, numero, titular)
            mostrarFormulario = false
        })
    }

    tarjetaAEliminar?.let { tarjeta ->
        ConfirmarEliminarDialog(nombre = "${tarjeta.banco} · ${tarjeta.numero}", onConfirm = { viewModel.eliminar(tarjeta.id); tarjetaAEliminar = null }, onDismiss = { tarjetaAEliminar = null })
    }
}

@Composable
private fun TarjetaCard(tarjeta: Tarjeta, onEditar: () -> Unit, onToggleActivo: () -> Unit, onEliminar: () -> Unit) {
    var menuAbierto by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tarjeta.banco, style = MaterialTheme.typography.titleMedium)
                Text(tarjeta.numero, style = MaterialTheme.typography.bodyMedium)
                if (!tarjeta.titular.isNullOrBlank()) Text(tarjeta.titular, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                EstadoChip(activo = tarjeta.activo)
            }
            Box {
                IconButton(onClick = { menuAbierto = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }, onClick = { menuAbierto = false; onEditar() })
                    DropdownMenuItem(text = { Text(if (tarjeta.activo) "Desactivar" else "Activar") }, leadingIcon = { Icon(if (tarjeta.activo) Icons.Default.ToggleOff else Icons.Default.ToggleOn, contentDescription = null) }, onClick = { menuAbierto = false; onToggleActivo() })
                    DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }, onClick = { menuAbierto = false; onEliminar() })
                }
            }
        }
    }
}

@Composable
private fun TarjetaFormDialog(tarjeta: Tarjeta?, isSaving: Boolean, onDismiss: () -> Unit, onGuardar: (banco: String, numero: String, titular: String) -> Unit) {
    var banco by remember { mutableStateOf(tarjeta?.banco ?: "") }
    var numero by remember { mutableStateOf(tarjeta?.numero ?: "") }
    var titular by remember { mutableStateOf(tarjeta?.titular ?: "") }
    val valido = banco.isNotBlank() && numero.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tarjeta == null) "Nueva tarjeta" else "Editar tarjeta") },
        text = {
            Column {
                OutlinedTextField(value = banco, onValueChange = { banco = it }, label = { Text("Banco") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = numero,
                    onValueChange = { if (it.length <= 16) numero = it.filter { c -> c.isDigit() } },
                    label = { Text("Número de cuenta / tarjeta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = titular, onValueChange = { titular = it }, label = { Text("Titular (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = valido && !isSaving, onClick = { onGuardar(banco.trim(), numero.trim(), titular.trim()) }) { Text(if (isSaving) "Guardando..." else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
