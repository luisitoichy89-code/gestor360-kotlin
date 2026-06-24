package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.domain.repository.IProductRepository
import org.luisito.gestor360.domain.result.Result

class ProductsViewModel(
    private val productRepo: IProductRepository
) : ViewModel() {
    
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage
    
    init {
        loadProducts()
    }
    
    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = productRepo.getProducts()) {
                is Result.Success -> {
                    _products.value = result.data
                }
                is Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }
    
    fun createProduct(product: Product) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = productRepo.createProduct(product)) {
                is Result.Success -> {
                    _successMessage.value = "Producto creado exitosamente"
                    loadProducts()
                }
                is Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }
    
    fun updateProduct(product: Product) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = productRepo.updateProduct(product.id, product)) {
                is Result.Success -> {
                    _successMessage.value = "Producto actualizado"
                    loadProducts()
                }
                is Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }
    
    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            when (val result = productRepo.deleteProduct(productId)) {
                is Result.Success -> {
                    _successMessage.value = "Producto eliminado"
                    loadProducts()
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
