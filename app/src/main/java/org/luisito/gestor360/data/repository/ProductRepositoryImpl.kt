package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.domain.repository.IProductRepository
import org.luisito.gestor360.domain.result.Result

class ProductRepositoryImpl : IProductRepository {
    private val supabase = SupabaseClient.instance

    override suspend fun getProducts(): Result<List<Product>> {
        return try {
            val products = supabase.from("products").select().decodeList<Product>()
            Result.Success(products)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al cargar productos", e)
        }
    }

    override suspend fun getProductById(id: String): Result<Product> {
        return try {
            val product = supabase.from("products")
                .select { filter { eq("id", id) } }
                .decodeList<Product>()
                .firstOrNull()
            if (product != null) Result.Success(product)
            else Result.Error("Producto no encontrado")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al obtener producto", e)
        }
    }

    override suspend fun createProduct(product: Product): Result<Unit> {
        return try {
            supabase.from("products").insert(product)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al crear producto", e)
        }
    }

    override suspend fun updateProduct(id: String, product: Product): Result<Unit> {
        return try {
            supabase.from("products")
                .update(product) { filter { eq("id", id) } }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al actualizar producto", e)
        }
    }

    override suspend fun deleteProduct(id: String): Result<Unit> {
        return try {
            supabase.from("products")
                .delete { filter { eq("id", id) } }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al eliminar producto", e)
        }
    }

    override suspend fun syncProducts(): Result<Unit> {
        return try {
            // Solo fuerza la sincronización
            getProducts()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error en sincronización", e)
        }
    }
}
