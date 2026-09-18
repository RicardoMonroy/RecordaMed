package com.example.recordamed.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.recordamed.MainActivity
import com.example.recordamed.R
import com.example.recordamed.ui.screens.alarm.AlarmActivity

object NotificationHelper {

    // Las propiedades de un NotificationChannel (importancia, sonido, bypassDnd)
    // quedan congeladas al crearlo: la app no puede modificarlas después, solo el
    // usuario desde los ajustes del sistema. El canal original se creó cuando
    // ACCESS_NOTIFICATION_POLICY aún no se declaraba, así que su setBypassDnd()
    // quedó inerte de forma permanente. La única salida es publicar en un canal
    // nuevo y borrar el viejo.
    const val CHANNEL_ID_ALARM = "recordamed_alarm_channel_v2"
    private const val CHANNEL_ID_ALARM_LEGACY = "recordamed_alarm_channel"
    private const val CHANNEL_NAME_ALARM = "Alarmas de Medicamentos"
    private const val CHANNEL_DESC_ALARM = "Notificaciones prioritarias para la toma de medicamentos"

    /**
     * ¿Puede la app saltarse No Molestar?
     *
     * `setBypassDnd(true)` solo surte efecto si el usuario concedió el acceso a la
     * política de notificaciones. Sin él Android no falla ni avisa: simplemente
     * ignora la bandera, y la alarma se queda muda de noche.
     */
    fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun createAlarmNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                ?.deleteNotificationChannel(CHANNEL_ID_ALARM_LEGACY)
            // El sonido y la vibración de la alarma los controla por completo
            // AudioVoiceManager/AlarmActivity (para poder usar la voz grabada, el
            // sonido elegido y una vibración suave). Si el canal de notificación
            // también trae su propio sonido/vibración por defecto, ambos suenan a
            // la vez — por eso aquí se dejan explícitamente apagados.
            val channel = NotificationChannel(
                CHANNEL_ID_ALARM,
                CHANNEL_NAME_ALARM,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_ALARM
                enableLights(true)
                enableVibration(false)
                setSound(null, null)
                setBypassDnd(true) // Permite sonar sobre No Molestar
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun buildAlarmNotification(
        context: Context,
        medicationId: Long,
        medicationName: String,
        dosage: String,
        scheduledTime: Long,
        isSnoozeRetry: Boolean = false
    ): NotificationCompat.Builder {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_MEDICATION_ID, medicationId)
            putExtra(AlarmActivity.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(AlarmActivity.EXTRA_DOSAGE, dosage)
            putExtra(AlarmActivity.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(AlarmActivity.EXTRA_IS_SNOOZE_RETRY, isSnoozeRetry)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (medicationId xor scheduledTime).toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hora de tomar: $medicationName")
            .setContentText("Dosis: $dosage. Toca para registrar tu toma.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
    }
}
