package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository

data class CartItem(val product: Product, var cantidad: Double)

data class SaleUiState(
    val isLoading: Boolean = false,
    val productos: List<Product> = emptyList(),
    val carrito: List<CartItem> = emptyList(),
    val ventas: List<Sale> = emptyList(),
    val error: String? = null,
    val ventaConfirmada: Boolean = false
)

class SaleViewModel(
    private val saleRepository: SaleRepository = SaleRepository(),
    private val productRepository: ProductRepository = ProductRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()
    private var almacenIdActual: String = ""
    private var androidIdActual: String = ""

    fun iniciar(androidId: String, almacenId: String) {
        androidIdActual = androidId
        almacenIdActual = almacenId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            productRepository.getProducts(androidId, almacenId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, productos = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun agregarAlCarrito(product: Product, cantidad: Double): String? {
        if (cantidad <= 0 || cantidad > product.stock) return "Stock insuficiente"
        val carrito = _uiState.value.carrito.toMutableList()
        val existente = carrito.find { it.product.id == product.id }
        if (existente != null) existente.cantidad += cantidad else carrito.add(CartItem(product, cantidad))
        _uiState.value = _uiState.value.copy(carrito = carrito)
        return null
    }

    fun quitarDelCarrito(index: Int) {
        val carrito = _uiState.value.carrito.toMutableList()
        if (index in carrito.indices) { carrito.removeAt(index); _uiState.value = _uiState.value.copy(carrito = carrito) }
    }

    fun limpiarCarrito() { _uiState.value = _uiState.value.copy(carrito = emptyList()) }

    fun confirmarVenta(
        metodo: String, efectivo: Double, transferencia: Double,
        usuarioId: String, clienteCi: String, clienteTel: String, clienteNombre: String
    ) {
        viewModelScope.launch {
            val carrito = _uiState.value.carrito
            carrito.forEach { item ->
                val total = item.cantidad * item.product.precio
                saleRepository.registrarVenta(
                    androidIdActual, item.product.id.toString(), item.cantidad, total,
                    metodo, efectivo, transferencia, usuarioId, almacenIdActual,
                    clienteCi, clienteTel, clienteNombre
                )
            }
            _uiState.value = _uiState.value.copy(carrito = emptyList(), ventaConfirmada = true)
        }
    }

    fun limpiarVentaConfirmada() { _uiState.value = _uiState.value.copy(ventaConfirmada = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
