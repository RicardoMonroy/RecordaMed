package com.example.recordamed.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.recordamed.data.local.dao.DoseLogDao
import com.example.recordamed.data.local.dao.DoseScheduleDao
import com.example.recordamed.data.local.dao.MedicationDao
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.data.local.entities.DoseScheduleEntity
import com.example.recordamed.data.local.entities.MedicationEntity

@Database(
    entities = [
        MedicationEntity::class,
        DoseScheduleEntity::class,
        DoseLogEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class RecordaMedDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun doseScheduleDao(): DoseScheduleDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        @Volatile
        private var INSTANCE: RecordaMedDatabase? = null

        /**
         * Migraciones explícitas entre versiones del esquema.
         *
         * La versión 3 es la primera cuyo esquema queda exportado a
         * `app/schemas/` y versionado en git. De aquí en adelante, todo cambio
         * de estructura sube la versión y agrega su migración a esta lista.
         */
        private val MIGRATIONS: Array<Migration> = arrayOf()

        fun getDatabase(context: Context): RecordaMedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecordaMedDatabase::class.java,
                    "recordamed_database"
                )
                    .addMigrations(*MIGRATIONS)
                    // Aquí había un `fallbackToDestructiveMigration()`: ante
                    // cualquier cambio de esquema Room borraba la base entera y
                    // con ella el historial de tomas, sin avisar. En una app de
                    // adherencia ese historial es el producto, así que una
                    // migración faltante ahora falla de forma ruidosa al
                    // desarrollar en vez de destruir datos en el dispositivo de
                    // alguien.
                    //
                    // El caso de downgrade sí se resuelve borrando: solo ocurre
                    // al instalar un build viejo sobre uno nuevo, algo que pasa
                    // en desarrollo y nunca en un dispositivo real.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
