package com.example.recordamed.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"]), Index(value = ["scheduledTime"])]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduledTime: Long,          // Timestamp de la dosis programada
    val takenTime: Long? = null,      // Timestamp real cuando se confirmó la toma
    val status: String = STATUS_PENDING // PENDING, TAKEN, SNOOZED, MISSED
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_TAKEN = "TAKEN"
        const val STATUS_SNOOZED = "SNOOZED"

        // Se marca automáticamente cuando pasan más de MISSED_GRACE_PERIOD_MINUTES
        // desde la hora programada sin que la toma se haya registrado — ya no
        // bloquea la tarjeta pidiendo registrar una toma que "caducó".
        const val STATUS_MISSED = "MISSED"

        const val MISSED_GRACE_PERIOD_MINUTES = 5
    }
}
