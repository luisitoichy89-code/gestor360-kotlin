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

/**
 * v9: productos_cache pasa a tener "id" de tipo TEXT (UUID generado en el
 * cliente) en vez de INTEGER, y se agrega la columna "pendienteSync". Esta es
 * la primera tabla de negocio que sale de fallbackToDestructiveMigration():
 * ahora tiene una migración real (MIGRACION_8_9) que reconstruye la tabla
 * preservando cada fila (los productos ya sincronizados conservan su id
 * numérico original, solo que ahora como texto — es el mismo id que ya tienen
 * en Supabase, así que no hay ninguna inconsistencia).
 *
 * Las demás tablas de negocio (Tarjetas, Ventas, Merma, Solicitudes) siguen
 * en fallbackToDestructiveMigration() hasta que se trabaje su módulo
 * correspondiente, tal como se acordó: se migran una por una, no todas de
 * golpe.
 */
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
        // CAST(id AS TEXT): los productos ya sincronizados conservan su id
        // numérico real (ahora como texto). pendienteSync = 0 porque todo lo
        // que ya estaba en el caché antes de esta migración vino del servidor.
        db.execSQL(
            """
            INSERT INTO productos_cache_new (id, nombre, precio, stock, ubicacion, categoria, localId, createdAt, updatedAt, pendienteSync)
            SELECT CAST(id AS TEXT), nombre, precio, stock, ubicacion, categoria, localId, createdAt, updatedAt, 0
            FROM productos_cache
            """.trimIndent()
        )
        db.execSQL("DROP TABLE productos_cache")
        db.execSQL("ALTER TABLE productos_cache_new RENAME TO productos_cache")

        // Caso borde: un producto creado offline con el sistema viejo (id
        // temporal negativo) que todavía no había sincronizado en el momento
        // de esta actualización. Su fila de caché sobrevive (ej. id "-123..."),
        // pero su acción "crear_producto" pendiente no tiene "p_id" en el
        // payload (el RPC viejo no lo pedía). SyncManager.repararAccionesLegacyCreacionProducto()
        // la detecta y la repara la primera vez que se intenta sincronizar
        // después de la actualización — ver comentario en SyncManager.
    }
}

@Database(
    entities = [
        ProductoEntity::class, AccionPendienteEntity::class, VentaEntity::class, ConflictoEntity::class,
        ProductoEliminadoCacheEntity::class,
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
    //
    // v9: productos_cache (id -> UUID string) con migración real, ver
    // MIGRACION_8_9 arriba. Primer módulo migrado del plan offline-first;
    // el resto sigue con fallbackToDestructiveMigration() hasta su turno.
    version = 9,
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
