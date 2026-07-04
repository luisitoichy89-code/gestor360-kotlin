package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product

class ProductRepository {
    suspend fun getProducts(androidId: String, almacenId: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest.rpc(
                "get_productos", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<Product>().filter { it.almacen_id == almacenId }
            Result.success(productos)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createProduct(nombre: String, precio: Double, stock: Double, almacenId: String, androidId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "crear_producto",
                buildJsonObject { put("p_android_id", androidId); put("p_nombre", nombre); put("p_precio", precio); put("p_stock", stock); put("p_almacen_id", almacenId) }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateProduct(id: Long, nombre: String, precio: Double, stock: Double, androidId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "actualizar_producto",
                buildJsonObject { put("p_android_id", androidId); put("p_id", id); put("p_nombre", nombre); put("p_precio", precio); put("p_stock", stock) }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteProduct(id: Long, androidId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "eliminar_producto", buildJsonObject { put("p_android_id", androidId); put("p_id", id) }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
