package com.gestor360.app
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "gestor360.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE locales (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, activo INTEGER DEFAULT 1, created_at TEXT)")
        db.execSQL("CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT DEFAULT '', nombre TEXT, rol TEXT, pin TEXT DEFAULT '', almacen_id TEXT, activo INTEGER DEFAULT 1)")
        db.execSQL("CREATE TABLE productos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, precio REAL, stock INTEGER, almacen_id TEXT)")
        db.execSQL("CREATE TABLE ventas (id INTEGER PRIMARY KEY AUTOINCREMENT, producto_id INTEGER, producto_nombre TEXT, cantidad REAL, precio_unit REAL, total REAL, metodo TEXT, efectivo REAL, transferencia REAL, usuario_id INTEGER, almacen_id TEXT, cliente_ci TEXT, cliente_tel TEXT, cliente_nombre TEXT, created_at TEXT)")
        db.execSQL("INSERT INTO locales (id,nombre,created_at) VALUES (1,'Local Principal',datetime('now'))")
        db.execSQL("INSERT INTO usuarios (username,password,nombre,rol,pin,almacen_id) VALUES ('admin','8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918','SUPER ADMIN','superadmin','123456','1')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}
