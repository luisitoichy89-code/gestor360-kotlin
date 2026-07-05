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

/**
 * Offline-first: lee siempre de Room primero (nunca bloquea esperando la red).
 * Crear/editar/eliminar se aplican al instante en el caché local y se encolan
 * en "acciones_pendientes"; si hay internet se dispara una sincronización de
 * inmediato, pero aunque falle, la acción ya quedó guardada para reintentarse
 * después (WorkManager o el botón "Sincronizar ahora").
 */
class ProductRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)
    private val trazaRepository: TrazaRepository = TrazaRepository()

    suspend fun getProducts(androidId: String): Result<List<Product>> {
        val cacheados = db.productoDao().obtenerTodos()
        if (cacheados.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheados.map { it.toModel() })
        }
        return refrescarDesdeServidor(androidId)
    }

    /** Trae la verdad del servidor y reemplaza el caché completo. Lo usa también SyncManager. */
    suspend fun refrescarDesdeServidor(androidId: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Product>()
            db.productoDao().limpiar()
            db.productoDao().insertarTodos(productos.map { it.toEntity() })
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
        ubicacion: String, categoria: String, almacenId: String? = null
    ): Result<Unit> {
        // Id temporal negativo para que se pueda mostrar en la lista antes de sincronizar.
        val idTemporal = -System.currentTimeMillis()
        val producto = Product(idTemporal, nombre, precio, stock, ubicacion, categoria, almacenId)
        db.productoDao().insertarUno(producto.toEntity())

        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_nombre", nombre); put("p_precio", precio)
            put("p_stock", stock); put("p_almacen_id", almacenId); put("p_ubicacion", ubicacion); put("p_categoria", categoria)
        }
        encolarYSincronizar(androidId, "crear_producto", payload, idTemporal)
        trazaRepository.registrar(androidId, "crear_producto", nombre)
        return Result.success(Unit)
    }

    suspend fun updateProduct(
        androidId: String, id: Long, nombre: String, precio: Double, stock: Double,
        ubicacion: String, categoria: String
    ): Result<Unit> {
        db.productoDao().obtenerPorId(id)?.let {
            db.productoDao().insertarUno(it.copy(nombre = nombre, precio = precio, stock = stock, ubicacion = ubicacion, categoria = categoria))
        }
        val payload = buildJsonObject {
            put("p_android_id", androidId); put("p_id", id); put("p_nombre", nombre)
            put("p_precio", precio); put("p_stock", stock); put("p_ubicacion", ubicacion); put("p_categoria", categoria)
        }
        encolarYSincronizar(androidId, "actualizar_producto", payload)
        trazaRepository.registrar(androidId, "actualizar_producto", "$nombre (id=$id)")
        return Result.success(Unit)
    }

    suspend fun registrarMerma(androidId: String, producto: Product, cantidad: Double): Result<Unit> {
        val nuevoStock = (producto.stock - cantidad).coerceAtLeast(0.0)
        return updateProduct(androidId, producto.id, producto.nombre, producto.precio, nuevoStock, producto.ubicacion ?: "", producto.categoria ?: "")
    }

    suspend fun deleteProduct(androidId: String, id: Long): Result<Unit> {
        db.productoDao().eliminar(id)
        val payload = buildJsonObject { put("p_android_id", androidId); put("p_id", id) }
        encolarYSincronizar(androidId, "eliminar_producto", payload)
        trazaRepository.registrar(androidId, "eliminar_producto", "id=$id")
        return Result.success(Unit)
    }

    /** Descuento optimista de stock local, usado por SaleRepository al vender offline. */
    suspend fun descontarStockLocal(id: Long, cantidad: Double) {
        db.productoDao().descontarStock(id, cantidad)
    }

    private suspend fun encolarYSincronizar(androidId: String, tipo: String, payload: kotlinx.serialization.json.JsonObject, idTemporal: Long? = null) {
        db.accionPendienteDao().encolar(
            AccionPendienteEntity(tipo = tipo, payloadJson = payload.toString(), idLocalTemporal = idTemporal)
        )
        if (NetworkMonitor.hayInternet(context)) {
            SyncWorker.sincronizarAhora(context)
        }
    }
}
