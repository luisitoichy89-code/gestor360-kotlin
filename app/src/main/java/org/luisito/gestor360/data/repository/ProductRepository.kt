package org.luisito.gestor360.data.repository

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.ProductoEliminadoCacheEntity
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager
import java.time.LocalDate
import java.util.UUID

class ProductRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)
    private val session = SessionManager(context)

    private fun localIdActivo(): Long =
        session.getLocalId() ?: throw IllegalStateException("No hay un local activo seleccionado")

    suspend fun getProducts(androidId: String): Result<List<Product>> {
        val localId = localIdActivo()
        val cacheados = db.productoDao().obtenerTodos(localId)
        if (cacheados.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheados.map { it.toModel() })
        }
        if (!NetworkMonitor.hayInternet(context)) {
            return Result.success(emptyList())
        }
        return try {
            refrescarDesdeServidor(androidId)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Product>> {
        return try {
            val localId = localIdActivo()
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Product>()
            // FIX: se busca la fila anterior de cada producto ANTES de limpiar,
            // para que toEntity() pueda conservar su createdAt/updatedAt real
            // en vez de pisarlos con "ahora" en cada refresh (ver ProductoEntity.kt).
            val anteriores = db.productoDao().obtenerTodos(localId).associateBy { it.id }
            // Antes de reinsertar lo confirmado por el servidor, se limpia solo
            // lo que ya estaba confirmado (pendienteSync = 0): así un producto
            // creado offline que todavía no sincronizó no desaparece de la lista
            // mientras se espera la confirmación.
            // BLINDAJE: limpiar + insertar ahora corre como una sola transacción
            // atómica (reemplazarSincronizados en ProductoDao) para que un corte
            // de luz o de red a mitad de camino no borre productos ya
            // sincronizados sin llegar a reinsertarlos.
            db.productoDao().reemplazarSincronizados(
                localId,
                productos.map { it.toEntity(localId, pendienteSync = false, anterior = anteriores[it.id]) }
            )
            Result.success(productos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchProducts(androidId: String, query: String): Result<List<Product>> {
        return getProducts(androidId).map { lista ->
            if (query.isBlank()) lista
            else lista.filter { it.nombre.contains(query, ignoreCase = true) }.take(30)
        }
    }

    suspend fun createProduct(
        androidId: String, nombre: String, precio: Double, stock: Double,
        ubicacion: String, categoria: String
    ): Result<Unit> {
        val localId = localIdActivo()
        // UUID generado en el dispositivo: es el id definitivo, el mismo antes
        // y después de sincronizar. Ya no existe la noción de "id temporal".
        val id = UUID.randomUUID().toString()
        val accionId = UUID.randomUUID().toString()
        val producto = Product(id, nombre, precio, stock, ubicacion, categoria, localId)
        db.productoDao().insertarUno(producto.toEntity(localId, pendienteSync = true))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id)
            put("p_nombre", nombre); put("p_precio", precio); put("p_stock", stock)
            put("p_ubicacion", ubicacion); put("p_categoria", categoria); put("p_accion_id", accionId)
        }
        encolarYSincronizar("crear_producto", payload)
        return Result.success(Unit)
    }

    suspend fun updateProduct(
        androidId: String, id: String, nombre: String, precio: Double, stock: Double,
        ubicacion: String, categoria: String
    ): Result<Unit> {
        val localId = localIdActivo()
        val accionId = UUID.randomUUID().toString()
        // FIX: el copy() no tocaba updatedAt, así que una edición real hecha
        // acá nunca quedaba marcada como "modificada hoy" (ver ProductoEntity.kt
        // / InventarioRepository "modificados", que depende de updatedAt).
        db.productoDao().obtenerPorId(id, localId)?.let {
            db.productoDao().insertarUno(
                it.copy(
                    nombre = nombre, precio = precio, stock = stock, ubicacion = ubicacion, categoria = categoria,
                    updatedAt = java.time.LocalDateTime.now().toString(), pendienteSync = true
                )
            )
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_nombre", nombre)
            put("p_precio", precio); put("p_stock", stock); put("p_ubicacion", ubicacion); put("p_categoria", categoria)
            put("p_accion_id", accionId)
        }
        encolarYSincronizar("actualizar_producto", payload)
        return Result.success(Unit)
    }

    suspend fun registrarMerma(androidId: String, producto: Product, cantidad: Double, motivo: String = "Merma"): Result<Unit> {
        val localId = localIdActivo()
        val accionId = UUID.randomUUID().toString()
        val nuevoStock = (producto.stock - cantidad).coerceAtLeast(0.0)
        db.productoDao().obtenerPorId(producto.id, localId)?.let {
            db.productoDao().insertarUno(it.copy(stock = nuevoStock, pendienteSync = true))
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_producto_id", producto.id)
            put("p_cantidad", cantidad); put("p_motivo", motivo); put("p_accion_id", accionId)
        }
        encolarYSincronizar("registrar_merma_admin", payload)
        return Result.success(Unit)
    }

    suspend fun deleteProduct(androidId: String, id: String): Result<Unit> {
        val localId = localIdActivo()

        // Si el producto se creó offline y esa creación todavía no sincronizó,
        // no tiene sentido avisarle al servidor de una eliminación: el servidor
        // nunca llegó a saber que este producto existía. Se cancela la
        // creación pendiente directamente.
        val creacionPendiente = db.accionPendienteDao().obtenerPendientes()
            .firstOrNull { it.tipo == "crear_producto" && it.payloadJson.contains("\"p_id\":\"$id\"") }
        if (creacionPendiente != null) {
            db.accionPendienteDao().eliminar(creacionPendiente)
            db.productoDao().eliminar(id, localId)
            return Result.success(Unit)
        }

        // Evitar encolar un eliminar_producto duplicado si ya hay uno pendiente.
        val yaPendiente = db.accionPendienteDao().obtenerPendientes()
            .any { it.tipo == "eliminar_producto" && it.payloadJson.contains("\"p_id\":\"$id\"") }
        if (yaPendiente) return Result.success(Unit)

        val fechaHoy = LocalDate.now().toString()
        val producto = db.productoDao().obtenerPorId(id, localId)
        if (producto != null) {
            db.productoEliminadoCacheDao().insertar(
                ProductoEliminadoCacheEntity(
                    id = producto.id, localId = localId, nombre = producto.nombre,
                    stock = producto.stock, fecha = fechaHoy
                )
            )
        }

        val accionId = UUID.randomUUID().toString()
        db.productoDao().eliminar(id, localId)
        db.accionPendienteDao().encolar(AccionPendienteEntity(tipo = "eliminar_producto", payloadJson = buildJsonObject {
            put("p_android_id", androidId)
            put("p_local_id", localId)
            put("p_id", id)
            put("p_accion_id", accionId)
        }.toString()))
        return Result.success(Unit)
    }

    suspend fun descontarStockLocal(id: String, cantidad: Double) {
        db.productoDao().descontarStock(id, cantidad, localIdActivo())
    }

    private suspend fun encolarYSincronizar(tipo: String, payload: kotlinx.serialization.json.JsonObject) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = tipo, payloadJson = payload.toString())
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }

    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Product>()
            db.productoDao().insertarTodos(productos.map { it.toEntity(localId, pendienteSync = false) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
