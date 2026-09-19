package com.example.recordamed.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.recordamed.domain.schedule.SleepWindow

/** Cuál de las dos horas se está editando en el diálogo. */
private enum class EditandoHora { NINGUNA, ACOSTARSE, DESPERTAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf(EditandoHora.NINGUNA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Tus horas de sueño",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )

            Text(
                text = "Los medicamentos que se toman «cada cierto tiempo» no te van a " +
                    "despertar: si una toma cae dentro de estas horas, se pasa a cuando " +
                    "despiertes. Los de horario fijo suenan igual, sin importar la hora.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.isSleepWindowAssumed) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Todavía no has puesto tus horas, así que estamos usando " +
                            "unas de ejemplo. Ajústalas para que se parezcan a las tuyas.",
                        fontSize = 15.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            HoraAjustable(
                etiqueta = "Me acuesto a las",
                minutoDelDia = uiState.sleepWindow.bedtimeMinuteOfDay,
                onClick = { editando = EditandoHora.ACOSTARSE },
            )

            HoraAjustable(
                etiqueta = "Me levanto a las",
                minutoDelDia = uiState.sleepWindow.wakeMinuteOfDay,
                onClick = { editando = EditandoHora.DESPERTAR },
            )

            if (uiState.sleepWindow.durationMinutes == 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Las dos horas son iguales, así que no hay franja de sueño: " +
                            "ninguna toma se pasará de hora.",
                        fontSize = 15.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tema oscuro", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Colores apagados, más cómodos de noche",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.isDarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) },
                )
            }
        }
    }

    if (editando != EditandoHora.NINGUNA) {
        val minutoActual = when (editando) {
            EditandoHora.ACOSTARSE -> uiState.sleepWindow.bedtimeMinuteOfDay
            else -> uiState.sleepWindow.wakeMinuteOfDay
        }
        SelectorDeHora(
            titulo = if (editando == EditandoHora.ACOSTARSE) {
                "¿A qué hora te acuestas?"
            } else {
                "¿A qué hora te levantas?"
            },
            horaInicial = minutoActual / 60,
            minutoInicial = minutoActual % 60,
            onCancelar = { editando = EditandoHora.NINGUNA },
            onAceptar = { hora, minuto ->
                if (editando == EditandoHora.ACOSTARSE) {
                    viewModel.updateBedtime(hora, minuto)
                } else {
                    viewModel.updateWakeTime(hora, minuto)
                }
                editando = EditandoHora.NINGUNA
            },
        )
    }
}

@Composable
private fun HoraAjustable(
    etiqueta: String,
    minutoDelDia: Int,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(etiqueta, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatearHora(minutoDelDia),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(onClick = onClick) {
                Text("Cambiar", fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDeHora(
    titulo: String,
    horaInicial: Int,
    minutoInicial: Int,
    onCancelar: () -> Unit,
    onAceptar: (Int, Int) -> Unit,
) {
    val estado = rememberTimePickerState(
        initialHour = horaInicial,
        initialMinute = minutoInicial,
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = { TimePicker(state = estado) },
        confirmButton = {
            TextButton(onClick = { onAceptar(estado.hour, estado.minute) }) {
                Text("Aceptar", fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar", fontSize = 17.sp)
            }
        },
    )
}

/** Formato de 12 horas, que es como lo lee la mayoría de la gente aquí. */
private fun formatearHora(minutoDelDia: Int): String {
    val hora24 = minutoDelDia / 60
    val minuto = minutoDelDia % 60
    val sufijo = if (hora24 < 12) "a. m." else "p. m."
    val hora12 = when {
        hora24 == 0 -> 12
        hora24 > 12 -> hora24 - 12
        else -> hora24
    }
    return "%d:%02d %s".format(hora12, minuto, sufijo)
}

/** Expuesto para que el resto de la app no tenga que repetir este formato. */
fun SleepWindow.descripcionLegible(): String =
    "de ${formatearHora(bedtimeMinuteOfDay)} a ${formatearHora(wakeMinuteOfDay)}"
