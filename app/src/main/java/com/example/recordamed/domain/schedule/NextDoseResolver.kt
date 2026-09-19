package com.example.recordamed.domain.schedule

import java.util.TimeZone

private const val ONE_MINUTE_MILLIS = 60L * 1000

/**
 * Única fuente de verdad sobre cuándo toca la siguiente dosis en el **esquema permisivo**.
 *
 * Existe por la misma razón que [DoseOccurrenceCalculator]: el instante que muestra la
 * tarjeta y el que se arma en `AlarmManager` tienen que salir del mismo sitio. Cuando el
 * planificador calculaba la hora permisiva por su cuenta y el repositorio seguía
 * recorriendo la cuadrícula de `dose_schedules` —inerte en este esquema—, la app llegó a
 * mostrar «Próxima: 5:00 p. m.» mientras la alarma real estaba puesta para las 7:00 de la
 * mañana siguiente: catorce horas de diferencia, con la persona viendo una cosa y el
 * teléfono haciendo otra.
 *
 * Todo lo que necesite saber cuándo toca una dosis permisiva debe pasar por aquí.
 */
object NextDoseResolver {

    /**
     * @param at instante de la siguiente toma.
     * @param wasDeferred la hora se corrió porque caía dentro de las horas de sueño.
     * @param resumedAfterMissed no hubo toma que registrar y el tratamiento se retomó al
     *   despertar. La interfaz lo distingue de [wasDeferred] porque el motivo que le da a
     *   la persona es distinto.
     */
    data class Result(
        val at: Long,
        val wasDeferred: Boolean = false,
        val resumedAfterMissed: Boolean = false,
    )

    /**
     * Siguiente dosis de un medicamento permisivo.
     *
     * @param lastTakenAt última toma realmente registrada, o `null` si aún no hay
     *   ninguna: en ese caso el tratamiento se ancla a la primera hora elegida al darlo
     *   de alta.
     */
    fun flexibleNextDose(
        medicationStartDate: Long,
        anchorMinuteOfDay: Int,
        intervalMinutes: Int,
        lastTakenAt: Long?,
        sleepWindow: SleepWindow,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Result? {
        if (intervalMinutes <= 0) return null

        if (lastTakenAt == null) {
            val primera = DoseOccurrenceCalculator.nextOccurrenceAfter(
                slotMinuteOfDay = anchorMinuteOfDay,
                anchorMinuteOfDay = anchorMinuteOfDay,
                medicationStartDate = medicationStartDate,
                now = now,
                timeZone = timeZone,
            )
            val ajustada = FlexibleDoseCalculator.deferIfAsleep(primera, sleepWindow, timeZone)
            return Result(at = ajustada, wasDeferred = ajustada != primera)
        }

        val crudo = lastTakenAt + intervalMinutes * ONE_MINUTE_MILLIS
        val ajustada = FlexibleDoseCalculator.deferIfAsleep(crudo, sleepWindow, timeZone)

        // La hora ya pasó: nadie registró la toma anterior y la cadena se rompió. Se
        // retoma al despertar siguiente, que es el ancla natural de este esquema.
        if (ajustada <= now) {
            return Result(
                at = FlexibleDoseCalculator.resumeAfterMissed(now, sleepWindow, timeZone),
                resumedAfterMissed = true,
            )
        }

        return Result(at = ajustada, wasDeferred = ajustada != crudo)
    }
}
