package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.ui.viewmodels.ProductViewModel
import org.luisito.gestor360.utils.ExportManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosScreen(
    androidId: String,
    rol: String,
    onBack: () -> Unit,
    viewModel: ProductViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var categoriaFiltro by remember { mutableStateOf("Todas") }
    var pagina by remember { mutableStateOf(0) }
    val porPagina = 25
    var mostrarFormulario by remember { mutableStateOf(false) }
    var productoEditar by remember { mutableStateOf<Product?>(null) }
    var mostrarConfirmarDescarga by remember { mutableStateOf(false) }
    val esAdmin = rol == "admin"

    LaunchedEffect(androidId) { viewModel.cargar(androidId, "1") }

    val productosFiltrados = uiState.productos
        .filter { if (categoriaFiltro == "Todas") true else it.categoria == categoriaFiltro }
        .filter { query.isBlank() || it.nombre.contains(query, true) }
    val totalPaginas = if (productosFiltrados.isEmpty()) 1 else (productosFiltrados.size - 1) / porPagina + 1
    val paginaSegura = pagina.coerceIn(0, totalPaginas - 1)
    val productosPaginados = productosFiltrados.drop(paginaSegura * porPagina).take(porPagina)
    val categorias = listOf("Todas") + uiState.productos.mapNotNull { it.categoria }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productos") },
                navigationIcon = { IconButton(onClick = { if (paginaSegura > 0) pagina = 0 else onBack() }) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { mostrarConfirmarDescarga = true }) { Icon(Icons.Default.Download, "Descargar") }
                }
            )
        },
        floatingActionButton = {
            if (esAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { productoEditar = null; mostrarFormulario = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Nuevo producto") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Buscador
            OutlinedTextField(value = query, onValueChange = { query = it; pagina = 0 }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
            
            // Chips de categoría
            if (categorias.size > 1) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    categorias.forEach { cat ->
                        FilterChip(selected = categoriaFiltro == cat, onClick = { categoriaFiltro = cat; pagina = 0 }, label = { Text(cat) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                productosPaginados.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay productos") }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        items(productosPaginados) { producto ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(producto.nombre, style = MaterialTheme.typography.titleSmall)
                                        Text("${producto.stock} uds · ${producto.precio} CUP", style = MaterialTheme.typography.bodySmall)
                                        if (!producto.ubicacion.isNullOrBlank()) Text("📍 ${producto.ubicacion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        if (!producto.categoria.isNullOrBlank()) Text("🏷 ${producto.categoria}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    if (esAdmin) {
                                        Row {
                                            IconButton(onClick = { productoEditar = producto; mostrarFormulario = true }) { Icon(Icons.Default.Edit, "Editar") }
                                            IconButton(onClick = { viewModel.eliminar(producto.id) }) { Icon(Icons.Default.Delete, "Eliminar") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Paginación
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { if (pagina > 0) pagina-- }, enabled = pagina > 0) { Text("← Anterior") }
                        Text("Pág ${pagina + 1} de $totalPaginas")
                        TextButton(onClick = { if (pagina < totalPaginas - 1) pagina++ }, enabled = pagina < totalPaginas - 1) { Text("Siguiente →") }
                    }
                }
            }
        }
    }

    if (mostrarFormulario) {
        var nombre by remember { mutableStateOf(productoEditar?.nombre ?: "") }
        var precio by remember { mutableStateOf(productoEditar?.precio?.toString() ?: "") }
        var stock by remember { mutableStateOf(productoEditar?.stock?.toString() ?: "") }
        var ubicacion by remember { mutableStateOf(productoEditar?.ubicacion ?: "") }
        var categoria by remember { mutableStateOf(productoEditar?.categoria ?: "") }
        AlertDialog(
            onDismissRequest = { mostrarFormulario = false },
            title = { Text(if (productoEditar != null) "Editar" else "Nuevo") },
            text = {
                Column {
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") })
                    OutlinedTextField(precio, { precio = it }, label = { Text("Precio") })
                    OutlinedTextField(stock, { stock = it }, label = { Text("Stock") })
                    OutlinedTextField(ubicacion, { ubicacion = it }, label = { Text("Ubicación (ej: A-03-12)") })
                    OutlinedTextField(categoria, { categoria = it }, label = { Text("Categoría") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (productoEditar != null) viewModel.editar(productoEditar!!.id, nombre, precio.toDoubleOrNull() ?: 0.0, stock.toDoubleOrNull() ?: 0.0, ubicacion, categoria)
                    else viewModel.crear(nombre, precio.toDoubleOrNull() ?: 0.0, stock.toDoubleOrNull() ?: 0.0, ubicacion, categoria)
                    mostrarFormulario = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { mostrarFormulario = false }) { Text("Cancelar") } }
        )
    }

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
}
