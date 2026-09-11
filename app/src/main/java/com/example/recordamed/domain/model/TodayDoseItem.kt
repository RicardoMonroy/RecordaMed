package com.example.recordamed.domain.model

data class TodayDoseItem(
    val medicationId: Long,
    val medicationName: String,
    val dosage: String,
    val instructions: String,
    val colorHex: Long,
    val iconType: String,
    val scheduledTime: Long,
    val formattedTime: String,
    val status: String,
    val voiceNotePath: String?,
    val isNext: Boolean = false,
    val countdownText: String = ""
)
