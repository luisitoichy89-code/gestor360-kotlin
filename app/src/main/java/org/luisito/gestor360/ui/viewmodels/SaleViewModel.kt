package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository

data class SaleUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val productos: List<Product> = emptyList(),
    val carrito: List<CartItem> = emptyList(),
    val error: String? = null,
    val ventaConfirmada: Boolean = false
) {
    val totalCarrito: Double get() = carrito.sumOf { it.subtotal }
}

class SaleViewModel(
    private val productRepository: ProductRepository = ProductRepository(),
    private val saleRepository: SaleRepository = SaleRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()
    private var androidIdActual: String = ""

    fun iniciar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            productRepository.getProducts(androidId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, productos = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun agregarAlCarrito(producto: Product, cantidad: Double): String? {
        if (cantidad <= 0) return "Cantidad inválida"
        val carrito = _uiState.value.carrito.toMutableList()
        val index = carrito.indexOfFirst { it.productId == producto.id }
        val cantidadActual = if (index >= 0) carrito[index].cantidad else 0.0
        if (cantidadActual + cantidad > producto.stock) return "Stock insuficiente (${producto.stock.toInt()} disponibles)"
        if (index >= 0) {
            val item = carrito[index]
            carrito[index] = item.copy(cantidad = item.cantidad + cantidad)
        } else {
            carrito.add(CartItem(producto.id, producto.nombre, producto.precio, cantidad, producto.stock))
        }
        _uiState.value = _uiState.value.copy(carrito = carrito)
        return null
    }

    fun quitarDelCarrito(index: Int) {
        val carrito = _uiState.value.carrito.toMutableList()
        if (index in carrito.indices) { carrito.removeAt(index); _uiState.value = _uiState.value.copy(carrito = carrito) }
    }

    fun limpiarCarrito() { _uiState.value = _uiState.value.copy(carrito = emptyList()) }

    fun confirmarVenta(metodo: String, efectivo: Double, transferencia: Double, usuarioId: Long, cliente: SaleRepository.DatosCliente?) {
        if (_uiState.value.isSaving) return
        if (_uiState.value.carrito.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            saleRepository.guardarVenta(androidIdActual, _uiState.value.carrito, metodo, efectivo, transferencia, cliente)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(carrito = emptyList(), ventaConfirmada = true, isSaving = false)
                    iniciar(androidIdActual)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "No se pudo guardar la venta")
                }
        }
    }

    fun cancelarVenta(motivo: String) {
        limpiarCarrito()
        // TODO: guardar motivo en historial de cancelaciones (cierre de turno)
    }

    fun anularVenta(ventaId: String) {
        viewModelScope.launch {
            saleRepository.anularVenta(androidIdActual, ventaId)
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun limpiarVentaConfirmada() { _uiState.value = _uiState.value.copy(ventaConfirmada = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
