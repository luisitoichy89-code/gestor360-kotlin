package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.ui.viewmodels.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosScreen(
    androidId: String,
    almacenId: String,
    onBack: () -> Unit,
    viewModel: ProductViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarFormulario by remember { mutableStateOf(false) }
    var productoEditar by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(androidId, almacenId) { viewModel.cargar(androidId, almacenId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Productos") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { productoEditar = null; mostrarFormulario = true }) { Icon(Icons.Default.Add, "Nuevo") } }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                uiState.productos.isEmpty() -> Text("No hay productos")
                else -> LazyColumn { items(uiState.productos) { p -> ProductoItem(p, onEdit = { productoEditar = p; mostrarFormulario = true }, onDelete = { viewModel.eliminar(p.id) }) } }
            }
        }
    }

    if (mostrarFormulario) {
        ProductoFormDialog(productoEditar, uiState.isSaving,
            onDismiss = { mostrarFormulario = false },
            onGuardar = { nombre, precio, stock ->
                if (productoEditar != null) viewModel.editar(productoEditar!!.id, nombre, precio, stock)
                else viewModel.crear(nombre, precio, stock)
                mostrarFormulario = false
            }
        )
    }
}

@Composable
private fun ProductoItem(p: Product, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(p.nombre, style = MaterialTheme.typography.titleSmall); Text("${p.stock} uds · ${p.precio} CUP") }
            Row { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") } }
        }
    }
}

@Composable
private fun ProductoFormDialog(producto: Product?, isSaving: Boolean, onDismiss: () -> Unit, onGuardar: (String, Double, Double) -> Unit) {
    var nombre by remember { mutableStateOf(producto?.nombre ?: "") }
    var precio by remember { mutableStateOf(producto?.precio?.toString() ?: "") }
    var stock by remember { mutableStateOf(producto?.stock?.toString() ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (producto != null) "Editar" else "Nuevo") },
        text = {
            Column {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") })
                OutlinedTextField(precio, { precio = it }, label = { Text("Precio") })
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") })
            }
        },
        confirmButton = { TextButton(onClick = { onGuardar(nombre, precio.toDoubleOrNull() ?: 0.0, stock.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "..." else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
