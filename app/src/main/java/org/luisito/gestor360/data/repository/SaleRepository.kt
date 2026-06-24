package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.Sale

class SaleRepository {
    private val supabase = SupabaseClient.instance
    
    suspend fun getAllSales(): List<Sale> {
        return try {
            supabase.from("sales")
                .select()
                .decodeList<Sale>()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun createSale(sale: Sale): Boolean {
        return try {
            supabase.from("sales")
                .insert(sale)
            true
        } catch (e: Exception) {
            false
        }
    }
}
