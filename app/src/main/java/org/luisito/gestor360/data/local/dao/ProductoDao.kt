package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.ProductoEntity

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos_cache ORDER BY nombre ASC")
    suspend fun obtenerTodos(): List<ProductoEntity>

    @Query("SELECT * FROM productos_cache WHERE id = :id")
    suspend fun obtenerPorId(id: Long): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(productos: List<ProductoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUno(producto: ProductoEntity)

    @Query("DELETE FROM productos_cache")
    suspend fun limpiar()

    @Query("DELETE FROM productos_cache WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Query("UPDATE productos_cache SET stock = stock - :cantidad WHERE id = :id")
    suspend fun descontarStock(id: Long, cantidad: Double)

    @Query("UPDATE productos_cache SET id = :idReal WHERE id = :idTemporal")
    suspend fun reemplazarIdTemporal(idTemporal: Long, idReal: Long)
}
