package com.example.recordamed

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.recordamed.service.NotificationHelper
import com.example.recordamed.ui.navigation.RecordaMedNavGraph
import com.example.recordamed.ui.theme.RecordaMedTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkBatteryOptimization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solicitar permisos críticos
        requestNotificationPermission()

        setContent {
            val isDark by com.example.recordamed.ui.theme.ThemeManager.isDarkMode.collectAsState()
            RecordaMedTheme(darkTheme = isDark) {
                RecordaMedNavGraph()
            }
        }
    }

    /**
     * El canal de alarmas nace sin capacidad de atravesar No Molestar si al crearlo la
     * app aún no tenía el acceso a la política de notificaciones, y sus propiedades ya
     * no se pueden cambiar. Como el canal se crea en Application.onCreate() —siempre
     * antes de que el usuario conceda nada—, hay que reevaluarlo al volver a la app:
     * es lo que hace que el permiso recién concedido tenga efecto de inmediato en lugar
     * de a la siguiente vez que se abra.
     */
    override fun onResume() {
        super.onResume()
        NotificationHelper.createAlarmNotificationChannel(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Si el fabricante no soporta el diálogo directo, no bloquear la app
                }
            }
        }
        checkFullScreenIntentPermission()
    }

    /**
     * Desde Android 14 (API 34), el sistema puede negar el permiso de mostrar la
     * alarma a pantalla completa aunque esté declarado en el manifiesto — sin él,
     * la alerta de "hora de tu medicina" se reduce a una notificación normal y no
     * despierta la pantalla. Si está denegado, se lleva al usuario directo al
     * ajuste correspondiente para habilitarlo.
     */
    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Si el fabricante no soporta esta pantalla, no bloquear la app
                }
            }
        }
        checkDndAccess()
    }

    /**
     * El canal de alarmas pide `setBypassDnd(true)`, pero Android solo lo respeta si
     * el usuario concedió el acceso a la política de notificaciones. Sin él no hay
     * error ni aviso: la bandera se ignora y la alarma se queda muda bajo No Molestar
     * o "Hora de dormir" — justo de noche, que es cuando un medicamento de horario
     * estricto más necesita sonar.
     *
     * Es un ajuste especial: no se puede conceder desde un diálogo, hay que abrir la
     * pantalla del sistema. Si el usuario no lo concede la app sigue funcionando; solo
     * pierde la capacidad de atravesar No Molestar.
     */
    private fun checkDndAccess() {
        if (NotificationHelper.hasDndAccess(this)) return
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (e: Exception) {
            // Algunos fabricantes no exponen esta pantalla; no bloquear la app
        }
    }
}