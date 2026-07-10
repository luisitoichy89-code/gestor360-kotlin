package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.LocalEntity

@Dao
interface LocalDao {
    @Query("SELECT * FROM locales_cache")
    suspend fun obtenerTodos(): List<LocalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(locales: List<LocalEntity>)

    /** Se usa antes de volcar la lista fresca del servidor (por si se eliminó/renombró algún local). */
    @Query("DELETE FROM locales_cache")
    suspend fun limpiar()
}
