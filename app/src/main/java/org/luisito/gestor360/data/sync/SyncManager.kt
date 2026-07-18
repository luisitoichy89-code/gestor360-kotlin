package org.luisito.gestor360.data.sync

import android.content.Context
import androidx.room.withTransaction
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.repository.AprobacionStockRepository
import org.luisito.gestor360.data.repository.DeviceVerificationRepository
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.TarjetaRepository
import org.luisito.gestor360.data.repository.TurnoRepository
import org.luisito.gestor360.data.repository.VerificacionEnCalienteResultado
import org.luisito.gestor360.utils.SessionManager

/**
 * `licenciaBloqueada`: true cuando NO se sincronizó nada porque, antes de
 * tocar la cola, se detectó que el usuario ya no está activo o la licencia
 * ya no es válida (ver SyncManager.sincronizar()). Útil si SyncWorker quiere
 * avisar de esto en una notificación en vez de tratarlo como un fallo de red más.
 */
data class SyncResultado(val exitosas: Int, val fallidas: Int, val error: String? = null, val licenciaBloqueada: Boolean = false)

class SyncManager(private val context: Context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private val deviceVerificationRepository = DeviceVerificationRepository(context)
    private val productRepository = ProductRepository(context)
    private val tarjetaRepository = TarjetaRepository(context)
    private val mermaRepository = MermaRepository(context)
    private val turnoRepository = TurnoRepository(context)
    private val aprobacionStockRepository = AprobacionStockRepository(context)

    companion object {
        /** Tamaño de cada lote — evita mandar todo junto y saturar conexiones lentas o RPCs con timeout. */
        private const val TAMANO_LOTE = 50

        /**
         * Tope total de acciones a procesar en UNA sola invocación de sincronizar(),
         * aunque queden más lotes pendientes. Sin esto, tras semanas/meses offline con
         * miles de acciones en cola, un solo ciclo intentaría vaciarla entera y podría
         * chocar contra el límite de ejecución que Android le da a un CoroutineWorker
         * en background. El resto se procesa en el siguiente ciclo (periódico cada
         * 15 min, o el próximo "sincronizar ahora" — ver SyncWorker).
         */
        private const val MAX_ACCIONES_POR_SESION = 500

        /** Pausa entre lotes para no ráfaguear la conexión ni a Supabase. */
        private const val PAUSA_ENTRE_LOTES_MS = 300L

        /**
         * Intentos fallidos antes de dejar de reintentar SOLA una acción y marcarla
         * error_permanente, para que no ocupe un lugar en la cola para siempre por
         * una sola acción rota (ej. referencia a un producto ya eliminado).
         * OJO: "registrar_venta" y "anular_venta" quedan afuera de este límite a
         * propósito — son dinero, y preferimos que reintenten para siempre a que se
         * abandonen solas. Ver TIPOS_NUNCA_ABANDONAR abajo.
         */
        private const val MAX_INTENTOS = 8

        /** Tipos que NUNCA pasan a error_permanente sin importar cuántas veces fallen. */
        private val TIPOS_NUNCA_ABANDONAR = setOf("registrar_venta", "anular_venta")
    }

    suspend fun sincronizar(androidId: String): SyncResultado {
        if (androidId.isBlank()) return SyncResultado(0, 0, "Sin sesión activa")
        if (!NetworkMonitor.hayInternet(context)) return SyncResultado(0, 0, "Sin conexión")

        // Antes de procesar CUALQUIER acción pendiente: si el usuario fue
        // desactivado o la licencia dejó de ser válida mientras el
        // dispositivo estuvo offline, no sincronizamos nada de lo pendiente
        // (evita colar ventas falsas de un empleado ya echado) y marcamos la
        // sesión como revocada para que la app vuelva a pedir verificación
        // de dispositivo la próxima vez. Si no hay respuesta clara del
        // servidor (sin internet real, error, timeout) esto NO bloquea —
        // ver VerificacionEnCalienteResultado.NoVerificado.
        when (val estado = deviceVerificationRepository.verificarEnCaliente(androidId)) {
            is VerificacionEnCalienteResultado.Bloqueado -> {
                // Igual que en MainActivity: NO se limpia la caché de licencia acá.
                // Bloqueamos ESTE sync y forzamos que la sesión se cierre (si la
                // app está abierta, ver SessionManager.sesionRevocada), pero la
                // caché que permite el acceso offline queda intacta.
                session.marcarSesionRevocada()
                return SyncResultado(0, 0, estado.mensaje, licenciaBloqueada = true)
            }
            else -> {}
        }

        repararAccionesLegacyCreacionProducto()

        var exitosas = 0
        var fallidas = 0
        var procesadasEnSesion = 0
        val eliminadosProductos = mutableListOf<Pair<String, Long>>()
        val eliminadosTarjetas = mutableListOf<Pair<String, Long>>()

        // Loop real por lotes: procesa de a TAMANO_LOTE (50) hasta vaciar la cola,
        // hasta llegar al tope de sesión, o hasta que se corte la conexión a mitad
        // de la puesta al día. Cada lote se trae de la base con LIMIT (no se carga
        // toda la cola pendiente en memoria de una vez).
        while (procesadasEnSesion < MAX_ACCIONES_POR_SESION) {
            if (!NetworkMonitor.hayInternet(context)) break

            val lote = db.accionPendienteDao().obtenerLotePendiente(TAMANO_LOTE)
            if (lote.isEmpty()) break

            for (accion in lote) {
                try {
                    val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject
                    val respuesta = SupabaseClientProvider.client.postgrest.rpc(accion.tipo, payload)

                    if (accion.idLocalTemporal != null) {
                        val localIdDeLaAccion = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                            ?: session.getLocalId()
                        if (localIdDeLaAccion != null) {
                            reemplazarIdTemporal(accion.tipo, accion.idLocalTemporal, respuesta, localIdDeLaAccion)
                        }
                    }

                    when (accion.tipo) {
                        "eliminar_producto" -> {
                            val pid = payload["p_id"]?.toString()?.trim('"')
                            val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                            if (pid != null && lid != null) eliminadosProductos.add(pid to lid)
                        }
                        "registrar_venta" -> {
                            // Confirma la venta local para que limpiarSincronizadas()
                            // (al final del ciclo) sí la pueda purgar de ventas_cache.
                            val ventaId = payload["p_id"]?.toString()?.trim('"')
                            if (ventaId != null) db.ventaDao().marcarSincronizada(ventaId)
                        }
                        "crear_tarjeta", "actualizar_tarjeta", "activar_tarjeta",
                        "crear_merma", "resolver_merma",
                        "crear_devolucion", "resolver_devolucion",
                        "solicitar_producto", "solicitar_aumento_stock",
                        "anular_venta" -> { }
                        "eliminar_tarjeta" -> {
                            val tid = payload["p_id"]?.toString()?.trim('"')
                            val lid = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull()
                            if (tid != null && lid != null) eliminadosTarjetas.add(tid to lid)
                        }
                    }

                    db.accionPendienteDao().actualizar(accion.copy(estado = "sincronizado"))
                    try {
                        val localIdAccion = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull() ?: session.getLocalId()
                        if (localIdAccion != null) {
                            SyncReporter.reportar(androidId, localIdAccion, accion.tipo, payload)
                        }
                    } catch (_: Exception) {}
                    exitosas++
                } catch (e: Exception) {
                    val intentos = accion.intentos + 1
                    val seAbandona = intentos >= MAX_INTENTOS && accion.tipo !in TIPOS_NUNCA_ABANDONAR
                    db.accionPendienteDao().actualizar(
                        accion.copy(
                            intentos = intentos,
                            ultimoError = e.message?.take(300),
                            estado = if (seAbandona) "error_permanente" else "pendiente"
                        )
                    )
                    try {
                        val localIdAccion = session.getLocalId()
                        if (localIdAccion != null) {
                            SyncReporter.reportarError(androidId, localIdAccion, accion.tipo, e.message ?: "Error desconocido")
                        }
                    } catch (_: Exception) {}
                    fallidas++
                }
            }

            procesadasEnSesion += lote.size
            if (lote.size < TAMANO_LOTE) break // ya no quedan más pendientes, no hace falta pausar
            kotlinx.coroutines.delay(PAUSA_ENTRE_LOTES_MS)
        }

        // BLINDAJE: aplicar los eliminados de este ciclo es un lote de borrados
        // sueltos (uno por producto/tarjeta eliminados). Antes, si el proceso se
        // cortaba a mitad de este bucle, algunos quedaban borrados localmente y
        // otros no, un estado intermedio que dependía del próximo refresh para
        // corregirse. db.withTransaction agrupa todo el lote en una sola
        // transacción SQLite: o se borran todos, o no se borra ninguno (y quedan
        // igual de correctos, porque el próximo refresh los va a traer o excluir
        // según corresponda). No cambia qué se borra, solo lo hace atómico.
        db.withTransaction {
            for ((pid, lid) in eliminadosProductos) db.productoDao().eliminar(pid, lid)
            for ((tid, lid) in eliminadosTarjetas) db.tarjetaDao().eliminar(tid, lid)
        }

        val localIdActivo = session.getLocalId()
        if (localIdActivo != null) {
            refrescarProductosYDetectarConflictos(androidId)
            tarjetaRepository.refrescarDesdeServidor(localIdActivo)
            mermaRepository.refrescarDesdeServidor(localIdActivo)
            turnoRepository.refrescarDesdeServidor(androidId)
            aprobacionStockRepository.refrescarDesdeServidor(androidId)
        }

        // BLINDAJE: estas tres limpiezas ("acciones_pendientes" ya sincronizadas,
        // ventas ya sincronizadas, turnos ya cerrados) se hacían como tres
        // llamadas sueltas. Si el proceso se cortaba entre medio, el estado
        // quedaba parcialmente limpio pero nunca inconsistente para lo que ya
        // se sincronizó de verdad (esas filas solo estaban marcadas, no
        // desaparecían de golpe) — igual, agruparlas en una sola transacción
        // evita cualquier corte a mitad de camino entre las tres queries y dejar
        // la cola en un estado ambiguo del que no hay forma de saber qué se
        // alcanzó a limpiar y qué no.
        db.withTransaction {
            db.accionPendienteDao().limpiarSincronizadas()
            db.ventaDao().limpiarSincronizadas()
            db.turnoDao().limpiarCerrados()
        }
        return SyncResultado(exitosas, fallidas, null)
    }

    private suspend fun reemplazarIdTemporal(
        tipo: String, idTemporal: Long,
        respuesta: io.github.jan.supabase.postgrest.result.PostgrestResult, localId: Long
    ) {
        when (tipo) {
            "abrir_turno" -> runCatching { respuesta.decodeAs<Long>() }.getOrNull()
                ?.let { db.turnoDao().reemplazarIdTemporal(idTemporal, it, localId) }
        }
    }

    private suspend fun repararAccionesLegacyCreacionProducto() {
        val legacy = db.accionPendienteDao().obtenerPendientes()
            .filter { it.tipo == "crear_producto" && !it.payloadJson.contains("\"p_id\"") && it.idLocalTemporal != null }

        for (accion in legacy) {
            try {
                val idTemporal = accion.idLocalTemporal ?: continue
                val payload = Json.parseToJsonElement(accion.payloadJson).jsonObject.toMutableMap()
                val localId = payload["p_local_id"]?.toString()?.trim('"')?.toLongOrNull() ?: continue
                val nuevoId = java.util.UUID.randomUUID().toString()
                payload["p_id"] = JsonPrimitive(nuevoId)
                payload["p_accion_id"] = JsonPrimitive(java.util.UUID.randomUUID().toString())

                val filaVieja = db.productoDao().obtenerPorId(idTemporal.toString(), localId)
                if (filaVieja != null) {
                    db.productoDao().eliminar(idTemporal.toString(), localId)
                    db.productoDao().insertarUno(filaVieja.copy(id = nuevoId))
                }
                db.accionPendienteDao().actualizar(
                    accion.copy(payloadJson = JsonObject(payload).toString(), idLocalTemporal = null)
                )
            } catch (_: Exception) { }
        }
    }

    private suspend fun refrescarProductosYDetectarConflictos(androidId: String) {
        productRepository.refrescarDesdeServidor(androidId).onSuccess { productosServidor ->
            productosServidor.filter { it.stock < 0 }.forEach { producto ->
                db.conflictoDao().insertar(
                    ConflictoEntity(
                        tipo = "stock_negativo",
                        descripcion = "\"${producto.nombre}\" quedó con stock ${producto.stock}. " +
                            "Probablemente dos dispositivos vendieron lo mismo estando sin conexión. " +
                            "Revisa y ajusta el stock manualmente.",
                        productoId = producto.id
                    )
                )
            }
        }
    }
}
