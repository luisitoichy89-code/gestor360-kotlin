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
        // Se busca antes de decidir la rama (antes se pedía más abajo, solo
        // para la rama sin turnoIds) porque ahora ambas ramas la necesitan
        // como base para el fallback offline forzado de más abajo.
        val cacheado = db.inventarioCacheDao().obtener(localId, fechaStr)

        if (!turnoIds.isNullOrEmpty()) {
            if (forzarRefresh && !NetworkMonitor.hayInternet(context)) {
                // Antes esta rama iba SIEMPRE directo al servidor sin mirar
                // conectividad: sin internet, refrescarDesdeServidor tiraba
                // excepción → Result.failure → InventarioScreen reemplazaba
                // toda la pantalla por el mensaje de error (ver auditoría,
                // Causa raíz A). Con turnoIds explícito no hay forma de
                // honrar ESE filtro puntual sin servidor, así que se degrada
                // a lo mejor disponible localmente: última copia confiable +
                // ventas de este dispositivo aún no sincronizadas.
                return Result.success(resolverOfflineForzado(cacheado, localId, fecha))
            }
            return refrescarDesdeServidor(androidId, fecha, turnoIds)
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
            // Con forzarRefresh = true esto ya no es una mejora silenciosa en
            // segundo plano: se espera la respuesta real del servidor antes
            // de devolver algo (isLoading queda en true mientras tanto, ver
            // InventarioViewModel.cargarFecha), así el botón Refrescar
            // efectivamente actualiza todos los datos de forma visible en
            // vez de devolver la caché de inmediato y refrescar "a
            // escondidas" (ver auditoría, Causa raíz C).
            return refrescarDesdeServidor(androidId, fecha)
                .onSuccess { onActualizadoDesdeServidor?.invoke(it) }
        }

        // Sin conexión y sin nada más que intentar contra el servidor. Antes
        // esto llamaba siempre construirDesdeRoom() sin importar si ya había
        // una caché de una sincronización anterior (ver auditoría, Causa
        // raíz B) — con forzarRefresh = true eso tiraba a la basura toda la
        // riqueza de la última copia confiable del servidor (nombres/roles
        // de otros vendedores, turno exacto, aprobaciones) para reconstruir
        // un InventarioDia más pobre desde cero. resolverOfflineForzado()
        // usa esa caché como base cuando existe y solo le suma lo que
        // realmente puede faltarle: las ventas de ESTE dispositivo con
        // sincronizada = false. Sin ninguna caché previa (dispositivo nuevo,
        // 100% offline) construirDesdeRoom() sigue siendo el único material
        // posible, igual que antes.
        return Result.success(resolverOfflineForzado(cacheado, localId, fecha))
    }

    /**
     * Punto único del fallback offline con forzarRefresh = true: reusa la
     * última caché del servidor si existe (fusionándole las ventas locales
     * pendientes de sincronizar) o, en su defecto, construye desde Room.
     */
    private suspend fun resolverOfflineForzado(cacheado: InventarioCacheEntity?, localId: Long, fecha: LocalDate): InventarioDia =
        if (cacheado != null) fusionarConVentasPendientes(cacheado.toModel(), localId, fecha)
        else construirDesdeRoom(localId, fecha)

    /**
     * Le suma a un InventarioDia ya calculado (de caché o de servidor) las
     * ventas hechas en este dispositivo que todavía no confirmaron con
     * Supabase. VentaEntity.sincronizada ya existía en el proyecto, pero
     * nada en este archivo lo leía — la caché de inventario se trataba
     * siempre como una foto fija, nunca como algo a completar con lo
     * pendiente local.
     *
     * El chequeo de duplicados por id importa: InventarioRepository y
     * SaleRepository sincronizan la tabla ventas_cache por caminos
     * separados (esta clase nunca llama ventaDao().marcarSincronizada ni
     * reemplazarDeLocal), así que una venta puede figurar ya en `base`
     * porque el servidor la confirmó, mientras localmente sigue marcada
     * sincronizada = false hasta que ese otro camino la alcance. Sin el
     * filtro, esa venta se contaría dos veces.
     */
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

        // Límite de hora del turno activo (si turno_cache ya lo conoce). Esto
        // es lo que evita que, 100% sin conexión, las ventas de un turno ya
        // cerrado se sigan sumando junto con las del turno nuevo: antes acá
        // solo se filtraba por fecha, así que un cierre de turno no cambiaba
        // nada en esta reconstrucción offline.
        val turnoActivo = db.turnoDao().obtenerActivo(localId)
        val turnoActivoId = turnoActivo?.id
        val turnoDesde = turnoActivo?.createdAt

        val ventasHoy = db.ventaDao().obtenerTodas(localId)
            .filter { it.createdAt?.startsWith(fechaStr) == true }
            .filter { venta ->
                when {
                    turnoActivoId == null -> true // no se conoce el turno activo: no se puede acotar más que por fecha (mismo comportamiento que antes)
                    venta.turnoId != null -> venta.turnoId == turnoActivoId // venta con turno real estampado: comparación exacta, sin ambigüedad posible
                    else -> turnoDesde == null || (venta.createdAt != null && venta.createdAt!! >= turnoDesde) // venta vieja sin turnoId (de antes de esta actualización, o sincronizada del servidor): fallback por hora
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
                // Para "hoy" el caché siempre debe reflejar el turno activo,
                // sin importar si esta llamada vino con turnoIds explícito
                // (por ejemplo justo después de cerrar_turno, con el id del
                // turno nuevo) o sin filtro. Con el fix del RPC get_inventario_dia,
                // ambos casos representan lo mismo para el día de hoy: el
                // turno actualmente activo. Guardarlo de inmediato evita que,
                // tras cerrar turno, el próximo arranque de la app muestre
                // por un momento la caché del turno ya cerrado mientras espera
                // el refresh en segundo plano. Para días pasados (calendario)
                // se mantiene el comportamiento anterior: esa caché es "todo
                // el día" y no se pisa con una selección parcial de turnos.
                db.inventarioCacheDao().guardar(resultado.toEntity(localId, fecha.toString()))
            }

            if (fecha == LocalDate.now()) {
                // turno_cache existía en el proyecto (TurnoDao/TurnoEntity)
                // pero nada lo llenaba nunca, así que obtenerActivo() siempre
                // devolvía null. Se actualiza acá, cada vez que se confirma
                // con el servidor cuál es el turno vigente de HOY (el campo
                // "turno" del RPC es siempre el más reciente, sin importar el
                // filtro de turnoIds pedido). Esto es lo que le permite a
                // construirDesdeRoom() saber, incluso sin ninguna conexión ni
                // caché de inventario, desde qué hora en adelante cuentan las
                // ventas del turno actual — sin esto, un teléfono 100% offline
                // (recién instalado, o con el caché de inventario borrado) no
                // tenía forma de distinguir el turno cerrado del nuevo.
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
