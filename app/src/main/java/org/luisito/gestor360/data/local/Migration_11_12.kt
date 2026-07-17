package org.luisito.gestor360.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v11 -> v12: agrega tabla tarjetas.
 * localId es INTEGER (Long) para reflejar que local_id en Supabase es
 * bigint, igual que productos.local_id — NO uuid.
 *
 * NO crea acciones_pendientes: se asume que ya existe desde Productos.
 * Verificar contra el esquema real antes de aplicar en producción.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tarjetas (
                id TEXT NOT NULL PRIMARY KEY,
                localId INTEGER NOT NULL,
                nombre TEXT NOT NULL,
                tipo TEXT,
                numeroCuenta TEXT,
                activo INTEGER NOT NULL DEFAULT 1,
                pendienteSync INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tarjetas_localId ON tarjetas(localId)")
    }
}
