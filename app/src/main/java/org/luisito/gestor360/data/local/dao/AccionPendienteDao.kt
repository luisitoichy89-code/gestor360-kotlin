package org.luisito.gestor360.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity

@Dao
interface AccionPendienteDao {
    @Insert
    suspend fun encolar(accion: AccionPendienteEntity): Long

    @Query("SELECT * FROM acciones_pendientes WHERE estado = 'pendiente' ORDER BY creadoEn ASC")
    suspend fun obtenerPendientes(): List<AccionPendienteEntity>

    @Query("SELECT COUNT(*) FROM acciones_pendientes WHERE estado = 'pendiente'")
    fun observarCantidadPendiente(): Flow<Int>

    @Update
    suspend fun actualizar(accion: AccionPendienteEntity)

    @Query("DELETE FROM acciones_pendientes WHERE estado = 'sincronizado'")
    suspend fun limpiarSincronizadas()
}
