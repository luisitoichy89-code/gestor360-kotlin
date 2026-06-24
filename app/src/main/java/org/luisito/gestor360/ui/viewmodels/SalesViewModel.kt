package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository

class SalesViewModel(
    private val productRepo: ProductRepository,
    private val saleRepo: SaleRepository
) : ViewModel() {
    
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products
    
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    init {
        loadProducts()
    }
    
    fun loadProducts() {
        viewModelScope.launch {
            _products.value = productRepo.getProducts()
        }
    }
    
    fun addToCart(product: Product, quantity: Int) {
        val currentCart = _cart.value.toMutableList()
        val existingItem = currentCart.find { it.product.id == product.id }
        
        if (existingItem != null) {
            existingItem.quantity += quantity
        } else {
            currentCart.add(CartItem(product, quantity))
        }
        
        _cart.value = currentCart
    }
    
    fun removeFromCart(productId: String) {
        _cart.value = _cart.value.filter { it.product.id != productId }
    }
    
    fun clearCart() {
        _cart.value = emptyList()
    }
    
    fun calculateTotal(): Double {
        return _cart.value.sumOf { it.product.price * it.quantity }
    }
    
    fun completeSale(
        metodo: String,
        efectivo: Double? = null,
        transferencia: Double? = null,
        clienteCi: String? = null,
        clienteTel: String? = null,
        clienteNombre: String? = null,
        usuarioId: String = "1",
        almacenId: String = "1"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val total = calculateTotal()
            val sale = Sale(
                id = System.currentTimeMillis().toString(),
                saleItems = _cart.value.map { item ->
                    mapOf(
                        "productId" to item.product.id,
                        "quantity" to item.quantity,
                        "price" to item.product.price,
                        "total" to (item.product.price * item.quantity)
                    )
                },
                total = total,
                metodo = metodo,
                efectivo = efectivo ?: 0.0,
                transferencia = transferencia ?: 0.0,
                usuarioId = usuarioId,
                almacenId = almacenId,
                clienteCi = clienteCi,
                clienteTel = clienteTel,
                clienteNombre = clienteNombre,
                timestamp = System.currentTimeMillis()
            )
            
            saleRepo.createSale(sale)
            clearCart()
            _isLoading.value = false
        }
    }
}

data class CartItem(
    val product: Product,
    var quantity: Int
)
