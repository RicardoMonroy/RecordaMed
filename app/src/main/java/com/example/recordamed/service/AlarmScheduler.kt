package com.example.recordamed.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.receiver.AlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleDoseAlarm(
        medicationId: Long,
        medicationName: String,
        dosage: String,
        hour: Int,
        minute: Int,
        voiceNotePath: String?,
        soundType: String = BuiltInSoundManager.SOUND_BELLS
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Si la hora ya pasó hoy, programar para mañana
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val triggerTime = calendar.timeInMillis
        scheduleAlarmAtTime(
            medicationId = medicationId,
            medicationName = medicationName,
            dosage = dosage,
            triggerTime = triggerTime,
            voiceNotePath = voiceNotePath,
            soundType = soundType
        )
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
        isSnoozeRetry: Boolean = false
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

        val requestCode = (medicationId xor triggerTime).toInt()
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
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Permiso para alarmas exactas denegado: ${e.message}", e)
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
     * Cancela la alarma armada para un horario diario (hora:minuto) de un medicamento,
     * sin necesidad de conocer el timestamp exacto con el que se programó originalmente.
     *
     * Como [scheduleDoseAlarm] siempre arma "la próxima ocurrencia" de esa hora —hoy si
     * aún no pasa, o mañana si ya pasó—, la alarma actualmente activa solo puede
     * corresponder a una de esas dos fechas. Se cancelan ambas por seguridad; cancelar
     * un PendingIntent que no existe no tiene efecto (usa FLAG_NO_CREATE).
     *
     * Se usa al editar un medicamento (el horario cambió, p.ej. tras una visita al
     * médico) o al eliminarlo, para no dejar recordatorios "fantasma" de horarios viejos.
     */
    fun cancelAlarmForHourMinute(medicationId: Long, hour: Int, minute: Int) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cancelAlarm(medicationId, today.timeInMillis)

        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        cancelAlarm(medicationId, tomorrow.timeInMillis)
    }
}
