package org.luisito.gestor360.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.luisito.gestor360.data.local.entities.MermaEntity

/**
 * No estaba incluido en los archivos a corregir (AppDatabase.kt y
 * MermaRepository.kt ya lo referencian), así que se escribe de cero acá,
 * calcado a TarjetaDao: métodos suspend + List, nada de Flow, mismo estilo
 * de nombres que ProductoDao/TarjetaDao.
 */
@Dao
interface MermaDao {

    @Query("SELECT * FROM mermas_cache WHERE localId = :localId ORDER BY id DESC")
    suspend fun obtenerTodas(localId: Long): List<MermaEntity>

    @Query("SELECT * FROM mermas_cache WHERE localId = :localId AND estado = 'pendiente' ORDER BY id DESC")
    suspend fun obtenerPendientes(localId: Long): List<MermaEntity>

    @Query("SELECT * FROM mermas_cache WHERE id = :id AND localId = :localId LIMIT 1")
    suspend fun obtenerPorId(id: String, localId: Long): MermaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUna(merma: MermaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(mermas: List<MermaEntity>)

    /**
     * Pull = refresco completo por local (sin updated_at no hay watermark
     * incremental, igual que Tarjetas/Productos). Reemplaza todo lo que ya
     * está sincronizado; las filas con pendienteSync=true no se tocan.
     */
    @Query("DELETE FROM mermas_cache WHERE localId = :localId AND pendienteSync = 0")
    suspend fun limpiarSincronizadasDeLocal(localId: Long)

    /**
     * BLINDAJE: limpiarSincronizadasDeLocal() + insertarTodas() se llamaban sueltas
     * desde MermaRepository.refrescarDesdeServidor(). Un corte a mitad de esas dos
     * llamadas perdía mermas ya sincronizadas del caché local. @Transaction agrupa
     * ambas queries en una sola transacción SQLite: todo o nada, el caché anterior
     * queda intacto si algo falla a mitad de camino.
     */
    @Transaction
    suspend fun reemplazarSincronizadas(localId: Long, mermas: List<MermaEntity>) {
        limpiarSincronizadasDeLocal(localId)
        insertarTodas(mermas)
    }

    @Query("DELETE FROM mermas_cache WHERE id = :id AND localId = :localId")
    suspend fun eliminar(id: String, localId: Long)
}
