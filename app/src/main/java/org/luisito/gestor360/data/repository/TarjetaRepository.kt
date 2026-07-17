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

    fun observarActivas(localId: String): Flow<List<TarjetaEntity>> =
        dao.observarTarjetasActivas(localId)

    fun observarTodas(localId: String): Flow<List<TarjetaEntity>> =
        dao.observarTarjetasDeLocal(localId)

    // ---------- Escrituras: solo admin (verificar rol en el ViewModel) ----------

    suspend fun crear(
        localId: String,
        nombre: String,
        tipo: String?,
        numeroCuenta: String?,
        creadoPor: String
    ) {
        val ahora = System.currentTimeMillis()
        val id = UUID.randomUUID().toString() // RN #1

        val tarjeta = TarjetaEntity(
            id = id,
            localId = localId,
            nombre = nombre,
            tipo = tipo,
            numeroCuenta = numeroCuenta,
            activo = true,
            creadoPor = creadoPor,
            createdAt = ahora,
            updatedAt = ahora,
            version = 1,
            pendienteSync = true
        )
        dao.insertar(tarjeta)

        encolarAccion(
            tipoAccion = "CREAR",
            entidadId = id,
            payload = TarjetaPayload(
                id = id, localId = localId, nombre = nombre, tipo = tipo,
                numeroCuenta = numeroCuenta, activo = true, creadoPor = creadoPor,
                updatedAt = ahora, deletedAt = null, version = 1
            )
        )
    }

    suspend fun actualizar(
        tarjeta: TarjetaEntity,
        nombre: String,
        tipo: String?,
        numeroCuenta: String?
    ) {
        val ahora = System.currentTimeMillis()
        val actualizada = tarjeta.copy(
            nombre = nombre,
            tipo = tipo,
            numeroCuenta = numeroCuenta,
            updatedAt = ahora,
            version = tarjeta.version + 1,
            pendienteSync = true
        )
        dao.actualizar(actualizada)

        encolarAccion(
            tipoAccion = "ACTUALIZAR",
            entidadId = tarjeta.id,
            payload = TarjetaPayload(
                id = tarjeta.id, localId = tarjeta.localId, nombre = nombre, tipo = tipo,
                numeroCuenta = numeroCuenta, activo = tarjeta.activo, creadoPor = tarjeta.creadoPor,
                updatedAt = ahora, deletedAt = null, version = actualizada.version
            )
        )
    }

    /** Soft delete: nunca se borra físicamente si puede estar referenciada en Ventas. */
    suspend fun desactivar(tarjeta: TarjetaEntity) {
        val ahora = System.currentTimeMillis()
        val desactivada = tarjeta.copy(
            activo = false,
            deletedAt = ahora,
            updatedAt = ahora,
            version = tarjeta.version + 1,
            pendienteSync = true
        )
        dao.actualizar(desactivada)

        encolarAccion(
            tipoAccion = "ELIMINAR",
            entidadId = tarjeta.id,
            payload = TarjetaPayload(
                id = tarjeta.id, localId = tarjeta.localId, nombre = tarjeta.nombre,
                tipo = tarjeta.tipo, numeroCuenta = tarjeta.numeroCuenta, activo = false,
                creadoPor = tarjeta.creadoPor, updatedAt = ahora, deletedAt = ahora,
                version = desactivada.version
            )
        )
    }

    private suspend fun encolarAccion(tipoAccion: String, entidadId: String, payload: TarjetaPayload) {
        accionPendienteDao.encolar(
            AccionPendienteEntity(
                accionId = UUID.randomUUID().toString(), // RN #2: idempotencia
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
        if (!quedanPendientes) {
            dao.marcarPendienteSync(accion.entidadId, pendiente = false)
        }
    }

    suspend fun marcarAccionFallida(accion: AccionPendienteEntity, error: String?) {
        accionPendienteDao.registrarIntentoFallido(accion.accionId, error)
    }

    suspend fun aplicarCambiosRemotos(remotas: List<TarjetaEntity>) =
        dao.upsertDesdeServidor(remotas)

    suspend fun obtenerUltimoUpdatedAt(localId: String): Long? =
        dao.obtenerUltimoUpdatedAt(localId)
}

@Serializable
data class TarjetaPayload(
    val id: String,
    val localId: String,
    val nombre: String,
    val tipo: String?,
    val numeroCuenta: String?,
    val activo: Boolean,
    val creadoPor: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    val version: Int
)

interface SyncScheduler {
    fun solicitarSyncInmediato()
}
