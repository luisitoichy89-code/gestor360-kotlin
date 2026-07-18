package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.ProductoEliminadoCacheEntity

@Dao
interface ProductoEliminadoCacheDao {
    @Query("SELECT * FROM productos_eliminados_cache WHERE localId = :localId AND fecha = :fecha")
    suspend fun obtenerPorFecha(localId: Long, fecha: String): List<ProductoEliminadoCacheEntity>

    // NUEVO: sin filtrar por fecha — se usa como fallback de nombre para
    // ventas de productos eliminados en cualquier día (no solo el día actual).
    @Query("SELECT * FROM productos_eliminados_cache WHERE localId = :localId")
    suspend fun obtenerTodos(localId: Long): List<ProductoEliminadoCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(entidad: ProductoEliminadoCacheEntity)

    @Query("DELETE FROM productos_eliminados_cache WHERE localId = :localId")
    suspend fun limpiarLocal(localId: Long)
}
