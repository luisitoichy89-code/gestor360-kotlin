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

    /**
     * BLINDAJE: limpiarDeLocal() + insertarTodas() se llamaban como dos operaciones
     * sueltas desde SaleRepository.refrescarDesdeServidor(). Un corte de luz o de
     * conexión justo entre el borrado y la reinserción dejaba ventas_cache vacía
     * para ese local, perdiendo localmente ventas que ya estaban sincronizadas
     * (hasta el próximo refresh exitoso). @Transaction agrupa ambas queries en una
     * sola transacción SQLite: o se aplican las dos, o no se aplica ninguna y el
     * caché anterior queda intacto. Mismo borrado, misma inserción, solo atómicos.
     */
    @Transaction
    suspend fun reemplazarDeLocal(localId: Long, ventas: List<VentaEntity>) {
        limpiarDeLocal(localId)
        insertarTodas(ventas)
    }

    @Query("DELETE FROM ventas_cache WHERE id = :id")
    suspend fun eliminar(id: String)
}
