package org.luisito.gestor360.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.luisito.gestor360.data.local.entities.TarjetaEntity

@Dao
interface TarjetaDao {

    @Query("SELECT * FROM tarjetas WHERE localId = :localId ORDER BY nombre ASC")
    suspend fun obtenerTodos(localId: Long): List<TarjetaEntity>

    @Query("SELECT * FROM tarjetas WHERE localId = :localId AND activo = 1 ORDER BY nombre ASC")
    suspend fun obtenerActivas(localId: Long): List<TarjetaEntity>

    @Query("SELECT * FROM tarjetas WHERE id = :id AND localId = :localId LIMIT 1")
    suspend fun obtenerPorId(id: String, localId: Long): TarjetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUno(tarjeta: TarjetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(tarjetas: List<TarjetaEntity>)

    /**
     * Pull = refresco completo por local (sin updated_at no hay watermark
     * incremental, igual que el resto del patrón). Reemplaza todo lo que
     * ya está sincronizado; las filas con pendienteSync=true (aún no
     * subidas) no se tocan para no perder cambios locales no confirmados.
     */
    @Query("DELETE FROM tarjetas WHERE localId = :localId AND pendienteSync = 0")
    suspend fun limpiarSincronizadasDeLocal(localId: Long)

    /**
     * BLINDAJE: limpiarSincronizadasDeLocal() + insertarTodos() se llamaban sueltas
     * desde TarjetaRepository.refrescarDesdeServidor(). Si el proceso se corta entre
     * el borrado y la inserción, las tarjetas ya sincronizadas se pierden del caché
     * local. @Transaction agrupa ambas queries en una sola transacción SQLite:
     * o se aplican las dos completas, o ninguna, y el caché anterior se conserva.
     */
    @Transaction
    suspend fun reemplazarSincronizadas(localId: Long, tarjetas: List<TarjetaEntity>) {
        limpiarSincronizadasDeLocal(localId)
        insertarTodos(tarjetas)
    }

    @Query("DELETE FROM tarjetas WHERE id = :id AND localId = :localId")
    suspend fun eliminar(id: String, localId: Long)
}
