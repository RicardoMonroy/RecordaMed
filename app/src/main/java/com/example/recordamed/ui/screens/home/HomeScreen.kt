package com.example.recordamed.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordamed.domain.model.MedicationCardItem
import com.example.recordamed.ui.theme.RecordaMedExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddMedication: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val medicationCards by viewModel.medicationCards.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "RecordaMed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isDark by com.example.recordamed.ui.theme.ThemeManager.isDarkMode.collectAsState()

                    IconButton(onClick = { com.example.recordamed.ui.theme.ThemeManager.toggleTheme(context) }) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Cambiar modo claro/oscuro",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historial de tomas",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddMedication,
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp)) },
                text = { Text("Nuevo Medicamento", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    ) { innerPadding ->
        // El contenido se limita a un ancho cómodo de lectura y se centra en
        // pantallas muy anchas (tablet en horizontal, por ejemplo). La cuadrícula
        // de abajo decide cuántas columnas entran dentro de ese ancho.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (medicationCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 640.dp)
                        .padding(24.dp)
                ) {
                    EmptyMedicationsCard(onAddClick = onNavigateToAddMedication)
                }
            } else {
                LazyVerticalGrid(
                    // Cada tarjeta pide al menos 320dp: en un celular vertical eso da
                    // 1 columna, en horizontal o tablet vertical da 2, y en una tablet
                    // en horizontal o un celular muy ancho puede llegar a 3.
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 1100.dp)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(
                                text = "Tus Medicamentos",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "Toca cualquier tarjeta para ver todos los horarios o editar",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(medicationCards, key = { it.medicationId }) { card ->
                        MedicationVisualCountdownCard(
                            card = card,
                            onClick = { onNavigateToDetail(card.medicationId) },
                            onTakenClick = { viewModel.markDoseTaken(card) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationVisualCountdownCard(
    card: MedicationCardItem,
    onClick: () -> Unit,
    onTakenClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(targetValue = card.progressFraction, label = "countdownProgress")

    // Un toque accidental en "Ya la tomé" registraría una toma que nunca ocurrió;
    // se pide confirmar antes de guardarla (desde la pantalla de la alarma, en
    // cambio, no se pide nada extra: ahí ya es evidente que se está respondiendo
    // a una alarma que está sonando).
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if (card.isDueNow) "¿Ya tomaste tu medicina?" else "¿Adelantar esta toma?") },
            text = {
                Text(
                    if (card.isDueNow) {
                        "Se va a registrar ahora la toma de \"${card.name}\" (${card.dosage})."
                    } else {
                        "Todavía no era la hora programada (${card.nextDoseFormattedTime}). Se va a registrar de una vez la toma de \"${card.name}\" (${card.dosage})."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onTakenClick()
                }) {
                    Text(if (card.isDueNow) "Sí, ya la tomé" else "Sí, adelantar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Trío de colores (fondo / texto / acento) ya calibrado para el tema actual,
    // así la tarjeta nunca queda con letras ilegibles al cambiar claro/oscuro.
    val statusColors = when {
        card.isDueNow -> RecordaMedExtras.colors.due
        card.isAllTakenToday -> RecordaMedExtras.colors.success
        else -> null
    }
    val accentColor = statusColors?.accent ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColors?.container
                ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = statusColors?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (card.isDueNow) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Fila superior: Ícono de sonido/voz + Nombre + Flecha a detalle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(accentColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(card.soundIcon, fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = card.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Dosis: ${card.dosage}" + if (card.instructions.isNotBlank()) " • ${card.instructions}" else "",
                            fontSize = 15.sp,
                            color = LocalContentColor.current.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ver detalle",
                    tint = LocalContentColor.current.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cuenta regresiva visual
            if (!card.isAllTakenToday) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Próxima: ${card.nextDoseFormattedTime}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = card.countdownText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de progreso visual que muestra cuánto falta
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = LocalContentColor.current.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Botón de registrar la toma: dormido si falta mucho tiempo (evita
                // registros adelantados sin querer), "Adelantar toma" dentro de la
                // ventana de 15 minutos antes, y "¡Tomar ahora!" cuando ya toca.
                if (card.canRegisterNow) {
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = statusColors?.onAccent ?: MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (card.isDueNow) "¡TOMAR AHORA!" else "ADELANTAR TOMA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = LocalContentColor.current.copy(alpha = 0.10f),
                            disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                        )
                    ) {
                        Text("😴", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aún no toca",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                // Estado completado hoy
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "¡Todas las tomas de hoy completadas!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyMedicationsCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Medication,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sin medicamentos agregados",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Agrega tu primer medicamento para programar recordatorios, elegir sonido o grabar tu voz.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar Medicamento", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
