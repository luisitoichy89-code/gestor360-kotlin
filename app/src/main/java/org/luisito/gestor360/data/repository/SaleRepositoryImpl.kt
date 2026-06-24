package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.Sale
import org.luisito.gestor360.domain.repository.ISaleRepository
import org.luisito.gestor360.domain.result.Result

class SaleRepositoryImpl : ISaleRepository {
    private val supabase = SupabaseClient.instance

    override suspend fun getSales(): Result<List<Sale>> {
        return try {
            val sales = supabase.from("sales")
                .select()
                .decodeList<Sale>()
            Result.Success(sales)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al cargar ventas", e)
        }
    }

    override suspend fun getSaleById(id: String): Result<Sale> {
        return try {
            val sale = supabase.from("sales")
                .select { filter { eq("id", id) } }
                .decodeList<Sale>()
                .firstOrNull()
            if (sale != null) Result.Success(sale)
            else Result.Error("Venta no encontrada")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al obtener venta", e)
        }
    }

    override suspend fun createSale(sale: Sale): Result<Unit> {
        return try {
            supabase.from("sales").insert(sale)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al crear venta", e)
        }
    }

    override suspend fun syncSales(): Result<Unit> {
        return try {
            getSales()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error en sincronización", e)
        }
    }
}
