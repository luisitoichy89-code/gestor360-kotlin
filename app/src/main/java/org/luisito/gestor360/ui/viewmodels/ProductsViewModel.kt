package org.luisito.gestor360.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.utils.SyncAction
import org.luisito.gestor360.utils.SyncManager

class ProductsViewModel(
    private val context: Context,
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val syncManager = SyncManager(context)

    fun loadProducts(almacenId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val products = repository.getProducts(almacenId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    products = products,
                    error = if (products.isEmpty()) "No hay productos" else null
                )
            }
        }
    }

    fun createProduct(nombre: String, precio: Double, stock: Double, almacenId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = repository.createProduct(nombre, precio, stock, almacenId)
            if (success) {
                syncManager.syncAfterAction(almacenId, SyncAction.PRODUCT_ADDED)
                loadProducts(almacenId)
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error al crear producto"
                    )
                }
            }
        }
    }

    fun updateProduct(id: Int, nombre: String, precio: Double, stock: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = repository.updateProduct(id, nombre, precio, stock)
            if (success) {
                val almacenId = _uiState.value.products.firstOrNull()?.almacen_id ?: "1"
                syncManager.syncAfterAction(almacenId, SyncAction.PRODUCT_UPDATED)
                loadProducts(almacenId)
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error al actualizar producto"
                    )
                }
            }
        }
    }

    fun deleteProduct(id: Int, almacenId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = repository.deleteProduct(id)
            if (success) {
                syncManager.syncAfterAction(almacenId, SyncAction.PRODUCT_DELETED)
                loadProducts(almacenId)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error al eliminar producto"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ProductsUiState(
    val isLoading: Boolean = false,
    val products: List<Product> = emptyList(),
    val error: String? = null
)
