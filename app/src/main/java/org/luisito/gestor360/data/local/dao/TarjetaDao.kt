package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.models.Tarjeta

@Dao
interface TarjetaDao {
    @Query("SELECT * FROM tarjetas")
    suspend fun obtenerTodas(): List<Tarjeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(tarjeta: Tarjeta)
}
