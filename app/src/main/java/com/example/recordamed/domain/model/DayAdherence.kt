package com.example.recordamed.domain.model

/**
 * Resumen de un día para el mapa de calor del historial: cuántas tomas
 * tocaban ese día para un medicamento y cuántas se tomaron / no se tomaron.
 */
data class DayAdherence(
    val dateMillis: Long, // medianoche (00:00) del día que representa
    val scheduledCount: Int,
    val takenCount: Int,
    val missedCount: Int
) {
    /** false para días antes de que el medicamento existiera (o tras su fin). */
    val hasData: Boolean get() = scheduledCount > 0

    /** 0f..1f, proporción de tomas registradas ese día. */
    val ratio: Float get() = if (scheduledCount == 0) 0f else takenCount.toFloat() / scheduledCount
}
