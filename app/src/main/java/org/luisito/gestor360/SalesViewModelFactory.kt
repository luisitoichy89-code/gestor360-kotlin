package org.luisito.gestor360

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.viewmodels.SalesViewModel

class SalesViewModelFactory(
    private val productRepository: ProductRepository,
    private val saleRepository: SaleRepository,
    private val almacenId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SalesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SalesViewModel(productRepository, saleRepository, almacenId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
