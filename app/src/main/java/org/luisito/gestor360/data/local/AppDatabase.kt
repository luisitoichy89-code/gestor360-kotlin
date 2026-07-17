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
/**
 * v12 -> v13: mermas_cache pasa de id bigint (asignado por el servidor, con
 * un id temporal negativo mientras no sincronizaba) a id TEXT uuid
 * (asignado en el dispositivo), igual que tarjetas y productos.
 *
 * A diferencia de MIGRACION_8_9/MIGRACION_10_11, acá NO hay un CAST real
 * posible: un id numérico viejo (1, 2, 3...) no se puede convertir en un
 * uuid que vaya a coincidir con nada en el servidor bajo el nuevo contrato
 * de crear_merma/resolver_merma (que ahora exige p_id uuid + p_accion_id).
 * Por eso esta migración es DESTRUCTIVA para mermas_cache únicamente.
 *
 * IMPORTANTE — decidir antes de aplicar en producción:
 * 1) Mermas ya resueltas (aprobada/rechazada) se pierden del caché local.
 *    Si hacen falta para reportes, exportarlas antes de actualizar.
 * 2) Mermas pendientes creadas offline bajo el contrato viejo (encoladas en
 *    acciones_pendientes con tipo "crear_merma" pero sin p_id/p_accion_id)
 *    van a fallar contra el nuevo RPC. Conviene limpiar/reconciliar esa
 *    cola antes de repartir esta actualización.
 */
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

/**
 * v13 -> v14: ventas_cache.tarjetaId pasa de Long a String (uuid). Mismo
 * motivo que productoId en v11: TarjetaEntity.id ya es un uuid generado en
 * el dispositivo (ver TarjetaEntity.kt), así que un tarjetaId bigint nunca
 * pudo hacer match contra una tarjeta real — es la causa de que las ventas
 * con tarjeta no aparecieran en la sección de tarjetas del inventario.
 *
 * A diferencia de MIGRACION_10_11 (productoId), acá NO hay un CAST útil:
 * los valores Long viejos de tarjetaId nunca correspondieron a un uuid real
 * (las tarjetas nunca tuvieron id numérico), así que convertirlos a texto
 * solo conservaría basura que tampoco va a matchear nada. Se ponen en NULL:
 * mismo criterio que MIGRATION_12_13 aplicó a mermas_cache.id.
 *
 * ATENCIÓN — igual que con producto_id (ver Sale.kt): esto destraba la
 * COMPILACIÓN en Android. Falta migrar del lado de Supabase la columna
 * tarjeta_id en la tabla "ventas" (bigint -> uuid) y los RPC
 * registrar_venta/get_ventas para que acepten/devuelvan uuid ahí también;
 * hasta entonces, vender con tarjeta puede seguir fallando en tiempo de
 * ejecución contra esos RPC viejos.
 */
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

@Database(
    entities = [
        ProductoEntity::class, AccionPendienteEntity::class, TarjetaEntity::class, VentaEntity::class, ConflictoEntity::class,
        ProductoEliminadoCacheEntity::class,
        TurnoEntity::class, MermaEntity::class,
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
    // v12: agrega tabla tarjetas (MIGRATION_11_12, ver Migration_11_12.kt).
    // v13: mermas_cache, id (bigint -> uuid) con migración DESTRUCTIVA
    // (MIGRATION_12_13, ver comentario arriba de la clase).
    // v14: ventas_cache, tarjetaId (bigint -> uuid) con migración DESTRUCTIVA
    // solo para esa columna (MIGRATION_13_14, ver comentario arriba de la clase).
    version = 14,
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
                    .addMigrations(MIGRACION_8_9, MIGRACION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
