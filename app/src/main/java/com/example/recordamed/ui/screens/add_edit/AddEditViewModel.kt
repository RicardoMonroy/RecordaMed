package com.example.recordamed.ui.screens.add_edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.data.local.entities.MedicationEntity
import com.example.recordamed.domain.schedule.ScheduleMode
import com.example.recordamed.service.AudioVoiceManager
import com.example.recordamed.service.BuiltInSoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AddEditUiState(
    val medicationId: Long = 0L,
    val name: String = "",
    val dosage: String = "",
    val instructions: String = "",
    val isTemporary: Boolean = false,
    /** Esquema de toma. Por defecto estricto: es el comportamiento histórico. */
    val scheduleMode: ScheduleMode = ScheduleMode.DEFAULT,
    val durationDays: Int = 7,
    val intervalHours: Int = 8,
    val intervalMinutes: Int = 0,
    val isCustomInterval: Boolean = false,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val formattedStartTime: String = "8:00 AM",
    val soundType: String = BuiltInSoundManager.SOUND_BELLS,
    val playingSoundId: String? = null,
    val voiceNotePath: String? = null,
    val isRecording: Boolean = false,
    val isPlayingVoicePreview: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val isEditMode: Boolean = false
)

class AddEditViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RecordaMedApp).repository
    private val alarmScheduler = (application as RecordaMedApp).alarmScheduler
    private val audioVoiceManager = AudioVoiceManager(application)

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val _uiState = MutableStateFlow(
        AddEditUiState(
            startHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            startMinute = Calendar.getInstance().get(Calendar.MINUTE),
            formattedStartTime = formatTime(
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().get(Calendar.MINUTE)
            )
        )
    )
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private fun formatTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return timeFormat.format(cal.time)
    }

    fun updateScheduleMode(mode: ScheduleMode) {
        _uiState.update { it.copy(scheduleMode = mode) }
    }

    fun loadMedication(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            val med = repository.getMedicationById(id) ?: return@launch
            // En el orden real de la secuencia (no por hora del día): con
            // horarios que cruzan la medianoche, la hora numéricamente menor
            // no es necesariamente la primera toma elegida.
            val schedules = repository.getSchedulesForMedicationInSequenceOrder(id)
            val firstSchedule = schedules.firstOrNull()

            val startH = firstSchedule?.timeHour ?: 8
            val startM = firstSchedule?.timeMinute ?: 0

            // El intervalo real es la distancia entre la primera y la
            // segunda toma de la secuencia — "24h / cantidad de tomas" solo
            // es correcto cuando el intervalo divide el día exacto (se
            // rompe, por ejemplo, con cada 15 minutos capado a 48 tomas).
            val (intervalH, intervalM) = if (schedules.size >= 2) {
                val a = schedules[0]
                val b = schedules[1]
                var diff = (b.timeHour * 60 + b.timeMinute) - (a.timeHour * 60 + a.timeMinute)
                if (diff <= 0) diff += 24 * 60
                (diff / 60) to (diff % 60)
            } else {
                24 to 0
            }
            val intervalHours = intervalH

            val isStandard = intervalM == 0 && intervalHours in listOf(4, 6, 8, 12, 24)

            _uiState.update {
                it.copy(
                    medicationId = med.id,
                    name = med.name,
                    dosage = med.dosage,
                    instructions = med.instructions,
                    isTemporary = med.isTemporary,
                    scheduleMode = ScheduleMode.fromStorage(med.scheduleMode),
                    soundType = med.soundType,
                    voiceNotePath = med.voiceNotePath,
                    intervalHours = intervalHours,
                    intervalMinutes = intervalM,
                    isCustomInterval = !isStandard,
                    startHour = startH,
                    startMinute = startM,
                    formattedStartTime = formatTime(startH, startM),
                    isEditMode = true
                )
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateDosage(dosage: String) = _uiState.update { it.copy(dosage = dosage) }
    fun updateInstructions(instructions: String) = _uiState.update { it.copy(instructions = instructions) }
    fun updateIsTemporary(isTemp: Boolean) = _uiState.update { it.copy(isTemporary = isTemp) }
    fun updateDurationDays(days: Int) = _uiState.update { it.copy(durationDays = days) }

    fun updateStartTime(hour: Int, minute: Int) {
        _uiState.update {
            it.copy(
                startHour = hour,
                startMinute = minute,
                formattedStartTime = formatTime(hour, minute)
            )
        }
    }

    // Presets rápidos (ej. 15m para lavados/pruebas, 30m, 4h para gotas, 6h, 8h, 12h, 24h)
    fun selectPreset(hours: Int, minutes: Int) {
        _uiState.update {
            it.copy(
                intervalHours = hours,
                intervalMinutes = minutes,
                isCustomInterval = false
            )
        }
    }

    fun selectCustomInterval() {
        _uiState.update { it.copy(isCustomInterval = true) }
    }

    // Controles +/- del intervalo personalizado: nunca abren el teclado, solo
    // suman o restan con cada pulsación (horas de 1 en 1, minutos de 5 en 5).
    fun incrementCustomHours() {
        _uiState.update { it.copy(intervalHours = (it.intervalHours + 1).coerceIn(0, 23)) }
    }

    fun decrementCustomHours() {
        _uiState.update { it.copy(intervalHours = (it.intervalHours - 1).coerceIn(0, 23)) }
    }

    fun incrementCustomMinutes() {
        _uiState.update { it.copy(intervalMinutes = (it.intervalMinutes + 5).coerceIn(0, 55)) }
    }

    fun decrementCustomMinutes() {
        _uiState.update { it.copy(intervalMinutes = (it.intervalMinutes - 5).coerceIn(0, 55)) }
    }

    fun updateSoundType(soundId: String) {
        _uiState.update { it.copy(soundType = soundId) }
    }

    fun playBuiltInSoundPreview(soundId: String) {
        audioVoiceManager.stopPlayback()
        _uiState.update { it.copy(playingSoundId = soundId, isPlayingVoicePreview = false) }
        audioVoiceManager.playBuiltInSoundPreview(soundId) {
            _uiState.update { it.copy(playingSoundId = null) }
        }
    }

    fun stopBuiltInSoundPreview() {
        audioVoiceManager.stopPlayback()
        _uiState.update { it.copy(playingSoundId = null) }
    }

    fun startRecordingVoice() {
        stopBuiltInSoundPreview()
        val success = audioVoiceManager.startRecording()
        if (success) {
            _uiState.update { it.copy(isRecording = true) }
        }
    }

    fun stopRecordingVoice() {
        val path = audioVoiceManager.stopRecording()
        _uiState.update {
            it.copy(
                isRecording = false,
                voiceNotePath = path ?: it.voiceNotePath
            )
        }
    }

    fun deleteVoiceNote() {
        audioVoiceManager.stopPlayback()
        _uiState.update { it.copy(voiceNotePath = null, isPlayingVoicePreview = false) }
    }

    fun playVoicePreview() {
        val path = _uiState.value.voiceNotePath ?: return
        stopBuiltInSoundPreview()
        _uiState.update { it.copy(isPlayingVoicePreview = true) }
        audioVoiceManager.playAudioPreview(path) {
            _uiState.update { it.copy(isPlayingVoicePreview = false) }
        }
    }

    fun stopVoicePreview() {
        audioVoiceManager.stopPlayback()
        _uiState.update { it.copy(isPlayingVoicePreview = false) }
    }

    fun saveMedication() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor ingresa el nombre del medicamento") }
            return
        }
        if (state.dosage.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor ingresa la dosis") }
            return
        }

        val totalIntervalMinutes = (state.intervalHours * 60) + state.intervalMinutes
        if (totalIntervalMinutes <= 0) {
            _uiState.update { it.copy(errorMessage = "El intervalo debe ser de al menos 1 minuto") }
            return
        }

        viewModelScope.launch {
            // Si estamos editando, cancelar primero las alarmas de los horarios
            // anteriores (p.ej. el médico cambió la hora o la frecuencia en una
            // consulta): si no se hace, el horario viejo seguiría sonando además
            // del nuevo.
            if (state.isEditMode) {
                alarmScheduler.cancelAllForMedication(repository, state.medicationId)
            }

            val now = System.currentTimeMillis()
            val endDate = if (state.isTemporary) {
                now + (state.durationDays.toLong() * 24 * 60 * 60 * 1000)
            } else null

            val medication = MedicationEntity(
                id = state.medicationId,
                name = state.name.trim(),
                dosage = state.dosage.trim(),
                instructions = state.instructions.trim(),
                isTemporary = state.isTemporary,
                startDate = now,
                endDate = endDate,
                voiceNotePath = state.voiceNotePath,
                soundType = state.soundType,
                isActive = true,
                scheduleMode = state.scheduleMode.name,
                // El intervalo se persiste desde esta versión. Antes se deducía restando
                // horarios consecutivos, cálculo repetido en tres sitios y que con un
                // solo horario no podía distinguir 24 h de cualquier otro valor.
                intervalMinutes = totalIntervalMinutes
            )

            // Generar los horarios del día a partir de startHour y startMinute
            val schedules = mutableListOf<Pair<Int, Int>>()
            val maxDosesPerDay = (1440 / totalIntervalMinutes).coerceIn(1, 48)

            var currentTotalMinutes = (state.startHour * 60) + state.startMinute
            for (i in 0 until maxDosesPerDay) {
                val hour = (currentTotalMinutes / 60) % 24
                val minute = currentTotalMinutes % 60
                schedules.add(hour to minute)
                currentTotalMinutes += totalIntervalMinutes
            }

            val medId = repository.saveMedicationWithSchedules(medication, schedules)

            // Todas las tomas —incluidos los intervalos cortos de prueba o de
            // lavados— se arman sobre la misma cuadrícula de horarios que se
            // acaba de guardar. Antes, un intervalo menor a 60 minutos se
            // armaba aparte con "ahora + intervalo", con un horario que no
            // coincidía con ninguna de las casillas guardadas — eso hacía que
            // el estado de "tomada/pendiente" de la tarjeta no cuadrara con la
            // alarma real que sonaba.
            //
            // El re-armado vive ahora en AlarmScheduler y calcula cada instante
            // con el mismo motor de dominio que usa el repositorio, de modo que
            // el timestamp de la alarma y el de la tarjeta son el mismo valor.
            alarmScheduler.rescheduleForMedication(repository, medId)

            _uiState.update { it.copy(isSaved = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioVoiceManager.release()
    }
}
