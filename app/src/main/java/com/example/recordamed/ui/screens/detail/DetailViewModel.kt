package com.example.recordamed.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.data.local.entities.DoseScheduleEntity
import com.example.recordamed.data.local.entities.MedicationEntity
import com.example.recordamed.domain.model.ScheduleSummary
import com.example.recordamed.service.AudioVoiceManager
import com.example.recordamed.service.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DetailUiState(
    val medication: MedicationEntity? = null,
    val scheduleSummary: ScheduleSummary? = null,
    // Solo para tratamientos temporales: fecha y hora de la última toma.
    val lastDoseText: String? = null,
    val isPlayingSound: Boolean = false,
    val isDeleted: Boolean = false
)

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RecordaMedApp).repository
    private val alarmScheduler: AlarmScheduler = (application as RecordaMedApp).alarmScheduler
    private val audioVoiceManager = AudioVoiceManager(application)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadMedication(id: Long) {
        viewModelScope.launch {
            val med = repository.getMedicationById(id)
            // En orden real de secuencia (primera toma, segunda...), no por
            // hora del día: con horarios frecuentes que cruzan la medianoche,
            // ordenar por hora pondría la última toma de la noche primero.
            val schedules = repository.getSchedulesForMedicationInSequenceOrder(id)

            _uiState.update {
                it.copy(
                    medication = med,
                    scheduleSummary = buildScheduleSummary(schedules),
                    lastDoseText = med?.let { m -> buildLastDoseText(m, schedules) }
                )
            }
        }
    }

    private fun buildScheduleSummary(schedules: List<DoseScheduleEntity>): ScheduleSummary? {
        if (schedules.isEmpty()) return null
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun format(s: DoseScheduleEntity): String {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, s.timeHour)
                set(Calendar.MINUTE, s.timeMinute)
            }
            return timeFormat.format(cal.time)
        }

        val intervalMinutes = if (schedules.size >= 2) {
            val a = schedules[0]
            val b = schedules[1]
            var diff = (b.timeHour * 60 + b.timeMinute) - (a.timeHour * 60 + a.timeMinute)
            if (diff <= 0) diff += 24 * 60
            diff
        } else {
            24 * 60
        }
        val intervalText = when {
            intervalMinutes % 60 == 0 -> "cada ${intervalMinutes / 60} ${if (intervalMinutes == 60) "hora" else "horas"}"
            intervalMinutes < 60 -> "cada $intervalMinutes minutos"
            else -> "cada ${intervalMinutes / 60}h ${intervalMinutes % 60}m"
        }

        // Los horarios individuales solo se listan cuando de verdad "dan la
        // vuelta" — es decir, cuando se completan exactamente las 24 horas y
        // el siguiente golpe caería otra vez en la primera hora (p.ej. cada
        // 8 horas: 6pm, 2am, 10am, y ahí cierra). Si no cierran el círculo
        // limpio (como cada 15 minutos, capado a 48 tomas que no alcanzan a
        // dar la vuelta completa) o son demasiados, se deja solo el resumen.
        val closesFullCircle = schedules.size * intervalMinutes == 24 * 60
        val compactTimes = if (closesFullCircle && schedules.size <= 12) schedules.map(::format) else emptyList()

        return ScheduleSummary(
            count = schedules.size,
            intervalText = intervalText,
            firstFormatted = format(schedules.first()),
            lastFormatted = format(schedules.last()),
            compactTimes = compactTimes
        )
    }

    /** Fecha y hora de la última toma programada, solo para tratamientos temporales. */
    private fun buildLastDoseText(medication: MedicationEntity, schedules: List<DoseScheduleEntity>): String? {
        if (!medication.isTemporary || medication.endDate == null || schedules.isEmpty()) return null

        val anchor = schedules.first()
        val intervalMinutes = if (schedules.size >= 2) {
            val a = schedules[0]
            val b = schedules[1]
            var diff = (b.timeHour * 60 + b.timeMinute) - (a.timeHour * 60 + a.timeMinute)
            if (diff <= 0) diff += 24 * 60
            diff
        } else {
            24 * 60
        }

        // Mismo punto de referencia que usa el resto de la app para calcular
        // horarios: la primera toma, el mismo día en que se creó el medicamento.
        val t0 = Calendar.getInstance().apply {
            timeInMillis = medication.startDate
            set(Calendar.HOUR_OF_DAY, anchor.timeHour)
            set(Calendar.MINUTE, anchor.timeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val intervalMillis = intervalMinutes * 60_000L
        val cyclesUntilEnd = ((medication.endDate - t0) / intervalMillis).coerceAtLeast(0)
        val lastDoseTime = t0 + cyclesUntilEnd * intervalMillis

        val dateTimeFormat = SimpleDateFormat("d 'de' MMMM, h:mm a", Locale.getDefault())
        return dateTimeFormat.format(Date(lastDoseTime))
    }

    fun playSound() {
        val med = _uiState.value.medication ?: return
        _uiState.update { it.copy(isPlayingSound = true) }
        if (!med.voiceNotePath.isNullOrBlank()) {
            audioVoiceManager.playAudioPreview(med.voiceNotePath) {
                _uiState.update { it.copy(isPlayingSound = false) }
            }
        } else {
            audioVoiceManager.playBuiltInSoundPreview(med.soundType) {
                _uiState.update { it.copy(isPlayingSound = false) }
            }
        }
    }

    fun stopSound() {
        audioVoiceManager.stopPlayback()
        _uiState.update { it.copy(isPlayingSound = false) }
    }

    fun deleteMedication() {
        val med = _uiState.value.medication ?: return
        viewModelScope.launch {
            // Cancelar las alarmas armadas antes de borrar; si no, un medicamento
            // eliminado podría seguir sonando una última vez en su próximo horario.
            val schedules = repository.getSchedulesForMedication(med.id)
            schedules.forEach { schedule ->
                alarmScheduler.cancelAlarmForHourMinute(med.id, schedule.timeHour, schedule.timeMinute)
            }
            repository.deleteMedication(med)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioVoiceManager.release()
    }
}
