package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.ui.components.PaymentDialog
import org.luisito.gestor360.ui.viewmodels.SalesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    almacenId: String = "1",
    usuarioId: Int = 1,
    onBack: () -> Unit
) {
    val viewModel: SalesViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProducts(almacenId)
    }

    val totalCarrito = uiState.cart.sumOf { it.subtotal }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛒 Vender") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (uiState.cart.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showPaymentDialog = true }
                ) {
                    Text("💰 ${String.format("%.2f", totalCarrito)}")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (uiState.error != null) {
                Text(text = uiState.error ?: "Error", color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.products) { product ->
                        ProductCard(
                            product = product,
                            onAdd = { viewModel.addToCart(product, 1.0) }
                        )
                    }
                }

                if (uiState.cart.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "🛒 Carrito",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.cart.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.nombre} x${item.cantidad}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateCartQuantity(
                                                    item.productId,
                                                    item.cantidad - 1
                                                )
                                            }
                                        ) {
                                            Text("−")
                                        }
                                        Text(
                                            text = "${String.format("%.2f", item.subtotal)}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.updateCartQuantity(
                                                    item.productId,
                                                    item.cantidad + 1
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Agregar")
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeFromCart(item.productId) }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Eliminar")
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total:",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${String.format("%.2f", totalCarrito)} CUP",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        PaymentDialog(
            total = totalCarrito,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { metodo, efectivo, transferencia ->
                viewModel.confirmSale(
                    metodo = metodo,
                    efectivo = efectivo,
                    transferencia = transferencia,
                    usuarioId = usuarioId,
                    almacenId = almacenId
                )
                showPaymentDialog = false
            },
            isProcessing = uiState.isProcessing
        )
    }

    if (uiState.saleCompleted) {
        LaunchedEffect(Unit) {
            viewModel.resetSaleState()
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = product.nombre,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Stock: ${product.stock} | ${product.precio} CUP",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(
                onClick = onAdd,
                enabled = product.stock > 0
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Agregar al carrito",
                    tint = if (product.stock > 0) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
