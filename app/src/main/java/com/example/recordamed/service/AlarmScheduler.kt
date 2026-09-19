package com.example.recordamed.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.data.local.entities.MedicationEntity
import com.example.recordamed.data.preferences.UserPreferences
import com.example.recordamed.data.repository.MedicationRepository
import com.example.recordamed.domain.schedule.DoseOccurrenceCalculator
import com.example.recordamed.domain.schedule.FlexibleDoseCalculator
import com.example.recordamed.domain.schedule.ScheduleMode
import com.example.recordamed.receiver.AlarmReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

/** Separa el espacio de códigos de las alarmas permisivas del de las estrictas. */
private const val FLEXIBLE_CODE_SALT = 0x5F1E_0000L

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val userPreferences by lazy { UserPreferences(context.applicationContext) }

    private val _exactAlarmsBlocked = MutableStateFlow(false)

    /**
     * Quedó alguna alarma sin programar porque el sistema negó el permiso de alarmas
     * exactas.
     *
     * Antes este caso solo se escribía en el log: la app seguía mostrando la próxima
     * toma con normalidad mientras ninguna alarma estaba realmente armada, y el usuario
     * se enteraba al no sonar. Exponerlo permite avisarlo en pantalla.
     */
    val exactAlarmsBlocked: StateFlow<Boolean> = _exactAlarmsBlocked.asStateFlow()

    /**
     * Desde Android 12 (API 31) las alarmas exactas requieren permiso. La app declara
     * `USE_EXACT_ALARM`, que el sistema concede en la instalación a las apps de alarma,
     * pero conviene comprobarlo en vez de darlo por hecho.
     */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /**
     * Re-arma las alarmas de un medicamento, según su esquema de toma.
     *
     * Es idempotente: los `PendingIntent` se crean con `FLAG_UPDATE_CURRENT`, así que
     * volver a llamarla reemplaza las alarmas existentes en lugar de duplicarlas.
     */
    suspend fun rescheduleForMedication(
        repository: MedicationRepository,
        medicationId: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        val medication = repository.getMedicationById(medicationId) ?: return
        if (!medication.isActive) return
        if (medication.isTemporary && medication.endDate != null && now >= medication.endDate) return

        when (ScheduleMode.fromStorage(medication.scheduleMode)) {
            ScheduleMode.STRICT -> rescheduleStrict(repository, medication, now)
            ScheduleMode.FLEXIBLE -> rescheduleFlexible(repository, medication, now)
        }
    }

    /**
     * Esquema estricto: una alarma por cada horario guardado, en horas de reloj fijas.
     *
     * El instante sale de [DoseOccurrenceCalculator], el mismo que usa el repositorio,
     * de modo que cancelar una alarma sí la encuentra.
     */
    private suspend fun rescheduleStrict(
        repository: MedicationRepository,
        medication: MedicationEntity,
        now: Long,
    ) {
        val schedules = repository.getSchedulesForMedicationInSequenceOrder(medication.id)
        val anchor = schedules.firstOrNull() ?: return
        val anchorMinute = DoseOccurrenceCalculator.minuteOfDay(anchor.timeHour, anchor.timeMinute)

        for (schedule in schedules) {
            val triggerTime = DoseOccurrenceCalculator.nextOccurrenceAfter(
                slotMinuteOfDay = DoseOccurrenceCalculator.minuteOfDay(schedule.timeHour, schedule.timeMinute),
                anchorMinuteOfDay = anchorMinute,
                medicationStartDate = medication.startDate,
                now = now,
            )
            scheduleAlarmAtTime(
                medicationId = medication.id,
                medicationName = medication.name,
                dosage = medication.dosage,
                triggerTime = triggerTime,
                voiceNotePath = medication.voiceNotePath,
                soundType = medication.soundType,
            )
        }
    }

    /**
     * Esquema permisivo: **una sola alarma viva**, la siguiente.
     *
     * No hay parrilla que recorrer porque la hora de la próxima toma no existe hasta que
     * se registra la anterior: se cuelga de cuándo la persona se la tomó de verdad. Si
     * aún no hay ninguna toma registrada, se parte de la hora de inicio elegida al dar de
     * alta el medicamento.
     */
    private suspend fun rescheduleFlexible(
        repository: MedicationRepository,
        medication: MedicationEntity,
        now: Long,
    ) {
        val intervalo = medication.intervalMinutes
        if (intervalo <= 0) {
            Log.w("AlarmScheduler", "Medicamento ${medication.id} es permisivo sin intervalo; no se arma nada")
            return
        }

        val sleepWindow = userPreferences.sleepWindow.first()
        val ultimaToma = repository.getLastTakenLog(medication.id)?.takenTime

        val triggerTime = if (ultimaToma != null) {
            FlexibleDoseCalculator.nextDoseAfterTaking(ultimaToma, intervalo, sleepWindow)
        } else {
            // Todavía no hay ninguna toma: se ancla a la primera hora elegida, corrida
            // si cayera dentro de las horas de sueño.
            val primera = repository.getSchedulesForMedicationInSequenceOrder(medication.id).firstOrNull()
                ?: return
            var candidato = DoseOccurrenceCalculator.nextOccurrenceAfter(
                slotMinuteOfDay = DoseOccurrenceCalculator.minuteOfDay(primera.timeHour, primera.timeMinute),
                anchorMinuteOfDay = DoseOccurrenceCalculator.minuteOfDay(primera.timeHour, primera.timeMinute),
                medicationStartDate = medication.startDate,
                now = now,
            )
            candidato = FlexibleDoseCalculator.deferIfAsleep(candidato, sleepWindow)
            candidato
        }

        // Nunca armar en el pasado.
        //
        // AlarmReceiver reprograma cada vez que suena una alarma. Si la toma no se
        // registra, el cálculo sigue partiendo de la última toma real —la vieja— y
        // devuelve el mismo instante, que para entonces ya pasó. Armar eso dispararía
        // de inmediato y otra vez, en bucle. Mientras la dosis siga sin registrarse no
        // hay siguiente hora que calcular: la habrá cuando la persona la marque.
        if (triggerTime <= now) {
            Log.d(
                "AlarmScheduler",
                "Medicamento ${medication.id}: la siguiente toma permisiva ya pasó; " +
                    "se espera a que se registre en vez de rearmar"
            )
            return
        }

        // Solo puede haber una alarma permisiva viva por medicamento, y su hora se
        // recalcula tras cada toma. Por eso se cancela la anterior antes de armar la
        // nueva: su código es estable por medicamento, no derivado del instante.
        cancelFlexibleAlarm(medication.id)
        scheduleAlarmAtTime(
            medicationId = medication.id,
            medicationName = medication.name,
            dosage = medication.dosage,
            triggerTime = triggerTime,
            voiceNotePath = medication.voiceNotePath,
            soundType = medication.soundType,
            requestCode = flexibleRequestCode(medication.id),
        )
    }

    /**
     * Re-arma las alarmas de todos los medicamentos activos.
     *
     * `BootReceiver` y `AlarmReceiver` tenían cada uno su propio bucle de re-armado, casi
     * iguales pero no idénticos. Ambos pasan ahora por aquí.
     */
    suspend fun rescheduleAll(
        repository: MedicationRepository,
        now: Long = System.currentTimeMillis(),
    ) {
        for (medication in repository.getActiveMedicationsSync()) {
            rescheduleForMedication(repository, medication.id, now)
        }
    }

    fun scheduleAlarmAtTime(
        medicationId: Long,
        medicationName: String,
        dosage: String,
        triggerTime: Long,
        voiceNotePath: String?,
        soundType: String = BuiltInSoundManager.SOUND_BELLS,
        // La hora "dueña" de la toma en la cuadrícula de horarios — normalmente
        // es la misma que triggerTime, salvo en una pospuesta, donde la alarma
        // debe sonar antes (triggerTime = ahora + unos minutos) pero seguir
        // representando la MISMA toma original para que el registro de
        // tomada/no tomada coincida con la tarjeta y el historial.
        originalScheduledTime: Long = triggerTime,
        isSnoozeRetry: Boolean = false,
        // Por defecto el identificador se deriva del instante, que es lo correcto en el
        // esquema estricto: cada hora de la parrilla es una alarma distinta. El permisivo
        // pasa el suyo, estable por medicamento.
        requestCode: Int = (medicationId xor triggerTime).toInt(),
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(AlarmReceiver.EXTRA_SCHEDULED_TIME, originalScheduledTime)
            putExtra(AlarmReceiver.EXTRA_VOICE_PATH, voiceNotePath)
            putExtra(AlarmReceiver.EXTRA_SOUND_TYPE, soundType)
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZE_RETRY, isSnoozeRetry)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Alarma programada para $medicationName a las $triggerTime")
            _exactAlarmsBlocked.value = false
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Permiso para alarmas exactas denegado: ${e.message}", e)
            _exactAlarmsBlocked.value = true
        }
    }

    fun snoozeAlarm(
        medicationId: Long,
        medicationName: String,
        dosage: String,
        originalScheduledTime: Long,
        voiceNotePath: String?,
        soundType: String = BuiltInSoundManager.SOUND_BELLS,
        snoozeMinutes: Int = DoseLogEntity.MISSED_GRACE_PERIOD_MINUTES
    ) {
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)
        scheduleAlarmAtTime(
            medicationId = medicationId,
            medicationName = medicationName,
            dosage = dosage,
            triggerTime = triggerTime,
            voiceNotePath = voiceNotePath,
            soundType = soundType,
            originalScheduledTime = originalScheduledTime,
            isSnoozeRetry = true
        )
    }

    fun cancelAlarm(medicationId: Long, scheduledTime: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = (medicationId xor scheduledTime).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /**
     * Identificador estable de la alarma permisiva de un medicamento.
     *
     * En el esquema permisivo la hora se recalcula tras cada toma, así que un código
     * derivado del instante dejaría alarmas huérfanas: al mover la hora, el código
     * cambiaría y la anterior quedaría armada sin forma de encontrarla. Como solo puede
     * haber una alarma viva por medicamento, basta con su id.
     *
     * El desplazamiento evita chocar con los códigos del esquema estricto, que salen de
     * `medicationId xor triggerTime`.
     */
    private fun flexibleRequestCode(medicationId: Long): Int =
        (medicationId xor FLEXIBLE_CODE_SALT).toInt()

    /** Cancela la única alarma permisiva viva de un medicamento, si la hay. */
    private fun cancelFlexibleAlarm(medicationId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            flexibleRequestCode(medicationId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /**
     * Cancela todas las alarmas armadas para un medicamento.
     *
     * Reemplaza al antiguo `cancelAlarmForHourMinute`, que adivinaba el timestamp
     * probando "hoy a esa hora" y "mañana a esa hora". Esa suposición dejó de ser
     * cierta al pasar el armado por [DoseOccurrenceCalculator]: el instante real se
     * ancla a la primera toma del medicamento y puede no caer en ninguna de esas dos
     * fechas, con lo que la cancelación no encontraba nada y quedaban alarmas fantasma.
     *
     * Solo hay dos candidatos posibles, y el calculador garantiza que sean esos: la
     * ocurrencia vigente del ciclo actual y la del siguiente. Se cancelan ambas;
     * cancelar un `PendingIntent` inexistente no tiene efecto (usa `FLAG_NO_CREATE`).
     *
     * Debe llamarse **antes** de guardar los cambios, para que los horarios y la fecha
     * de alta leídos aquí sigan siendo los que se usaron al armar las alarmas.
     *
     * Se usa al editar un medicamento (cambió el horario tras una visita al médico) y
     * al eliminarlo.
     */
    suspend fun cancelAllForMedication(
        repository: MedicationRepository,
        medicationId: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        val medication = repository.getMedicationById(medicationId) ?: return

        // El esquema permisivo tiene una sola alarma viva, con código estable: no hay
        // parrilla que recorrer. Se cancela siempre, incluso si el medicamento es
        // estricto ahora, por si cambió de esquema y quedó una alarma del anterior.
        cancelFlexibleAlarm(medicationId)

        val schedules = repository.getSchedulesForMedicationInSequenceOrder(medicationId)
        val anchor = schedules.firstOrNull() ?: return
        val anchorMinute = DoseOccurrenceCalculator.minuteOfDay(anchor.timeHour, anchor.timeMinute)

        for (schedule in schedules) {
            val current = DoseOccurrenceCalculator.currentOccurrence(
                slotMinuteOfDay = DoseOccurrenceCalculator.minuteOfDay(schedule.timeHour, schedule.timeMinute),
                anchorMinuteOfDay = anchorMinute,
                medicationStartDate = medication.startDate,
                now = now,
            )
            cancelAlarm(medicationId, current)
            cancelAlarm(medicationId, current + ONE_DAY_MILLIS)
        }
    }
}
