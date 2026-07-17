package org.luisito.gestor360.ui.screens
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel
import org.luisito.gestor360.ui.theme.NeuCard
import org.luisito.gestor360.ui.theme.NeuButton
import org.luisito.gestor360.ui.theme.NeuOutlinedButton
import org.luisito.gestor360.ui.theme.neuShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetasScreen(androidId: String, onBack: (() -> Unit)? = null, viewModel: TarjetaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var tarjetaEnEdicion by remember { mutableStateOf<TarjetaEntity?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var tarjetaAEliminar by remember { mutableStateOf<TarjetaEntity?>(null) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Tarjetas", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { tarjetaEnEdicion = null; mostrarFormulario = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nueva") }, shape = RoundedCornerShape(16.dp)) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Cuentas disponibles para transferencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                uiState.tarjetas.isEmpty() -> EstadoVacio("No hay cuentas registradas")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.tarjetas, key = { it.id }) { tarjeta -> TarjetaCard(tarjeta, { tarjetaEnEdicion = tarjeta; mostrarFormulario = true }, { viewModel.toggleActivo(tarjeta) }, { tarjetaAEliminar = tarjeta }) }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (mostrarFormulario) TarjetaFormDialog(tarjetaEnEdicion, uiState.tarjetas, uiState.isSaving, { mostrarFormulario = false }) { nombre, numeroCuenta, tipo ->
        if (tarjetaEnEdicion == null) viewModel.crear(nombre, tipo, numeroCuenta) else viewModel.editar(tarjetaEnEdicion!!.id, nombre, tipo, numeroCuenta, tarjetaEnEdicion!!.activo)
        mostrarFormulario = false
    }

    tarjetaAEliminar?.let { ConfirmarEliminarDialog("${it.nombre} · ${it.numeroCuenta}", { viewModel.eliminar(it.id); tarjetaAEliminar = null }, { tarjetaAEliminar = null }) }
}

private fun agruparNumeroTarjeta(numero: String): String = numero.chunked(4).joinToString("-")

private val formatoNumeroTarjeta = VisualTransformation { texto ->
    val digitos = texto.text
    val formateado = buildString {
        digitos.forEachIndexed { i, c ->
            append(c)
            if ((i + 1) % 4 == 0 && i != digitos.lastIndex) append('-')
        }
    }
    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val guiones = if (offset == 0) 0 else (offset - 1) / 4
            return (offset + guiones).coerceAtMost(formateado.length)
        }
        override fun transformedToOriginal(offset: Int): Int {
            val guiones = offset / 5
            return (offset - guiones).coerceIn(0, digitos.length)
        }
    }
    TransformedText(AnnotatedString(formateado), offsetMapping)
}

@Composable
private fun TarjetaCard(tarjeta: TarjetaEntity, onEditar: () -> Unit, onToggleActivo: () -> Unit, onEliminar: () -> Unit) {
    var menuAbierto by remember { mutableStateOf(false) }
    NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tarjeta.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(agruparNumeroTarjeta(tarjeta.numeroCuenta ?: ""), style = MaterialTheme.typography.bodyMedium)
                if (!tarjeta.tipo.isNullOrBlank()) Text(tarjeta.tipo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp)); EstadoChip(activo = tarjeta.activo)
            }
            Box {
                IconButton(onClick = { menuAbierto = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuAbierto = false; onEditar() })
                    DropdownMenuItem(text = { Text(if (tarjeta.activo) "Desactivar" else "Activar") }, leadingIcon = { Icon(if (tarjeta.activo) Icons.Default.ToggleOff else Icons.Default.ToggleOn, null) }, onClick = { menuAbierto = false; onToggleActivo() })
                    DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuAbierto = false; onEliminar() })
                }
            }
        }
    }
}

@Composable
private fun TarjetaFormDialog(tarjeta: TarjetaEntity?, tarjetasExistentes: List<TarjetaEntity>, isSaving: Boolean, onDismiss: () -> Unit, onGuardar: (nombre: String, numeroCuenta: String, tipo: String) -> Unit) {
    var nombre by remember { mutableStateOf(tarjeta?.nombre ?: "") }; var numeroCuenta by remember { mutableStateOf(tarjeta?.numeroCuenta ?: "") }; var tipo by remember { mutableStateOf(tarjeta?.tipo ?: "") }
    val nombreVacio = nombre.isBlank()
    val numeroVacio = numeroCuenta.isBlank()
    val numeroIncompleto = numeroCuenta.isNotBlank() && numeroCuenta.length != 16
    val numeroDuplicado = remember(numeroCuenta, tarjetasExistentes, tarjeta) {
        val numeroNormalizado = numeroCuenta.trim()
        numeroNormalizado.length == 16 && tarjetasExistentes.any { it.id != tarjeta?.id && it.numeroCuenta?.trim() == numeroNormalizado }
    }
    val valido = !nombreVacio && !numeroVacio && !numeroIncompleto && !numeroDuplicado
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp),
        title = { Text(if (tarjeta == null) "Nueva tarjeta" else "Editar tarjeta", fontWeight = FontWeight.Bold) },
        text = { Column {
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Banco") }, isError = nombreVacio, supportingText = { if (nombreVacio) Text("El banco es obligatorio") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(numeroCuenta, { numeroCuenta = it.filter { c -> c.isDigit() }.take(16) }, label = { Text("Número de cuenta") }, placeholder = { Text("0000-0000-0000-0000") }, visualTransformation = formatoNumeroTarjeta, isError = numeroVacio || numeroIncompleto || numeroDuplicado, supportingText = { when { numeroVacio -> Text("El número de cuenta es obligatorio"); numeroIncompleto -> Text("Debe tener exactamente 16 dígitos (${numeroCuenta.length}/16)"); numeroDuplicado -> Text("Ya existe una tarjeta con ese número") } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(tipo, { tipo = it }, label = { Text("Tipo de cuenta (opcional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }},
        confirmButton = { TextButton(enabled = valido && !isSaving, onClick = { onGuardar(nombre.trim(), numeroCuenta.trim(), tipo.trim()) }) { Text(if (isSaving) "Guardando..." else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }
    )
}
