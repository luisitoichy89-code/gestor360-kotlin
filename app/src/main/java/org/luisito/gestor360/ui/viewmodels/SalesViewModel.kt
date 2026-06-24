package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.domain.repository.IProductRepository
import org.luisito.gestor360.domain.repository.ISaleRepository
import org.luisito.gestor360.domain.result.Result

class SalesViewModel(
    private val productRepo: IProductRepository,
    private val saleRepo: ISaleRepository
) : ViewModel() {
    
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products
    
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems
    
    private val _sales = MutableStateFlow<List<Sale>>(emptyList())
    val sales: StateFlow<List<Sale>> = _sales
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage
    
    init {
        loadProducts()
        loadSales()
    }
    
    fun loadProducts() {
        viewModelScope.launch {
            when (val result = productRepo.getProducts()) {
                is Result.Success -> _products.value = result.data
                is Result.Error -> _error.value = result.message
            }
        }
    }
    
    fun loadSales() {
        viewModelScope.launch {
            when (val result = saleRepo.getSales()) {
                is Result.Success -> _sales.value = result.data
                is Result.Error -> _error.value = result.message
            }
        }
    }
    
    fun addToCart(product: Product, quantity: Int = 1) {
        val currentCart = _cartItems.value.toMutableList()
        val existingItem = currentCart.find { it.product.id == product.id }
        
        if (existingItem != null) {
            existingItem.quantity += quantity
        } else {
            currentCart.add(CartItem(product, quantity))
        }
        
        _cartItems.value = currentCart
    }
    
    fun removeFromCart(productId: String) {
        _cartItems.value = _cartItems.value.filter { it.product.id != productId }
    }
    
    fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            removeFromCart(productId)
            return
        }
        
        val currentCart = _cartItems.value.toMutableList()
        val item = currentCart.find { it.product.id == productId }
        item?.let {
            it.quantity = quantity
            _cartItems.value = currentCart
        }
    }
    
    fun clearCart() {
        _cartItems.value = emptyList()
    }
    
    fun getTotal(): Double {
        return _cartItems.value.sumOf { it.product.price * it.quantity }
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
            _error.value = null
            
            val total = getTotal()
            if (total == 0.0) {
                _error.value = "El carrito está vacío"
                _isLoading.value = false
                return@launch
            }
            
            val sale = Sale(
                id = System.currentTimeMillis().toString(),
                saleItems = _cartItems.value.map { item ->
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
            
            when (val result = saleRepo.createSale(sale)) {
                is Result.Success -> {
                    _successMessage.value = "Venta completada exitosamente"
                    clearCart()
                    loadSales()
                }
                is Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }
    
    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}

data class CartItem(
    val product: Product,
    var quantity: Int
)
