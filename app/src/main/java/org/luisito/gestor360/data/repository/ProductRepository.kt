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
 * Offline-first: lee siempre de Room primero.
 *
 * AISLAMIENTO POR LOCAL: cuando el usuario tiene un local asignado (vendedor normal)
 * o el admin seleccionó uno en SelectorDeLocalBar, el caché se lee filtrado por
 * local_id. El servidor ya devuelve solo los datos del local correcto gracias al
 * SQL fix (get_productos usa local_id de usuarios vía android_id). El filtro en
 * caché es una segunda capa de seguridad para el caso en que el admin cargó datos
 * de varios locales y cambia de uno a otro sin internet.
 */
class ProductRepository(
    private val context: Context = AppContextHolder.context
) {
    private val db = AppDatabase.obtener(context)

    private fun localIdActivo(): Long? = SessionManager(context).getLocalId()

    suspend fun getProducts(androidId: String): Result<List<Product>> {
        val localId = localIdActivo()
        val cacheados = if (localId != null) db.productoDao().obtenerPorLocal(localId)
                        else db.productoDao().obtenerTodos()

        if (cacheados.isNotEmpty()) {
            if (NetworkMonitor.hayInternet(context)) {
                CoroutineScope(Dispatchers.IO).launch { refrescarDesdeServidor(androidId) }
            }
            return Result.success(cacheados.map { it.toModel() })
        }
        // Caché vacío — traer del servidor y filtrar en memoria
        return refrescarDesdeServidor(androidId).map { todos ->
            val localId2 = localIdActivo()
            if (localId2 != null) todos.filter { it.local_id == localId2 } else todos
        }
    }

    /**
     * Trae la verdad del servidor y reemplaza el caché completo.
     * Devuelve TODOS los productos sin filtrar (para que SyncManager pueda
     * detectar conflictos de stock en todos los locales del admin).
     */
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
        val localId = localIdActivo()
        val idTemporal = -System.currentTimeMillis()
        // El id temporal incluye local_id para que aparezca en la lista del local correcto
        val producto = Product(idTemporal, nombre, precio, stock, ubicacion, categoria, almacenId, localId)
        db.productoDao().insertarUno(producto.toEntity())

        val payload = buildJsonObject {
            put("p_android_id", androidId)
            put("p_nombre", nombre)
            put("p_precio", precio)
            put("p_stock", stock)
            put("p_almacen_id", almacenId)
            put("p_ubicacion", ubicacion)
            put("p_categoria", categoria)
            // El servidor resuelve local_id desde android_id, pero lo incluimos
            // por si la versión del RPC ya lo acepta como parámetro opcional.
            if (localId != null) put("p_local_id", localId)
        }
        encolarYSincronizar(androidId, "crear_producto", payload, idTemporal)
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
        return Result.success(Unit)
    }

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
