package com.example.recordamed.domain.schedule

import java.util.Calendar
import java.util.TimeZone

private const val ONE_MINUTE_MILLIS = 60L * 1000

/**
 * Calcula cuándo toca la siguiente dosis en el **esquema permisivo**.
 *
 * Es un modelo distinto al de [DoseOccurrenceCalculator], no una variante. Allí las tomas
 * son horas de reloj absolutas, conocidas de antemano y materializadas en `dose_schedules`.
 * Aquí lo que manda es el **tiempo entre tomas**: la siguiente no existe hasta que se
 * registra la anterior, porque se cuelga de la hora en que la persona realmente se la
 * tomó, no de la hora a la que estaba planeada.
 *
 * Eso es deliberado. Si alguien debía tomarla a las 8:00 y se la toma a las 9:15, lo que
 * el médico indicó —"cada 8 horas"— se respeta contando desde las 9:15, no desde las 8:00.
 * Contar desde la hora planeada acortaría el intervalo real a 6 horas 45.
 *
 * La segunda regla es que **no se despierta a nadie de madrugada**: una toma que caiga
 * dentro de las horas de sueño se difiere hasta la hora de despertar. Nunca se adelanta ni
 * se omite. Adelantarla acortaría el intervalo que indicó el médico; omitirla perdería una
 * dosis sin avisar. Diferir puede dejar menos tomas ese día, y esa consecuencia debe ser
 * visible para la persona en lugar de decidirse en silencio.
 */
object FlexibleDoseCalculator {

    /**
     * Instante de la siguiente dosis a partir de la toma real anterior.
     *
     * @param lastTakenAt cuándo se registró realmente la toma anterior.
     * @param intervalMinutes minutos que deben pasar entre tomas.
     * @param sleepWindow horas de sueño de la persona.
     */
    fun nextDoseAfterTaking(
        lastTakenAt: Long,
        intervalMinutes: Int,
        sleepWindow: SleepWindow,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        require(intervalMinutes > 0) { "El intervalo debe ser mayor que cero" }
        val candidato = lastTakenAt + intervalMinutes * ONE_MINUTE_MILLIS
        return deferIfAsleep(candidato, sleepWindow, timeZone)
    }

    /**
     * Si [candidate] cae dentro de las horas de sueño, lo mueve a la hora de despertar más
     * próxima. Si no, lo devuelve intacto.
     *
     * Sirve también para la primera toma de un tratamiento: si la persona elige una hora
     * de inicio que cae de madrugada, se corre igualmente a cuando despierta.
     */
    fun deferIfAsleep(
        candidate: Long,
        sleepWindow: SleepWindow,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        if (!sleepWindow.contains(minuteOfDay(candidate, timeZone))) return candidate
        return nextWakeTimeAtOrAfter(candidate, sleepWindow, timeZone)
    }

    /**
     * ¿Se difirió esta toma respecto a lo que pedía el intervalo?
     *
     * La interfaz lo necesita para poder explicarlo —"se pasó a las 7:00 porque tocaba a
     * las 2:00"— en vez de que la hora cambie sin motivo aparente.
     */
    fun wasDeferred(
        lastTakenAt: Long,
        intervalMinutes: Int,
        sleepWindow: SleepWindow,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val candidato = lastTakenAt + intervalMinutes * ONE_MINUTE_MILLIS
        return deferIfAsleep(candidato, sleepWindow, timeZone) != candidato
    }

    /**
     * Cuándo se retoma el tratamiento después de una toma que no se realizó.
     *
     * La cadena del esquema permisivo se sostiene sobre la toma anterior, así que una
     * toma perdida la rompe: no hay hora real de la que colgar la siguiente. Se retoma a
     * la **hora de despertar siguiente**, que es el ancla natural de este esquema — el
     * día empieza con la primera toma de la mañana.
     *
     * Las alternativas se descartaron: seguir contando como si la toma se hubiera hecho
     * asume una dosis que no ocurrió, y reintentar al rato el mismo día insiste hasta
     * volverse molesto, que es justo lo que esta app quiere evitar.
     *
     * La consecuencia hay que asumirla con los ojos abiertos: si alguien se salta la
     * última toma de la tarde, no vuelve a sonar hasta la mañana siguiente.
     */
    fun resumeAfterMissed(
        missedAt: Long,
        sleepWindow: SleepWindow,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long = nextWakeTimeStrictlyAfter(missedAt, sleepWindow, timeZone)

    /** Minuto del día (0..1439) de un instante. */
    fun minuteOfDay(timestamp: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Primera hora de despertar que ocurre en [from] o después. */
    private fun nextWakeTimeAtOrAfter(
        from: Long,
        sleepWindow: SleepWindow,
        timeZone: TimeZone,
    ): Long = wakeTime(from, sleepWindow, timeZone, estricto = false)

    private fun nextWakeTimeStrictlyAfter(
        from: Long,
        sleepWindow: SleepWindow,
        timeZone: TimeZone,
    ): Long = wakeTime(from, sleepWindow, timeZone, estricto = true)

    /**
     * Hora de despertar más próxima a partir de [from].
     *
     * Se busca hacia delante en vez de calcular la diferencia de minutos porque la
     * ventana puede cruzar la medianoche: alguien que duerme de 22:00 a 7:00 y tiene una
     * toma a las 23:00 debe despertar a las 7:00 del día **siguiente**, no del mismo.
     */
    private fun wakeTime(
        from: Long,
        sleepWindow: SleepWindow,
        timeZone: TimeZone,
        estricto: Boolean,
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, sleepWindow.wakeMinuteOfDay / 60)
            set(Calendar.MINUTE, sleepWindow.wakeMinuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yaPaso = if (estricto) cal.timeInMillis <= from else cal.timeInMillis < from
        if (yaPaso) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
