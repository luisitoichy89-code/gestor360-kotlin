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
import org.luisito.gestor360.data.models.Tarjeta
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
    var tarjetaEnEdicion by remember { mutableStateOf<Tarjeta?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var tarjetaAEliminar by remember { mutableStateOf<Tarjeta?>(null) }

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

    if (mostrarFormulario) TarjetaFormDialog(tarjetaEnEdicion, uiState.tarjetas, uiState.isSaving, { mostrarFormulario = false }) { banco, numero, titular ->
        if (tarjetaEnEdicion == null) viewModel.crear(banco, numero, titular) else viewModel.editar(tarjetaEnEdicion!!.id, banco, numero, titular)
        mostrarFormulario = false
    }

    tarjetaAEliminar?.let { ConfirmarEliminarDialog("${it.banco} · ${it.numero}", { viewModel.eliminar(it.id); tarjetaAEliminar = null }, { tarjetaAEliminar = null }) }
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
private fun TarjetaCard(tarjeta: Tarjeta, onEditar: () -> Unit, onToggleActivo: () -> Unit, onEliminar: () -> Unit) {
    var menuAbierto by remember { mutableStateOf(false) }
    NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tarjeta.banco, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(agruparNumeroTarjeta(tarjeta.numero), style = MaterialTheme.typography.bodyMedium)
                if (!tarjeta.titular.isNullOrBlank()) Text(tarjeta.titular, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun TarjetaFormDialog(tarjeta: Tarjeta?, tarjetasExistentes: List<Tarjeta>, isSaving: Boolean, onDismiss: () -> Unit, onGuardar: (banco: String, numero: String, titular: String) -> Unit) {
    var banco by remember { mutableStateOf(tarjeta?.banco ?: "") }; var numero by remember { mutableStateOf(tarjeta?.numero ?: "") }; var titular by remember { mutableStateOf(tarjeta?.titular ?: "") }
    val bancoVacio = banco.isBlank()
    val numeroVacio = numero.isBlank()
    val numeroIncompleto = numero.isNotBlank() && numero.length != 16
    val numeroDuplicado = remember(numero, tarjetasExistentes, tarjeta) {
        val numeroNormalizado = numero.trim()
        numeroNormalizado.length == 16 && tarjetasExistentes.any { it.id != tarjeta?.id && it.numero.trim() == numeroNormalizado }
    }
    val valido = !bancoVacio && !numeroVacio && !numeroIncompleto && !numeroDuplicado
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp),
        title = { Text(if (tarjeta == null) "Nueva tarjeta" else "Editar tarjeta", fontWeight = FontWeight.Bold) },
        text = { Column {
            OutlinedTextField(banco, { banco = it }, label = { Text("Banco") }, isError = bancoVacio, supportingText = { if (bancoVacio) Text("El banco es obligatorio") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(numero, { numero = it.filter { c -> c.isDigit() }.take(16) }, label = { Text("Número de cuenta") }, placeholder = { Text("0000-0000-0000-0000") }, visualTransformation = formatoNumeroTarjeta, isError = numeroVacio || numeroIncompleto || numeroDuplicado, supportingText = { when { numeroVacio -> Text("El número de cuenta es obligatorio"); numeroIncompleto -> Text("Debe tener exactamente 16 dígitos (${numero.length}/16)"); numeroDuplicado -> Text("Ya existe una tarjeta con ese número") } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(titular, { titular = it }, label = { Text("Titular (opcional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }},
        confirmButton = { TextButton(enabled = valido && !isSaving, onClick = { onGuardar(banco.trim(), numero.trim(), titular.trim()) }) { Text(if (isSaving) "Guardando..." else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }
    )
}
