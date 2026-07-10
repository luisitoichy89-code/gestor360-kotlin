package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.TarjetaEntity

@Dao
interface TarjetaDao {
    @Query("SELECT * FROM tarjetas_cache WHERE localId = :localId ORDER BY banco ASC")
    suspend fun obtenerTodas(localId: Long): List<TarjetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(tarjetas: List<TarjetaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUna(tarjeta: TarjetaEntity)

    @Query("UPDATE tarjetas_cache SET id = :idReal WHERE id = :idTemporal AND localId = :localId")
    suspend fun reemplazarIdTemporal(idTemporal: Long, idReal: Long, localId: Long)

    @Query("UPDATE tarjetas_cache SET activo = :activo WHERE id = :id AND localId = :localId")
    suspend fun setActivo(id: Long, activo: Boolean, localId: Long)

    @Query("DELETE FROM tarjetas_cache WHERE id = :id AND localId = :localId")
    suspend fun eliminar(id: Long, localId: Long)

    /** Limpia todo el caché (se usa al cambiar de local activo). */
    @Query("DELETE FROM tarjetas_cache")
    suspend fun limpiar()
}
