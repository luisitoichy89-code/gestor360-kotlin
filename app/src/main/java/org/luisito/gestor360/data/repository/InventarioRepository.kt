package org.luisito.gestor360.data.repository

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
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
import java.time.LocalDate

class InventarioRepository(private val context: Context = AppContextHolder.context) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)
    private var refreshJob: Job? = null

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getInventarioDia(
        androidId: String,
        fecha: LocalDate,
        forzarRefresh: Boolean = false,
        turnoIds: List<Long>? = null,
        vendedorId: Long? = null,
        onActualizadoDesdeServidor: (suspend (InventarioDia) -> Unit)? = null
    ): Result<InventarioDia> {
        val localId = localIdActivo()

        if (!turnoIds.isNullOrEmpty()) {
            return refrescarDesdeServidor(androidId, fecha, turnoIds)
        }

        val cacheado = db.inventarioCacheDao().obtener(localId, fecha.toString())

        if (cacheado != null && !forzarRefresh) {
            if (NetworkMonitor.hayInternet(context)) {
                refreshJob?.cancel()
                refreshJob = CoroutineScope(Dispatchers.IO).launch {
                    refrescarDesdeServidor(androidId, fecha)
                        .onSuccess { servidor -> onActualizadoDesdeServidor?.invoke(servidor) }
                }
            }
            return Result.success(cacheado.toModel())
        }

        if (NetworkMonitor.hayInternet(context)) {
            return refrescarDesdeServidor(androidId, fecha)
                .onSuccess { onActualizadoDesdeServidor?.invoke(it) }
        }

        val desdeOffline = cacheado?.let { fusionarConVentasPendientes(it.toModel(), localId, fecha) }
            ?: construirDesdeRoom(localId, fecha)
        return Result.success(desdeOffline)
    }

    suspend fun refrescar(androidId: String, fecha: LocalDate): Result<InventarioDia> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return refrescarDesdeServidor(androidId, fecha)
    }

    suspend fun getTurnosDelDia(androidId: String, fecha: LocalDate): Result<List<TurnoInfo>> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para ver los turnos de otros días"))
        }
        val localId = localIdActivo()
        return try {
            val turnos = SupabaseClientProvider.client.postgrest
                .rpc("get_turnos_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                })
                .decodeList<TurnoInfo>()
            Result.success(turnos)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error obteniendo turnos del día $fecha", e)
            Result.failure(e)
        }
    }

    private suspend fun construirDesdeRoom(localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }

        val eliminados = db.productoEliminadoCacheDao().obtenerPorFecha(localId, fechaStr)
            .map { e ->
                ProductoEliminadoInfo(id = e.id, nombre = e.nombre, stock = e.stock, fecha = e.fecha, resuelto_por_nombre = null)
            }
        val eliminadosPorId = db.productoEliminadoCacheDao().obtenerTodos(localId)
            .associate { e -> e.id to ProductoEliminadoInfo(id = e.id, nombre = e.nombre, stock = e.stock, fecha = e.fecha, resuelto_por_nombre = null) }

        val turnoActivo = db.turnoDao().obtenerActivo(localId)
        val turnoActivoId = turnoActivo?.id
        val turnoDesde = turnoActivo?.createdAt

        val ventasHoy = db.ventaDao().obtenerTodas(localId)
            .filter { it.createdAt?.startsWith(fechaStr) == true }
            .filter { venta ->
                when {
                    turnoActivoId == null -> true
                    venta.turnoId != null -> venta.turnoId == turnoActivoId
                    else -> turnoDesde == null || (venta.createdAt != null && venta.createdAt!! >= turnoDesde)
                }
            }
            .filter { venta ->
                when (session.getRol()) {
                    "seller" -> venta.usuarioId == session.getUserId()
                    else -> true
                }
            }

        val ventasInfo = ventasHoy.map { it.toVentaInfo(localId, nombreUsuarioLocal, eliminadosPorId) }

        val productosVendidos = fusionarProductosVendidos(emptyList(), ventasHoy, localId, eliminadosPorId)

        val totales = TotalesVentas(
            efectivo = ventasHoy.sumOf { it.efectivo },
            transferencia = ventasHoy.sumOf { it.transferencia },
            cantidad_ventas = ventasHoy.size.toLong()
        )

        val nuevos = db.productoDao().obtenerTodos(localId).filter { p ->
            p.createdAt?.startsWith(fechaStr) == true
        }.map { p ->
            ProductoInfo(
                id = p.id, nombre = p.nombre, precio = p.precio, stock = p.stock,
                ubicacion = p.ubicacion, fecha = p.createdAt,
                solicitado_por_nombre = null, resuelto_por_nombre = null
            )
        }

        val modificados = db.productoDao().obtenerTodos(localId).filter { p ->
            p.updatedAt?.startsWith(fechaStr) == true && p.createdAt?.startsWith(fechaStr) != true
        }.map { p ->
            ProductoInfo(
                id = p.id, nombre = p.nombre, precio = p.precio, stock = p.stock,
                ubicacion = p.ubicacion, fecha = p.updatedAt,
                solicitado_por_nombre = null, resuelto_por_nombre = null
            )
        }

        val devolucionesCache = db.devolucionCacheDao().obtener(localId)
        val devueltos = if (devolucionesCache != null) {
            devolucionesCache.toModel().filter { d ->
                d.created_at?.startsWith(fechaStr) == true || d.resuelto_at?.startsWith(fechaStr) == true
            }.map { d ->
                DevueltoInfo(
                    id = d.id ?: "", producto_nombre = d.producto_nombre, cantidad = d.cantidad,
                    metodo = d.metodo, estado = d.estado,
                    solicitado_por_nombre = d.solicitado_por_nombre,
                    resuelto_por_nombre = d.resuelto_por_nombre, resuelto_por_rol = null,
                    fecha = d.resuelto_at ?: d.created_at
                )
            }
        } else emptyList()

        val mermasLocales = db.mermaDao().obtenerPendientes(localId).map { m -> MermaInfo(id = m.id, producto_nombre = m.productoNombre, cantidad = m.cantidad, motivo = m.motivo ?: "", estado = m.estado, solicitado_por_nombre = m.solicitadoPorNombre, resuelto_por_nombre = null, fecha = null) }

        return InventarioDia(
            fecha = fechaStr,
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
            fecha = createdAt, cliente_ci = clienteCi, cliente_tel = clienteTel, cliente_nombre = clienteNombre,
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

    private suspend fun fusionarConVentasPendientes(cacheado: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        val turnoActivo = db.turnoDao().obtenerActivo(localId)
        val turnoActivoId = turnoActivo?.id
        val turnoDesde = turnoActivo?.createdAt

        val idsEnCache = cacheado.ventas.map { it.id }.toSet()
        val pendientes = db.ventaDao().obtenerTodas(localId)
            .filter { it.createdAt?.startsWith(fechaStr) == true }
            .filter { venta ->
                when {
                    turnoActivoId == null -> true
                    venta.turnoId != null -> venta.turnoId == turnoActivoId
                    else -> turnoDesde == null || (venta.createdAt != null && venta.createdAt!! >= turnoDesde)
                }
            }
            .filter { venta ->
                when (session.getRol()) {
                    "seller" -> venta.usuarioId == session.getUserId()
                    else -> true
                }
            }
            .filter { it.id !in idsEnCache }

        if (pendientes.isEmpty()) return cacheado

        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }
        val eliminadosPorId = db.productoEliminadoCacheDao().obtenerTodos(localId)
            .associate { e -> e.id to ProductoEliminadoInfo(id = e.id, nombre = e.nombre, stock = e.stock, fecha = e.fecha, resuelto_por_nombre = null) }

        val pendientesInfo = pendientes.map { it.toVentaInfo(localId, nombreUsuarioLocal, eliminadosPorId) }

        return cacheado.copy(
            ventas = (pendientesInfo + cacheado.ventas).sortedByDescending { it.fecha },
            productos_vendidos = fusionarProductosVendidos(cacheado.productos_vendidos, pendientes, localId, eliminadosPorId),
            totales_ventas = cacheado.totales_ventas.copy(
                efectivo = cacheado.totales_ventas.efectivo + pendientes.sumOf { it.efectivo },
                transferencia = cacheado.totales_ventas.transferencia + pendientes.sumOf { it.transferencia },
                cantidad_ventas = cacheado.totales_ventas.cantidad_ventas + pendientes.size
            )
        )
    }

    suspend fun refrescarDesdeServidor(androidId: String, fecha: LocalDate, turnoIds: List<Long>? = null): Result<InventarioDia> {
        val localId = localIdActivo()
        return try {
            val resultado = if (fecha == LocalDate.now() && turnoIds.isNullOrEmpty()) {
                val turnoActivoId = db.turnoDao().obtenerActivo(localId)?.id
                if (turnoActivoId == null) {
                    val diaVacio = construirDesdeRoom(localId, fecha)
                    db.inventarioCacheDao().guardar(diaVacio.toEntity(localId, fecha.toString()))
                    return Result.success(diaVacio)
                }
                SupabaseClientProvider.client.postgrest
                    .rpc("get_inventario_turno", buildJsonObject {
                        put("p_android_id", androidId); put("p_local_id", localId); put("p_turno_id", turnoActivoId)
                    })
                    .decodeAs<InventarioDia>()
            } else {
                SupabaseClientProvider.client.postgrest
                    .rpc("get_inventario_dia", buildJsonObject {
                        put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                        if (!turnoIds.isNullOrEmpty()) {
                            put("p_turno_ids", buildJsonArray { turnoIds.forEach { add(JsonPrimitive(it)) } })
                        }
                    })
                    .decodeAs<InventarioDia>()
            }

            if (turnoIds.isNullOrEmpty() || fecha == LocalDate.now()) {
                db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            }

            if (fecha == LocalDate.now()) {
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
            }
            Result.success(resultado)
        } catch (e: Exception) {
            Log.e("InventarioRepo", "Error refrescando servidor para fecha=$fecha", e)
            Result.failure(e)
        }
    }

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<InventarioDia> {
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.failure(IllegalStateException("Necesitas conexión para cerrar el turno"))
        }
        refreshJob?.cancel()
        return try {
            val localId = localIdActivo()
            val nuevoTurnoId = SupabaseClientProvider.client.postgrest.rpc("cerrar_turno", buildJsonObject {
                put("p_android_id", androidId); put("p_local_id", localId); put("p_turno_id", turnoId); put("p_cierre", cierre)
            }).decodeAs<Long>()

            val now = java.time.LocalDateTime.now().toString()
            val dbHelper = db.openHelper.writableDatabase
            dbHelper.execSQL(
                "UPDATE turno_cache SET cierre = ?, diferencia = ? WHERE id = ? AND localId = ?",
                arrayOf<Any>(cierre, 0.0, turnoId, localId)
            )
            dbHelper.execSQL(
                "INSERT OR REPLACE INTO turno_cache (id, localId, apertura, cierre, diferencia, createdAt) VALUES (?, ?, 0.0, NULL, NULL, ?)",
                arrayOf<Any>(nuevoTurnoId, localId, now)
            )
            dbHelper.execSQL(
                "DELETE FROM ventas_cache WHERE localId = ?",
                arrayOf<Any>(localId)
            )
            dbHelper.execSQL(
                "DELETE FROM inventario_cache WHERE localId = ?",
                arrayOf<Any>(localId)
            )

            val fechaHoy = LocalDate.now()
            val diaEnCero = construirDesdeRoom(localId, fechaHoy)
            db.inventarioCacheDao().guardar(diaEnCero.toEntity(localId, fechaHoy.toString()))
            Result.success(diaEnCero)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun precargarLocal(androidId: String, localId: Long, fecha: LocalDate = LocalDate.now()): Result<Unit> {
        return try {
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                })
                .decodeAs<InventarioDia>()
            db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            if (fecha == LocalDate.now()) {
                resultado.turno?.let { t ->
                    db.turnoDao().insertar(
                        TurnoEntity(
                            id = t.id, usuarioId = null, apertura = t.apertura,
                            cierre = t.cierre, diferencia = t.diferencia,
                            createdAt = t.created_at, localId = localId
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
