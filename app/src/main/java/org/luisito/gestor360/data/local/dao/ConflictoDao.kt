package org.luisito.gestor360.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.luisito.gestor360.data.local.entities.ConflictoEntity

@Dao
interface ConflictoDao {
    @Insert
    suspend fun insertar(conflicto: ConflictoEntity)

    @Query("SELECT * FROM conflictos WHERE resuelto = 0 ORDER BY detectadoEn DESC")
    fun observarPendientes(): Flow<List<ConflictoEntity>>

    @Query("UPDATE conflictos SET resuelto = 1 WHERE id = :id")
    suspend fun marcarResuelto(id: Long)
}
