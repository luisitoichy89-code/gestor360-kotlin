package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.ProductRepository

class ProductsViewModel(
    private val productRepo: ProductRepository
) : ViewModel() {
    
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    init {
        loadProducts()
    }
    
    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _products.value = productRepo.getProducts()
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }
    
    fun createProduct(product: Product) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (productRepo.createProduct(product)) {
                    loadProducts()
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }
    
    fun updateProduct(product: Product) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (productRepo.updateProduct(product.id, product)) {
                    loadProducts()
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }
    
    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (productRepo.deleteProduct(productId)) {
                    loadProducts()
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }
}
