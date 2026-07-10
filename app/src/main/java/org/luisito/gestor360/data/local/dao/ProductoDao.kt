package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.ProductoEntity

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos_cache WHERE localId = :localId ORDER BY nombre ASC")
    suspend fun obtenerTodos(localId: Long): List<ProductoEntity>

    @Query("SELECT * FROM productos_cache WHERE id = :id AND localId = :localId")
    suspend fun obtenerPorId(id: Long, localId: Long): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(productos: List<ProductoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUno(producto: ProductoEntity)

    /** Limpia el caché completo (se usa al cambiar de local activo, para no mezclar locales). */
    @Query("DELETE FROM productos_cache")
    suspend fun limpiar()

    @Query("DELETE FROM productos_cache WHERE localId = :localId")
    suspend fun limpiarDeLocal(localId: Long)

    @Query("DELETE FROM productos_cache WHERE id = :id AND localId = :localId")
    suspend fun eliminar(id: Long, localId: Long)

    @Query("UPDATE productos_cache SET stock = stock - :cantidad WHERE id = :id AND localId = :localId")
    suspend fun descontarStock(id: Long, cantidad: Double, localId: Long)

    @Query("UPDATE productos_cache SET id = :idReal WHERE id = :idTemporal AND localId = :localId")
    suspend fun reemplazarIdTemporal(idTemporal: Long, idReal: Long, localId: Long)
}
