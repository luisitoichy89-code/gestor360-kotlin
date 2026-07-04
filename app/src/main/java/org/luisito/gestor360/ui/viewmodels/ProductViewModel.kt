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
    private var almacenIdActual: String = ""
    private var androidIdActual: String = ""

    fun cargar(androidId: String, almacenId: String) {
        androidIdActual = androidId
        almacenIdActual = almacenId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getProducts(androidId, almacenId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, productos = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun crear(nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            repository.createProduct(nombre, precio, stock, almacenIdActual, androidIdActual)
                .onSuccess { cargar(androidIdActual, almacenIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun editar(id: Long, nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            repository.updateProduct(id, nombre, precio, stock, androidIdActual)
                .onSuccess { cargar(androidIdActual, almacenIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun eliminar(id: Long) {
        viewModelScope.launch {
            repository.deleteProduct(id, androidIdActual)
                .onSuccess { cargar(androidIdActual, almacenIdActual) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
