package com.example.recordamed.domain.schedule

/**
 * Las horas de sueño de la persona, expresadas en minutos desde la medianoche.
 *
 * Sirve al esquema permisivo: cuando una toma cae dentro de esta franja se difiere hasta
 * la hora de despertar, en lugar de sonar de madrugada. Los médicos suelen recomendar no
 * interrumpir el descanso, y lo que manda en ese esquema es el tiempo entre tomas, no la
 * hora de reloj.
 *
 * Es una sola ventana para la persona, no una por medicamento: alguien tiene un único
 * horario de sueño.
 *
 * @param bedtimeMinuteOfDay hora de acostarse.
 * @param wakeMinuteOfDay hora de despertar.
 */
data class SleepWindow(
    val bedtimeMinuteOfDay: Int,
    val wakeMinuteOfDay: Int,
) {

    /**
     * ¿La ventana cruza la medianoche?
     *
     * Es el caso normal (se duerme a las 22:00 y se despierta a las 7:00) y la razón por
     * la que no basta con comparar `inicio <= m && m < fin`: esa comparación daría
     * siempre falso para una ventana nocturna.
     */
    val crossesMidnight: Boolean get() = bedtimeMinuteOfDay > wakeMinuteOfDay

    /** Duración en minutos, contando el cruce de medianoche. */
    val durationMinutes: Int
        get() = if (crossesMidnight) {
            MINUTES_PER_DAY - bedtimeMinuteOfDay + wakeMinuteOfDay
        } else {
            wakeMinuteOfDay - bedtimeMinuteOfDay
        }

    /**
     * ¿Está [minuteOfDay] dentro de las horas de sueño?
     *
     * El inicio es inclusivo y el final exclusivo: una toma justo a la hora de despertar
     * **no** se considera dormida y por tanto no se difiere. Con la ventana vacía
     * (acostarse y despertar a la misma hora) no hay franja de sueño y nunca se difiere
     * nada, que es lo prudente frente a interpretarla como el día entero.
     */
    fun contains(minuteOfDay: Int): Boolean {
        if (bedtimeMinuteOfDay == wakeMinuteOfDay) return false
        val m = floorMod(minuteOfDay, MINUTES_PER_DAY)
        return if (crossesMidnight) {
            m >= bedtimeMinuteOfDay || m < wakeMinuteOfDay
        } else {
            m >= bedtimeMinuteOfDay && m < wakeMinuteOfDay
        }
    }

    companion object {

        /**
         * Franja por defecto: de 22:00 a 7:00.
         *
         * Es una suposición razonable para no dejar el campo vacío, pero sigue siendo una
         * suposición: la persona debe poder ajustarla, y la app no debe dar por hecho que
         * acertó.
         */
        val DEFAULT = SleepWindow(
            bedtimeMinuteOfDay = 22 * 60,
            wakeMinuteOfDay = 7 * 60,
        )

        /** Normaliza valores fuera de rango en vez de confiar en quien los escribe. */
        fun of(bedtimeMinuteOfDay: Int, wakeMinuteOfDay: Int): SleepWindow = SleepWindow(
            bedtimeMinuteOfDay = floorMod(bedtimeMinuteOfDay, MINUTES_PER_DAY),
            wakeMinuteOfDay = floorMod(wakeMinuteOfDay, MINUTES_PER_DAY),
        )

        private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus
    }
}
