package org.luisito.gestor360.domain.repository

import org.luisito.gestor360.domain.result.Result
import org.luisito.gestor360.data.models.Sale

interface ISaleRepository {
    suspend fun getSales(): Result<List<Sale>>
    suspend fun getSaleById(id: String): Result<Sale>
    suspend fun createSale(sale: Sale): Result<Unit>
    suspend fun syncSales(): Result<Unit>
}
