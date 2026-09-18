package com.example.recordamed.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.data.local.entities.DoseLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que hace sonar la alarma.
 *
 * Antes el sonido, la vibración y el temporizador de "no respondida" vivían dentro de
 * [com.example.recordamed.ui.screens.alarm.AlarmActivity]. Eso hacía que **todo**
 * dependiera de que esa actividad llegara a abrirse, y se comprobó en un Pixel 10 con
 * Android 17 que hay situaciones en las que no se abre:
 *
 *  - Con No Molestar en silencio total, el sistema suprime el full-screen intent porque
 *    la política consolidada trae `allowPriorityChannels=false`.
 *  - El arranque directo de la actividad desde el receiver queda bloqueado por las
 *    restricciones de *background activity launch* de API 34+.
 *
 * Con las dos vías cerradas a la vez, la alarma disparaba puntualmente y al usuario no le
 * llegaba absolutamente nada: ni sonido, ni pantalla, ni aviso. Para una app cuyo único
 * trabajo es que alguien no olvide una toma, ese es el peor fallo posible, y encima
 * silencioso.
 *
 * Un servicio en primer plano no depende de ninguna actividad. El audio sale con
 * `USAGE_ALARM`, que está exento de No Molestar por defecto, así que en el peor caso la
 * alarma **suena** aunque la pantalla no se encienda. Que se vea es deseable; que suene
 * es lo que no puede fallar.
 *
 * El temporizador de gracia también se mudó aquí por la misma razón: si vivía en la
 * actividad y ésta no arrancaba, la toma se quedaba en PENDING indefinidamente.
 */
class AlarmSoundService : Service() {

    private var audioVoiceManager: AudioVoiceManager? = null
    private var vibrator: Vibrator? = null
    private var autoMissJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var medicationId: Long = -1L
    private var scheduledTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        medicationId = intent?.getLongExtra(EXTRA_MEDICATION_ID, -1L) ?: -1L
        val medicationName = intent?.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medicamento"
        val dosage = intent?.getStringExtra(EXTRA_DOSAGE) ?: "1 toma"
        scheduledTime = intent?.getLongExtra(EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val voicePath = intent?.getStringExtra(EXTRA_VOICE_PATH)
        val soundType = intent?.getStringExtra(EXTRA_SOUND_TYPE) ?: BuiltInSoundManager.SOUND_BELLS
        val isSnoozeRetry = intent?.getBooleanExtra(EXTRA_IS_SNOOZE_RETRY, false) ?: false

        val notification = NotificationHelper.buildAlarmNotification(
            context = this,
            medicationId = medicationId,
            medicationName = medicationName,
            dosage = dosage,
            scheduledTime = scheduledTime,
            isSnoozeRetry = isSnoozeRetry,
        ).setOngoing(true).build()

        try {
            ServiceCompat.startForeground(
                this,
                notificationId(medicationId, scheduledTime),
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Si el sistema rechaza el servicio en primer plano no hay que tirar la app:
            // la notificación con full-screen intent sigue siendo la otra vía.
            Log.e("AlarmSoundService", "No se pudo iniciar en primer plano: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        startSound(voicePath, soundType)
        startGentleVibration()
        startAutoMissTimer()

        return START_NOT_STICKY
    }

    private fun startSound(voicePath: String?, soundType: String) {
        audioVoiceManager?.stopPlayback()
        audioVoiceManager = AudioVoiceManager(this).also {
            it.playAlarmSound(voicePath, soundType, onLoop = true)
        }
    }

    private fun startGentleVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 600, 1200, 600, 1200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    /**
     * Si nadie responde en el periodo de gracia, la toma se marca como no realizada y se
     * deja de sonar, en vez de quedar sonando indefinidamente.
     */
    private fun startAutoMissTimer() {
        autoMissJob?.cancel()
        autoMissJob = scope.launch {
            delay(DoseLogEntity.MISSED_GRACE_PERIOD_MINUTES * 60 * 1000L)
            val app = applicationContext as? RecordaMedApp
            app?.repository?.markDoseMissed(medicationId, scheduledTime)
            stopEverything()
        }
    }

    private fun stopEverything() {
        autoMissJob?.cancel()
        audioVoiceManager?.stopPlayback()
        audioVoiceManager?.release()
        audioVoiceManager = null
        vibrator?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoMissJob?.cancel()
        audioVoiceManager?.stopPlayback()
        audioVoiceManager?.release()
        vibrator?.cancel()
        scope.cancel()
    }

    companion object {
        const val ACTION_STOP = "com.example.recordamed.action.STOP_ALARM"

        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_DOSAGE = "extra_dosage"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_VOICE_PATH = "extra_voice_path"
        const val EXTRA_SOUND_TYPE = "extra_sound_type"
        const val EXTRA_IS_SNOOZE_RETRY = "extra_is_snooze_retry"

        /** Mismo identificador que usa el resto de la app para esta toma. */
        fun notificationId(medicationId: Long, scheduledTime: Long): Int =
            (medicationId xor scheduledTime).toInt()

        fun start(
            context: Context,
            medicationId: Long,
            medicationName: String,
            dosage: String,
            scheduledTime: Long,
            voicePath: String?,
            soundType: String,
            isSnoozeRetry: Boolean,
        ) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                putExtra(EXTRA_MEDICATION_ID, medicationId)
                putExtra(EXTRA_MEDICATION_NAME, medicationName)
                putExtra(EXTRA_DOSAGE, dosage)
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                putExtra(EXTRA_VOICE_PATH, voicePath)
                putExtra(EXTRA_SOUND_TYPE, soundType)
                putExtra(EXTRA_IS_SNOOZE_RETRY, isSnoozeRetry)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // En API 31+ arrancar un servicio en primer plano desde segundo plano solo
                // se permite bajo ciertas exenciones. La de una alarma exacta aplica, pero
                // si el sistema la rechaza no debe caerse el receiver.
                Log.e("AlarmSoundService", "No se pudo arrancar el servicio: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("AlarmSoundService", "No se pudo detener el servicio: ${e.message}", e)
            }
        }
    }
}
