package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.ProductRepository

data class ProductUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val productos: List<Product> = emptyList(),
    val error: String? = null,
    val mensaje: String? = null
)

class ProductViewModel(
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    private var almacenIdActual: String = ""

    fun cargar(almacenId: String) {
        almacenIdActual = almacenId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getProducts(almacenId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, productos = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Error al cargar productos") }
        }
    }

    fun refrescar() {
        if (almacenIdActual.isNotBlank()) cargar(almacenIdActual)
    }

    fun crear(nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.createProduct(nombre, precio, stock, almacenIdActual)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun editar(id: Long, nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.updateProduct(id, nombre, precio, stock)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun registrarMerma(producto: Product, cantidad: Double) {
        viewModelScope.launch {
            repository.registrarMerma(producto.id, producto.stock, cantidad)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun eliminar(id: Long) {
        viewModelScope.launch {
            repository.deleteProduct(id)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
