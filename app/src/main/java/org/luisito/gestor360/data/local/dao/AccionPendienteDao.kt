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

    @Query("DELETE FROM acciones_pendientes WHERE idLocalTemporal = :idTemporal AND estado = 'pendiente'")
    suspend fun cancelarPorIdTemporal(idTemporal: Long)

    /**
     * NUEVO (módulo Productos): borra una acción pendiente puntual. Se usa,
     * por ejemplo, cuando se elimina un producto que se creó offline y nunca
     * llegó a sincronizar — en ese caso no hay nada que avisarle al servidor,
     * simplemente se cancela la creación completa antes de que se dispare.
     * Genérico y aditivo: no cambia el comportamiento de ningún otro módulo.
     */
    @Delete
    suspend fun eliminar(accion: AccionPendienteEntity)
}
