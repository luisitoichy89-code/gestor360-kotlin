package com.gestor360.tarjetas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TarjetaDao {

    @Query("SELECT * FROM tarjetas WHERE localId = :localId ORDER BY nombre ASC")
    fun observarTarjetasDeLocal(localId: Long): Flow<List<TarjetaEntity>>

    @Query("SELECT * FROM tarjetas WHERE localId = :localId AND activo = 1 ORDER BY nombre ASC")
    fun observarTarjetasActivas(localId: Long): Flow<List<TarjetaEntity>>

    @Query("SELECT * FROM tarjetas WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): TarjetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(tarjeta: TarjetaEntity)

    @Update
    suspend fun actualizar(tarjeta: TarjetaEntity)

    @Query("UPDATE tarjetas SET pendienteSync = :pendiente WHERE id = :id")
    suspend fun marcarPendienteSync(id: String, pendiente: Boolean)

    /**
     * Pull = refresco completo por local (sin updated_at no hay watermark
     * incremental, igual que el resto del patrón). Reemplaza todo lo que
     * ya está SYNCED; las filas con pendienteSync=true (aún no subidas)
     * no se tocan para no perder cambios locales no confirmados.
     */
    @Query("DELETE FROM tarjetas WHERE localId = :localId AND pendienteSync = 0")
    suspend fun limpiarSincronizadasDeLocal(localId: Long)

    @Query("DELETE FROM tarjetas WHERE id = :id")
    suspend fun eliminarLocal(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDesdeServidor(tarjetas: List<TarjetaEntity>)
}
