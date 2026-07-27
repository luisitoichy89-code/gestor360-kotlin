package org.luisito.gestor360.data.repository

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.VentaEntity
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.InventarioCacheEntity
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
        onActualizadoDesdeServidor: (suspend (InventarioDia) -> Unit)? = null
    ): Result<InventarioDia> {
        val localId = localIdActivo()
        val fechaStr = fecha.toString()
        val cacheado = db.inventarioCacheDao().obtener(localId, fechaStr)

        if (!turnoIds.isNullOrEmpty()) {
            // Sin conexión: insistir contra el servidor solo dejaría el error en
            // pantalla con los datos del turno viejo. Para HOY reconstruimos desde
            // Room, que ya refleja el turno recién cerrado/abierto (así el cierre
            // de turno siempre se ve reiniciado, aunque no haya red en ese momento).
            if (!NetworkMonitor.hayInternet(context)) {
                return if (fecha == LocalDate.now()) {
                    val reconstruido = construirDesdeRoom(localId, fecha)
                    db.inventarioCacheDao().guardar(reconstruido.toEntity(localId, fechaStr))
                    Result.success(reconstruido)
                } else {
                    Result.success(resolverOfflineForzado(cacheado, localId, fecha))
                }
            }

            val resultado = refrescarDesdeServidor(androidId, fecha, turnoIds)
            // Había red pero la recarga igual falló (timeout, error puntual del
            // servidor, etc.): no dejamos en pantalla los datos del turno viejo,
            // reconstruimos localmente con el turno ya actualizado en Room.
            if (resultado.isFailure && fecha == LocalDate.now()) {
                val reconstruido = construirDesdeRoom(localId, fecha)
                db.inventarioCacheDao().guardar(reconstruido.toEntity(localId, fechaStr))
                return Result.success(reconstruido)
            }
            return resultado
        }

        if (cacheado != null && !forzarRefresh) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch {
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

        return Result.success(resolverOfflineForzado(cacheado, localId, fecha))
    }

    private suspend fun resolverOfflineForzado(cacheado: InventarioCacheEntity?, localId: Long, fecha: LocalDate): InventarioDia =
        if (cacheado != null) fusionarConVentasPendientes(cacheado.toModel(), localId, fecha)
        else construirDesdeRoom(localId, fecha)

    private suspend fun fusionarConVentasPendientes(base: InventarioDia, localId: Long, fecha: LocalDate): InventarioDia {
        val fechaStr = fecha.toString()
        val idsConocidos = base.ventas.map { it.id }.toSet()
        val pendientes = db.ventaDao().obtenerTodas(localId)
            .filter { !it.sincronizada && it.createdAt?.startsWith(fechaStr) == true && it.id !in idsConocidos }
        if (pendientes.isEmpty()) return base

        val nombreUsuarioLocal = session.getNombre().takeIf { it.isNotBlank() }
        val eliminadosPorId = db.productoEliminadoCacheDao().obtenerTodos(localId)
            .associate { e -> e.id to ProductoEliminadoInfo(id = e.id, nombre = e.nombre, stock = e.stock, fecha = e.fecha, resuelto_por_nombre = null) }

        val ventasNuevasInfo = pendientes.map { it.toVentaInfo(localId, nombreUsuarioLocal, eliminadosPorId) }

        return base.copy(
            ventas = base.ventas + ventasNuevasInfo,
            productos_vendidos = fusionarProductosVendidos(base.productos_vendidos, pendientes, localId, eliminadosPorId),
            totales_ventas = base.totales_ventas.copy(
                efectivo = base.totales_ventas.efectivo + pendientes.sumOf { it.efectivo },
                transferencia = base.totales_ventas.transferencia + pendientes.sumOf { it.transferencia },
                cantidad_ventas = base.totales_ventas.cantidad_ventas + pendientes.size
            )
        )
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

    suspend fun refrescarDesdeServidor(androidId: String, fecha: LocalDate, turnoIds: List<Long>? = null): Result<InventarioDia> {
        val localId = localIdActivo()
        return try {
            val resultado = SupabaseClientProvider.client.postgrest
                .rpc("get_inventario_dia", buildJsonObject {
                    put("p_android_id", androidId); put("p_local_id", localId); put("p_fecha", fecha.toString())
                    if (!turnoIds.isNullOrEmpty()) {
                        put("p_turno_ids", buildJsonArray { turnoIds.forEach { add(JsonPrimitive(it)) } })
                    }
                })
                .decodeAs<InventarioDia>()
            if (turnoIds.isNullOrEmpty() || fecha == LocalDate.now()) {
                db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            }

            if (fecha == LocalDate.now()) {
                resultado.turno?.let { t ->
                    db.turnoDao().limpiarCerrados()
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

    suspend fun cerrarTurno(androidId: String, turnoId: Long, cierre: Double): Result<Long> {
        return TurnoRepository(context).cerrarTurno(androidId, turnoId, cierre)
    }

    suspend fun haySolicitudesPendientes(): Boolean {
        val localId = localIdActivo()
        val aprobacionesPendientes = db.aprobacionStockCacheDao().obtener(localId)
            ?.toModel()
            ?.any { it.estado == "pendiente" } ?: false
        val mermasPendientes = db.mermaDao().obtenerPendientes(localId).isNotEmpty()
        val devolucionesPendientes = db.devolucionCacheDao().obtener(localId)
            ?.toModel()
            ?.any { it.estado == "pendiente" } ?: false
        return aprobacionesPendientes || mermasPendientes || devolucionesPendientes
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
