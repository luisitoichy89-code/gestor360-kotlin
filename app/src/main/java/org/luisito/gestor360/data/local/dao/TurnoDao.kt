package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.TurnoEntity

@Dao
abstract class TurnoDao {
    @Query("SELECT * FROM turno_cache WHERE cierre IS NULL AND localId = :localId ORDER BY id DESC LIMIT 1")
    abstract suspend fun obtenerActivo(localId: Long): TurnoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertar(turno: TurnoEntity)

    @Query("UPDATE turno_cache SET cierre = :cierre, diferencia = :diferencia WHERE id = :id AND localId = :localId")
    abstract suspend fun cerrar(id: Long, cierre: Double, diferencia: Double, localId: Long)

    @Transaction
    open suspend fun cerrarYRegistrarNuevo(turnoAnteriorId: Long, cierreAnterior: Double, localId: Long, nuevo: TurnoEntity) {
        cerrar(turnoAnteriorId, cierreAnterior, 0.0, localId)
        insertar(nuevo)
    }

    @Query("UPDATE turno_cache SET id = :idReal WHERE id = :idTemporal AND localId = :localId")
    abstract suspend fun reemplazarIdTemporal(idTemporal: Long, idReal: Long, localId: Long)

    @Query("DELETE FROM turno_cache WHERE cierre IS NOT NULL")
    abstract suspend fun limpiarCerrados()

    @Query("DELETE FROM turno_cache")
    abstract suspend fun limpiarTodos()
}
