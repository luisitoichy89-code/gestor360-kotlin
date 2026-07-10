package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.MermaEntity

@Dao
interface MermaDao {
    @Query("SELECT * FROM mermas_cache WHERE estado = 'pendiente' AND localId = :localId ORDER BY id DESC")
    suspend fun obtenerPendientes(localId: Long): List<MermaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(mermas: List<MermaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUna(merma: MermaEntity)

    @Query("UPDATE mermas_cache SET estado = :estado WHERE id = :id AND localId = :localId")
    suspend fun actualizarEstado(id: Long, estado: String, localId: Long)

    @Query("DELETE FROM mermas_cache WHERE estado != 'pendiente'")
    suspend fun limpiarResueltas()

    /** Limpia todo el caché (se usa al cambiar de local activo). */
    @Query("DELETE FROM mermas_cache")
    suspend fun limpiarTodas()
}
