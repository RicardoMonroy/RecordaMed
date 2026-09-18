package com.example.recordamed.domain.schedule

import java.util.Calendar
import java.util.TimeZone

const val MINUTES_PER_DAY: Int = 24 * 60

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
private const val ONE_MINUTE_MILLIS = 60L * 1000

/**
 * Única fuente de verdad sobre *cuándo* toca una dosis.
 *
 * Antes este cálculo vivía duplicado en dos sitios que no coincidían:
 * `MedicationRepository` anclaba cada toma a la primera del medicamento y
 * `AlarmScheduler` preguntaba por su cuenta "¿esta hora ya pasó hoy?". Como el
 * identificador de una alarma se deriva de su timestamp, bastaba con que los dos
 * cálculos cayeran en días distintos —cosa habitual con horarios que cruzan la
 * medianoche— para que cancelar una alarma no encontrara nada y ésta sonara aunque
 * la toma ya estuviera registrada.
 *
 * Todo el proyecto debe pasar por aquí. La regla que lo mantiene consistente es que
 * [nextOccurrenceAfter] se deriva siempre de [currentOccurrence], nunca de un cálculo
 * paralelo.
 *
 * **Limitación conocida:** los ciclos se cuentan en bloques fijos de 24 horas, así que
 * al cruzar un cambio de horario de verano la toma se corre una hora respecto a la hora
 * de reloj. Se conserva el comportamiento anterior a propósito; corregirlo es un cambio
 * aparte y con sus propias pruebas.
 */
object DoseOccurrenceCalculator {

    /**
     * Convierte hora y minuto a minutos desde la medianoche.
     */
    fun minuteOfDay(hour: Int, minute: Int): Int = hour * 60 + minute

    /**
     * Timestamp de la ocurrencia **vigente** de una toma: la del ciclo de 24 h en que
     * cae [now]. Puede quedar en el pasado, y debe poder quedarse ahí — así es como la
     * app distingue una toma atrasada de una futura.
     *
     * El cálculo se ancla siempre a la primera toma del medicamento
     * ([anchorMinuteOfDay], la de `sequenceIndex == 0`) en lugar de comparar hora:minuto
     * contra "hoy". Con horarios frecuentes que cruzan la medianoche, esa comparación
     * directa confunde la toma de esta noche con la de esta mañana; medir la distancia
     * en minutos desde el ancla no tiene esa ambigüedad.
     *
     * @param slotMinuteOfDay minuto del día de la toma que se quiere resolver.
     * @param anchorMinuteOfDay minuto del día de la primera toma de la secuencia.
     * @param medicationStartDate fecha de alta del medicamento; fija el día de T0.
     * @param now instante de referencia. Se recibe por parámetro para poder probarlo.
     */
    fun currentOccurrence(
        slotMinuteOfDay: Int,
        anchorMinuteOfDay: Int,
        medicationStartDate: Long,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val offsetMinutes = floorMod(slotMinuteOfDay - anchorMinuteOfDay, MINUTES_PER_DAY)

        // T0: la primera toma, el mismo día calendario en que se dio de alta el
        // medicamento, a la hora elegida — sin importar si esa hora ya había pasado al
        // guardar. No hay que empujarla al día siguiente en ese caso: si se guarda a las
        // 8:16 p.m. un horario que empieza a las 6:00 p.m., la toma de las 6 ya pasó (se
        // marcará atrasada, que es lo correcto) pero las siguientes de HOY deben seguir
        // viéndose hoy, no saltar directo a mañana.
        val t0 = Calendar.getInstance(timeZone).apply {
            timeInMillis = medicationStartDate
            set(Calendar.HOUR_OF_DAY, anchorMinuteOfDay / 60)
            set(Calendar.MINUTE, anchorMinuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cyclesElapsed = if (now < t0) 0L else (now - t0) / ONE_DAY_MILLIS
        val cycleStart = t0 + cyclesElapsed * ONE_DAY_MILLIS

        return cycleStart + offsetMinutes * ONE_MINUTE_MILLIS
    }

    /**
     * Timestamp de la próxima ocurrencia **estrictamente futura**: la que debe armarse
     * en `AlarmManager`.
     *
     * Se deriva de [currentOccurrence] a propósito. Es lo que garantiza que el timestamp
     * con el que se arma una alarma sea exactamente uno de los que el resto de la app
     * puede calcular, y por tanto que cancelarla funcione.
     */
    fun nextOccurrenceAfter(
        slotMinuteOfDay: Int,
        anchorMinuteOfDay: Int,
        medicationStartDate: Long,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val current = currentOccurrence(
            slotMinuteOfDay = slotMinuteOfDay,
            anchorMinuteOfDay = anchorMinuteOfDay,
            medicationStartDate = medicationStartDate,
            now = now,
            timeZone = timeZone,
        )
        return if (current > now) current else current + ONE_DAY_MILLIS
    }

    /** Módulo que siempre devuelve un resultado no negativo, a diferencia de `%`. */
    private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus
}
