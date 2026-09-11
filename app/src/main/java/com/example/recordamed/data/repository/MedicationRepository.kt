package com.example.recordamed.data.repository

import com.example.recordamed.data.local.dao.DoseLogDao
import com.example.recordamed.data.local.dao.DoseScheduleDao
import com.example.recordamed.data.local.dao.MedicationDao
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.data.local.entities.DoseScheduleEntity
import com.example.recordamed.data.local.entities.MedicationEntity
import com.example.recordamed.domain.model.DayAdherence
import com.example.recordamed.domain.model.TodayDoseItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val ONE_MINUTE_MILLIS = 60 * 1000L

/**
 * Calcula el timestamp real y vigente de una toma, anclado siempre a la
 * PRIMERA toma real del medicamento (sequenceIndex == 0), nunca a una
 * comparación directa de hora:minuto contra "hoy". Esto evita cualquier
 * ambigüedad cuando un horario frecuente cruza la medianoche: en vez de
 * preguntar "¿esta hora:minuto ya pasó hoy?" (que puede confundir una toma
 * de esta noche con una de esta mañana, mucho antes de crearse el
 * medicamento), se calcula su distancia real en minutos desde la primera
 * toma, y esa distancia se aplica sobre el ciclo de 24 horas vigente.
 */
private fun resolveCurrentOccurrence(
    schedule: DoseScheduleEntity,
    anchor: DoseScheduleEntity,
    medicationStartDate: Long,
    now: Long
): Long {
    val anchorMinutes = anchor.timeHour * 60 + anchor.timeMinute
    val slotMinutes = schedule.timeHour * 60 + schedule.timeMinute
    val offsetMinutes = (((slotMinutes - anchorMinutes) % 1440) + 1440) % 1440

    // T0: la primera toma, el mismo día calendario en que se creó el
    // medicamento, a la hora elegida — sin importar si esa hora ya había
    // pasado en el momento exacto de guardar. No hace falta empujarla al día
    // siguiente en ese caso: si guardas a las 8:16 p.m. con horario desde
    // las 6:00 p.m., la toma de las 6 ya pasó (se marcará atrasada/no
    // tomada, correctamente) pero las siguientes de HOY (10 p.m., etc.)
    // deben seguir viéndose hoy, no saltar directo a mañana a las 6.
    val t0 = Calendar.getInstance().apply {
        timeInMillis = medicationStartDate
        set(Calendar.HOUR_OF_DAY, anchor.timeHour)
        set(Calendar.MINUTE, anchor.timeMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ¿En qué ciclo de 24h (relativo a T0) cae "ahora"?
    val cyclesElapsed = if (now < t0) 0 else (now - t0) / ONE_DAY_MILLIS
    val cycleStart = t0 + cyclesElapsed * ONE_DAY_MILLIS

    return cycleStart + offsetMinutes * ONE_MINUTE_MILLIS
}

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val scheduleDao: DoseScheduleDao,
    private val doseLogDao: DoseLogDao
) {

    fun getActiveMedications(): Flow<List<MedicationEntity>> = medicationDao.getActiveMedications()

    suspend fun getMedicationById(id: Long): MedicationEntity? = medicationDao.getMedicationById(id)

    suspend fun getSchedulesForMedication(medicationId: Long): List<DoseScheduleEntity> {
        return scheduleDao.getSchedulesForMedicationSync(medicationId)
    }

    suspend fun getSchedulesForMedicationInSequenceOrder(medicationId: Long): List<DoseScheduleEntity> {
        return scheduleDao.getSchedulesForMedicationInSequenceOrder(medicationId)
    }

    suspend fun saveMedicationWithSchedules(
        medication: MedicationEntity,
        schedules: List<Pair<Int, Int>> // (hour, minute)
    ): Long {
        val medId = if (medication.id == 0L) {
            medicationDao.insertMedication(medication)
        } else {
            medicationDao.updateMedication(medication)
            medication.id
        }

        scheduleDao.deleteSchedulesForMedication(medId)
        // El orden de la lista ES el orden real de la secuencia (primera
        // toma, segunda...) tal como se generó — se guarda ese índice.
        val scheduleEntities = schedules.mapIndexed { index, (hour, minute) ->
            DoseScheduleEntity(
                medicationId = medId,
                timeHour = hour,
                timeMinute = minute,
                sequenceIndex = index
            )
        }
        scheduleDao.insertSchedules(scheduleEntities)
        return medId
    }

    suspend fun deleteMedication(medication: MedicationEntity) {
        scheduleDao.deleteSchedulesForMedication(medication.id)
        medicationDao.deleteMedication(medication)
    }

    fun getTodayDoses(): Flow<List<TodayDoseItem>> {
        // OJO: "ahora" y el resto de este cálculo deben quedar DENTRO del
        // combine — este Flow se construye una sola vez (al crear el
        // ViewModel) pero se vuelve a evaluar cada vez que hay un cambio en
        // la base de datos o pasa el tiempo. Si "ahora" se calculara aquí
        // afuera, quedaría congelado en el instante en que se abrió la
        // pantalla — y cualquier medicamento creado después (con
        // med.startDate posterior a ese "ahora" congelado) parecía "aún no
        // empezar", dejándolo sin ninguna toma para mostrar. Por el mismo
        // motivo se usa getAllLogs() en vez de acotar por rango de fecha: un
        // rango calculado una sola vez también se quedaría desactualizado.
        return combine(
            medicationDao.getActiveMedications(),
            scheduleDao.getAllSchedules(),
            doseLogDao.getAllLogs()
        ) { medications, allSchedules, logs ->
            val now = System.currentTimeMillis()
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val medMap = medications.associateBy { it.id }
            val logMap = logs.associateBy { "${it.medicationId}_${it.scheduledTime}" }
            val schedulesByMed = allSchedules.groupBy { it.medicationId }

            val items = mutableListOf<TodayDoseItem>()

            for (schedule in allSchedules) {
                val med = medMap[schedule.medicationId] ?: continue

                // Si es tratamiento temporal, verificar rango de fechas
                if (med.isTemporary) {
                    if (now < med.startDate || (med.endDate != null && now > med.endDate)) {
                        continue
                    }
                }

                val anchor = schedulesByMed[med.id]?.find { it.sequenceIndex == 0 } ?: schedule
                val scheduledTimestamp = resolveCurrentOccurrence(schedule, anchor, med.startDate, now)

                // Si la hora elegida ya había pasado al momento de crear el
                // medicamento (p.ej. "primera toma: 7pm" guardado a las
                // 8:23pm), esa toma puntual no se muestra ni se cuenta como
                // atrasada: lo más probable es que la persona ya la haya
                // tomado antes de configurar el recordatorio, y no hay forma
                // de saberlo — no se asume ni tomada ni no tomada, se
                // ignora, y la app apunta directo a la siguiente real.
                if (scheduledTimestamp < med.startDate) continue

                val doseCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                    set(Calendar.MINUTE, schedule.timeMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val log = logMap["${med.id}_$scheduledTimestamp"]
                val status = log?.status ?: DoseLogEntity.STATUS_PENDING

                items.add(
                    TodayDoseItem(
                        medicationId = med.id,
                        medicationName = med.name,
                        dosage = med.dosage,
                        instructions = med.instructions,
                        colorHex = med.colorHex,
                        iconType = med.iconType,
                        scheduledTime = scheduledTimestamp,
                        formattedTime = timeFormat.format(doseCal.time),
                        status = status,
                        voiceNotePath = med.voiceNotePath
                    )
                )
            }

            // Ordenar cronológicamente
            items.sortBy { it.scheduledTime }

            // Marcar la siguiente toma pendiente
            val nextIndex = items.indexOfFirst { it.status == DoseLogEntity.STATUS_PENDING && it.scheduledTime >= (now - 30 * 60 * 1000) }
            if (nextIndex != -1) {
                val nextItem = items[nextIndex]
                val diffMinutes = ((nextItem.scheduledTime - now) / (60 * 1000)).toInt()
                val countdown = when {
                    diffMinutes > 60 -> "En ${(diffMinutes / 60)} h ${(diffMinutes % 60)} min"
                    diffMinutes > 0 -> "En $diffMinutes minutos"
                    diffMinutes == 0 -> "¡Toca ahora!"
                    else -> "Retrasada por ${-diffMinutes} min"
                }
                items[nextIndex] = nextItem.copy(isNext = true, countdownText = countdown)
            }

            items
        }
    }

    suspend fun markDoseTaken(medicationId: Long, scheduledTime: Long) {
        val existingLog = doseLogDao.getLogForDose(medicationId, scheduledTime)
        val now = System.currentTimeMillis()
        if (existingLog == null) {
            doseLogDao.insertOrUpdateLog(
                DoseLogEntity(
                    medicationId = medicationId,
                    scheduledTime = scheduledTime,
                    takenTime = now,
                    status = DoseLogEntity.STATUS_TAKEN
                )
            )
        } else {
            doseLogDao.updateDoseStatus(
                medicationId = medicationId,
                scheduledTime = scheduledTime,
                status = DoseLogEntity.STATUS_TAKEN,
                takenTime = now
            )
        }
    }

    suspend fun snoozeDose(medicationId: Long, scheduledTime: Long) {
        val existingLog = doseLogDao.getLogForDose(medicationId, scheduledTime)
        if (existingLog == null) {
            doseLogDao.insertOrUpdateLog(
                DoseLogEntity(
                    medicationId = medicationId,
                    scheduledTime = scheduledTime,
                    status = DoseLogEntity.STATUS_SNOOZED
                )
            )
        } else {
            doseLogDao.updateDoseStatus(
                medicationId = medicationId,
                scheduledTime = scheduledTime,
                status = DoseLogEntity.STATUS_SNOOZED,
                takenTime = null
            )
        }
    }

    fun getAllLogs(): Flow<List<DoseLogEntity>> = doseLogDao.getAllLogs()

    suspend fun getRecentLogsForMedication(medicationId: Long, limit: Int = 60): List<DoseLogEntity> =
        doseLogDao.getRecentLogsForMedication(medicationId, limit)

    suspend fun markDoseMissed(medicationId: Long, scheduledTime: Long) {
        val existingLog = doseLogDao.getLogForDose(medicationId, scheduledTime)
        if (existingLog == null) {
            doseLogDao.insertOrUpdateLog(
                DoseLogEntity(
                    medicationId = medicationId,
                    scheduledTime = scheduledTime,
                    status = DoseLogEntity.STATUS_MISSED
                )
            )
        } else if (existingLog.status != DoseLogEntity.STATUS_TAKEN) {
            // No se sobreescribe si justo se acaba de registrar como tomada.
            doseLogDao.updateDoseStatus(
                medicationId = medicationId,
                scheduledTime = scheduledTime,
                status = DoseLogEntity.STATUS_MISSED,
                takenTime = null
            )
        }
    }

    /**
     * Recorre las tomas de HOY que ya pasaron su hora programada por más del
     * plazo de gracia y siguen sin resolverse (ni tomadas ni pospuestas a
     * tiempo), y las marca como "no tomada". Sin esto, una toma que nadie
     * registró se queda "atrasada" para siempre y bloquea la tarjeta —
     * obligando a registrar como tomada una toma que ya caducó, que es
     * justo lo que no queremos. Se llama al abrir la pantalla principal.
     */
    suspend fun expireStaleDoses() {
        val now = System.currentTimeMillis()
        val graceMillis = DoseLogEntity.MISSED_GRACE_PERIOD_MINUTES * 60 * 1000L
        val schedules = scheduleDao.getAllSchedulesSync()
        val schedulesByMed = schedules.groupBy { it.medicationId }

        for (schedule in schedules) {
            val med = medicationDao.getMedicationById(schedule.medicationId) ?: continue
            if (!med.isActive) continue

            // Igual que en getTodayDoses(): se ancla a la primera toma real
            // del medicamento en vez de comparar hora:minuto contra "hoy" —
            // así una casilla que cruza la medianoche nunca se confunde con
            // una toma de esta mañana, horas antes de crearse el medicamento.
            val anchor = schedulesByMed[med.id]?.find { it.sequenceIndex == 0 } ?: schedule
            val scheduledTimestamp = resolveCurrentOccurrence(schedule, anchor, med.startDate, now)

            // No se asume "no tomada" una toma anterior a que el medicamento
            // existiera — probablemente ya se tomó antes de configurar el
            // recordatorio (ver el mismo criterio en getTodayDoses()).
            if (scheduledTimestamp < med.startDate) continue

            if (scheduledTimestamp > now - graceMillis) continue // aún dentro del plazo de gracia
            if (med.isTemporary && med.endDate != null && scheduledTimestamp > med.endDate) continue

            val log = doseLogDao.getLogForDose(schedule.medicationId, scheduledTimestamp)
            when {
                log == null -> doseLogDao.insertOrUpdateLog(
                    DoseLogEntity(
                        medicationId = schedule.medicationId,
                        scheduledTime = scheduledTimestamp,
                        status = DoseLogEntity.STATUS_MISSED
                    )
                )
                log.status == DoseLogEntity.STATUS_PENDING || log.status == DoseLogEntity.STATUS_SNOOZED ->
                    doseLogDao.updateDoseStatus(
                        medicationId = schedule.medicationId,
                        scheduledTime = scheduledTimestamp,
                        status = DoseLogEntity.STATUS_MISSED,
                        takenTime = null
                    )
            }
        }
    }

    private fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Historial día por día de un medicamento para el mapa de calor: cuántas
     * tomas tocaban cada día y cuántas se registraron como tomadas / no
     * tomadas. Los días sin datos (antes de que el medicamento existiera, o
     * futuros) quedan con scheduledCount = 0.
     */
    suspend fun getAdherenceHeatmap(medicationId: Long, weeks: Int = 18): List<DayAdherence> {
        val medication = medicationDao.getMedicationById(medicationId) ?: return emptyList()
        val scheduledPerDay = scheduleDao.getSchedulesForMedicationSync(medicationId).size
        if (scheduledPerDay == 0) return emptyList()

        val todayStart = startOfDay(System.currentTimeMillis())
        val totalDays = weeks * 7
        val rangeStart = todayStart - (totalDays - 1) * ONE_DAY_MILLIS
        val rangeEndExclusive = todayStart + ONE_DAY_MILLIS

        val logs = doseLogDao.getLogsForMedicationBetween(medicationId, rangeStart, rangeEndExclusive - 1)
        val logsByDay = logs.groupBy { startOfDay(it.scheduledTime) }

        val medStart = startOfDay(medication.startDate)
        val medEnd = if (medication.isTemporary && medication.endDate != null) startOfDay(medication.endDate) else null

        return (0 until totalDays).map { i ->
            val day = rangeStart + i * ONE_DAY_MILLIS
            val withinLifetime = day >= medStart && (medEnd == null || day <= medEnd)
            if (!withinLifetime) {
                DayAdherence(day, scheduledCount = 0, takenCount = 0, missedCount = 0)
            } else {
                val dayLogs = logsByDay[day].orEmpty()
                val taken = dayLogs.count { it.status == DoseLogEntity.STATUS_TAKEN }
                val loggedMissed = dayLogs.count { it.status == DoseLogEntity.STATUS_MISSED }
                // Para días ya pasados, cualquier toma programada sin registro
                // también cuenta como no tomada (aunque el barrido automático
                // no haya llegado a escribirla explícitamente).
                val impliedMissed = if (day < todayStart) {
                    (scheduledPerDay - dayLogs.size).coerceAtLeast(0)
                } else 0
                DayAdherence(
                    dateMillis = day,
                    scheduledCount = scheduledPerDay,
                    takenCount = taken,
                    missedCount = loggedMissed + impliedMissed
                )
            }
        }
    }
}
