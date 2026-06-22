package org.luisito.gestor360.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.luisito.gestor360.data.model.Sale

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: Sale)

    @Query("SELECT * FROM ventas WHERE almacenId = :almacenId AND createdAt LIKE :fecha ORDER BY createdAt DESC")
    suspend fun getSalesByDate(almacenId: String, fecha: String): List<Sale>

    @Query("SELECT SUM(total) FROM ventas WHERE almacenId = :almacenId AND createdAt LIKE :fecha")
    suspend fun getTotalByDate(almacenId: String, fecha: String): Double?

    @Query("SELECT * FROM ventas WHERE almacenId = :almacenId ORDER BY createdAt DESC LIMIT 50")
    suspend fun getLastSales(almacenId: String): List<Sale>
}
