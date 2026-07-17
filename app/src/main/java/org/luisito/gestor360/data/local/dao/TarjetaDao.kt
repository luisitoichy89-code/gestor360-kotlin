package com.gestor360.tarjetas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TarjetaDao {

    @Query(
        """
        SELECT * FROM tarjetas
        WHERE localId = :localId AND deletedAt IS NULL
        ORDER BY nombre ASC
        """
    )
    fun observarTarjetasDeLocal(localId: String): Flow<List<TarjetaEntity>>

    @Query(
        """
        SELECT * FROM tarjetas
        WHERE localId = :localId AND activo = 1 AND deletedAt IS NULL
        ORDER BY nombre ASC
        """
    )
    fun observarTarjetasActivas(localId: String): Flow<List<TarjetaEntity>>

    @Query("SELECT * FROM tarjetas WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): TarjetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(tarjeta: TarjetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDesdeServidor(tarjetas: List<TarjetaEntity>)

    @Update
    suspend fun actualizar(tarjeta: TarjetaEntity)

    @Query("UPDATE tarjetas SET pendienteSync = :pendiente WHERE id = :id")
    suspend fun marcarPendienteSync(id: String, pendiente: Boolean)

    @Query("SELECT MAX(updatedAt) FROM tarjetas WHERE localId = :localId AND pendienteSync = 0")
    suspend fun obtenerUltimoUpdatedAt(localId: String): Long?
}
