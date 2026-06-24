package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.luisito.gestor360.domain.repository.IProductRepository

class ProductsViewModelFactory(
    private val productRepo: IProductRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProductsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProductsViewModel(productRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

fun ProductsViewModel.factory(productRepo: IProductRepository) = ProductsViewModelFactory(productRepo)
