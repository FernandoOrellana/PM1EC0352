package com.uth.bitacora

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "bitacora.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_APUNTES = "apuntes"
        const val COLUMN_ID = "id"
        const val COLUMN_ASIGNATURA = "asignatura"
        const val COLUMN_TEMA = "tema"
        const val COLUMN_FECHA = "fecha"
        const val COLUMN_DESCRIPCION = "descripcion"
        const val COLUMN_AUDIO = "audio"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_APUNTES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_ASIGNATURA + " TEXT, "
                + COLUMN_TEMA + " TEXT, "
                + COLUMN_FECHA + " TEXT, "
                + COLUMN_DESCRIPCION + " TEXT, "
                + COLUMN_AUDIO + " TEXT)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_APUNTES")
        onCreate(db)
    }

    // Insertar Apunte
    fun insertApunte(apunte: Apunte): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ASIGNATURA, apunte.asignatura)
            put(COLUMN_TEMA, apunte.tema)
            put(COLUMN_FECHA, apunte.fecha)
            put(COLUMN_DESCRIPCION, apunte.descripcion)
            put(COLUMN_AUDIO, apunte.audio)
        }
        val id = db.insert(TABLE_APUNTES, null, values)
        db.close()
        return id
    }

    // Listar todos los apuntes
    fun getAllApuntes(): List<Apunte> {
        val list = mutableListOf<Apunte>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_APUNTES ORDER BY $COLUMN_ID DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val apunte = Apunte(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    asignatura = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ASIGNATURA)),
                    tema = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEMA)),
                    fecha = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA)),
                    descripcion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPCION)),
                    audio = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUDIO))
                )
                list.add(apunte)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // Filtrar los apuntes por asignatura con consulta parametrizada (?)
    fun getApuntesByAsignatura(asignatura: String): List<Apunte> {
        val list = mutableListOf<Apunte>()
        val db = readableDatabase
        // Consulta parametrizada (?)
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_APUNTES WHERE $COLUMN_ASIGNATURA = ? ORDER BY $COLUMN_ID DESC",
            arrayOf(asignatura)
        )

        if (cursor.moveToFirst()) {
            do {
                val apunte = Apunte(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    asignatura = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ASIGNATURA)),
                    tema = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEMA)),
                    fecha = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA)),
                    descripcion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPCION)),
                    audio = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUDIO))
                )
                list.add(apunte)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // Obtener un apunte por ID
    fun getApunteById(id: Int): Apunte? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_APUNTES WHERE $COLUMN_ID = ?", arrayOf(id.toString()))
        var apunte: Apunte? = null
        if (cursor.moveToFirst()) {
            apunte = Apunte(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                asignatura = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ASIGNATURA)),
                tema = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEMA)),
                fecha = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA)),
                descripcion = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPCION)),
                audio = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUDIO))
            )
        }
        cursor.close()
        db.close()
        return apunte
    }

    // Actualizar apunte
    fun updateApunte(apunte: Apunte): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ASIGNATURA, apunte.asignatura)
            put(COLUMN_TEMA, apunte.tema)
            put(COLUMN_FECHA, apunte.fecha)
            put(COLUMN_DESCRIPCION, apunte.descripcion)
            put(COLUMN_AUDIO, apunte.audio)
        }
        val rows = db.update(TABLE_APUNTES, values, "$COLUMN_ID = ?", arrayOf(apunte.id.toString()))
        db.close()
        return rows
    }

    // Eliminar apunte
    fun deleteApunte(id: Int): Int {
        val db = writableDatabase
        val rows = db.delete(TABLE_APUNTES, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return rows
    }
}
