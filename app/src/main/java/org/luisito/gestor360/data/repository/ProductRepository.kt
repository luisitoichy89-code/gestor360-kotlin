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
import org.luisito.gestor360.data.local.entities.toEntity
import org.luisito.gestor360.data.local.entities.toModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

/**
 * Offline-first: lee siempre de Room primero (nunca bloquea esperando la red).
 * Crear/editar/eliminar se aplican al instante en el caché local y se encolan
 * en "acciones_pendientes"; si hay internet se dispara una sincronización de
 * inmediato, pero aunque falle, la acción ya quedó guardada para reintentarse
 * después (WorkManager o el botón "Sincronizar ahora").
 *
 * TODO lo que toca el servidor o el caché va filtrado por local_id, leído de
 * SessionManager en el momento (nunca cacheado), porque el local activo puede
 * cambiar en caliente si el usuario es admin de varios locales.
 */
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
        return refrescarDesdeServidor(androidId)
    }

    /** Trae la verdad del servidor (ya filtrada por local_id) y reemplaza el caché de ese local. */
    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Product>> {
        val localId = localIdActivo()
        return try {
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Product>()
            db.productoDao().limpiarDeLocal(localId)
            db.productoDao().insertarTodos(productos.map { it.toEntity(localId) })
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
        // Id temporal negativo para que se pueda mostrar en la lista antes de sincronizar.
        val idTemporal = -(System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong())
        val producto = Product(idTemporal, nombre, precio, stock, ubicacion, categoria, localId)
        db.productoDao().insertarUno(producto.toEntity(localId))

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_nombre", nombre); put("p_precio", precio)
            put("p_stock", stock); put("p_ubicacion", ubicacion); put("p_categoria", categoria)
        }
        encolarYSincronizar(androidId, "crear_producto", payload, idTemporal)
        return Result.success(Unit)
    }

    suspend fun updateProduct(
        androidId: String, id: Long, nombre: String, precio: Double, stock: Double,
        ubicacion: String, categoria: String
    ): Result<Unit> {
        val localId = localIdActivo()
        db.productoDao().obtenerPorId(id, localId)?.let {
            db.productoDao().insertarUno(it.copy(nombre = nombre, precio = precio, stock = stock, ubicacion = ubicacion, categoria = categoria))
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id); put("p_nombre", nombre)
            put("p_precio", precio); put("p_stock", stock); put("p_ubicacion", ubicacion); put("p_categoria", categoria)
        }
        encolarYSincronizar(androidId, "actualizar_producto", payload)
        return Result.success(Unit)
    }

    suspend fun registrarMerma(androidId: String, producto: Product, cantidad: Double, motivo: String = "Merma"): Result<Unit> {
        val localId = localIdActivo()
        val nuevoStock = (producto.stock - cantidad).coerceAtLeast(0.0)
        // Aplicar YA en el caché local (optimista), igual que updateProduct.
        db.productoDao().obtenerPorId(producto.id, localId)?.let {
            db.productoDao().insertarUno(it.copy(stock = nuevoStock))
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_local_id", localId); put("p_producto_id", producto.id)
            put("p_cantidad", cantidad); put("p_motivo", motivo)
        }
        encolarYSincronizar(androidId, "registrar_merma_admin", payload)
        return Result.success(Unit)
    }

    suspend fun deleteProduct(androidId: String, id: Long): Result<Unit> {
        val localId = localIdActivo()
        db.productoDao().eliminar(id, localId)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId); put("p_id", id) }
        encolarYSincronizar(androidId, "eliminar_producto", payload)
        return Result.success(Unit)
    }

    /** Descuento optimista de stock local, usado por SaleRepository al vender offline. */
    suspend fun descontarStockLocal(id: Long, cantidad: Double) {
        db.productoDao().descontarStock(id, cantidad, localIdActivo())
    }

    private suspend fun encolarYSincronizar(androidId: String, tipo: String, payload: kotlinx.serialization.json.JsonObject, idTemporal: Long? = null) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = tipo, payloadJson = payload.toString(), idLocalTemporal = idTemporal)
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }

    /**
     * Precarga el caché de UN local específico (puede no ser el local activo
     * ahora mismo — así el admin puede tener el local 2 listo sin haber
     * "entrado" a él todavía). Nunca borra lo que ya había en caché si la
     * llamada de red falla: solo reemplaza cuando la descarga fue exitosa.
     */
    suspend fun precargarLocal(androidId: String, localId: Long): Result<Unit> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId); put("p_local_id", localId) })
                .decodeList<Product>()
            db.productoDao().limpiarDeLocal(localId)
            db.productoDao().insertarTodos(productos.map { it.toEntity(localId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
