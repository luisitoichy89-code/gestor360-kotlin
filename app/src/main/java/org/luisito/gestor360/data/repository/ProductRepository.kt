package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product

class ProductRepository {

    suspend fun getProducts(androidId: String, almacenId: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest.rpc(
                "get_productos", mapOf("p_android_id" to androidId)
            ).decodeList<Product>().filter { it.almacen_id == almacenId }
            Result.success(productos)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createProduct(nombre: String, precio: Double, stock: Double, almacenId: String, androidId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "crear_producto",
                mapOf("p_android_id" to androidId, "p_nombre" to nombre, "p_precio" to precio, "p_stock" to stock, "p_almacen_id" to almacenId)
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateProduct(id: Long, nombre: String, precio: Double, stock: Double, androidId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "actualizar_producto",
                mapOf("p_android_id" to androidId, "p_id" to id, "p_nombre" to nombre, "p_precio" to precio, "p_stock" to stock)
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteProduct(id: Long, androidId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "eliminar_producto", mapOf("p_android_id" to androidId, "p_id" to id)
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
