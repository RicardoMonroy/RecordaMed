package com.example.recordamed.ui.screens.add_edit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordamed.service.BuiltInSoundManager
import com.example.recordamed.ui.theme.RecordaMedExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScreen(
    medicationId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        if (medicationId != null && medicationId > 0) {
            viewModel.loadMedication(medicationId)
        }
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startRecordingVoice()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    // Diálogo con Reloj Gráfico Material 3
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.startHour,
            initialMinute = uiState.startMinute,
            is24Hour = false
        )

        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
                    .background(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Selecciona la hora de inicio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateStartTime(timePickerState.hour, timePickerState.minute)
                                showTimePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Aceptar")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) "Editar Medicamento" else "Nuevo Medicamento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { innerPadding ->
      // Se limita y centra el ancho del formulario para que se lea bien en tablets.
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Nombre del medicamento
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Nombre del medicamento", fontSize = 17.sp) },
                placeholder = { Text("Ej. Gotas para los ojos, Losartán, Lavado...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                singleLine = true
            )

            // 2. Dosis
            OutlinedTextField(
                value = uiState.dosage,
                onValueChange = { viewModel.updateDosage(it) },
                label = { Text("Dosis o Cantidad", fontSize = 16.sp) },
                placeholder = { Text("Ej. 2 gotas, 1 tableta, 10 ml...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 17.sp),
                singleLine = true
            )

            // 3. Indicaciones ampliadas (Campo cómodo y espacioso para texto largo)
            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = { viewModel.updateInstructions(it) },
                label = { Text("Indicaciones médicas (Opcional)", fontSize = 16.sp) },
                placeholder = { Text("Ej. Aplicar en ojo derecho después de limpiar. Tomar con suficiente agua después de los alimentos...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
            )

            HorizontalDivider()

            // 4. Duración del tratamiento
            Text(
                text = "Duración del tratamiento",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TreatmentTypeOption(
                    title = "Permanente",
                    subtitle = "Indefinido / Crónico",
                    isSelected = !uiState.isTemporary,
                    onClick = { viewModel.updateIsTemporary(false) },
                    modifier = Modifier.weight(1f)
                )

                TreatmentTypeOption(
                    title = "Temporal",
                    subtitle = "Por unos días",
                    isSelected = uiState.isTemporary,
                    onClick = { viewModel.updateIsTemporary(true) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (uiState.isTemporary) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Duración en días:", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (uiState.durationDays > 1) viewModel.updateDurationDays(uiState.durationDays - 1)
                                }
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Menos")
                            }
                            Text(
                                "${uiState.durationDays} días",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { viewModel.updateDurationDays(uiState.durationDays + 1) }
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Más")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 5. Horario de inicio con Reloj Gráfico
            Text(
                text = "Hora de la primera toma",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Primera toma a las:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.formattedStartTime,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Button(
                        onClick = { showTimePicker = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar")
                    }
                }
            }

            // 6. Frecuencia e Intervalo (Presets + Minutos + Personalizado)
            Text(
                text = "¿Cada cuánto tiempo se toma?",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            // Chips compactos organizados: en pantallas angostas se acomodan en
            // varias filas cortas; en tablet u horizontal, la misma fila
            // aprovecha el ancho y caben más chips por línea.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val is15m = !uiState.isCustomInterval && uiState.intervalHours == 0 && uiState.intervalMinutes == 15
                    FilterChip(
                        selected = is15m,
                        onClick = { viewModel.selectPreset(0, 15) },
                        label = { Text("⚡ 15 min", fontSize = 14.sp, fontWeight = if (is15m) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp)
                    )

                    val is30m = !uiState.isCustomInterval && uiState.intervalHours == 0 && uiState.intervalMinutes == 30
                    FilterChip(
                        selected = is30m,
                        onClick = { viewModel.selectPreset(0, 30) },
                        label = { Text("⚡ 30 min", fontSize = 14.sp, fontWeight = if (is30m) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp)
                    )

                    val is4h = !uiState.isCustomInterval && uiState.intervalHours == 4 && uiState.intervalMinutes == 0
                    FilterChip(
                        selected = is4h,
                        onClick = { viewModel.selectPreset(4, 0) },
                        label = { Text("C/4 hrs", fontSize = 14.sp, fontWeight = if (is4h) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp)
                    )

                    listOf(6, 8, 12, 24).forEach { hours ->
                        val isSelected = !uiState.isCustomInterval && uiState.intervalHours == hours && uiState.intervalMinutes == 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPreset(hours, 0) },
                            label = {
                                Text(
                                    text = if (hours == 24) "1 al día" else "C/$hours hrs",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    FilterChip(
                        selected = uiState.isCustomInterval,
                        onClick = { viewModel.selectCustomInterval() },
                        label = { Text("Personalizado (Horas y Minutos)...", fontSize = 14.sp) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                if (uiState.isCustomInterval) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            IntervalStepper(
                                label = "Horas",
                                value = uiState.intervalHours,
                                onDecrement = { viewModel.decrementCustomHours() },
                                onIncrement = { viewModel.incrementCustomHours() }
                            )

                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                            IntervalStepper(
                                label = "Minutos",
                                value = uiState.intervalMinutes,
                                onDecrement = { viewModel.decrementCustomMinutes() },
                                onIncrement = { viewModel.incrementCustomMinutes() }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 7. Catálogo de 3 Sonidos Relajantes
            Text(
                text = "Sonido para la alarma",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            // En la alarma real solo suena una cosa: si hay una nota de voz
            // grabada, ella tiene prioridad y el sonido de abajo no se
            // reproduce — nunca suenan los dos a la vez.
            if (uiState.voiceNotePath != null) {
                Text(
                    text = "Ahora mismo va a sonar tu nota de voz grabada, no este sonido — bórrala más abajo si prefieres usar uno de estos.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // En pantallas angostas cada sonido ocupa su propia fila; en
            // tablet u horizontal, dos tarjetas caben lado a lado.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val selectedColors = RecordaMedExtras.colors.success
                BuiltInSoundManager.SOUND_OPTIONS.forEach { sound ->
                    val isSelected = uiState.soundType == sound.id
                    val isPlaying = uiState.playingSoundId == sound.id

                    Card(
                        modifier = Modifier
                            .widthIn(min = 260.dp)
                            .weight(1f)
                            .clickable { viewModel.updateSoundType(sound.id) }
                            .then(
                                if (isSelected) Modifier.border(2.dp, selectedColors.accent, RoundedCornerShape(16.dp))
                                else Modifier
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) selectedColors.container else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) selectedColors.onContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(sound.icon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = sound.title,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = sound.description,
                                        fontSize = 12.sp,
                                        color = LocalContentColor.current.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (isPlaying) viewModel.stopBuiltInSoundPreview()
                                    else viewModel.playBuiltInSoundPreview(sound.id)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Escuchar prueba",
                                    tint = if (isSelected) selectedColors.accent else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 8. Grabación de Voz Familiar
            val voiceColors = RecordaMedExtras.colors.voice
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = voiceColors.container,
                    contentColor = voiceColors.onContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = voiceColors.accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Opcional: Grabar voz de familiar",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Si grabas tu voz, sonará ella sola en la alarma — no el sonido de arriba.",
                        fontSize = 13.sp,
                        color = LocalContentColor.current.copy(alpha = 0.75f)
                    )

                    if (uiState.voiceNotePath == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = LocalContentColor.current.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "¿Qué decir? Un par de ideas:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "🗣️ En primera persona: \"Ya es hora de tomar mi pastilla para la presión, con un vaso de agua.\"",
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "👨‍👩‍👧 De un familiar: \"Hola mamá, es hora de tu medicina del corazón. Tómala con calma, te quiero mucho.\"",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.voiceNotePath == null) {
                        Button(
                            onClick = {
                                if (uiState.isRecording) {
                                    viewModel.stopRecordingVoice()
                                } else {
                                    if (hasAudioPermission) {
                                        viewModel.startRecordingVoice()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else voiceColors.accent,
                                contentColor = if (uiState.isRecording) MaterialTheme.colorScheme.onError else voiceColors.onAccent
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isRecording) "Detener grabación" else "Tocar para grabar voz",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (uiState.isPlayingVoicePreview) {
                                        viewModel.stopVoicePreview()
                                    } else {
                                        viewModel.playVoicePreview()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlayingVoicePreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isPlayingVoicePreview) "Detener" else "Escuchar voz")
                            }

                            OutlinedButton(
                                onClick = { viewModel.deleteVoiceNote() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Borrar")
                            }
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 9. Botón Guardar
            Button(
                onClick = { viewModel.saveMedication() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (uiState.isEditMode) "ACTUALIZAR MEDICAMENTO" else "GUARDAR MEDICAMENTO",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
      }
    }
}

/**
 * Selector de horas o minutos con botones +/- únicamente: el usuario solo
 * pulsa, nunca se abre el teclado.
 */
@Composable
fun IntervalStepper(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Menos $label")
            }
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 34.dp)
                    .padding(horizontal = 4.dp)
            )
            IconButton(onClick = onIncrement) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Más $label")
            }
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TreatmentTypeOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColors = RecordaMedExtras.colors.success

    Card(
        modifier = modifier
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, selectedColors.accent, RoundedCornerShape(14.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) selectedColors.container else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) selectedColors.onContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) selectedColors.onContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = if (isSelected) selectedColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
