package com.example.recordamed.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordamed.data.local.entities.MedicationEntity
import com.example.recordamed.service.BuiltInSoundManager
import com.example.recordamed.ui.theme.RecordaMedExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        viewModel.loadMedication(medicationId)
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    val medication = uiState.medication

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar medicamento?") },
            text = { Text("Se cancelarán todas las alarmas programadas para este medicamento.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMedication()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(medication?.name ?: "Detalle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    if (medication != null) {
                        IconButton(onClick = { onNavigateToEdit(medication.id) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar medicamento",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (medication == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
          // Se limita y centra el ancho para que se lea bien también en tablets.
          BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val isWide = maxWidth > maxHeight
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Tarjeta resumen
                item {
                    val heroColors = RecordaMedExtras.colors.success
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = heroColors.container,
                            contentColor = heroColors.onContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(heroColors.accent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Medication,
                                        contentDescription = null,
                                        tint = heroColors.onAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = medication.name,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dosis: ${medication.dosage}",
                                        fontSize = 18.sp,
                                        color = heroColors.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (medication.instructions.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    color = LocalContentColor.current.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Indicación: ${medication.instructions}",
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Tipo y Duración  /  3. Sonido configurado / Voz
                // En horizontal (celular acostado o tablet) las dos tarjetas
                // caben lado a lado; en vertical se apilan como antes.
                if (isWide) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            TreatmentTypeCard(medication = medication, lastDoseText = uiState.lastDoseText, modifier = Modifier.weight(1f))
                            SoundInfoCard(
                                medication = medication,
                                isPlayingSound = uiState.isPlayingSound,
                                onPlayToggle = { if (uiState.isPlayingSound) viewModel.stopSound() else viewModel.playSound() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    item { TreatmentTypeCard(medication = medication, lastDoseText = uiState.lastDoseText, modifier = Modifier.fillMaxWidth()) }
                    item {
                        SoundInfoCard(
                            medication = medication,
                            isPlayingSound = uiState.isPlayingSound,
                            onPlayToggle = { if (uiState.isPlayingSound) viewModel.stopSound() else viewModel.playSound() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 4. Horarios programados — resumen compacto, nunca una lista
                // interminable (un medicamento muy frecuente puede tener
                // decenas de horarios al día).
                item {
                    Text(
                        text = "Horarios programados",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    val summary = uiState.scheduleSummary
                    if (summary != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${summary.count} ${if (summary.count == 1) "toma" else "tomas"} al día",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = summary.intervalText,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Desde ${summary.firstFormatted} hasta ${summary.lastFormatted}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (summary.compactTimes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        summary.compactTimes.forEach { timeText ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    text = timeText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Botón Editar
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onNavigateToEdit(medication.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EDITAR MEDICAMENTO", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun TreatmentTypeCard(medication: MedicationEntity, lastDoseText: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (medication.isTemporary) Icons.Default.Timer else Icons.Default.AllInclusive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = if (medication.isTemporary) "Tratamiento Temporal" else "Tratamiento Permanente",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (medication.isTemporary) {
                        "Última toma: ${lastDoseText ?: "sin definir"}"
                    } else {
                        "Continuo / Crónico"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SoundInfoCard(
    medication: MedicationEntity,
    isPlayingSound: Boolean,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val soundOption = BuiltInSoundManager.SOUND_OPTIONS.find { it.id == medication.soundType }
    val hasVoice = !medication.voiceNotePath.isNullOrBlank()
    val voiceColors = RecordaMedExtras.colors.voice

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = voiceColors.container,
            contentColor = voiceColors.onContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (hasVoice) "🎙️" else (soundOption?.icon ?: "🔔"), fontSize = 26.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (hasVoice) "Nota de voz familiar" else (soundOption?.title ?: "Alarma"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sonido que suena con la alarma",
                        fontSize = 13.sp,
                        color = LocalContentColor.current.copy(alpha = 0.75f)
                    )
                }
            }

            FilledTonalIconButton(onClick = onPlayToggle) {
                Icon(
                    imageVector = if (isPlayingSound) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = voiceColors.accent
                )
            }
        }
    }
}
