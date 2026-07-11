package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.InventarioCacheEntity

@Dao
interface InventarioCacheDao {
    @Query("SELECT * FROM inventario_cache WHERE localId = :localId AND fecha = :fecha LIMIT 1")
    suspend fun obtener(localId: Long, fecha: String): InventarioCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(entidad: InventarioCacheEntity)

    /** Limpia todo el caché (se usa al cambiar de local activo). */
    @Query("DELETE FROM inventario_cache")
    suspend fun limpiarTodas()
}
