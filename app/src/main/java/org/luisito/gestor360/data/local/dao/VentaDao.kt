package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.VentaEntity

@Dao
interface VentaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(ventas: List<VentaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUna(venta: VentaEntity)

    @Query("SELECT * FROM ventas_cache WHERE localId = :localId ORDER BY createdAt DESC")
    suspend fun obtenerTodas(localId: Long): List<VentaEntity>

    @Query(
        """
        SELECT * FROM ventas_cache
        WHERE localId = :localId AND turnoId = :turnoId AND usuarioId = :usuarioId
        ORDER BY createdAt DESC
        """
    )
    suspend fun obtenerPorTurnoYUsuario(localId: Long, turnoId: Long, usuarioId: Long): List<VentaEntity>

    @Query("UPDATE ventas_cache SET sincronizada = 1 WHERE id = :id")
    suspend fun marcarSincronizada(id: String)

    @Query("DELETE FROM ventas_cache WHERE sincronizada = 1 AND localId = :localId")
    suspend fun limpiarSincronizadas(localId: Long)

    @Query("DELETE FROM ventas_cache WHERE localId = :localId")
    suspend fun limpiarDeLocal(localId: Long)

    @Transaction
    suspend fun reemplazarDeLocal(localId: Long, ventas: List<VentaEntity>) {
        limpiarDeLocal(localId)
        insertarTodas(ventas)
    }

    @Query("DELETE FROM ventas_cache WHERE id = :id")
    suspend fun eliminar(id: String)
}
