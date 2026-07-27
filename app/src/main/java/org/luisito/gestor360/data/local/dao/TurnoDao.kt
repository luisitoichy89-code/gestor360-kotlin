package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.TurnoEntity

@Dao
interface TurnoDao {
    /**
     * BLINDAJE: ORDER BY id DESC como red de seguridad. Si por cualquier
     * motivo quedaran dos filas con cierre IS NULL para el mismo local (no
     * debería pasar desde que existe cerrarYRegistrarNuevo, pero un caché
     * viejo de antes de ese fix puede tenerla), esto garantiza que "el turno
     * activo" sea siempre el más reciente y no una fila vieja al azar.
     */
    @Query("SELECT * FROM turno_cache WHERE cierre IS NULL AND localId = :localId ORDER BY id DESC LIMIT 1")
    suspend fun obtenerActivo(localId: Long): TurnoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(turno: TurnoEntity)

    @Query("UPDATE turno_cache SET cierre = :cierre, diferencia = :diferencia WHERE id = :id AND localId = :localId")
    suspend fun cerrar(id: Long, cierre: Double, diferencia: Double, localId: Long)

    /**
     * BLINDAJE (causa raíz de "el inventario por vendedor no vuelve a cero"):
     * cerrarTurno() en InventarioRepository insertaba el turno nuevo pero
     * nunca marcaba el turno anterior como cerrado en Room. Quedaban dos filas
     * con cierre IS NULL para el mismo local, y obtenerActivo() (sin ORDER BY)
     * podía devolver la vieja. Cualquier venta registrada después del cierre
     * (SaleRepository.guardarVenta usa obtenerActivo() para etiquetar
     * turno_id) terminaba con el turno_id VIEJO, así que las ventas de un
     * vendedor seguían apareciendo bajo el turno que el admin ya había
     * cerrado. @Transaction hace que "cerrar el viejo" + "registrar el nuevo"
     * sea una sola operación atómica: nunca queda más de un turno abierto en
     * caché para el mismo local.
     */
    @Transaction
    suspend fun cerrarYRegistrarNuevo(turnoAnteriorId: Long, cierreAnterior: Double, localId: Long, nuevo: TurnoEntity) {
        cerrar(turnoAnteriorId, cierreAnterior, 0.0, localId)
        insertar(nuevo)
    }

    @Query("UPDATE turno_cache SET id = :idReal WHERE id = :idTemporal AND localId = :localId")
    suspend fun reemplazarIdTemporal(idTemporal: Long, idReal: Long, localId: Long)

    @Query("DELETE FROM turno_cache WHERE cierre IS NOT NULL")
    suspend fun limpiarCerrados()

    /** Limpia todo el caché (se usa al cambiar de local activo). */
    @Query("DELETE FROM turno_cache")
    suspend fun limpiarTodos()
}
