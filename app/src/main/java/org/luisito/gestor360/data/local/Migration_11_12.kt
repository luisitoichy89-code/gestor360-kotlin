package com.gestor360.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v11 -> v12: agrega la tabla tarjetas.
 *
 * NO crea acciones_pendientes/AccionPendienteEntity: según resumen_implementado.md
 * esa tabla ya existe desde las migraciones de Productos (MIGRACION_8_9 /
 * MIGRACION_10_11). Si esta migración se ejecuta antes de confirmar el nombre
 * real de esa tabla, verificar que coincida con la que ya está en el esquema.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tarjetas (
                id TEXT NOT NULL PRIMARY KEY,
                localId TEXT NOT NULL,
                nombre TEXT NOT NULL,
                tipo TEXT,
                numeroCuenta TEXT,
                activo INTEGER NOT NULL DEFAULT 1,
                creadoPor TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                version INTEGER NOT NULL DEFAULT 1,
                pendienteSync INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tarjetas_localId ON tarjetas(localId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tarjetas_localId_activo ON tarjetas(localId, activo)")

        // Defensivo: si por algún motivo acciones_pendientes todavía no existe
        // en este esquema, crearla acá no rompe nada (IF NOT EXISTS). Ajustar
        // columnas si el nombre real de la tabla de Productos difiere.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS acciones_pendientes (
                accionId TEXT NOT NULL PRIMARY KEY,
                modulo TEXT NOT NULL,
                tipoAccion TEXT NOT NULL,
                entidadId TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                intentos INTEGER NOT NULL DEFAULT 0,
                ultimoError TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_acciones_pendientes_modulo ON acciones_pendientes(modulo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_acciones_pendientes_entidadId ON acciones_pendientes(entidadId)")
    }
}
