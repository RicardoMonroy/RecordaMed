package com.example.recordamed.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.domain.model.MedicationCardItem
import com.example.recordamed.domain.model.TodayDoseItem
import com.example.recordamed.service.BuiltInSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cuánto antes de la hora programada se permite adelantar el registro de una toma. */
private const val EARLY_REGISTER_WINDOW_MINUTES = 15
private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L

class HomeViewModel : ViewModel() {

    private val repository = RecordaMedApp.instance.repository
    private val alarmScheduler = RecordaMedApp.instance.alarmScheduler

    private val todayDosesFlow = repository.getTodayDoses()
    private val activeMedicationsFlow = repository.getActiveMedications()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Las cuentas regresivas y el estado habilitado/dormido del botón dependen
    // de la hora actual, no solo de la base de datos: sin este pulso, una
    // tarjeta se quedaría congelada con el texto/tiempo de cuando se abrió la
    // pantalla hasta que algo más disparara una recomposición.
    private val tickerFlow = flow {
        while (true) {
            emit(Unit)
            delay(30_000L)
        }
    }

    init {
        // Una toma que nadie registró y que ya pasó su plazo de gracia deja de
        // marcarse como "atrasada" para siempre: se resuelve como "no tomada".
        // Antes esto solo corría una vez, al crear la pantalla — un
        // medicamento nuevo creado DESPUÉS de esa primera vez (p.ej.
        // borrando y creando de nuevo sin salir de la app) nunca se volvía a
        // revisar, así que una primera toma ya vencida se quedaba pegada
        // como "atrasada" en vez de resolverse y pasar a la siguiente. Se
        // repite con el mismo pulso de 30s que ya refresca las tarjetas.
        viewModelScope.launch {
            tickerFlow.collect {
                repository.expireStaleDoses()
            }
        }
    }

