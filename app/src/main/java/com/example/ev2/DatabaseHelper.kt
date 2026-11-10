package com.example.ev2

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class SensorEvent(
    val id: Int,
    val fechaHora: String,
    val sensor: String,
    val amenaza: String,
    val ventilador: String,
    val origen: String
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "IoTApp.db"
        private const val DATABASE_VERSION = 2

        // Tabla de usuarios
        private const val TABLE_USERS = "usuarios"
        private const val COLUMN_USER_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_PASSWORD_HASH = "password_hash"

        // Tabla de eventos IoT
        private const val TABLE_EVENTS = "eventos_iot"
        private const val COLUMN_EVENT_ID = "id"
        private const val COLUMN_FECHA_HORA = "fecha_hora"
        private const val COLUMN_SENSOR = "sensor" // MQ-2 o KY-026
        private const val COLUMN_AMENAZA = "amenaza" // GAS, HUMO, FUEGO
        private const val COLUMN_VENTILADOR = "ventilador" // ON, OFF
        private const val COLUMN_ORIGEN = "origen"

        // Llave compartida para cifrado simétrico AES-128 de tramas IoT (Norma ISO/IEC 27400)
        private const val AES_SECRET = "IOT27400_SEC_KEY" // Exactamente 16 bytes (128 bits)
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT UNIQUE,
                $COLUMN_PASSWORD_HASH TEXT
            )
        """.trimIndent()
        db.execSQL(createUsersTable)

        val createEventsTable = """
            CREATE TABLE $TABLE_EVENTS (
                $COLUMN_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FECHA_HORA TEXT,
                $COLUMN_SENSOR TEXT,
                $COLUMN_AMENAZA TEXT,
                $COLUMN_VENTILADOR TEXT,
                $COLUMN_ORIGEN TEXT
            )
        """.trimIndent()
        db.execSQL(createEventsTable)

        // Insertar usuario por defecto (admin / 1234)
        val adminHash = hashSHA256("1234")
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, "admin")
            put(COLUMN_PASSWORD_HASH, adminHash)
        }
        db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        onCreate(db)
    }

    /**
     * Función de hash criptográfico SHA-256 (ISO/IEC 27400 - Protección de credenciales en reposo)
     */
    fun hashSHA256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Cifrado simétrico AES-128 + Base64 para proteger datos sensibles en tránsito (ISO/IEC 27400)
     */
    fun encryptPayload(plainText: String): String {
        return try {
            val keySpec = SecretKeySpec(AES_SECRET.toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    /**
     * Descifrado simétrico AES-128 para tramas recibidas por Socket TCP
     */
    fun decryptPayload(cipherText: String): String {
        return try {
            val keySpec = SecretKeySpec(AES_SECRET.toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }

    // ==========================================
    // METODOS DE USUARIOS
    // ==========================================

    @Synchronized
    fun registerUser(username: String, pass: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, username.trim())
            put(COLUMN_PASSWORD_HASH, hashSHA256(pass))
        }
        return db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    fun validateLogin(username: String, pass: String): Boolean {
        val db = this.readableDatabase
        val hash = hashSHA256(pass)
        val query = "SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERNAME = ? AND $COLUMN_PASSWORD_HASH = ?"
        return db.rawQuery(query, arrayOf(username.trim(), hash)).use { cursor ->
            cursor.count > 0
        }
    }

    // ==========================================
    // METODOS DE EVENTOS IOT
    // ==========================================

    @Synchronized
    fun insertSensorEvent(
        fechaHora: String,
        sensor: String,
        amenaza: String,
        ventilador: String,
        origen: String
    ): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FECHA_HORA, fechaHora)
            put(COLUMN_SENSOR, sensor)
            put(COLUMN_AMENAZA, amenaza)
            put(COLUMN_VENTILADOR, ventilador)
            put(COLUMN_ORIGEN, origen)
        }
        return db.insert(TABLE_EVENTS, null, values) != -1L
    }

    @Synchronized
    fun getRecentEvents(): List<SensorEvent> {
        val eventList = mutableListOf<SensorEvent>()
        val db = this.readableDatabase

        db.rawQuery("SELECT * FROM $TABLE_EVENTS ORDER BY $COLUMN_EVENT_ID DESC LIMIT 15", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val event = SensorEvent(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_EVENT_ID)),
                        fechaHora = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA_HORA)),
                        sensor = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENSOR)),
                        amenaza = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AMENAZA)),
                        ventilador = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VENTILADOR)),
                        origen = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORIGEN))
                    )
                    eventList.add(event)
                } while (cursor.moveToNext())
            }
        }
        return eventList
    }

    @Synchronized
    fun clearEvents() {
        val db = this.writableDatabase
        db.delete(TABLE_EVENTS, null, null)
    }
}