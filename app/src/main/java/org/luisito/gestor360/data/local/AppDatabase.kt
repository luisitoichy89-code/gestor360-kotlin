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
 * v11: VentaEntity.productoId y MermaEntity.productoId pasan de Long a String
 * (mismo motivo que productos_cache en v9: ahora referencian un Product.id
 * que es uuid). A diferencia de ProductoEliminadoCacheEntity/ConflictoEntity
 * (v10, destructivas), ventas_cache y mermas_cache SÍ llevan migración real:
 * ambas pueden contener filas creadas offline (venta o merma solicitada sin
 * conexión, con id temporal) que todavía no se sincronizaron con el
 * servidor — una destructiva las borraría antes de que lleguen a encolarse
 * o resolverse, que es pérdida de datos de negocio real.
 *
 * aprobaciones_cache y devoluciones_cache NO se tocan acá: son JSON blob por
 * local (columna "json" TEXT), el cambio de tipo de producto_id vive dentro
 * del JSON serializado, no en una columna de la tabla, así que no requieren
 * migración de esquema.
 */
val MIGRACION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ventas_cache: PK simple (id)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ventas_cache_new (
                id TEXT NOT NULL,
                productoId TEXT NOT NULL,
                productoNombre TEXT,
                cantidad REAL NOT NULL,
                total REAL NOT NULL,
                metodo TEXT NOT NULL,
                efectivo REAL NOT NULL,
                transferencia REAL NOT NULL,
                usuarioId INTEGER,
                localId INTEGER NOT NULL,
                clienteCi TEXT,
                clienteTel TEXT,
                clienteNombre TEXT,
                tarjetaId INTEGER,
                createdAt TEXT,
                sincronizada INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ventas_cache_new (id, productoId, productoNombre, cantidad, total, metodo, efectivo, transferencia, usuarioId, localId, clienteCi, clienteTel, clienteNombre, tarjetaId, createdAt, sincronizada)
            SELECT id, CAST(productoId AS TEXT), productoNombre, cantidad, total, metodo, efectivo, transferencia, usuarioId, localId, clienteCi, clienteTel, clienteNombre, tarjetaId, createdAt, sincronizada
            FROM ventas_cache
            """.trimIndent()
        )
        db.execSQL("DROP TABLE ventas_cache")
        db.execSQL("ALTER TABLE ventas_cache_new RENAME TO ventas_cache")

        // mermas_cache: PK compuesta (id, localId)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mermas_cache_new (
                id INTEGER NOT NULL,
                productoId TEXT NOT NULL,
                productoNombre TEXT NOT NULL,
                cantidad REAL NOT NULL,
                motivo TEXT,
                solicitadoPor INTEGER,
                solicitadoPorNombre TEXT,
                estado TEXT NOT NULL,
                localId INTEGER NOT NULL,
                PRIMARY KEY(id, localId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO mermas_cache_new (id, productoId, productoNombre, cantidad, motivo, solicitadoPor, solicitadoPorNombre, estado, localId)
            SELECT id, CAST(productoId AS TEXT), productoNombre, cantidad, motivo, solicitadoPor, solicitadoPorNombre, estado, localId
            FROM mermas_cache
            """.trimIndent()
        )
        db.execSQL("DROP TABLE mermas_cache")
        db.execSQL("ALTER TABLE mermas_cache_new RENAME TO mermas_cache")
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
    // v11: ventas_cache y mermas_cache, productoId (Long -> String) con
    // migración real (MIGRACION_10_11): ambas pueden tener filas creadas
    // offline sin sincronizar todavía.
    version = 11,
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
                    .addMigrations(MIGRACION_8_9, MIGRACION_10_11)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
