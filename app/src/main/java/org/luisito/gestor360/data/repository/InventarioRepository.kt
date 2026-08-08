package org.luisito.gestor360.data.repository

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.VentaEntity
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.*
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

class InventarioRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private val turnoRepository = TurnoRepository(context)
    private var refreshJob: Job? = null

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getInventarioDia(
        androidId: String,
        forzarRefresh: Boolean = false,
        turnoIds: List<Long>? = null,
        vendedorId: Long? = null,
        onActualizadoDesdeServidor: (suspend (InventarioDia) -> Unit)? = null
    ): Result<InventarioDia> {
        val localId = localIdActivo()

        if (!turnoIds.isNullOrEmpty()) {
            return refrescarDesdeServidor(androidId, turnoIds)
        }

        val turnoActivoId = db.turnoDao().obtenerActivo(localId)?.id
        val cacheado = if (turnoActivoId != null) {
            db.inventarioCacheDao().obtenerPorTurno(localId, turnoActivoId)
        } else null

        if (cacheado != null && !forzarRefresh) {
            if (NetworkMonitor.hayInternet(context)) {
                refreshJob?.cancel()
                refreshJob = CoroutineScope(Dispatchers.IO).launch {
                    refrescarDesdeServidor(androidId)
                        .onSuccess { servidor -> onActualizadoDesdeServidor?.invoke(servidor) }
                }
            }
            return Result.success(cacheado.toModel())
        }

        if (NetworkMonitor.hayInternet(context)) {
            return refrescarDesdeServidor(androidId)
                .onSuccess { onActualizadoDesdeServidor?.invoke(it) }
        }

        val desdeOffline = cacheado?.let { fusionarConVentasPendientes(it.toModel(), localId) }
            ?: construirDesdeRoom(localId)
        return Result.success(desdeOffline)
    }

    suspend fun refrescar(androidId: String): Result<InventarioDia> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return refrescarDesdeServidor(androidId)
    }

    suspend fun getTurnosDelDia(androidId: String): Result<List<TurnoInfo>> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para ver los turnos de otros días"))
        }
        val localId = localIdActivo()
        return try {
            val turnos = SupabaseClientProvider.client.postgrest
                .rpc("get_turnos", buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_local_id", localId)
                })
                .decodeList<TurnoInfo>()
            Result.success(turnos)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error obteniendo turnos", e)
            Result.failure(e)
        }
    }

    private suspend fun construirDesdeRoom(localId: Long): InventarioDia {
        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }

        val eliminados = db.productoEliminadoCacheDao().obtenerTodos(localId)
            .map { e ->
                ProductoEliminadoInfo(
                    id = e.id, nombre = e.nombre, stock = e.stock,
                    fecha = e.fecha, resuelto_por_nombre = null
                )
            }
        val eliminadosPorId = eliminados.associate { it.id to it }

        val turnoActivo = db.turnoDao().obtenerActivo(localId)
        val turnoActivoId = turnoActivo?.id

        val ventasHoy = if (turnoActivoId != null) {
            db.ventaDao().obtenerTodas(localId)
                .filter { it.turnoId == turnoActivoId }
                .filter { venta ->
                    when (session.getRol()) {
                        "seller" -> venta.usuarioId == session.getUserId()
                        else -> true
                    }
                }
        } else {
            emptyList()
        }

        val ventasInfo = ventasHoy.map { it.toVentaInfo(localId, nombreUsuarioLocal, eliminadosPorId) }
        val productosVendidos = fusionarProductosVendidos(emptyList(), ventasHoy, localId, eliminadosPorId)

        val totales = TotalesVentas(
            efectivo = ventasHoy.sumOf { it.efectivo },
            transferencia = ventasHoy.sumOf { it.transferencia },
            cantidad_ventas = ventasHoy.size.toLong()
        )

        val nuevos = if (turnoActivoId != null) {
            db.productoDao().obtenerTodos(localId)
                .filter { it.turnoId == turnoActivoId }
                .map { p ->
                    ProductoInfo(
                        id = p.id, nombre = p.nombre, precio = p.precio, stock = p.stock,
                        ubicacion = p.ubicacion, fecha = null,
                        solicitado_por_nombre = null, resuelto_por_nombre = null
                    )
                }
        } else emptyList()

        val modificados = emptyList<ProductoInfo>()

        val devolucionesCache = db.devolucionCacheDao().obtener(localId)
        val devueltos = if (devolucionesCache != null) {
            devolucionesCache.toModel()
                .filter { it.turno_id == turnoActivoId || it.turno_id_resuelto == turnoActivoId }
                .map { d ->
                    DevueltoInfo(
                        id = d.id ?: "", producto_nombre = d.producto_nombre, cantidad = d.cantidad,
                        metodo = d.metodo, estado = d.estado,
                        solicitado_por_nombre = d.solicitado_por_nombre,
                        resuelto_por_nombre = d.resuelto_por_nombre, resuelto_por_rol = null,
                        fecha = d.resuelto_at ?: d.created_at
                    )
                }
        } else emptyList()

        val mermasLocales = db.mermaDao().obtenerPendientes(localId).map { m ->
            MermaInfo(
                id = m.id, producto_nombre = m.productoNombre, cantidad = m.cantidad,
                motivo = m.motivo ?: "", estado = m.estado,
                solicitado_por_nombre = m.solicitadoPorNombre, resuelto_por_nombre = null, fecha = null
            )
        }

        return InventarioDia(
            turno = turnoActivo?.let { t ->
                TurnoInfo(
                    id = t.id, apertura = t.apertura, cierre = t.cierre,
                    diferencia = t.diferencia, created_at = t.createdAt,
                    usuario_nombre = null, usuario_rol = null
                )
            },
            ventas = ventasInfo,
            productos_vendidos = productosVendidos,
            productos_nuevos = nuevos,
            productos_modificados = modificados,
            productos_eliminados = eliminados,
            mermas = mermasLocales,
            devueltos = devueltos,
            totales_ventas = totales
        )
    }

    private suspend fun VentaEntity.toVentaInfo(
        localId: Long,
        nombreUsuarioLocal: String?,
        eliminadosPorId: Map<String, ProductoEliminadoInfo>
    ): VentaInfo {
        val productoNombreLocal = productoNombre
            ?: db.productoDao().obtenerPorId(productoId.toString(), localId)?.nombre
            ?: eliminadosPorId[productoId]?.nombre
            ?: "Producto #$productoId"
        var tarjetaBanco: String? = null
        var tarjetaNumero: String? = null
        var tarjetaTitular: String? = null
        if (tarjetaId != null) {
            val tarjeta = db.tarjetaDao().obtenerPorId(tarjetaId, localId)
            tarjetaBanco = tarjeta?.nombre
            tarjetaNumero = tarjeta?.numeroCuenta
            tarjetaTitular = null
        }
        return VentaInfo(
            id = id, producto_nombre = productoNombreLocal,
            cantidad = cantidad, total = total, metodo = metodo,
            efectivo = efectivo, transferencia = transferencia,
            anulada = false, usuario_nombre = nombreUsuarioLocal, usuario_rol = null,
            fecha = createdAt, cliente_ci = clienteCi, cliente_tel = clienteTel,
            cliente_nombre = clienteNombre,
            tarjeta_banco = tarjetaBanco, tarjeta_numero = tarjetaNumero, tarjeta_titular = tarjetaTitular
        )
    }

    private suspend fun fusionarProductosVendidos(
        existentes: List<ProductoVendidoInfo>,
        pendientes: List<VentaEntity>,
        localId: Long,
        eliminadosPorId: Map<String, ProductoEliminadoInfo> = emptyMap()
    ): List<ProductoVendidoInfo> {
        val porNombre = existentes.associateBy { it.nombre }.toMutableMap()
        val todosProductos = db.productoDao().obtenerTodos(localId).associateBy { it.nombre }

        for (venta in pendientes) {
            val nombre = venta.productoNombre
                ?: db.productoDao().obtenerPorId(venta.productoId.toString(), localId)?.nombre
                ?: eliminadosPorId[venta.productoId]?.nombre
                ?: "Producto #${venta.productoId}"

            val actual = porNombre[nombre] ?: ProductoVendidoInfo(nombre = nombre)
            val producto = todosProductos[nombre]

            porNombre[nombre] = actual.copy(
                total_vendido = actual.total_vendido + venta.cantidad,
                total_actual = producto?.stock ?: actual.total_actual,
                total_inicial = (producto?.stock ?: 0.0) + (actual.total_vendido + venta.cantidad)
            )
        }

        return porNombre.values.toList()
    }

    private suspend fun fusionarConVentasPendientes(
        cacheado: InventarioDia,
        localId: Long
    ): InventarioDia {
        val turnoActivo = db.turnoDao().obtenerActivo(localId)
        val turnoActivoId = turnoActivo?.id

        val idsEnCache = cacheado.ventas.map { it.id }.toSet()
        val pendientes = if (turnoActivoId != null) {
            db.ventaDao().obtenerTodas(localId)
                .filter { it.turnoId == turnoActivoId }
                .filter { venta ->
                    when (session.getRol()) {
                        "seller" -> venta.usuarioId == session.getUserId()
                        else -> true
                    }
                }
                .filter { it.id !in idsEnCache }
        } else {
            emptyList()
        }

        if (pendientes.isEmpty()) return cacheado

        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }
        val eliminadosPorId = db.productoEliminadoCacheDao().obtenerTodos(localId)
            .associate { e ->
                e.id to ProductoEliminadoInfo(
                    id = e.id, nombre = e.nombre, stock = e.stock,
                    fecha = e.fecha, resuelto_por_nombre = null
                )
            }

        val pendientesInfo = pendientes.map { it.toVentaInfo(localId, nombreUsuarioLocal, eliminadosPorId) }

        return cacheado.copy(
            ventas = (pendientesInfo + cacheado.ventas).sortedByDescending { it.fecha },
            productos_vendidos = fusionarProductosVendidos(
                cacheado.productos_vendidos, pendientes, localId, eliminadosPorId
            ),
            totales_ventas = cacheado.totales_ventas.copy(
                efectivo = cacheado.totales_ventas.efectivo + pendientes.sumOf { it.efectivo },
                transferencia = cacheado.totales_ventas.transferencia + pendientes.sumOf { it.transferencia },
                cantidad_ventas = cacheado.totales_ventas.cantidad_ventas + pendientes.size
            )
        )
    }

    suspend fun refrescarDesdeServidor(
        androidId: String,
        turnoIds: List<Long>? = null
    ): Result<InventarioDia> {
        val localId = localIdActivo()
        return try {
            val esSinSeleccion = turnoIds.isNullOrEmpty()
            var turnoActivoId = if (esSinSeleccion) {
                db.turnoDao().obtenerActivo(localId)?.id
            } else null

            if (turnoActivoId == null && esSinSeleccion) {
                turnoActivoId = turnoRepository.obtenerTurnoActivo(androidId).getOrNull()?.id
            }

            if (turnoActivoId == null) {
                val diaVacio = construirDesdeRoom(localId)
                db.inventarioCacheDao().guardar(diaVacio.toEntity(localId, turnoActivoId ?: 0))
                return Result.success(diaVacio)
            }

            val inventarioTurno = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_turno", buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_local_id", localId)
                    put("p_turno_id", turnoActivoId)
                })
                .decodeList<InventarioTurno>()
                .firstOrNull()
                ?: return Result.failure(IllegalStateException("RPC get_inventario_turno devolvió vacío"))

            val resultado = inventarioTurno.toInventarioDiaCompat()

            if (turnoIds.isNullOrEmpty()) {
                db.inventarioCacheDao().guardar(resultado.toEntity(localId, turnoActivoId))
            }

            resultado.turno?.let { t ->
                db.turnoDao().limpiarCerrados(localId)
                db.turnoDao().insertar(
                    TurnoEntity(
                        id = t.id, usuarioId = null, apertura = t.apertura,
                        cierre = t.cierre, diferencia = t.diferencia,
                        createdAt = t.created_at, localId = localId
                    )
                )
            }

            Result.success(resultado)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error refrescando servidor", e)
            Result.failure(e)
        }
    }

    private fun InventarioTurno.toInventarioDiaCompat(): InventarioDia {
        return InventarioDia(
            turno = this.turno?.let { t ->
                t.copy(diferencia = t.diferenciaCalculada)
            },
            ventas = this.ventas,
            productos_vendidos = this.productos_vendidos,
            productos_nuevos = this.productos_nuevos,
            productos_modificados = this.productos_modificados,
            productos_eliminados = this.productos_eliminados,
            mermas = this.mermas,
            devueltos = this.devueltos,
            totales_ventas = this.totales_ventas,
            totales_por_tarjeta = this.totales_por_tarjeta
        )
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<InventarioDia> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno"))
        }
        refreshJob?.cancel()
        return try {
            val localId = localIdActivo()
            SupabaseClientProvider.client.postgrest.rpc(
                "fn_cerrar_y_reciclar_turno_v2", buildJsonObject {
                    put("p_turno_viejo_id", turnoId)
                    put("p_turno_nuevo_id", turnoId + 1)
                    put("p_local_id", localId)
                    put("p_cierre_valor", cierre)
                }
            ).decodeAs<Int>()

            val diaEnCero = construirDesdeRoom(localId)
            db.inventarioCacheDao().guardar(diaEnCero.toEntity(localId, turnoId + 1))
            Result.success(diaEnCero)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            var turnoActivoId = db.turnoDao().obtenerActivo(localId)?.id
            if (turnoActivoId == null) {
                turnoRepository.precargarLocal(androidId, localId)
                turnoActivoId = db.turnoDao().obtenerActivo(localId)?.id
            }
            if (turnoActivoId == null) {
                return Result.success(Unit)
            }
            val inventarioTurno = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_turno", buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_local_id", localId)
                    put("p_turno_id", turnoActivoId)
                })
                .decodeList<InventarioTurno>()
                .firstOrNull()
                ?: return Result.success(Unit)

            val resultado = inventarioTurno.toInventarioDiaCompat()
            db.inventarioCacheDao().guardar(resultado.toEntity(localId, turnoActivoId))

            resultado.turno?.let { t ->
                db.turnoDao().insertar(
                    TurnoEntity(
                        id = t.id, usuarioId = null, apertura = t.apertura,
                        cierre = t.cierre, diferencia = t.diferencia,
                        createdAt = t.created_at, localId = localId
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun contarColaPendiente(androidId: String): Result<Int> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para revisar la cola"))
        }
        return try {
            val cola = SupabaseClientProvider.client.postgrest
                .rpc("get_sync_queue_jerarquico")
                .decodeList<JsonElement>()
            Result.success(cola.size)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error consultando cola", e)
            Result.failure(e)
        }
    }

    suspend fun contarVentasSinTurno(): Result<Int> {
        return try {
            val localId = localIdActivo()
            val sinTurno = db.ventaDao().obtenerTodas(localId).count { it.turnoId == null }
            Result.success(sinTurno)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error analizando ventas sin turno", e)
            Result.failure(e)
        }
    }

    suspend fun contarDevolucionesPendientes(): Result<Int> {
        return try {
            val localId = localIdActivo()
            val resueltos = setOf("aprobada_stock", "aprobada_merma", "rechazada")
            val pendientes = db.devolucionCacheDao().obtener(localId)
                ?.toModel()
                ?.count { it.estado !in resueltos } ?: 0
            Result.success(pendientes)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error analizando devoluciones", e)
            Result.failure(e)
        }
    }

    suspend fun contarMermasPendientes(): Result<Int> {
        return try {
            val localId = localIdActivo()
            Result.success(db.mermaDao().obtenerPendientes(localId).size)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error analizando mermas", e)
            Result.failure(e)
        }
    }

    suspend fun haySolicitudesPendientes(): Result<Int> {
        return try {
            val localId = localIdActivo()
            val pendientes = db.aprobacionStockCacheDao().obtener(localId)
                ?.toModel()
                ?.count { it.estado == "pendiente" } ?: 0
            Result.success(pendientes)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error analizando aprobaciones", e)
            Result.failure(e)
        }
    }
}
