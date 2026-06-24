package org.luisito.gestor360.utils

import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository

class SyncManager(
    private val productRepo: ProductRepository,
    private val saleRepo: SaleRepository
) {
    suspend fun syncAll() {
        try {
            productRepo.getProducts()
            saleRepo.getSales()
        } catch (e: Exception) {
            // Log error
        }
    }
}
