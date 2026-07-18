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

    /**
     * BLINDAJE: limpiarSincronizadosDeLocal() + insertarTodos() se llamaban como dos
     * operaciones sueltas desde el repository. Si el proceso se cortaba (apagón, app
     * matada por el sistema, corte de red a mitad del refresh) justo entre esas dos
     * llamadas, el borrado ya había quedado escrito en disco pero la inserción no
     * llegaba a correr: productos ya sincronizados desaparecían del caché local para
     * siempre (hasta el próximo refresh exitoso). @Transaction hace que Room corra
     * ambas queries dentro de una única transacción SQLite: si algo falla o el
     * proceso muere a mitad de camino, ninguna de las dos queda aplicada y el caché
     * previo se conserva intacto. No cambia qué se borra ni qué se inserta, solo
     * las agrupa de forma atómica.
     */
    @Transaction
    suspend fun reemplazarSincronizados(localId: Long, productos: List<ProductoEntity>) {
        limpiarSincronizadosDeLocal(localId)
        insertarTodos(productos)
    }

    @Query("DELETE FROM productos_cache WHERE id = :id AND localId = :localId")
    suspend fun eliminar(id: String, localId: Long)

    @Query("UPDATE productos_cache SET stock = stock - :cantidad WHERE id = :id AND localId = :localId")
    suspend fun descontarStock(id: String, cantidad: Double, localId: Long)

    // reemplazarIdTemporal() ya no existe: el id se genera una sola vez (UUID)
    // en el cliente y es el mismo antes y después de sincronizar.
}