    val medicationCards: StateFlow<List<MedicationCardItem>> = combine(
        activeMedicationsFlow,
        todayDosesFlow,
        tickerFlow
    ) { medications, todayDoses, _ ->
        val now = System.currentTimeMillis()
        val dosesByMed = todayDoses.groupBy { it.medicationId }

        medications.map { med ->
            val doses = dosesByMed[med.id] ?: emptyList()
            // Solo lo realmente accionable cuenta como "pendiente": una toma ya
            // resuelta como MISSED (caducó sin respuesta) no debe seguir
            // bloqueando la tarjeta ni pidiendo registrarla fuera de tiempo.
            val pendingDose = doses.firstOrNull {
                it.status == DoseLogEntity.STATUS_PENDING || it.status == DoseLogEntity.STATUS_SNOOZED
            }

            val soundOption = BuiltInSoundManager.SOUND_OPTIONS.find { it.id == med.soundType }
            val soundIcon = if (!med.voiceNotePath.isNullOrBlank()) "🎙️" else (soundOption?.icon ?: "🔔")

            if (pendingDose != null) {
                val diffMillis = pendingDose.scheduledTime - now
                val diffMinutes = (diffMillis / (60 * 1000)).toInt()

                val countdownText: String
                val isDueNow: Boolean
                val progressFraction: Float

                if (diffMinutes <= 0) {
                    countdownText = if (diffMinutes == 0) "¡Toca ahora!" else "Retrasada por ${-diffMinutes} min"
                    isDueNow = true
                    progressFraction = 1.0f
                } else {
                    countdownText = if (diffMinutes < 60) {
                        "En $diffMinutes min"
                    } else {
                        val hours = diffMinutes / 60
                        val mins = diffMinutes % 60
                        "En ${hours}h ${mins}m"
                    }
                    isDueNow = false
                    // Estimar progreso de la ventana de 8 horas / intervalo
                    val intervalWindow = 8 * 60 * 60 * 1000L
                    progressFraction = (1.0f - (diffMillis.toFloat() / intervalWindow)).coerceIn(0.05f, 0.95f)
                }

                MedicationCardItem(
                    medicationId = med.id,
                    name = med.name,
                    dosage = med.dosage,
                    instructions = med.instructions,
                    soundIcon = soundIcon,
                    isTemporary = med.isTemporary,
                    nextDoseScheduledTime = pendingDose.scheduledTime,
                    nextDoseFormattedTime = pendingDose.formattedTime,
                    countdownText = countdownText,
                    progressFraction = progressFraction,
                    isDueNow = isDueNow,
                    isAllTakenToday = false,
                    canRegisterNow = diffMinutes <= EARLY_REGISTER_WINDOW_MINUTES
                )
            } else if (doses.isNotEmpty() && doses.all { it.status == DoseLogEntity.STATUS_TAKEN }) {
                // Todas las dosis de hoy se tomaron de verdad — sí amerita el mensaje de logro.
                val lastDose = doses.lastOrNull()
                MedicationCardItem(
                    medicationId = med.id,
                    name = med.name,
                    dosage = med.dosage,
                    instructions = med.instructions,
                    soundIcon = soundIcon,
                    isTemporary = med.isTemporary,
                    nextDoseScheduledTime = null,
                    nextDoseFormattedTime = lastDose?.formattedTime ?: "",
                    countdownText = "Completado por hoy",
                    progressFraction = 1.0f,
                    isDueNow = false,
                    isAllTakenToday = true,
                    canRegisterNow = false
                )
            } else {
                // Ya no queda nada accionable en el ciclo actual (todo tomado
                // o ya caducado sin respuesta): se muestra la cuenta
                // regresiva hacia la primera toma del siguiente ciclo —
                // siempre 24h después de la primera toma (la más temprana)
                // de este mismo ciclo, nunca "la hora numéricamente más
                // chica" (que con horarios que cruzan la medianoche no es lo
                // mismo, y fue la causa de que se mostrara una hora que no
                // correspondía).
                if (doses.isNotEmpty()) {
                    val nextCycleTime = doses.minOf { it.scheduledTime } + ONE_DAY_MILLIS
                    val diffMinutes = ((nextCycleTime - now) / (60 * 1000)).toInt()
                    val countdownText = if (diffMinutes < 60) {
                        "Mañana, en $diffMinutes min"
                    } else {
                        "Mañana, en ${diffMinutes / 60}h ${diffMinutes % 60}m"
                    }

                    MedicationCardItem(
                        medicationId = med.id,
                        name = med.name,
                        dosage = med.dosage,
                        instructions = med.instructions,
                        soundIcon = soundIcon,
                        isTemporary = med.isTemporary,
                        nextDoseScheduledTime = nextCycleTime,
                        nextDoseFormattedTime = timeFormat.format(Date(nextCycleTime)),
                        countdownText = countdownText,
                        progressFraction = 0f,
                        isDueNow = false,
                        isAllTakenToday = false,
                        canRegisterNow = diffMinutes <= EARLY_REGISTER_WINDOW_MINUTES
                    )
                } else {
                    MedicationCardItem(
                        medicationId = med.id,
                        name = med.name,
                        dosage = med.dosage,
                        instructions = med.instructions,
                        soundIcon = soundIcon,
                        isTemporary = med.isTemporary,
                        nextDoseScheduledTime = null,
                        nextDoseFormattedTime = "",
                        countdownText = "Sin horarios configurados",
                        progressFraction = 0f,
                        isDueNow = false,
                        isAllTakenToday = false,
                        canRegisterNow = false
                    )
                }
            }
        }
            // De arriba hacia abajo: primero el medicamento cuya próxima toma
            // está más cerca (o ya venció), sin importar el orden en que se
            // hayan creado.
            .sortedWith(compareBy(nullsLast()) { it.nextDoseScheduledTime })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun markDoseTaken(card: MedicationCardItem) {
        val scheduledTime = card.nextDoseScheduledTime ?: return
        viewModelScope.launch {
            repository.markDoseTaken(card.medicationId, scheduledTime)
            alarmScheduler.cancelAlarm(card.medicationId, scheduledTime)
            // En el esquema permisivo la siguiente toma se cuelga de ésta, así que hay
            // que recalcularla ahora. En el estricto la llamada es inofensiva: vuelve a
            // armar la misma parrilla de horas fijas.
            alarmScheduler.rescheduleForMedication(repository, card.medicationId)
        }
    }
}
