package org.luisito.gestor360.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AccionPendienteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun encolar(accion: AccionPendienteEntity)

    @Query("SELECT * FROM acciones_pendientes WHERE modulo = :modulo ORDER BY createdAt ASC")
    suspend fun obtenerPendientesPorModulo(modulo: String): List<AccionPendienteEntity>

    @Query("SELECT COUNT(*) FROM acciones_pendientes WHERE modulo = :modulo AND entidadId = :entidadId")
    suspend fun contarPendientesDeEntidad(modulo: String, entidadId: String): Int

    @Delete
    suspend fun eliminar(accion: AccionPendienteEntity)

    @Query("DELETE FROM acciones_pendientes WHERE accionId = :accionId")
    suspend fun eliminarPorId(accionId: String)

    @Query(
        """
        UPDATE acciones_pendientes
        SET intentos = intentos + 1, ultimoError = :error
        WHERE accionId = :accionId
        """
    )
    suspend fun registrarIntentoFallido(accionId: String, error: String?)
}
