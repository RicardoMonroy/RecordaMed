package com.example.recordamed.ui.screens.alarm

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.recordamed.MainActivity
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.service.AlarmSoundService
import com.example.recordamed.data.local.entities.DoseLogEntity
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {


    private var medicationId: Long = -1L
    private var medicationName: String = "Medicamento"
    private var dosage: String = "1 dosis"
    private var scheduledTime: Long = 0L
    private var voicePath: String? = null
    private var soundType: String = "BELLS"
    private var isSnoozeRetry: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Despertar y mantener la pantalla encendida sobre el bloqueo
        setupLockScreenFlags()

        // 2. Obtener datos del Intent
        medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medicamento"
        dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: "1 dosis"
        scheduledTime = intent.getLongExtra(EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
        voicePath = intent.getStringExtra(EXTRA_VOICE_PATH)
        soundType = intent.getStringExtra(EXTRA_SOUND_TYPE) ?: "BELLS"
        // Solo se permite posponer una vez: si esto ya es el timbrado de la
        // pospuesta, no se ofrece la opción de nuevo.
        isSnoozeRetry = intent.getBooleanExtra(EXTRA_IS_SNOOZE_RETRY, false)

        // 3. El sonido, la vibración y el temporizador de gracia los lleva
        // AlarmSoundService. Vivían aquí, y eso hacía que la alarma entera dependiera
        // de que esta actividad llegara a abrirse — cosa que no siempre ocurre.

        // 4. Encendido progresivo de pantalla (Luz gradual de 0.05 a 1.0 en 18 segundos)
        startGradualBrightnessIncrease()

        // 6. Interfaz de pantalla completa
        setContent {
            AlarmScreen(
                medicationName = medicationName,
                dosage = dosage,
                canSnooze = !isSnoozeRetry,
                onTakenClick = { handleTaken() },
                onSnoozeClick = { handleSnooze() }
            )
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startGradualBrightnessIncrease() {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 0.05f
        window.attributes = layoutParams

        val animator = ValueAnimator.ofFloat(0.05f, 1.0f).apply {
            duration = 18000 // 18 segundos para alcanzar brillo máximo
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                val params = window.attributes
                params.screenBrightness = value
                window.attributes = params
            }
        }
        animator.start()
    }

    /** Callar la alarma es ahora pedirle al servicio que se detenga. */
    private fun stopSensors() {
        AlarmSoundService.stop(this)
    }

    private fun handleTaken() {
        stopSensors()
        val app = application as RecordaMedApp
        lifecycleScope.launch {
            app.repository.markDoseTaken(medicationId, scheduledTime)
            returnToApp()
        }
    }

    private fun handleSnooze() {
        stopSensors()
        val app = application as RecordaMedApp
        lifecycleScope.launch {
            app.repository.snoozeDose(medicationId, scheduledTime)
            app.alarmScheduler.snoozeAlarm(
                medicationId = medicationId,
                medicationName = medicationName,
                dosage = dosage,
                originalScheduledTime = scheduledTime,
                voiceNotePath = voicePath,
                soundType = soundType
            )
            returnToApp()
        }
    }

    /**
     * AlarmActivity vive en su propia tarea (para poder abrirse desde un
     * BroadcastReceiver con la pantalla bloqueada), así que un simple
     * finish() no regresa a RecordaMed — deja al usuario en el launcher, con
     * la app en segundo plano tal como estaba antes de que sonara la alarma.
     * Eso es lo que se percibía como "la pantalla principal no se
     * actualiza": en realidad no volvía a mostrarse. Se trae MainActivity al
     * frente explícitamente para que la persona vea de una vez la tarjeta ya
     * actualizada.
     */
    private fun returnToApp() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(mainIntent)
        finish()
    }

    /** Nadie respondió en los 10 minutos que dura una pospuesta: se marca la toma como no realizada y se deja de sonar. */
    private fun handleMissed() {
        stopSensors()
        val app = application as RecordaMedApp
        lifecycleScope.launch {
            app.repository.markDoseMissed(medicationId, scheduledTime)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensors()
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

@Composable
fun AlarmScreen(
    medicationName: String,
    dosage: String,
    canSnooze: Boolean,
    onTakenClick: () -> Unit,
    onSnoozeClick: () -> Unit
) {
    // Fondo cálido y suave que acompaña la iluminación gradual
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1B241C)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // En horizontal (celular acostado o tablet), el alto disponible se
            // reduce mucho — se reparte el contenido en dos mitades una junto a
            // la otra en vez de apilarlo, para que nada quede cortado ni haya
            // que hacer scroll en la pantalla más importante de la app.
            if (maxWidth > maxHeight) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    AlarmHeader(
                        medicationName = medicationName,
                        dosage = dosage,
                        alignment = Alignment.Start,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f)
                    )
                    AlarmActions(
                        canSnooze = canSnooze,
                        onTakenClick = onTakenClick,
                        onSnoozeClick = onSnoozeClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    AlarmHeader(
                        medicationName = medicationName,
                        dosage = dosage,
                        alignment = Alignment.CenterHorizontally,
                        textAlign = TextAlign.Center
                    )
                    AlarmActions(
                        canSnooze = canSnooze,
                        onTakenClick = onTakenClick,
                        onSnoozeClick = onSnoozeClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmHeader(
    medicationName: String,
    dosage: String,
    alignment: Alignment.Horizontal,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(Color(0xFF2E7D32).copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Medication,
                contentDescription = "Medicina",
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF81C784)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "¡Es hora de tu medicina!",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE8F5E9),
            textAlign = textAlign
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = medicationName,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = textAlign
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = dosage,
            fontSize = 24.sp,
            color = Color(0xFFA5D6A7),
            textAlign = textAlign
        )
    }
}

/** Botones gigantes accesibles para adulto mayor. */
@Composable
private fun AlarmActions(
    canSnooze: Boolean,
    onTakenClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botón principal gigante: YA LA TOMÉ
        Button(
            onClick = onTakenClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(22.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "YA LA TOMÉ",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        // Posponer solo está disponible la primera vez que suena. Si esto ya
        // es la pospuesta y sigue sin respuesta, la próxima vez se marca
        // directo como no tomada — no hay una segunda pospuesta.
        if (canSnooze) {
            Spacer(modifier = Modifier.height(18.dp))

            OutlinedButton(
                onClick = onSnoozeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFF9C4))
            ) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFFFFF59D)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recordarme en ${DoseLogEntity.MISSED_GRACE_PERIOD_MINUTES} minutos",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFF59D)
                )
            }
        }
    }
}
