package com.example.recordamed.data.local.dao

import androidx.room.*
import com.example.recordamed.data.local.entities.DoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    fun getLogsBetween(startTime: Long, endTime: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime LIMIT 1")
    suspend fun getLogForDose(medicationId: Long, scheduledTime: Long): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId AND scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    suspend fun getLogsForMedicationBetween(medicationId: Long, startTime: Long, endTime: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY scheduledTime DESC LIMIT :limit")
    suspend fun getRecentLogsForMedication(medicationId: Long, limit: Int): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs ORDER BY scheduledTime DESC")
    fun getAllLogs(): Flow<List<DoseLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: DoseLogEntity): Long

    @Query("UPDATE dose_logs SET status = :status, takenTime = :takenTime WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime")
    suspend fun updateDoseStatus(medicationId: Long, scheduledTime: Long, status: String, takenTime: Long?)

    @Query("UPDATE dose_logs SET status = :status, takenTime = :takenTime WHERE id = :id")
    suspend fun updateDoseStatusById(id: Long, status: String, takenTime: Long?)
}
