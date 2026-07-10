package org.luisito.gestor360.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.luisito.gestor360.data.local.dao.AccionPendienteDao
import org.luisito.gestor360.data.local.dao.ConflictoDao
import org.luisito.gestor360.data.local.dao.LocalDao
import org.luisito.gestor360.data.local.dao.MermaDao
import org.luisito.gestor360.data.local.dao.ProductoDao
import org.luisito.gestor360.data.local.dao.TarjetaDao
import org.luisito.gestor360.data.local.dao.TurnoDao
import org.luisito.gestor360.data.local.dao.VentaDao
import org.luisito.gestor360.data.local.entities.AccionPendienteEntity
import org.luisito.gestor360.data.local.entities.ConflictoEntity
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
        UserEntity::class, LocalEntity::class
    ],
    // v5: PK compuesta (id, localId) en Producto/Merma/Turno/Tarjeta — antes la PK
    // era solo "id" y el caché de dos locales con ids de servidor repetidos se
    // pisaba entre sí (ver comentarios en esas entidades). fallbackToDestructiveMigration
    // recrea las tablas de caché en el próximo arranque; no hay pérdida real porque
    // todo esto se resincroniza del servidor (y las acciones pendientes sin
    // sincronizar viven en AccionPendienteEntity, que no cambió).
    version = 5,
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
