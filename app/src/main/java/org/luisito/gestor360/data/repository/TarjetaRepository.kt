package com.gestor360.tarjetas.data

import com.gestor360.core.sync.AccionPendienteDao
import com.gestor360.core.sync.AccionPendienteEntity
import com.gestor360.tarjetas.data.local.TarjetaDao
import com.gestor360.tarjetas.data.local.TarjetaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

private const val MODULO = "tarjetas"

class TarjetaRepository(
    private val dao: TarjetaDao,
    private val accionPendienteDao: AccionPendienteDao,
    private val syncScheduler: SyncScheduler
) {

    // ---------- Lecturas ----------

    fun observarActivas(localId: Long): Flow<List<TarjetaEntity>> =
        dao.observarTarjetasActivas(localId)

    fun observarTodas(localId: Long): Flow<List<TarjetaEntity>> =
        dao.observarTarjetasDeLocal(localId)

    // ---------- Escrituras: solo admin (validado también server-side en el RPC) ----------

    suspend fun crear(localId: Long, nombre: String, tipo: String?, numeroCuenta: String?) {
        val id = UUID.randomUUID().toString() // RN #1

        dao.insertar(
            TarjetaEntity(
                id = id, localId = localId, nombre = nombre, tipo = tipo,
                numeroCuenta = numeroCuenta, activo = true, pendienteSync = true
            )
        )

        encolarAccion(
            tipoAccion = "CREAR",
            entidadId = id,
            payload = TarjetaPayload(
                id = id, localId = localId, nombre = nombre, tipo = tipo,
                numeroCuenta = numeroCuenta, activo = true
            )
        )
    }

    suspend fun actualizar(tarjeta: TarjetaEntity, nombre: String, tipo: String?, numeroCuenta: String?) {
        val actualizada = tarjeta.copy(
            nombre = nombre, tipo = tipo, numeroCuenta = numeroCuenta, pendienteSync = true
        )
        dao.actualizar(actualizada)

        encolarAccion(
            tipoAccion = "ACTUALIZAR",
            entidadId = tarjeta.id,
            payload = TarjetaPayload(
                id = tarjeta.id, localId = tarjeta.localId, nombre = nombre, tipo = tipo,
                numeroCuenta = numeroCuenta, activo = tarjeta.activo
            )
        )
    }

    /** Toggle informativo (no es delete). Usa el mismo RPC actualizar_tarjeta con p_activo=false. */
    suspend fun cambiarActivo(tarjeta: TarjetaEntity, activo: Boolean) {
        val actualizada = tarjeta.copy(activo = activo, pendienteSync = true)
        dao.actualizar(actualizada)

        encolarAccion(
            tipoAccion = "ACTUALIZAR",
            entidadId = tarjeta.id,
            payload = TarjetaPayload(
                id = tarjeta.id, localId = tarjeta.localId, nombre = tarjeta.nombre,
                tipo = tarjeta.tipo, numeroCuenta = tarjeta.numeroCuenta, activo = activo
            )
        )
    }

    /** DELETE real, igual que Productos. Ojo con FKs desde Ventas (ver comentario en el SQL). */
    suspend fun eliminar(tarjeta: TarjetaEntity) {
        dao.actualizar(tarjeta.copy(pendienteSync = true))

        encolarAccion(
            tipoAccion = "ELIMINAR",
            entidadId = tarjeta.id,
            payload = TarjetaPayload(
                id = tarjeta.id, localId = tarjeta.localId, nombre = tarjeta.nombre,
                tipo = tarjeta.tipo, numeroCuenta = tarjeta.numeroCuenta, activo = tarjeta.activo
            )
        )
        // No se borra la fila local hasta confirmar el DELETE remoto: si el
        // dispositivo está offline, la fila debe seguir visible/consistente
        // hasta que el worker aplique la eliminación exitosamente.
        // El worker llama a dao.eliminarLocal(id) al recibir éxito (ver marcarAccionCompletada).
    }

    private suspend fun encolarAccion(tipoAccion: String, entidadId: String, payload: TarjetaPayload) {
        accionPendienteDao.encolar(
            AccionPendienteEntity(
                accionId = UUID.randomUUID().toString(),
                modulo = MODULO,
                tipoAccion = tipoAccion,
                entidadId = entidadId,
                payloadJson = Json.encodeToString(TarjetaPayload.serializer(), payload),
                createdAt = System.currentTimeMillis()
            )
        )
        syncScheduler.solicitarSyncInmediato()
    }

    // ---------- Usado por el SyncWorker ----------

    suspend fun obtenerAccionesPendientes(): List<AccionPendienteEntity> =
        accionPendienteDao.obtenerPendientesPorModulo(MODULO)

    suspend fun marcarAccionCompletada(accion: AccionPendienteEntity) {
        accionPendienteDao.eliminar(accion)
        val quedanPendientes = accionPendienteDao.contarPendientesDeEntidad(MODULO, accion.entidadId) > 0
        if (quedanPendientes) return

        if (accion.tipoAccion == "ELIMINAR") {
            dao.eliminarLocal(accion.entidadId)
        } else {
            dao.marcarPendienteSync(accion.entidadId, pendiente = false)
        }
    }

    suspend fun marcarAccionFallida(accion: AccionPendienteEntity, error: String?) {
        accionPendienteDao.registrarIntentoFallido(accion.accionId, error)
    }

    /** Pull = refresco completo por local. */
    suspend fun reemplazarConDatosDeServidor(localId: Long, remotas: List<TarjetaEntity>) {
        dao.limpiarSincronizadasDeLocal(localId)
        dao.upsertDesdeServidor(remotas)
    }
}

@Serializable
data class TarjetaPayload(
    val id: String,
    val localId: Long,
    val nombre: String,
    val tipo: String?,
    val numeroCuenta: String?,
    val activo: Boolean
)

interface SyncScheduler {
    fun solicitarSyncInmediato()
}
