package com.example.recordamed.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
data class DoseScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val timeHour: Int,    // 0-23
    val timeMinute: Int,  // 0-59
    // Posición (0 = primera toma elegida, 1 = la siguiente, ...) dentro de la
    // secuencia generada para este medicamento. Con horarios frecuentes que
    // cruzan la medianoche, la casilla de menor hora:minuto NO es
    // necesariamente la primera de la secuencia real — este índice es la
    // única forma inequívoca de saberlo.
    val sequenceIndex: Int = 0
)
