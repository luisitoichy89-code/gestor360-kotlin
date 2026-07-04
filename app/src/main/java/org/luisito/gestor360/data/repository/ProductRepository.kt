package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product

/**
 * Todo pasa por RPC. Le agregué p_ubicacion y p_categoria a crear_producto y
 * actualizar_producto para las nuevas mejoras — necesitas actualizar esas dos
 * funciones en Postgres para que acepten y guarden estos dos parámetros nuevos
 * (columnas ubicacion/categoria en la tabla productos). Te doy el SQL aparte.
 */
class ProductRepository(
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {

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

    suspend fun searchProducts(androidId: String, query: String): Result<List<Product>> {
        return getProducts(androidId).map { lista ->
            if (query.isBlank()) lista
            else lista.filter { it.nombre.contains(query, ignoreCase = true) }.take(30)
        }
    }

    /**
     * OJO: crear_producto exige p_almacen_id (uuid) en Postgres. Por ahora mando null
     * porque no tengo claro a qué debe apuntar ese uuid en tu esquema — ver pregunta aparte.
     */
    suspend fun createProduct(
        androidId: String,
        nombre: String,
        precio: Double,
        stock: Double,
        ubicacion: String,
        categoria: String,
        almacenId: String? = null
    ): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_nombre", nombre)
                put("p_precio", precio)
                put("p_stock", stock)
                put("p_almacen_id", almacenId)
                put("p_ubicacion", ubicacion)
                put("p_categoria", categoria)
            }
            SupabaseClientProvider.client.postgrest.rpc("crear_producto", params)
            trazaRepository.registrar(androidId, "crear_producto", nombre)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProduct(
        androidId: String,
        id: Long,
        nombre: String,
        precio: Double,
        stock: Double,
        ubicacion: String,
        categoria: String
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
            }
            SupabaseClientProvider.client.postgrest.rpc("actualizar_producto", params)
            trazaRepository.registrar(androidId, "actualizar_producto", "$nombre (id=$id)")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registrarMerma(androidId: String, producto: Product, cantidad: Double): Result<Unit> {
        val nuevoStock = (producto.stock - cantidad).coerceAtLeast(0.0)
        return updateProduct(
            androidId, producto.id, producto.nombre, producto.precio, nuevoStock,
            producto.ubicacion ?: "", producto.categoria ?: ""
        )
    }

    suspend fun deleteProduct(androidId: String, id: Long): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
            }
            SupabaseClientProvider.client.postgrest.rpc("eliminar_producto", params)
            trazaRepository.registrar(androidId, "eliminar_producto", "id=$id")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
