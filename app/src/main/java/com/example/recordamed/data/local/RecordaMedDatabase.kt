package com.example.recordamed.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    exportSchema = false
)
abstract class RecordaMedDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun doseScheduleDao(): DoseScheduleDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        @Volatile
        private var INSTANCE: RecordaMedDatabase? = null

        fun getDatabase(context: Context): RecordaMedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecordaMedDatabase::class.java,
                    "recordamed_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
