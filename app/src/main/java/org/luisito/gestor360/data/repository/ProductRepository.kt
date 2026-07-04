package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product

class ProductRepository {

    suspend fun getProducts(androidId: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client.postgrest
                .rpc("get_productos", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Product>()
            Result.success(productos)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createProduct(
        androidId: String, nombre: String, precio: Double, stock: Double,
        ubicacion: String, categoria: String, almacenId: String
    ): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_nombre", nombre)
                put("p_precio", precio)
                put("p_stock", stock)
                put("p_ubicacion", ubicacion)
                put("p_categoria", categoria)
                put("p_almacen_id", almacenId)
            }
            SupabaseClientProvider.client.postgrest.rpc("crear_producto", params)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateProduct(
        androidId: String, id: Long, nombre: String, precio: Double, stock: Double,
        ubicacion: String, categoria: String, almacenId: String
    ): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
                put("p_nombre", nombre)
                put("p_precio", precio)
                put("p_stock", stock)
                put("p_ubicacion", ubicacion)
                put("p_categoria", categoria)
                put("p_almacen_id", almacenId)
            }
            SupabaseClientProvider.client.postgrest.rpc("actualizar_producto", params)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteProduct(androidId: String, id: Long): Result<Unit> {
        return try {
            val params = buildJsonObject { put("p_android_id", androidId); put("p_id", id) }
            SupabaseClientProvider.client.postgrest.rpc("eliminar_producto", params)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
