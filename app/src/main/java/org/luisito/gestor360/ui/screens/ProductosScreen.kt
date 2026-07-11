package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.AprobacionStockViewModel
import org.luisito.gestor360.ui.viewmodels.DevolucionViewModel
import org.luisito.gestor360.ui.viewmodels.MermaViewModel
import org.luisito.gestor360.ui.viewmodels.ProductViewModel

private const val PRODUCTOS_POR_PAGINA = 25

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosScreen(
    androidId: String, rol: String, onBack: (() -> Unit)? = null,
    viewModel: ProductViewModel = viewModel(),
    mermaViewModel: MermaViewModel = viewModel(),
    aprobacionVM: AprobacionStockViewModel = viewModel(),
    devolucionViewModel: DevolucionViewModel = viewModel()
) {
    val esAdmin = rol == "admin"
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val mermaUiState by mermaViewModel.uiState.collectAsState()
    val aprobUiState by aprobacionVM.uiState.collectAsState()
    val devolucionUiState by devolucionViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }
    var pagina by remember { mutableStateOf(0) }
    var productoEnEdicion by remember { mutableStateOf<Product?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }
    var mostrarAumentoStock by remember { mutableStateOf<Product?>(null) }
    var productoParaMerma by remember { mutableStateOf<Product?>(null) }
    var productoParaDevolucion by remember { mutableStateOf<Product?>(null) }
    var productoAEliminar by remember { mutableStateOf<Product?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(aprobUiState.mensaje, aprobUiState.error) {
        aprobUiState.mensaje?.let { snackbarHostState.showSnackbar(it); aprobacionVM.clearMensaje() }
        aprobUiState.error?.let { snackbarHostState.showSnackbar("Error: $it"); aprobacionVM.clearError() }
    }
    LaunchedEffect(mermaUiState.mensaje, mermaUiState.error) {
        mermaUiState.mensaje?.let { snackbarHostState.showSnackbar(it); mermaViewModel.clearMensaje() }
        mermaUiState.error?.let { snackbarHostState.showSnackbar("Error: $it"); mermaViewModel.clearError() }
    }
    LaunchedEffect(devolucionUiState.mensaje, devolucionUiState.error) {
        devolucionUiState.mensaje?.let { snackbarHostState.showSnackbar(it); devolucionViewModel.clearMensaje() }
        devolucionUiState.error?.let { snackbarHostState.showSnackbar("Error: $it"); devolucionViewModel.clearError() }
    }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }
    val categorias = remember(uiState.productos) { uiState.productos.mapNotNull { it.categoria?.takeIf { c -> c.isNotBlank() } }.distinct().sorted() }
    val filtrados = remember(uiState.productos, query, categoriaSeleccionada) { uiState.productos.filter { p -> (query.isBlank() || p.nombre.contains(query, true)) && (categoriaSeleccionada == null || p.categoria == categoriaSeleccionada) } }
    LaunchedEffect(query, categoriaSeleccionada, uiState.productos) { pagina = 0 }
    val totalPaginas = maxOf(1, (filtrados.size + PRODUCTOS_POR_PAGINA - 1) / PRODUCTOS_POR_PAGINA)
    val paginaSegura = pagina.coerceIn(0, totalPaginas - 1)
    val pagina0 = filtrados.drop(paginaSegura * PRODUCTOS_POR_PAGINA).take(PRODUCTOS_POR_PAGINA)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = { TopAppBar(title = { Text("Productos", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { if (onBack != null) IconButton(onClick = { if (paginaSegura > 0) pagina = 0 else onBack() }) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) } }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = { productoEnEdicion = null; mostrarFormulario = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(if (esAdmin) "Nuevo producto" else "Solicitar") }, shape = RoundedCornerShape(16.dp)) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(16.dp)) {
                BuscadorField(query = query, onQueryChange = { query = it }, placeholder = "Buscar producto...")
                if (categorias.isNotEmpty()) { Spacer(modifier = Modifier.height(10.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item { FilterChip(selected = categoriaSeleccionada == null, onClick = { categoriaSeleccionada = null }, label = { Text("Todas") }) }; items(categorias) { cat -> FilterChip(selected = categoriaSeleccionada == cat, onClick = { categoriaSeleccionada = if (categoriaSeleccionada == cat) null else cat }, label = { Text(cat) }) } } }
            }
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                filtrados.isEmpty() -> EstadoVacio("Sin productos")
                else -> LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pagina0, key = { it.id }) { p -> ProductoCard(producto = p, esAdmin = esAdmin, onEditar = { productoEnEdicion = p; mostrarFormulario = true }, onMerma = { productoParaMerma = p }, onAumentarStock = { mostrarAumentoStock = p }, onDevolucion = { productoParaDevolucion = p }, onEliminar = { productoAEliminar = p }) }
                }
            }
        }
    }

    if (mostrarFormulario) ProductoFormDialog(productoEnEdicion, esAdmin, categorias, uiState.isSaving, { mostrarFormulario = false }) { nombre, precio, stock, ubicacion, categoria ->
        if (esAdmin) { if (productoEnEdicion == null) viewModel.crear(nombre, precio, stock, ubicacion, categoria) else viewModel.editar(productoEnEdicion!!.id, nombre, precio, stock, ubicacion, categoria) } else aprobacionVM.solicitarProducto(androidId, nombre, precio, stock)
        mostrarFormulario = false
    }

    if (mostrarAumentoStock != null) { var c by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = { mostrarAumentoStock = null }, title = { Text("Agregar stock · ${mostrarAumentoStock!!.nombre}") }, text = { OutlinedTextField(c, { c = it.filter { it.isDigit() } }, label = { Text("Cantidad a agregar") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }, confirmButton = { TextButton(onClick = { val cant = c.toDoubleOrNull() ?: 0.0; if (cant > 0) { aprobacionVM.solicitarAumento(androidId, mostrarAumentoStock!!.id, cant); mostrarAumentoStock = null } }) { Text("Enviar a aprobación") } }, dismissButton = { TextButton(onClick = { mostrarAumentoStock = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }) }

    productoParaMerma?.let { p -> MermaDialog(p, esAdmin, if (esAdmin) uiState.isSaving else mermaUiState.isSaving, { productoParaMerma = null }) { cantidad, motivo -> if (esAdmin) { viewModel.registrarMerma(p, cantidad); productoParaMerma = null } else { mermaViewModel.solicitar(androidId = androidId, productoId = p.id, productoNombre = p.nombre, cantidad = cantidad, motivo = motivo, onListo = { productoParaMerma = null }) } } }

    productoParaDevolucion?.let { p -> DevolucionDialog(p, devolucionUiState.isSaving, { productoParaDevolucion = null }) { cantidad, metodo, motivo -> devolucionViewModel.solicitar(androidId = androidId, productoId = p.id, productoNombre = p.nombre, cantidad = cantidad, metodo = metodo, motivo = motivo, onListo = { productoParaDevolucion = null }) } }

    if (esAdmin) productoAEliminar?.let { p -> ConfirmarEliminarDialog(p.nombre, { viewModel.eliminar(p.id); productoAEliminar = null }, { productoAEliminar = null }) }
}

@Composable
private fun ProductoCard(producto: Product, esAdmin: Boolean, onEditar: () -> Unit, onMerma: () -> Unit, onAumentarStock: () -> Unit, onDevolucion: () -> Unit, onEliminar: () -> Unit) {
    var menuAbierto by remember { mutableStateOf(false) }
    val stockBajo = producto.stock <= 5; val sinStock = producto.stock <= 0
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Inventory2, null, tint = when { sinStock -> MaterialTheme.colorScheme.error; stockBajo -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(producto.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${producto.precio} CUP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (!producto.ubicacion.isNullOrBlank() || !producto.categoria.isNullOrBlank()) Text(listOfNotNull(producto.ubicacion?.takeIf { it.isNotBlank() }?.let { "📍 $it" }, producto.categoria?.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                EstadoChip(activo = !sinStock, textoActivo = if (stockBajo) "Stock bajo: ${producto.stock.toInt()}" else "Stock: ${producto.stock.toInt()}", textoInactivo = "Sin stock")
            }
            Box { IconButton(onClick = { menuAbierto = true }) { Icon(Icons.Default.MoreVert, "Más") }; DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) { if (esAdmin) { DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuAbierto = false; onEditar() }) }; if (!esAdmin) { DropdownMenuItem(text = { Text("Agregar stock") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menuAbierto = false; onAumentarStock() }); DropdownMenuItem(text = { Text("Solicitar devolución") }, leadingIcon = { Icon(Icons.Default.AssignmentReturn, null) }, onClick = { menuAbierto = false; onDevolucion() }) }; DropdownMenuItem(text = { Text(if (esAdmin) "Registrar merma" else "Proponer merma") }, leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) }, onClick = { menuAbierto = false; onMerma() }); if (esAdmin) { DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuAbierto = false; onEliminar() }) } } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductoFormDialog(producto: Product?, esAdmin: Boolean, categoriasExistentes: List<String>, isSaving: Boolean, onDismiss: () -> Unit, onGuardar: (nombre: String, precio: Double, stock: Double, ubicacion: String, categoria: String) -> Unit) {
    var nombre by remember { mutableStateOf(producto?.nombre ?: "") }; var precioTexto by remember { mutableStateOf(producto?.precio?.toString() ?: "") }; var stockTexto by remember { mutableStateOf(producto?.stock?.toString() ?: "") }; var ubicacion by remember { mutableStateOf(producto?.ubicacion ?: "") }; var categoria by remember { mutableStateOf(producto?.categoria ?: "") }; var menuCategoriaAbierto by remember { mutableStateOf(false) }
    val precio = precioTexto.toDoubleOrNull(); val stock = stockTexto.toDoubleOrNull()
    val valido = nombre.isNotBlank() && precio != null && precio > 0 && stock != null && stock >= 0
    val sugerencias = remember(categoria, categoriasExistentes) { if (categoria.isBlank()) categoriasExistentes else categoriasExistentes.filter { it.contains(categoria, true) && it != categoria } }

    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text(if (producto == null) if (esAdmin) "Nuevo producto" else "Solicitar producto" else "Editar producto", fontWeight = FontWeight.Bold) }, text = { Column {
        OutlinedTextField(nombre, { nombre = it.uppercase() }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(10.dp))
        OutlinedTextField(precioTexto, { precioTexto = it }, label = { Text("Precio (CUP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(10.dp))
        OutlinedTextField(stockTexto, { stockTexto = it }, label = { Text(if (producto == null) "Stock inicial" else "Stock total") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(10.dp))
        OutlinedTextField(ubicacion, { ubicacion = it.uppercase() }, label = { Text("Ubicación (ej: A-03-12)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(10.dp))
        ExposedDropdownMenuBox(expanded = menuCategoriaAbierto && sugerencias.isNotEmpty(), onExpandedChange = { menuCategoriaAbierto = it }) { OutlinedTextField(categoria, { categoria = it; menuCategoriaAbierto = true }, label = { Text("Categoría") }, singleLine = true, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(expanded = menuCategoriaAbierto && sugerencias.isNotEmpty(), onDismissRequest = { menuCategoriaAbierto = false }) { sugerencias.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { categoria = s; menuCategoriaAbierto = false }) } } }
    } }, confirmButton = { TextButton(enabled = valido && !isSaving, onClick = { onGuardar(nombre.trim(), precio ?: 0.0, stock ?: 0.0, ubicacion.trim(), categoria.trim()) }) { Text(if (isSaving) "Guardando..." else if (!esAdmin) "Enviar a aprobación" else "Guardar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }) }

@Composable
private fun MermaDialog(producto: Product, esAdmin: Boolean, isSaving: Boolean, onDismiss: () -> Unit, onConfirmar: (cantidad: Double, motivo: String) -> Unit) {
    var cantidadTexto by remember { mutableStateOf("") }; var motivo by remember { mutableStateOf("") }
    val cantidad = cantidadTexto.toDoubleOrNull(); val valido = cantidad != null && cantidad > 0 && cantidad <= producto.stock
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text(if (esAdmin) "Registrar merma" else "Proponer merma", fontWeight = FontWeight.Bold) }, text = { Column {
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(12.dp)) { Text(producto.nombre, fontWeight = FontWeight.Bold); Text("Stock disponible: ${producto.stock.toInt()}", style = MaterialTheme.typography.bodySmall) } }
        Spacer(Modifier.height(12.dp))
        if (!esAdmin) { Text("Esta solicitud quedará pendiente de aprobación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
        OutlinedTextField(cantidadTexto, { cantidadTexto = it }, label = { Text("Cantidad") }, singleLine = true, isError = cantidad != null && cantidad > producto.stock, supportingText = { if (cantidad != null && cantidad > producto.stock) Text("No puede superar el stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp)); OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    } }, confirmButton = { TextButton(enabled = valido, onClick = { onConfirmar(cantidad ?: 0.0, motivo.trim()) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(if (isSaving) "Enviando..." else if (esAdmin) "Registrar" else "Enviar a aprobación", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } })
}

@Composable
private fun DevolucionDialog(producto: Product, isSaving: Boolean, onDismiss: () -> Unit, onConfirmar: (cantidad: Double, metodo: String, motivo: String) -> Unit) {
    var cantidadTexto by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("cash") }
    var motivo by remember { mutableStateOf("") }
    val cantidad = cantidadTexto.toDoubleOrNull()
    val valido = cantidad != null && cantidad > 0

    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text("Solicitar devolución", fontWeight = FontWeight.Bold) }, text = { Column {
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(12.dp)) { Text(producto.nombre, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.height(12.dp))
        Text("Esta solicitud quedará pendiente de aprobación del admin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(cantidadTexto, { cantidadTexto = it }, label = { Text("Cantidad devuelta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        Text("Método de devolución del dinero", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            FilterChip(selected = metodo == "cash", onClick = { metodo = "cash" }, label = { Text("Efectivo") })
            FilterChip(selected = metodo == "transfer", onClick = { metodo = "transfer" }, label = { Text("Transferencia") })
            FilterChip(selected = metodo == "mixed", onClick = { metodo = "mixed" }, label = { Text("Mixto") })
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    } }, confirmButton = { TextButton(enabled = valido && !isSaving, onClick = { onConfirmar(cantidad ?: 0.0, metodo, motivo.trim()) }) { Text(if (isSaving) "Enviando..." else "Enviar a aprobación", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } })
}
