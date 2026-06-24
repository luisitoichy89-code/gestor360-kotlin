package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository

class SalesViewModel(
    private val productRepository: ProductRepository = ProductRepository(),
    private val saleRepository: SaleRepository = SaleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    fun loadProducts(almacenId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val products = productRepository.getProducts(almacenId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    products = products,
                    error = if (products.isEmpty()) "No hay productos" else null
                )
            }
        }
    }

    fun addToCart(product: Product, cantidad: Double) {
        _uiState.update { state ->
            val existing = state.cart.find { it.productId == product.id }
            val newCart = if (existing != null) {
                state.cart.map {
                    if (it.productId == product.id) {
                        it.copy(cantidad = it.cantidad + cantidad)
                    } else it
                }
            } else {
                state.cart + CartItem(
                    productId = product.id,
                    nombre = product.nombre,
                    precio = product.precio,
                    cantidad = cantidad,
                    stockDisponible = product.stock
                )
            }
            state.copy(cart = newCart)
        }
    }

    fun removeFromCart(productId: Int) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filter { it.productId != productId })
        }
    }

    fun updateCartQuantity(productId: Int, cantidad: Double) {
        _uiState.update { state ->
            val newCart = state.cart.map {
                if (it.productId == productId) {
                    it.copy(cantidad = cantidad)
                } else it
            }.filter { it.cantidad > 0 }
            state.copy(cart = newCart)
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList()) }
    }

    fun confirmSale(
        metodo: String,
        efectivo: Double,
        transferencia: Double,
        usuarioId: Int,
        almacenId: String,
        clienteCi: String = "",
        clienteTel: String = "",
        clienteNombre: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val cart = _uiState.value.cart
            var allSuccess = true

            for (item in cart) {
                val total = item.subtotal
                val success = saleRepository.createSale(
                    productoId = item.productId,
                    productoNombre = item.nombre,
                    cantidad = item.cantidad,
                    precioUnit = item.precio,
                    total = total,
                    metodo = metodo,
                    efectivo = efectivo / cart.size,
                    transferencia = transferencia / cart.size,
                    usuarioId = usuarioId,
                    almacenId = almacenId,
                    clienteCi = clienteCi,
                    clienteTel = clienteTel,
                    clienteNombre = clienteNombre
                )
                if (!success) allSuccess = false
            }

            if (allSuccess) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        cart = emptyList(),
                        saleCompleted = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error al procesar la venta"
                    )
                }
            }
        }
    }

    fun resetSaleState() {
        _uiState.update { it.copy(saleCompleted = false, error = null) }
    }
}

data class SalesUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val products: List<Product> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val saleCompleted: Boolean = false,
    val error: String? = null
)
