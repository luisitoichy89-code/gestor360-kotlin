package org.luisito.gestor360.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.luisito.gestor360.data.local.dao.AccionPendienteDao
import org.luisito.gestor360.data.local.dao.AprobacionStockCacheDao
import org.luisito.gestor360.data.local.dao.ConflictoDao
import org.luisito.gestor360.data.local.dao.DevolucionCacheDao
import org.luisito.gestor360.data.local.dao.InventarioCacheDao
import org.luisito.gestor360.data.local.dao.LocalDao
import org.luisito.gestor360.data.local.dao.MermaDao
import org.luisito.gestor360.data.local.dao.ProductoDao
import org.luisito.gestor360.data.local.dao.TarjetaDao
import org.luisito.gestor360.data.local.dao.TurnoDao
import org.luisito.gestor360.data.local.dao.VentaDao
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.AprobacionStockCacheEntity
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.local.entities.DevolucionCacheEntity
import org.luisito.gestor360.data.local.entities.InventarioCacheEntity
import org.luisito.gestor360.data.local.entities.LocalEntity
import org.luisito.gestor360.data.local.entities.MermaEntity
import org.luisito.gestor360.data.local.entities.ProductoEntity
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.VentaEntity

@Database(
    entities = [
        ProductoEntity::class, AccionPendienteEntity::class, VentaEntity::class, ConflictoEntity::class,
        TurnoEntity::class, TarjetaEntity::class, MermaEntity::class,
        UserEntity::class, LocalEntity::class, InventarioCacheEntity::class, DevolucionCacheEntity::class,
        AprobacionStockCacheEntity::class
    ],
    // v5: PK compuesta (id, localId) en Producto/Merma/Turno/Tarjeta — antes la PK
    // era solo "id" y el caché de dos locales con ids de servidor repetidos se
    // pisaba entre sí (ver comentarios en esas entidades). fallbackToDestructiveMigration
    // recrea las tablas de caché en el próximo arranque; no hay pérdida real porque
    // todo esto se resincroniza del servidor (y las acciones pendientes sin
    // sincronizar viven en AccionPendienteEntity, que no cambió).
    //
    // v6: se agregan inventario_cache y devoluciones_cache para que
    // InventarioRepository y DevolucionRepository queden offline-first (antes
    // pegaban directo al servidor, sin caché — ver auditoría). Misma lógica de
    // fallbackToDestructiveMigration: se recrean tablas de caché, cero pérdida real.
    //
    // v7: se agrega aprobaciones_cache para que AprobacionStockRepository también
    // quede offline-first por local (antes pedía siempre en vivo y devolvía vacío
    // sin internet — la última pieza que faltaba para que un admin pueda cambiar
    // de local sin internet y ver todo, no solo con el local con el que entró).
    // v8: se agrega tarjetaId a VentaEntity (ventas_cache) — ahora la venta
    // guarda qué tarjeta se usó para cobrar. Misma lógica de
    // fallbackToDestructiveMigration: se recrea la tabla de caché, cero
    // pérdida real (las ventas ya sincronizadas se vuelven a traer del
    // servidor; las pendientes de sincronizar viven en AccionPendienteEntity).
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun accionPendienteDao(): AccionPendienteDao
    abstract fun ventaDao(): VentaDao
    abstract fun conflictoDao(): ConflictoDao
    abstract fun turnoDao(): TurnoDao
    abstract fun tarjetaDao(): TarjetaDao
    abstract fun mermaDao(): MermaDao
    abstract fun userDao(): UserDao
    abstract fun localDao(): LocalDao
    abstract fun inventarioCacheDao(): InventarioCacheDao
    abstract fun devolucionCacheDao(): DevolucionCacheDao
    abstract fun aprobacionStockCacheDao(): AprobacionStockCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun obtener(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gestor360.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
