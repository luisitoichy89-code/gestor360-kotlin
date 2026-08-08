package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.InventarioCacheEntity

@Dao
interface InventarioCacheDao {
    @Query("SELECT * FROM inventario_cache WHERE localId = :localId AND turnoId = :turnoId LIMIT 1")
    suspend fun obtenerPorTurno(localId: Long, turnoId: Long): InventarioCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(entidad: InventarioCacheEntity)

    @Query("DELETE FROM inventario_cache")
    suspend fun limpiarTodas()
}
