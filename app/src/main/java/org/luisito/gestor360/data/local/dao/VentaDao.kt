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

    /**
     * NUEVO: marca UNA venta puntual como confirmada por el servidor. Se usa
     * desde SyncManager justo después de que el RPC "registrar_venta" tiene
     * éxito. Antes nada llamaba esto, así que limpiarSincronizadas() de abajo
     * nunca encontraba nada que borrar y ventas_cache crecía sin límite para
     * siempre, aunque la venta ya estuviera segura en Supabase.
     */
    @Query("UPDATE ventas_cache SET sincronizada = 1 WHERE id = :id")
    suspend fun marcarSincronizada(id: String)

    @Query("DELETE FROM ventas_cache WHERE sincronizada = 1")
    suspend fun limpiarSincronizadas()

    @Query("DELETE FROM ventas_cache WHERE localId = :localId")
    suspend fun limpiarDeLocal(localId: Long)

    @Query("DELETE FROM ventas_cache WHERE id = :id")
    suspend fun eliminar(id: String)
}
