package org.luisito.gestor360.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.luisito.gestor360.data.local.entities.AprobacionStockCacheEntity

@Dao
interface AprobacionStockCacheDao {
    @Query("SELECT * FROM aprobaciones_cache WHERE localId = :localId")
    suspend fun obtener(localId: Long): AprobacionStockCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(entity: AprobacionStockCacheEntity)
}
