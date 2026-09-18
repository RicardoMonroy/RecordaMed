package com.example.recordamed.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.service.AlarmSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medicamento"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: "1 toma"
        val scheduledTime = intent.getLongExtra(EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
        val voicePath = intent.getStringExtra(EXTRA_VOICE_PATH)
        val soundType = intent.getStringExtra(EXTRA_SOUND_TYPE) ?: "BELLS"
        val isSnoozeRetry = intent.getBooleanExtra(EXTRA_IS_SNOOZE_RETRY, false)

        // Adquirir un WakeLock temporal para mantener la CPU despierta mientras
        // se arma la notificación y la alarma. No enciende la pantalla: de eso
        // se encargan el full-screen intent y el `turnScreenOn` de AlarmActivity.
        //
        // Aquí iba además `ACQUIRE_CAUSES_WAKEUP`, que solo surte efecto con los
        // niveles de WakeLock que encienden pantalla y por tanto no hacía nada
        // combinado con PARTIAL_WAKE_LOCK. Se midió en dispositivo: ni en API 30
        // ni en API 37 el encendido de pantalla provino de este WakeLock.
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RecordaMed:AlarmWakeLock"
        )
        wakeLock.acquire(30 * 1000L) // 30 segundos

        // Delegar en el servicio en primer plano: él publica la notificación con
        // full-screen intent, reproduce el sonido con USAGE_ALARM, vibra y arma el
        // temporizador de gracia.
        //
        // Antes esto se hacía en dos pasos desde aquí: publicar la notificación y
        // abrir AlarmActivity con context.startActivity(). Ese segundo paso lleva
        // bloqueado desde API 34 —verificado en un Pixel 10 con Android 17, que lo
        // rechaza con "Background activity launch blocked ... notPendingIntent"— y
        // además dejaba todo el aviso colgando de que la actividad arrancara. El
        // arranque de la pantalla queda ahora en manos del full-screen intent, que sí
        // es un PendingIntent y por tanto sí está exento de esa restricción.
        AlarmSoundService.start(
            context = context,
            medicationId = medicationId,
            medicationName = medicationName,
            dosage = dosage,
            scheduledTime = scheduledTime,
            voicePath = voicePath,
            soundType = soundType,
            isSnoozeRetry = isSnoozeRetry,
        )

        // Volver a armar el recordatorio para mañana a esta misma hora.
        // AlarmManager solo permite alarmas EXACTAS de un solo disparo: si no se
        // reprograma aquí, el medicamento deja de recordarse a partir del día
        // siguiente (solo BootReceiver reprograma, y eso solo ocurre si el
        // teléfono se reinicia). Antes de rearmar, se confirma que el medicamento
        // siga activo y, si es un tratamiento temporal, que no haya vencido.
        // Si este disparo es la pospuesta (no el original), no hace falta volver
        // a rearmar: el original ya dejó lista la ocurrencia de mañana.
        if (!isSnoozeRetry) {
            val pendingResult = goAsync()
            val app = context.applicationContext as RecordaMedApp
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val medication = app.repository.getMedicationById(medicationId)
                    val now = System.currentTimeMillis()
                    val stillValid = medication != null &&
                            medication.isActive &&
                            (!medication.isTemporary || medication.endDate == null || now < medication.endDate)

                    if (stillValid) {
                        // Re-arma todos los horarios de este medicamento desde el motor
                        // de dominio, en vez de reconstruir a mano la hora del disparo
                        // actual: así el instante coincide con el que calcula el
                        // repositorio y cancelar la alarma sí la encuentra.
                        app.alarmScheduler.rescheduleForMedication(app.repository, medicationId, now)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_VOICE_PATH = "extra_voice_path"
        const val EXTRA_SOUND_TYPE = "extra_sound_type"
        const val EXTRA_IS_SNOOZE_RETRY = "extra_is_snooze_retry"
    }
}
