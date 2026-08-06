package org.luisito.gestor360.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.luisito.gestor360.data.local.entities.MovimientoInventarioEntity

@Dao
interface MovimientoInventarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(movimiento: MovimientoInventarioEntity)

    @Query("SELECT * FROM movimientos_inventario_cache WHERE turnoId = :turnoId AND localId = :localId")
    suspend fun obtenerPorTurno(turnoId: Long, localId: Long): List<MovimientoInventarioEntity>

    @Query("DELETE FROM movimientos_inventario_cache WHERE sincronizada = 1")
    suspend fun limpiarSincronizadas()
}
