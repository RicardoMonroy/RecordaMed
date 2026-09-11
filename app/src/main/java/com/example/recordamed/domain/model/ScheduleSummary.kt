package com.example.recordamed.domain.model

/**
 * Resumen legible de los horarios de un medicamento, para no tener que
 * mostrar una lista larga de casillas individuales (un medicamento cada
 * 15 minutos puede tener más de 40 horarios al día).
 */
data class ScheduleSummary(
    val count: Int,
    val intervalText: String,
    val firstFormatted: String,
    val lastFormatted: String,
    // Horarios individuales formateados, solo cuando hay pocos (para
    // mostrarlos como chips); vacío si conviene quedarse solo con el resumen.
    val compactTimes: List<String>
)
