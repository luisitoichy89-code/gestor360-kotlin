package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.ui.util.mensajeAmigable

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
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.mensajeAmigable("No se pudieron cargar los productos")) }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual)
    }

    fun crear(nombre: String, precio: Double, stock: Double, ubicacion: String, categoria: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.createProduct(androidIdActual, nombre, precio, stock, ubicacion, categoria)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo guardar el producto")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun editar(id: String, nombre: String, precio: Double, stock: Double, ubicacion: String, categoria: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.updateProduct(androidIdActual, id, nombre, precio, stock, ubicacion, categoria)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo actualizar el producto")) }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun registrarMerma(producto: Product, cantidad: Double, motivo: String = "Merma") {
        viewModelScope.launch {
            repository.registrarMerma(androidIdActual, producto, cantidad, motivo)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo registrar la merma")) }
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch {
            repository.deleteProduct(androidIdActual, id)
                .onSuccess { refrescar() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.mensajeAmigable("No se pudo eliminar el producto")) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
