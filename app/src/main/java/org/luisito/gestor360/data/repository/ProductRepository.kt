package org.luisito.gestor360.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.model.Product
import org.luisito.gestor360.data.room.AppDatabase
import org.luisito.gestor360.data.room.ProductDao

class ProductRepository(private val context: Context) {
    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val productDao: ProductDao = db.productDao()

    suspend fun getProductsByAlmacen(almacenId: String): List<Product> = withContext(Dispatchers.IO) {
        productDao.getProductsByAlmacen(almacenId)
    }

    suspend fun searchProducts(query: String, almacenId: String): List<Product> = withContext(Dispatchers.IO) {
        productDao.searchProducts("%$query%", almacenId)
    }

    suspend fun reduceStock(productId: Long, cantidad: Int): Boolean = withContext(Dispatchers.IO) {
        val affected = productDao.reduceStock(productId, cantidad)
        affected > 0
    }
}
