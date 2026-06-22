package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.model.CartItem
import org.luisito.gestor360.data.model.Product
import org.luisito.gestor360.data.model.Sale
import org.luisito.gestor360.data.remote.SupabaseSync
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository

class SalesViewModel(
    private val productRepository: ProductRepository,
    private val saleRepository: SaleRepository,
    private val almacenId: String = "1"
) : ViewModel() {

    private val _productos = MutableStateFlow<List<Product>>(emptyList())
    val productos: StateFlow<List<Product>> = _productos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    fun loadProductos() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val productos = productRepository.getProductsByAlmacen(almacenId)
                _productos.value = productos
            } catch (e: Exception) {
                // Manejar error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchProductos(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadProductos()
                return@launch
            }
            _isLoading.value = true
            try {
                val resultados = productRepository.searchProducts(query, almacenId)
                _productos.value = resultados
            } catch (e: Exception) {
                // Manejar error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveSale(
        cartItems: List<CartItem>,
        total: Double,
        method: String,
        cashAmount: Double,
        transferAmount: Double,
        usuarioId: Long,
        clienteId: String
    ): Boolean {
        viewModelScope.launch {
            try {
                // 1. Reducir stock de cada producto localmente
                cartItems.forEach { item ->
                    productRepository.reduceStock(item.id, item.cantidad)
                }

                // 2. Guardar cada item como una venta individual
                val sales = cartItems.map { item ->
                    Sale(
                        productoId = item.id,
                        productoNombre = item.nombre,
                        cantidad = item.cantidad,
                        precioUnit = item.precio,
                        total = item.subtotal,
                        metodo = method,
                        efectivo = cashAmount,
                        transferencia = transferAmount,
                        usuarioId = usuarioId,
                        almacenId = almacenId
                    )
                }

                // 3. Guardar en Room y sincronizar con Supabase
                sales.forEach { sale ->
                    val saleId = saleRepository.saveSale(sale)
                    val synced = SupabaseSync.syncSale(sale, clienteId)
                    if (!synced) {
                        // Marcar como pendiente de sincronización (para implementar después)
                    }
                }

                // 4. Sincronizar stock actualizado con Supabase
                cartItems.forEach { item ->
                    val product = productRepository.getProductById(item.id)
                    if (product != null) {
                        SupabaseSync.syncProductStock(item.id, product.stock, clienteId)
                    }
                }

                _syncStatus.value = "Venta guardada y sincronizada"
                return@launch true
            } catch (e: Exception) {
                _syncStatus.value = "Error al guardar la venta: ${e.message}"
                return@launch false
            }
        }
        return true
    }
}
