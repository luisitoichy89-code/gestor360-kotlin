package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.luisito.gestor360.domain.repository.IProductRepository
import org.luisito.gestor360.domain.repository.ISaleRepository

class SalesViewModelFactory(
    private val productRepo: IProductRepository,
    private val saleRepo: ISaleRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SalesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SalesViewModel(productRepo, saleRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

fun SalesViewModel.factory(productRepo: IProductRepository, saleRepo: ISaleRepository) = 
    SalesViewModelFactory(productRepo, saleRepo)
