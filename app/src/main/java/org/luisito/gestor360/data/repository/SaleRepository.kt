package org.luisito.gestor360.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.model.Sale
import org.luisito.gestor360.data.room.AppDatabase
import org.luisito.gestor360.data.room.SaleDao
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SaleRepository(private val context: Context) {
    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val saleDao: SaleDao = db.saleDao()

    suspend fun saveSale(sale: Sale): Long = withContext(Dispatchers.IO) {
        val saleWithDate = sale.copy(
            createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        saleDao.insertSale(saleWithDate)
        saleWithDate.id
    }

    suspend fun getSalesByDate(almacenId: String, fecha: String): List<Sale> = withContext(Dispatchers.IO) {
        saleDao.getSalesByDate(almacenId, fecha)
    }

    suspend fun getTotalByDate(almacenId: String, fecha: String): Double = withContext(Dispatchers.IO) {
        saleDao.getTotalByDate(almacenId, fecha) ?: 0.0
    }

    suspend fun getLastSales(almacenId: String): List<Sale> = withContext(Dispatchers.IO) {
        saleDao.getLastSales(almacenId)
    }
}
