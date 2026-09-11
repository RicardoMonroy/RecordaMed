package com.example.recordamed.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val instructions: String = "",
    val colorHex: Long = 0xFF2E7D32, // Default friendly green
    val iconType: String = "PILL",   // PILL, CAPSULE, DROPS, SYRUP
    val isTemporary: Boolean = false, // false = crónico/indeterminado, true = temporal
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,        // Opcional para tratamientos temporales
    val voiceNotePath: String? = null,// Ruta del archivo .m4a con la voz familiar
    val soundType: String = "BELLS",  // BELLS, HARP, BOWL
    val isActive: Boolean = true
)
