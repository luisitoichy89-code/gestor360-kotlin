package org.luisito.gestor360.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.luisito.gestor360.data.model.Product

@Dao
interface ProductDao {
    @Query("SELECT * FROM productos WHERE almacenId = :almacenId ORDER BY nombre")
    suspend fun getProductsByAlmacen(almacenId: String): List<Product>

    @Query("SELECT * FROM productos WHERE nombre LIKE :query AND almacenId = :almacenId ORDER BY nombre")
    suspend fun searchProducts(query: String, almacenId: String): List<Product>

    @Insert
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE productos SET stock = stock - :cantidad WHERE id = :productId AND stock >= :cantidad")
    suspend fun reduceStock(productId: Long, cantidad: Int): Int

    @Query("SELECT * FROM productos WHERE id = :productId")
    suspend fun getProductById(productId: Long): Product?
}
