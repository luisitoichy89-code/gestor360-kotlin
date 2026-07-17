package org.luisito.gestor360.data.local.dao

import androidx.room.*
import org.luisito.gestor360.data.local.entities.ProductoEntity

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos_cache WHERE localId = :localId ORDER BY nombre ASC")
    suspend fun obtenerTodos(localId: Long): List<ProductoEntity>

    @Query("SELECT * FROM productos_cache WHERE id = :id AND localId = :localId")
    suspend fun obtenerPorId(id: String, localId: Long): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(productos: List<ProductoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUno(producto: ProductoEntity)

    /** Limpia el caché completo (se usa al cambiar de local activo, para no mezclar locales). */
    @Query("DELETE FROM productos_cache")
    suspend fun limpiar()

    @Query("DELETE FROM productos_cache WHERE localId = :localId")
    suspend fun limpiarDeLocal(localId: Long)

    /**
     * Antes: "DELETE ... WHERE id > 0" (id positivo = ya sincronizado, por
     * convención de ids autoincrementales). Con UUID esa convención no existe,
     * así que ahora se limpia por el flag pendienteSync: solo se borran las
     * filas ya confirmadas por el servidor, las que siguen pendientes de
     * sincronizar se preservan aunque se refresque el caché.
     */
    @Query("DELETE FROM productos_cache WHERE pendienteSync = 0 AND localId = :localId")
    suspend fun limpiarSincronizadosDeLocal(localId: Long)

    @Query("DELETE FROM productos_cache WHERE id = :id AND localId = :localId")
    suspend fun eliminar(id: String, localId: Long)

    @Query("UPDATE productos_cache SET stock = stock - :cantidad WHERE id = :id AND localId = :localId")
    suspend fun descontarStock(id: String, cantidad: Double, localId: Long)

    // reemplazarIdTemporal() ya no existe: el id se genera una sola vez (UUID)
    // en el cliente y es el mismo antes y después de sincronizar.
}
