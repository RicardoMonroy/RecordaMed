package com.example.recordamed.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.domain.model.DayAdherence
import com.example.recordamed.ui.theme.RecordaMedExtras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Tomas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.medications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aún no hay medicamentos para mostrar historial",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val selectedIndex = uiState.medications.indexOfFirst { it.id == uiState.selectedMedicationId }
                    .coerceAtLeast(0)

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    uiState.medications.forEachIndexed { index, med ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectMedication(med.id) },
                            text = { Text(med.name, fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 1100.dp)
                            .fillMaxSize()
                    ) {
                        if (maxWidth > maxHeight) {
                            // Horizontal: el mapa de calor a la izquierda y el
                            // historial reciente a la derecha, cada uno con su
                            // propio scroll — se aprovechan las dos mitades del
                            // ancho en vez de apilar todo en una sola columna larga.
                            Row(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                HeatmapCard(
                                    heatmap = uiState.heatmap,
                                    modifier = Modifier.weight(1f)
                                )
                                RecentLogsList(
                                    logs = uiState.recentLogs,
                                    dateFormat = dateFormat,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item { HeatmapCard(heatmap = uiState.heatmap, modifier = Modifier.fillMaxWidth()) }

                                item {
                                    Text(
                                        text = "Historial reciente",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }

                                if (uiState.recentLogs.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Todavía no hay tomas registradas para este medicamento.",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    items(uiState.recentLogs, key = { it.id }) { log ->
                                        HistoryLogCard(log = log, dateFormat = dateFormat)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(heatmap: List<DayAdherence>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Últimos ${heatmap.size / 7} semanas",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            AdherenceHeatmap(days = heatmap)
            Spacer(modifier = Modifier.height(12.dp))
            HeatmapLegend()
        }
    }
}

@Composable
private fun RecentLogsList(
    logs: List<DoseLogEntity>,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Historial reciente",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    text = "Todavía no hay tomas registradas para este medicamento.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(logs, key = { it.id }) { log ->
                HistoryLogCard(log = log, dateFormat = dateFormat)
            }
        }
    }
}

/**
 * Mapa de calor estilo "calendario de contribuciones": una columna por semana,
 * una casilla por día, coloreada según cuántas tomas de ese día se
 * registraron. Verde = todas tomadas, rojo = ninguna, ámbar = mezcla, y un
 * tono neutro para días sin datos (antes de crear el medicamento, o el resto
 * de hoy que aún no toca).
 */
@Composable
private fun AdherenceHeatmap(days: List<DayAdherence>) {
    if (days.isEmpty()) {
        Text(
            text = "Aún no hay datos para este medicamento.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val extras = RecordaMedExtras.colors
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val cellSize = 14.dp
    val cellGap = 3.dp

    val firstDayOfWeek = remember(days) {
        Calendar.getInstance().apply { timeInMillis = days.first().dateMillis }.get(Calendar.DAY_OF_WEEK) - 1
    }
    val weeks = remember(days, firstDayOfWeek) {
        val leadingPad: List<DayAdherence?> = List(firstDayOfWeek) { null }
        val combined = leadingPad + days
        val trailingPad = (7 - combined.size % 7) % 7
        (combined + List(trailingPad) { null }).chunked(7)
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(cellGap)
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                week.forEach { day ->
                    val resolved = (day?.takenCount ?: 0) + (day?.missedCount ?: 0)
                    val color = when {
                        day == null -> Color.Transparent
                        !day.hasData || resolved == 0 -> neutralColor
                        day.missedCount == 0 -> extras.success.accent
                        day.takenCount == 0 -> extras.missed.accent
                        else -> extras.due.accent
                    }
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapLegend() {
    val extras = RecordaMedExtras.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        LegendDot(color = extras.success.accent, label = "Tomadas")
        LegendDot(color = extras.due.accent, label = "Parcial")
        LegendDot(color = extras.missed.accent, label = "No tomadas")
        LegendDot(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f), label = "Sin datos")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Cada registro se colorea entero (verde = tomada, rojo = no tomada, ámbar =
 * pospuesta) en vez de llevar una etiqueta aparte — el color y el ícono ya
 * dicen el estado de un vistazo.
 */
@Composable
fun HistoryLogCard(
    log: DoseLogEntity,
    dateFormat: SimpleDateFormat
) {
    val extras = RecordaMedExtras.colors
    val (statusColor, statusText, statusIcon) = when (log.status) {
        DoseLogEntity.STATUS_TAKEN -> Triple(extras.success, "Tomada", Icons.Default.Check)
        DoseLogEntity.STATUS_MISSED -> Triple(extras.missed, "No tomada", Icons.Default.Close)
        DoseLogEntity.STATUS_SNOOZED -> Triple(extras.due, "Pospuesta", Icons.Default.Snooze)
        else -> Triple(extras.due, "Pendiente", Icons.Default.AccessTime)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.container,
            contentColor = statusColor.onContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(statusColor.accent.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = statusColor.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Programada: ${dateFormat.format(Date(log.scheduledTime))}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (log.takenTime != null) {
                        "Registrada: ${dateFormat.format(Date(log.takenTime))}"
                    } else {
                        statusText
                    },
                    fontSize = 14.sp,
                    color = LocalContentColor.current.copy(alpha = 0.8f)
                )
            }
        }
    }
}
