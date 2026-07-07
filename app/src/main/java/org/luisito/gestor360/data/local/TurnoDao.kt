package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.TurnoEntity

@Dao
interface TurnoDao {
    @Query("SELECT * FROM turno_cache WHERE cierre IS NULL LIMIT 1")
    suspend fun obtenerActivo(): TurnoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(turno: TurnoEntity)

    @Query("UPDATE turno_cache SET cierre = :cierre, diferencia = :diferencia WHERE id = :id")
    suspend fun cerrar(id: Long, cierre: Double, diferencia: Double)

    @Query("UPDATE turno_cache SET id = :idReal WHERE id = :idTemporal")
    suspend fun reemplazarIdTemporal(idTemporal: Long, idReal: Long)

    @Query("DELETE FROM turno_cache WHERE cierre IS NOT NULL")
    suspend fun limpiarCerrados()
}
