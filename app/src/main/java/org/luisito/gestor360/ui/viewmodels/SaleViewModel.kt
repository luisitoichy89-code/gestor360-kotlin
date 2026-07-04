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
    val isSearching: Boolean = false,
    val resultadosBusqueda: List<Product> = emptyList(),
    val carrito: List<CartItem> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val ventaConfirmada: Double? = null
) {
    val total: Double get() = carrito.sumOf { it.subtotal }
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
        cargarTop5()
    }

    fun buscarProducto(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(resultadosBusqueda = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            productRepository.searchProducts(androidIdActual, query)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isSearching = false, resultadosBusqueda = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isSearching = false, error = e.message) }
        }
    }

    private fun cargarTop5() {
        viewModelScope.launch {
            }
        }
    }

    fun limpiarBusqueda() {
        _uiState.value = _uiState.value.copy(resultadosBusqueda = emptyList())
    }

    fun agregarAlCarrito(producto: Product, cantidad: Double): String? {
        if (producto.stock <= 0) return "Sin stock disponible"

        val carritoActual = _uiState.value.carrito.toMutableList()
        val indiceExistente = carritoActual.indexOfFirst { it.productId == producto.id }

        if (indiceExistente >= 0) {
            val existente = carritoActual[indiceExistente]
            val nuevaCantidad = existente.cantidad + cantidad
            if (nuevaCantidad > producto.stock) return "Stock insuficiente (${producto.stock} disponibles)"
            carritoActual[indiceExistente] = existente.copy(cantidad = nuevaCantidad)
        } else {
            if (cantidad > producto.stock) return "Stock insuficiente (${producto.stock} disponibles)"
            carritoActual.add(
                CartItem(
                    productId = producto.id,
                    nombre = producto.nombre,
                    precio = producto.precio,
                    cantidad = cantidad,
                    stockDisponible = producto.stock
                )
            )
        }

        _uiState.value = _uiState.value.copy(carrito = carritoActual, resultadosBusqueda = emptyList())
        return null
    }

    fun quitarDelCarrito(index: Int) {
        val carritoActual = _uiState.value.carrito.toMutableList()
        if (index in carritoActual.indices) carritoActual.removeAt(index)
        _uiState.value = _uiState.value.copy(carrito = carritoActual)
    }

    fun limpiarCarrito() {
        _uiState.value = _uiState.value.copy(carrito = emptyList())
    }

    fun confirmarVenta(
        metodo: String,
        montoEfectivo: Double,
        montoTransferencia: Double,
        cliente: SaleRepository.DatosCliente? = null
    ) {
        val carrito = _uiState.value.carrito
        if (carrito.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val totalVenta = _uiState.value.total
            saleRepository.guardarVenta(
                androidId = androidIdActual,
                carrito = carrito,
                metodo = metodo,
                montoEfectivo = montoEfectivo,
                montoTransferencia = montoTransferencia,
                cliente = cliente
            ).onSuccess {
                _uiState.value = _uiState.value.copy(carrito = emptyList(), ventaConfirmada = totalVenta)
                cargarTop5()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error al guardar la venta")
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun limpiarVentaConfirmada() {
        _uiState.value = _uiState.value.copy(ventaConfirmada = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
