package com.example.recordamed.data.local.dao

import androidx.room.*
import com.example.recordamed.data.local.entities.DoseScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseScheduleDao {
    @Query("SELECT * FROM dose_schedules WHERE medicationId = :medicationId ORDER BY timeHour ASC, timeMinute ASC")
    fun getSchedulesForMedication(medicationId: Long): Flow<List<DoseScheduleEntity>>

    @Query("SELECT * FROM dose_schedules WHERE medicationId = :medicationId ORDER BY timeHour ASC, timeMinute ASC")
    suspend fun getSchedulesForMedicationSync(medicationId: Long): List<DoseScheduleEntity>

    // Orden real de la secuencia (primera toma, segunda, ...) en vez de por
    // hora del día — importante para horarios frecuentes que cruzan la
    // medianoche, donde la casilla de menor hora (ej. 12:01 a.m.) en realidad
    // es la ÚLTIMA de la secuencia, no la primera.
    @Query("SELECT * FROM dose_schedules WHERE medicationId = :medicationId ORDER BY sequenceIndex ASC")
    suspend fun getSchedulesForMedicationInSequenceOrder(medicationId: Long): List<DoseScheduleEntity>

    @Query("SELECT * FROM dose_schedules ORDER BY timeHour ASC, timeMinute ASC")
    fun getAllSchedules(): Flow<List<DoseScheduleEntity>>

    @Query("SELECT * FROM dose_schedules ORDER BY timeHour ASC, timeMinute ASC")
    suspend fun getAllSchedulesSync(): List<DoseScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<DoseScheduleEntity>)

    @Query("DELETE FROM dose_schedules WHERE medicationId = :medicationId")
    suspend fun deleteSchedulesForMedication(medicationId: Long)
}
