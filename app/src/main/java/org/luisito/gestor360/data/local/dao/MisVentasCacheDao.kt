package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.MisVentasCacheEntity

@Dao
interface MisVentasCacheDao {
    @Query("SELECT * FROM mis_ventas_cache WHERE localId = :localId AND usuarioId = :usuarioId ORDER BY createdAt DESC")
    suspend fun obtenerMisVentas(localId: Long, usuarioId: Long): List<MisVentasCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(venta: MisVentasCacheEntity)

    @Query("DELETE FROM mis_ventas_cache WHERE localId = :localId AND usuarioId = :usuarioId AND turnoId != :turnoActivoId")
    suspend fun limpiarTurnosViejos(localId: Long, usuarioId: Long, turnoActivoId: Long)

    @Query("DELETE FROM mis_ventas_cache WHERE localId = :localId AND usuarioId = :usuarioId")
    suspend fun limpiarTodo(localId: Long, usuarioId: Long)

    @Query("UPDATE mis_ventas_cache SET sincronizada = 1 WHERE id = :id")
    suspend fun marcarSincronizada(id: String)
}
