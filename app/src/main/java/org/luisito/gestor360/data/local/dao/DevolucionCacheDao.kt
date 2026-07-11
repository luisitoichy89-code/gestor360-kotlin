package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.DevolucionCacheEntity

@Dao
interface DevolucionCacheDao {
    @Query("SELECT * FROM devoluciones_cache WHERE localId = :localId LIMIT 1")
    suspend fun obtener(localId: Long): DevolucionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(entidad: DevolucionCacheEntity)

    /** Limpia todo el caché (se usa al cambiar de local activo). */
    @Query("DELETE FROM devoluciones_cache")
    suspend fun limpiarTodas()
}
