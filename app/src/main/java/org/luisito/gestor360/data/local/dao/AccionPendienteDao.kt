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

    /**
     * NUEVO: igual que obtenerPendientes() pero acotado a un lote. Pensado para
     * que SyncManager sincronice en tandas (ej. de 50) en vez de traer y mandar
     * todo de un tirón cuando la cola se acumuló por semanas/meses offline —
     * eso evita timeouts del RPC y que un fallo a mitad de una sincronización
     * gigante deje todo en un estado ambiguo.
     * Uso sugerido en SyncManager:
     *   var lote = dao.obtenerLotePendiente(50)
     *   while (lote.isNotEmpty()) {
     *       // procesar lote, marcar cada acción como sincronizada o fallida
     *       lote = dao.obtenerLotePendiente(50)
     *   }
     */
    @Query("SELECT * FROM acciones_pendientes WHERE estado = 'pendiente' ORDER BY creadoEn ASC LIMIT :tamanoLote")
    suspend fun obtenerLotePendiente(tamanoLote: Int = 50): List<AccionPendienteEntity>

    /**
     * NUEVO: acciones que dejaron de reintentarse solas tras agotar los
     * intentos (ver MAX_INTENTOS en SyncManager). No incluye "registrar_venta"
     * ni "anular_venta" — esas nunca se abandonan automáticamente. Pensada
     * para una futura pantalla de "conflictos de sincronización" donde el
     * admin las revise y decida si reintentar a mano o descartar.
     */
    @Query("SELECT * FROM acciones_pendientes WHERE estado = 'error_permanente' ORDER BY creadoEn ASC")
    suspend fun obtenerConErrorPermanente(): List<AccionPendienteEntity>

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
