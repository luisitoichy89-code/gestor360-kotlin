package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.ui.components.BuscadorField
import org.luisito.gestor360.ui.components.ConfirmarEliminarDialog
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoChip
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.MermaViewModel
import org.luisito.gestor360.ui.viewmodels.ProductViewModel

/**
 * CRUD de productos/inventario para el local activo.
 * El manejo de merma depende del rol:
 *  - admin: registra la merma directamente (descuenta stock al instante).
 *  - seller: solo "propone" la merma; queda pendiente hasta que el admin la
 *    aprueba desde AprobacionesScreen (no descuenta stock todavía).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosScreen(
    almacenId: String,
    clienteId: String,
    usuarioId: Long,
    usuarioNombre: String,
    rol: String,
    onBack: (() -> Unit)? = null,
    viewModel: ProductViewModel = viewModel(),
    mermaViewModel: MermaViewModel = viewModel()
) {
    val esAdmin = rol == "admin"
    val uiState by viewModel.uiState.collectAsState()
    val mermaUiState by mermaViewModel.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var productoEnEdicion by remember { mutableStateOf<Product?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var productoParaMerma by remember { mutableStateOf<Product?>(null) }
    var productoAEliminar by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(almacenId) { viewModel.cargar(almacenId) }

    val filtrados = remember(uiState.productos, query) {
        if (query.isBlank()) uiState.productos
        else uiState.productos.filter { it.nombre.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productos") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refrescar() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                }
            )
        },
        floatingActionButton = {
            if (esAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { productoEnEdicion = null; mostrarFormulario = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo producto") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            mermaUiState.mensaje?.let { mensaje ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(mensaje, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                LaunchedEffect(mensaje) {
                    kotlinx.coroutines.delay(2500)
                    mermaViewModel.clearMensaje()
                }
            }

            BuscadorField(query = query, onQueryChange = { query = it }, placeholder = "Buscar producto...")
            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                filtrados.isEmpty() -> EstadoVacio(if (query.isBlank()) "Aún no hay productos en este local" else "Sin resultados para \"$query\"")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtrados, key = { it.id }) { producto ->
                        ProductoCard(
                            producto = producto,
                            esAdmin = esAdmin,
                            onEditar = { productoEnEdicion = producto; mostrarFormulario = true },
                            onMerma = { productoParaMerma = producto },
                            onEliminar = { productoAEliminar = producto }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (mostrarFormulario && esAdmin) {
        ProductoFormDialog(
            producto = productoEnEdicion,
            isSaving = uiState.isSaving,
            onDismiss = { mostrarFormulario = false },
            onGuardar = { nombre, precio, stock ->
                val existente = productoEnEdicion
                if (existente == null) viewModel.crear(nombre, precio, stock)
                else viewModel.editar(existente.id, nombre, precio, stock)
                mostrarFormulario = false
            }
        )
    }

    productoParaMerma?.let { producto ->
        MermaDialog(
            producto = producto,
            esAdmin = esAdmin,
            isSaving = if (esAdmin) uiState.isSaving else mermaUiState.isSaving,
            onDismiss = { productoParaMerma = null },
            onConfirmar = { cantidad, motivo ->
                if (esAdmin) {
                    viewModel.registrarMerma(producto, cantidad)
                    productoParaMerma = null
                } else {
                    mermaViewModel.solicitar(
                        productoId = producto.id,
                        productoNombre = producto.nombre,
                        cantidad = cantidad,
                        motivo = motivo,
                        almacenId = almacenId,
                        clienteId = clienteId,
                        solicitadoPor = usuarioId,
                        solicitadoPorNombre = usuarioNombre,
                        onListo = { productoParaMerma = null }
                    )
                }
            }
        )
    }

    if (esAdmin) {
        productoAEliminar?.let { producto ->
            ConfirmarEliminarDialog(
                nombre = producto.nombre,
                onConfirm = { viewModel.eliminar(producto.id); productoAEliminar = null },
                onDismiss = { productoAEliminar = null }
            )
        }
    }
}

@Composable
private fun ProductoCard(
    producto: Product,
    esAdmin: Boolean,
    onEditar: () -> Unit,
    onMerma: () -> Unit,
    onEliminar: () -> Unit
) {
    var menuAbierto by remember { mutableStateOf(false) }
    val stockBajo = producto.stock <= 5
    val sinStock = producto.stock <= 0

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = when {
                    sinStock -> MaterialTheme.colorScheme.error
                    stockBajo -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(producto.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${producto.precio} CUP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                EstadoChip(
                    activo = !sinStock,
                    textoActivo = if (stockBajo) "Stock bajo: ${formatearCantidad(producto.stock)}" else "Stock: ${formatearCantidad(producto.stock)}",
                    textoInactivo = "Sin stock"
                )
            }
            Box {
                IconButton(onClick = { menuAbierto = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    if (esAdmin) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuAbierto = false; onEditar() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (esAdmin) "Registrar merma" else "Proponer merma") },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) },
                        onClick = { menuAbierto = false; onMerma() }
                    )
                    if (esAdmin) {
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuAbierto = false; onEliminar() }
                        )
                    }
                }
            }
        }
    }
}

private fun formatearCantidad(valor: Double): String =
    if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()

@Composable
private fun ProductoFormDialog(
    producto: Product?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, precio: Double, stock: Double) -> Unit
) {
    var nombre by remember { mutableStateOf(producto?.nombre ?: "") }
    var precioTexto by remember { mutableStateOf(producto?.precio?.toString() ?: "") }
    var stockTexto by remember { mutableStateOf(producto?.stock?.toString() ?: "") }

    val precio = precioTexto.toDoubleOrNull()
    val stock = stockTexto.toDoubleOrNull()
    val valido = nombre.isNotBlank() && precio != null && precio > 0 && stock != null && stock >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (producto == null) "Nuevo producto" else "Editar producto") },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it.uppercase() },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = precioTexto, onValueChange = { precioTexto = it },
                    label = { Text("Precio (CUP)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = stockTexto, onValueChange = { stockTexto = it },
                    label = { Text(if (producto == null) "Stock inicial" else "Stock total") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valido && !isSaving, onClick = { onGuardar(nombre.trim(), precio ?: 0.0, stock ?: 0.0) }) {
                Text(if (isSaving) "Guardando..." else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MermaDialog(
    producto: Product,
    esAdmin: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (cantidad: Double, motivo: String) -> Unit
) {
    var cantidadTexto by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    val cantidad = cantidadTexto.toDoubleOrNull()
    val valido = cantidad != null && cantidad > 0 && cantidad <= producto.stock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (esAdmin) "Registrar merma" else "Proponer merma") },
        text = {
            Column {
                Text("${producto.nombre} — stock disponible: ${formatearCantidad(producto.stock)}")
                if (!esAdmin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Esto queda pendiente hasta que el admin la apruebe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = cantidadTexto,
                    onValueChange = { cantidadTexto = it },
                    label = { Text("Cantidad a dar de baja") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = cantidad != null && cantidad > producto.stock,
                    supportingText = {
                        if (cantidad != null && cantidad > producto.stock) Text("No puede superar el stock disponible")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo (rotura, vencimiento, robo...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = { onConfirmar(cantidad ?: 0.0, motivo.trim()) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(if (isSaving) "Enviando..." else if (esAdmin) "Registrar" else "Enviar a aprobar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
