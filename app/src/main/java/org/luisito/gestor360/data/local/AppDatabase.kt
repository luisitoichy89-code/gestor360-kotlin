package org.luisito.gestor360.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.luisito.gestor360.data.local.dao.AccionPendienteDao
import org.luisito.gestor360.data.local.dao.AprobacionStockCacheDao
import org.luisito.gestor360.data.local.dao.ConflictoDao
import org.luisito.gestor360.data.local.dao.DevolucionCacheDao
import org.luisito.gestor360.data.local.dao.InventarioCacheDao
import org.luisito.gestor360.data.local.dao.LocalDao
import org.luisito.gestor360.data.local.dao.MermaDao
import org.luisito.gestor360.data.local.dao.ProductoDao
import org.luisito.gestor360.data.local.dao.ProductoEliminadoCacheDao
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
import org.luisito.gestor360.data.local.entities.ProductoEliminadoCacheEntity
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.data.local.entities.TurnoEntity
import org.luisito.gestor360.data.local.entities.VentaEntity

val MIGRACION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS productos_cache_new (
                id TEXT NOT NULL,
                nombre TEXT NOT NULL,
                precio REAL NOT NULL,
                stock REAL NOT NULL,
                ubicacion TEXT,
                categoria TEXT,
                localId INTEGER NOT NULL,
                createdAt TEXT,
                updatedAt TEXT,
                pendienteSync INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(id, localId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO productos_cache_new (id, nombre, precio, stock, ubicacion, categoria, localId, createdAt, updatedAt, pendienteSync)
            SELECT CAST(id AS TEXT), nombre, precio, stock, ubicacion, categoria, localId, createdAt, updatedAt, 0
            FROM productos_cache
            """.trimIndent()
        )
        db.execSQL("DROP TABLE productos_cache")
        db.execSQL("ALTER TABLE productos_cache_new RENAME TO productos_cache")
    }
}

/**
 * v10: ProductoEliminadoCacheEntity.id y ConflictoEntity.productoId pasan de
 * Long a String (mismo motivo que productos_cache: ahora referencian un
 * Product.id que es uuid). A diferencia de productos_cache, estas dos NO
 * llevan migración real: ambas son cachés puramente locales que nunca se
 * traen de vuelta del servidor —
 *   - productos_eliminados_cache es un log local del día (para el reporte de
 *     inventario), se genera solo cuando se borra un producto desde este
 *     dispositivo.
 *   - conflictos se genera solo cuando SyncManager detecta un stock negativo
 *     tras sincronizar.
 * En ambos casos, perder las filas viejas en la próxima apertura (vía
 * fallbackToDestructiveMigration) como mucho borra "hoy se eliminó tal
 * producto" o "hay un conflicto de stock sin resolver" — visible de nuevo la
 * próxima vez que ocurra, no es una pérdida de datos de negocio real. Por
 * eso se dejan en destructiva en vez de escribirles una migración real.
 */
@Database(
    entities = [
        ProductoEntity::class, AccionPendienteEntity::class, VentaEntity::class, ConflictoEntity::class,
        ProductoEliminadoCacheEntity::class,
        TurnoEntity::class, TarjetaEntity::class, MermaEntity::class,
        UserEntity::class, LocalEntity::class, InventarioCacheEntity::class, DevolucionCacheEntity::class,
        AprobacionStockCacheEntity::class
    ],
    // v5-v8: ver historial en la versión anterior de este archivo.
    // v9: productos_cache (id -> UUID string) con migración real (MIGRACION_8_9).
    // v10: ProductoEliminadoCacheEntity.id y ConflictoEntity.productoId
    // (Long -> String), bajo fallbackToDestructiveMigration (ver comentario
    // arriba de la clase: ambas son cachés locales sin pérdida real).
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun productoEliminadoCacheDao(): ProductoEliminadoCacheDao
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
                )
                    .addMigrations(MIGRACION_8_9)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
