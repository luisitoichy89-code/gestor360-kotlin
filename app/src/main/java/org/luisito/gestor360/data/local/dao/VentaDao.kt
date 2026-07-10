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

    @Query("DELETE FROM ventas_cache WHERE sincronizada = 1")
    suspend fun limpiarSincronizadas()

    @Query("DELETE FROM ventas_cache WHERE id = :id")
    suspend fun eliminar(id: String)
}
