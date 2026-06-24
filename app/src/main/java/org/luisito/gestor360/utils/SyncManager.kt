package org.luisito.gestor360.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.repository.ProductRepository

class SyncManager(
    private val context: Context,
    private val productRepository: ProductRepository = ProductRepository()
) {

    suspend fun syncAll(almacenId: String): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // Sincronizar productos
                val products = productRepository.getProducts(almacenId)
                // Aquí se guardarían en Room local

                SyncResult.Success
            } catch (e: Exception) {
                SyncResult.Error(e.message ?: "Error de sincronización")
            }
        }
    }

    suspend fun syncAfterAction(almacenId: String, action: SyncAction) {
        withContext(Dispatchers.IO) {
            when (action) {
                SyncAction.PRODUCT_ADDED,
                SyncAction.PRODUCT_UPDATED,
                SyncAction.PRODUCT_DELETED,
                SyncAction.SALE_CREATED,
                SyncAction.MERMA_CREATED -> {
                    syncAll(almacenId)
                }
                else -> { /* No hacer nada */ }
            }
        }
    }
}

enum class SyncAction {
    PRODUCT_ADDED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,
    SALE_CREATED,
    MERMA_CREATED,
    MANUAL
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}
