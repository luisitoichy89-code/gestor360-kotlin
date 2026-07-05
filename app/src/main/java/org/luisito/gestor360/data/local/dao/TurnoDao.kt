package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.models.Turno

@Dao
interface TurnoDao {
    @Query("SELECT * FROM turnos WHERE abierto = 1 LIMIT 1")
    suspend fun obtenerActivo(): Turno?

    @Query("SELECT * FROM turnos ORDER BY created_at DESC")
    suspend fun obtenerTodos(): List<Turno>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(turno: Turno)

    @Query("UPDATE turnos SET abierto = 0, cierre = :cierre WHERE id = :id")
    suspend fun marcarCerrado(id: Long, cierre: Double)
}
