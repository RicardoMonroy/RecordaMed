package com.example.recordamed.domain.model

data class MedicationCardItem(
    val medicationId: Long,
    val name: String,
    val dosage: String,
    val instructions: String,
    val soundIcon: String,
    val isTemporary: Boolean,
    val nextDoseScheduledTime: Long?,
    val nextDoseFormattedTime: String,
    val countdownText: String,
    val progressFraction: Float, // 0.0f a 1.0f para la barra de progreso
    val isDueNow: Boolean,
    val isAllTakenToday: Boolean,
    // true si ya toca (isDueNow) o falta 15 minutos o menos: el botón se
    // habilita para registrar la toma. Si falta más tiempo, el botón queda
    // dormido para evitar registros adelantados por error.
    val canRegisterNow: Boolean
,
    /**
     * Por qué la próxima toma no está donde el intervalo la pondría, cuando ese sea el
     * caso. La app corre horas por su cuenta —para no despertar a nadie de madrugada— y
     * un cambio de hora sin explicación se parece demasiado a un error.
     */
    val scheduleNote: String? = null
)
