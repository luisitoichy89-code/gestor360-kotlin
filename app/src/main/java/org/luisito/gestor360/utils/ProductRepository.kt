package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product

/**
 * Todo pasa por RPC (get_productos, crear_producto, actualizar_producto, eliminar_producto).
 * Cada función recibe p_android_id y resuelve el cliente_id/almacen del negocio del lado
 * del servidor — el cliente ya no lee/escribe tablas directamente ni maneja cliente_id.
 *
 * OJO: si alguna llamada falla con "Could not find the function...", significa que el
 * nombre de parámetro que uso aquí no coincide exactamente con tu firma real en Postgres.
 * Postgres/PostgREST resuelve el RPC por nombre de función + nombres de parámetros, así
 * que debe calzar carácter por carácter. Mándame el `\df+ nombre_funcion` o el CREATE
 * FUNCTION real si alguna de estas no pega.
 */
class ProductRepository {

    suspend fun getProducts(androidId: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Product>()
            Result.success(productos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sin RPC de búsqueda dedicado: se filtra en memoria sobre get_productos. */
    suspend fun searchProducts(androidId: String, query: String): Result<List<Product>> {
        return getProducts(androidId).map { lista ->
            if (query.isBlank()) lista
            else lista.filter { it.nombre.contains(query, ignoreCase = true) }.take(30)
        }
    }

    suspend fun createProduct(androidId: String, nombre: String, precio: Double, stock: Double): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_nombre", nombre)
                put("p_precio", precio)
                put("p_stock", stock)
            }
            SupabaseClientProvider.client.postgrest.rpc("crear_producto", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProduct(androidId: String, id: Long, nombre: String, precio: Double, stock: Double): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
                put("p_nombre", nombre)
                put("p_precio", precio)
                put("p_stock", stock)
            }
            SupabaseClientProvider.client.postgrest.rpc("actualizar_producto", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Merma directa (rol admin): reduce stock de una vez vía actualizar_producto. */
    suspend fun registrarMerma(androidId: String, producto: Product, cantidad: Double): Result<Unit> {
        val nuevoStock = (producto.stock - cantidad).coerceAtLeast(0.0)
        return updateProduct(androidId, producto.id, producto.nombre, producto.precio, nuevoStock)
    }

    suspend fun deleteProduct(androidId: String, id: Long): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
            }
            SupabaseClientProvider.client.postgrest.rpc("eliminar_producto", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
