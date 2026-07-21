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
import org.luisito.gestor360.data.local.dao.UserDao
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
import org.luisito.gestor360.data.local.entities.UserEntity
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

val MIGRACION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
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

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS mermas_cache")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mermas_cache (
                id TEXT NOT NULL PRIMARY KEY,
                localId INTEGER NOT NULL,
                productoId TEXT NOT NULL,
                productoNombre TEXT NOT NULL,
                cantidad REAL NOT NULL,
                motivo TEXT,
                solicitadoPor INTEGER,
                solicitadoPorNombre TEXT,
                estado TEXT NOT NULL DEFAULT 'pendiente',
                pendienteSync INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mermas_cache_localId ON mermas_cache(localId)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
                tarjetaId TEXT,
                createdAt TEXT,
                sincronizada INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ventas_cache_new (id, productoId, productoNombre, cantidad, total, metodo, efectivo, transferencia, usuarioId, localId, clienteCi, clienteTel, clienteNombre, tarjetaId, createdAt, sincronizada)
            SELECT id, productoId, productoNombre, cantidad, total, metodo, efectivo, transferencia, usuarioId, localId, clienteCi, clienteTel, clienteNombre, NULL, createdAt, sincronizada
            FROM ventas_cache
            """.trimIndent()
        )
        db.execSQL("DROP TABLE ventas_cache")
        db.execSQL("ALTER TABLE ventas_cache_new RENAME TO ventas_cache")
    }
}

/**
 * NUEVO (Fase 1 de inventario/turnos): agrega la columna turnoId a las
 * tablas de caché que ahora la necesitan del lado servidor (ver
 * migracion_turno_id.sql). Solo ALTER TABLE ... ADD COLUMN, sin recrear
 * tablas ni tocar datos existentes — quedan con turnoId = NULL, igual que
 * sus contrapartes en Supabase.
 *
 * devoluciones_cache NO se toca: guarda un JSON completo por local (ver
 * DevolucionCacheEntity), no columnas individuales, así que el nuevo campo
 * turno_id de Devolucion (ver Devolucion.kt) no requiere cambio de esquema.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ventas_cache ADD COLUMN turnoId INTEGER")
        db.execSQL("ALTER TABLE mermas_cache ADD COLUMN turnoId INTEGER")
        db.execSQL("ALTER TABLE productos_eliminados_cache ADD COLUMN turnoId INTEGER")
    }
}

@Database(
    entities = [
        ProductoEntity::class, AccionPendienteEntity::class, TarjetaEntity::class, VentaEntity::class, ConflictoEntity::class,
        ProductoEliminadoCacheEntity::class,
        TurnoEntity::class, MermaEntity::class,
        UserEntity::class, LocalEntity::class, InventarioCacheEntity::class, DevolucionCacheEntity::class,
        AprobacionStockCacheEntity::class
    ],
    version = 15,
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
                    .addMigrations(MIGRACION_8_9, MIGRACION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build().also { db ->
                        runCatching { db.openHelper.writableDatabase.execSQL("PRAGMA auto_vacuum = FULL") }
                        INSTANCE = db
                    }
            }
    }
}
