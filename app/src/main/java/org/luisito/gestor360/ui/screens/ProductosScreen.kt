package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import android.util.Log
import org.luisito.gestor360.utils.ExportManager
import org.luisito.gestor360.ui.components.BuscadorField
import org.luisito.gestor360.ui.components.ConfirmarEliminarDialog
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoChip
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.MermaViewModel
import org.luisito.gestor360.ui.viewmodels.ProductViewModel

private const val PRODUCTOS_POR_PAGINA = 25

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosScreen(
    androidId: String,
    rol: String,
    onBack: (() -> Unit)? = null,
    viewModel: ProductViewModel = viewModel(),
    mermaViewModel: MermaViewModel = viewModel()
) {
    val esAdmin = rol == "admin"
    val uiState by viewModel.uiState.collectAsState()
    val mermaUiState by mermaViewModel.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }
    var pagina by remember { mutableStateOf(0) }
    var productoEnEdicion by remember { mutableStateOf<Product?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var productoParaMerma by remember { mutableStateOf<Product?>(null) }
    var productoAEliminar by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    val categorias = remember(uiState.productos) {
        uiState.productos.mapNotNull { it.categoria?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
    }

    val filtrados = remember(uiState.productos, query, categoriaSeleccionada) {
        uiState.productos.filter { producto ->
            val coincideNombre = query.isBlank() || producto.nombre.contains(query, ignoreCase = true)
            val coincideCategoria = categoriaSeleccionada == null || producto.categoria == categoriaSeleccionada
            coincideNombre && coincideCategoria
        }
    }

    // Si el filtro cambia y la página actual queda fuera de rango, vuelve a la primera.
    LaunchedEffect(query, categoriaSeleccionada, uiState.productos) {
        pagina = 0
    }

    val totalPaginas = maxOf(1, (filtrados.size + PRODUCTOS_POR_PAGINA - 1) / PRODUCTOS_POR_PAGINA)
    val paginaSegura = pagina.coerceIn(0, totalPaginas - 1)
    val pagina0 = filtrados.drop(paginaSegura * PRODUCTOS_POR_PAGINA).take(PRODUCTOS_POR_PAGINA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productos") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = {
                            // La flecha de arriba sale de la paginación primero, no de la pantalla.
                            if (paginaSegura > 0) pagina = 0 else onBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refrescar() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                    var mostrarConfirmarDescarga by remember { mutableStateOf(false) }
                    if (mostrarConfirmarDescarga) {
                        AlertDialog(
                            onDismissRequest = { mostrarConfirmarDescarga = false },
                            title = { Text("Descargar productos") },
                            text = { Text("Se descargará un archivo CSV con la lista de productos.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val uri = ExportManager.exportarProductosCSV(context, uiState.productos)
                                    uri?.let { ExportManager.compartir(context, it, "Productos") }
                                    mostrarConfirmarDescarga = false
                                }) { Text("Descargar") }
                            },
                            dismissButton = { TextButton(onClick = { mostrarConfirmarDescarga = false }) { Text("Cancelar") } }
                        )
                    }
                    IconButton(onClick = { mostrarConfirmarDescarga = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar")
                    }
                    IconButton(onClick = {
                        val uri = ExportManager.exportarProductosCSV(context, uiState.productos)
                        uri?.let { ExportManager.compartir(context, it, "Productos") }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar")
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            Column(modifier = Modifier.padding(16.dp)) {
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

                if (categorias.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = categoriaSeleccionada == null,
                                onClick = { categoriaSeleccionada = null },
                                label = { Text("Todas") }
                            )
                        }
                        items(categorias) { categoria ->
                            FilterChip(
                                selected = categoriaSeleccionada == categoria,
                                onClick = { categoriaSeleccionada = if (categoriaSeleccionada == categoria) null else categoria },
                                label = { Text(categoria) }
                            )
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                filtrados.isEmpty() -> EstadoVacio(if (query.isBlank() && categoriaSeleccionada == null) "Aún no hay productos en este local" else "Sin resultados")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pagina0, key = { it.id }) { producto ->
                        ProductoCard(
                            producto = producto,
                            esAdmin = esAdmin,
                            onEditar = { productoEnEdicion = producto; mostrarFormulario = true },
                            onMerma = { productoParaMerma = producto },
                            onEliminar = { productoAEliminar = producto }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            if (filtrados.isNotEmpty() && totalPaginas > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (paginaSegura > 0) pagina = paginaSegura - 1 }, enabled = paginaSegura > 0) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        Text("Anterior")
                    }
                    Text("Página ${paginaSegura + 1} de $totalPaginas", style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = { if (paginaSegura < totalPaginas - 1) pagina = paginaSegura + 1 },
                        enabled = paginaSegura < totalPaginas - 1
                    ) {
                        Text("Siguiente")
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (mostrarFormulario && esAdmin) {
        ProductoFormDialog(
            producto = productoEnEdicion,
            categoriasExistentes = categorias,
            isSaving = uiState.isSaving,
            onDismiss = { mostrarFormulario = false },
            onGuardar = { nombre, precio, stock, ubicacion, categoria ->
                val existente = productoEnEdicion
                if (existente == null) viewModel.crear(nombre, precio, stock, ubicacion, categoria)
                else viewModel.editar(existente.id, nombre, precio, stock, ubicacion, categoria)
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
                        androidId = androidId,
                        productoId = producto.id,
                        cantidad = cantidad,
                        motivo = motivo,
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
                if (!producto.ubicacion.isNullOrBlank() || !producto.categoria.isNullOrBlank()) {
                    Text(
                        listOfNotNull(
                            producto.ubicacion?.takeIf { it.isNotBlank() }?.let { "📍 $it" },
                            producto.categoria?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductoFormDialog(
    producto: Product?,
    categoriasExistentes: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, precio: Double, stock: Double, ubicacion: String, categoria: String) -> Unit
) {
    var nombre by remember { mutableStateOf(producto?.nombre ?: "") }
    var precioTexto by remember { mutableStateOf(producto?.precio?.toString() ?: "") }
    var stockTexto by remember { mutableStateOf(producto?.stock?.toString() ?: "") }
    var ubicacion by remember { mutableStateOf(producto?.ubicacion ?: "") }
    var categoria by remember { mutableStateOf(producto?.categoria ?: "") }
    var menuCategoriaAbierto by remember { mutableStateOf(false) }

    val precio = precioTexto.toDoubleOrNull()
    val stock = stockTexto.toDoubleOrNull()
    val valido = nombre.isNotBlank() && precio != null && precio > 0 && stock != null && stock >= 0

    val sugerencias = remember(categoria, categoriasExistentes) {
        if (categoria.isBlank()) categoriasExistentes
        else categoriasExistentes.filter { it.contains(categoria, ignoreCase = true) && it != categoria }
    }

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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ubicacion, onValueChange = { ubicacion = it.uppercase() },
                    label = { Text("Ubicación (ej: A-03-12)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = menuCategoriaAbierto && sugerencias.isNotEmpty(),
                    onExpandedChange = { menuCategoriaAbierto = it }
                ) {
                    OutlinedTextField(
                        value = categoria,
                        onValueChange = { categoria = it; menuCategoriaAbierto = true },
                        label = { Text("Categoría") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = menuCategoriaAbierto && sugerencias.isNotEmpty(),
                        onDismissRequest = { menuCategoriaAbierto = false }
                    ) {
                        sugerencias.forEach { sugerencia ->
                            DropdownMenuItem(
                                text = { Text(sugerencia) },
                                onClick = { categoria = sugerencia; menuCategoriaAbierto = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido && !isSaving,
                onClick = { onGuardar(nombre.trim(), precio ?: 0.0, stock ?: 0.0, ubicacion.trim(), categoria.trim()) }
            ) { Text(if (isSaving) "Guardando..." else "Guardar") }
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
