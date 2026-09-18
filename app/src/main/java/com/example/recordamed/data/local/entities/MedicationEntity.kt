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
    val isActive: Boolean = true,

    /**
     * Esquema de toma: "STRICT" o "FLEXIBLE". Ver
     * [com.example.recordamed.domain.schedule.ScheduleMode].
     *
     * Se guarda como texto, igual que [iconType] y [soundType], para no necesitar un
     * TypeConverter. Por defecto STRICT, que es el único comportamiento que la app
     * tenía antes: así ningún tratamiento ya existente cambia de forma al actualizar.
     */
    val scheduleMode: String = "STRICT",

    /**
     * Minutos entre tomas. 0 significa "desconocido".
     *
     * El intervalo no se persistía: se reconstruía restando horarios consecutivos, con
     * el mismo cálculo repetido en tres sitios, y con un solo horario no había forma de
     * distinguir 24 h de cualquier otro valor. En el esquema permisivo el intervalo es
     * el dato que manda —la siguiente toma es la anterior más el intervalo— así que
     * deducirlo deja de ser aceptable.
     *
     * Los medicamentos dados de alta antes de esta versión quedan en 0 y siguen
     * apoyándose en la deducción; los nuevos lo guardan.
     */
    val intervalMinutes: Int = 0
)
