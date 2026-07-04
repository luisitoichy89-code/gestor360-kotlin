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
    val error: String? = null
)

class ProductViewModel(
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    private var androidIdActual: String = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getProducts(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, productos = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Error al cargar productos") }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual)
    }

    fun crear(nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.createProduct(androidIdActual, nombre, precio, stock)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun editar(id: Long, nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.updateProduct(androidIdActual, id, nombre, precio, stock)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun registrarMerma(producto: Product, cantidad: Double) {
        viewModelScope.launch {
            repository.registrarMerma(androidIdActual, producto, cantidad)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun eliminar(id: Long) {
        viewModelScope.launch {
            repository.deleteProduct(androidIdActual, id)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
