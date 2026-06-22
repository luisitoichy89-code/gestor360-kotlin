package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.model.CartItem
import org.luisito.gestor360.data.model.Product
import org.luisito.gestor360.data.model.User
import org.luisito.gestor360.ui.components.ScaffoldWithDrawer
import org.luisito.gestor360.ui.viewmodels.CartViewModel
import org.luisito.gestor360.ui.viewmodels.SalesViewModel

@Composable
fun ProductSelectionScreen(
    user: User,
    cartViewModel: CartViewModel,
    salesViewModel: SalesViewModel,
    onNavigateToCheckout: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val productos by salesViewModel.productos.collectAsState()
    val isLoading by salesViewModel.isLoading.collectAsState()
    var searchText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        salesViewModel.loadProductos()
    }

    val cartItems by cartViewModel.cartItems.collectAsState()
    val total by cartViewModel.total.collectAsState()

    ScaffoldWithDrawer(
        rol = user.rol,
        title = "🛒 Ventas",
        onItemClick = { route ->
            when (route) {
                "ventas" -> { /* Ya estamos en ventas */ }
                "productos" -> { /* Navegar a productos */ }
                "merma" -> { /* Navegar a merma */ }
                "logout" -> onLogout()
            }
        },
        onLogout = onLogout
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { 
                    searchText = it
                    scope.launch {
                        salesViewModel.searchProductos(it)
                    }
                },
                placeholder = { Text("🔍 Buscar producto...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(productos) { producto ->
                        ProductCard(
                            producto = producto,
                            onAddToCart = { cantidad ->
                                cartViewModel.addItem(
                                    CartItem(
                                        id = producto.id,
                                        nombre = producto.nombre,
                                        precio = producto.precio,
                                        cantidad = cantidad,
                                        stockDisponible = producto.stock
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // FAB flotante para checkout
            if (cartItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onNavigateToCheckout,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Text("$${total}")
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    producto: Product,
    onAddToCart: (Int) -> Unit
) {
    var cantidad by remember { mutableStateOf(1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = producto.nombre,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "$${producto.precio}",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Stock: ${producto.stock}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (cantidad > 1) cantidad-- }
                ) {
                    Text("−", fontSize = 20.sp)
                }
                Text(
                    text = cantidad.toString(),
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { if (cantidad < producto.stock) cantidad++ }
                ) {
                    Text("+", fontSize = 20.sp)
                }
            }

            Button(
                onClick = { onAddToCart(cantidad) },
                modifier = Modifier.fillMaxWidth(),
                enabled = cantidad > 0 && cantidad <= producto.stock
            ) {
                Text("Agregar")
            }
        }
    }
}
