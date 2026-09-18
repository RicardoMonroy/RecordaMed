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

    // Las propiedades de un NotificationChannel (importancia, sonido, bypassDnd) quedan
    // congeladas al crearlo: la app no puede modificarlas después, solo el usuario desde
    // los ajustes del sistema.
    //
    // Eso obliga a versionar el id, y por partida doble. Android también descarta en
    // silencio `setBypassDnd(true)` si al crear el canal la app aún no tenía concedido
    // el acceso a la política de notificaciones — y el canal se crea en
    // Application.onCreate(), que en la primera ejecución corre siempre antes de que el
    // usuario haya concedido nada. Un único id quedaría atrapado para siempre sin
    // bypass, por mucho que el permiso llegue después.
    //
    // La salida es tener un id para cada estado y publicar en el que corresponda:
    // al conceder el acceso se crea el canal capaz de atravesar No Molestar.
    private const val CHANNEL_ID_BASIC = "recordamed_alarm_v3"
    private const val CHANNEL_ID_DND = "recordamed_alarm_dnd_v3"
    private val LEGACY_CHANNEL_IDS = listOf("recordamed_alarm_channel", "recordamed_alarm_channel_v2")

    private const val CHANNEL_NAME_ALARM = "Alarmas de Medicamentos"
    private const val CHANNEL_DESC_ALARM = "Notificaciones prioritarias para la toma de medicamentos"

    /**
     * ¿Puede la app saltarse No Molestar?
     *
     * `setBypassDnd(true)` solo surte efecto si el usuario concedió el acceso a la
     * política de notificaciones. Sin él Android no falla ni avisa: simplemente ignora la
     * bandera, y la alarma se queda muda de noche.
     */
    fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    /** Canal en el que debe publicarse, según el permiso disponible ahora mismo. */
    fun alarmChannelId(context: Context): String =
        if (hasDndAccess(context)) CHANNEL_ID_DND else CHANNEL_ID_BASIC

    /**
     * Crea el canal que corresponde al estado actual del permiso y retira el otro, para
     * que el usuario no vea entradas duplicadas en los ajustes del sistema.
     *
     * Es idempotente y barata, así que conviene llamarla también al volver a la app: es
     * lo que permite que el canal con bypass aparezca en cuanto se concede el acceso,
     * sin esperar a reiniciar la aplicación.
     */
    fun createAlarmNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        LEGACY_CHANNEL_IDS.forEach { notificationManager.deleteNotificationChannel(it) }

        val wantsDnd = hasDndAccess(context)
        val activeId = if (wantsDnd) CHANNEL_ID_DND else CHANNEL_ID_BASIC
        val obsoleteId = if (wantsDnd) CHANNEL_ID_BASIC else CHANNEL_ID_DND
        notificationManager.deleteNotificationChannel(obsoleteId)

        // El sonido y la vibración de la alarma los controla por completo
        // AudioVoiceManager/AlarmActivity (para poder usar la voz grabada, el sonido
        // elegido y una vibración suave). Si el canal también trajera los suyos por
        // defecto, ambos sonarían a la vez — por eso aquí se apagan explícitamente.
        val channel = NotificationChannel(
            activeId,
            CHANNEL_NAME_ALARM,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC_ALARM
            enableLights(true)
            enableVibration(false)
            setSound(null, null)
            if (wantsDnd) setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(channel)
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

        return NotificationCompat.Builder(context, alarmChannelId(context))
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
