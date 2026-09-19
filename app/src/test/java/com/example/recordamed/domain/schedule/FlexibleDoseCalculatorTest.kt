package com.example.recordamed.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

/**
 * Zona horaria fija a UTC para que los resultados no dependan de la máquina.
 */
class FlexibleDoseCalculatorTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** Ventana de sueño habitual: de 22:00 a 7:00, cruzando la medianoche. */
    private val sueño = SleepWindow(bedtimeMinuteOfDay = 22 * 60, wakeMinuteOfDay = 7 * 60)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, day, hour, minute, 0)
        }.timeInMillis

    private fun describir(t: Long): String =
        Calendar.getInstance(utc).apply { timeInMillis = t }.let {
            "%02d/%02d %02d:%02d".format(
                it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.MONTH) + 1,
                it.get(Calendar.HOUR_OF_DAY),
                it.get(Calendar.MINUTE),
            )
        }

    // --- el intervalo cuelga de la toma real ----------------------------------------

    @Test
    fun `la siguiente toma se cuenta desde la hora real, no desde la planeada`() {
        // Debía tomarla a las 8:00 y se la tomó a las 9:15. Con "cada 8 horas", la
        // siguiente es a las 17:15, no a las 16:00: contar desde la hora planeada
        // acortaría el intervalo real a 6 h 45.
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 9, 15),
            intervalMinutes = 8 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(19, 17, 15)), describir(resultado))
    }

    @Test
    fun `una toma que cae en pleno dia no se toca`() {
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 8, 0),
            intervalMinutes = 6 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(19, 14, 0)), describir(resultado))
        assertFalse(
            FlexibleDoseCalculator.wasDeferred(at(19, 8, 0), 6 * 60, sueño, utc)
        )
    }

    // --- no se despierta a nadie de madrugada ----------------------------------------

    @Test
    fun `una toma de madrugada se difiere a la hora de despertar`() {
        // Tomada a las 20:00, cada 8 h tocaría a las 04:00. Se difiere a las 07:00.
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 20, 0),
            intervalMinutes = 8 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
        assertTrue(FlexibleDoseCalculator.wasDeferred(at(19, 20, 0), 8 * 60, sueño, utc))
    }

    @Test
    fun `una toma justo antes de medianoche se difiere al despertar del dia siguiente`() {
        // Tomada a las 21:00, cada 4 h tocaría a las 01:00 del día 20. Despertar es a
        // las 7:00 — del día 20, no del 19. Aquí es donde falla calcular la diferencia
        // de minutos en vez de buscar la siguiente ocurrencia hacia delante.
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 21, 0),
            intervalMinutes = 4 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
    }

    @Test
    fun `una toma a las 23 horas espera al despertar del dia siguiente`() {
        // Tomada a las 19:00, cada 4 h tocaría a las 23:00 del mismo día, ya dentro de
        // la ventana. El despertar aplicable son las 7:00 del día siguiente.
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 19, 0),
            intervalMinutes = 4 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
    }

    @Test
    fun `una toma justo a la hora de despertar no se difiere`() {
        // Tomada a las 23:00, cada 8 h toca a las 07:00: el final de la ventana es
        // exclusivo, así que ya es hora de estar despierto y no hay nada que diferir.
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 23, 0),
            intervalMinutes = 8 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
        assertFalse(
            "las 7:00 no están dentro de la ventana, así que no es un diferimiento",
            FlexibleDoseCalculator.wasDeferred(at(19, 23, 0), 8 * 60, sueño, utc),
        )
    }

    // --- nunca se comprime ni se adelanta --------------------------------------------

    @Test
    fun `diferir alarga el intervalo, nunca lo acorta`() {
        val tomada = at(19, 20, 0)
        val intervalo = 8 * 60
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(tomada, intervalo, sueño, utc)

        val transcurridoMinutos = (resultado - tomada) / (60 * 1000)
        assertTrue(
            "el intervalo real ($transcurridoMinutos min) nunca debe ser menor que el indicado ($intervalo min)",
            transcurridoMinutos >= intervalo,
        )
    }

    @Test
    fun `un intervalo mayor que el dia despierto sigue funcionando`() {
        // Cada 20 h, tomada a las 8:00 del día 19: tocaría a las 04:00 del 20, que cae
        // en la ventana, así que se difiere a las 07:00 del 20. Son 23 h, más que el
        // intervalo: correcto, porque diferir solo puede alargar.
        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 8, 0),
            intervalMinutes = 20 * 60,
            sleepWindow = sueño,
            timeZone = utc,
        )

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
    }

    // --- la primera toma de un tratamiento -------------------------------------------

    @Test
    fun `deferIfAsleep corre la primera toma si se eligio de madrugada`() {
        val elegida = at(19, 3, 0)
        val resultado = FlexibleDoseCalculator.deferIfAsleep(elegida, sueño, utc)

        assertEquals(describir(at(19, 7, 0)), describir(resultado))
    }

    @Test
    fun `deferIfAsleep no toca una hora diurna`() {
        val elegida = at(19, 10, 30)
        assertEquals(elegida, FlexibleDoseCalculator.deferIfAsleep(elegida, sueño, utc))
    }

    // --- sin ventana de sueño configurada --------------------------------------------

    @Test
    fun `con una ventana vacia nunca se difiere nada`() {
        val sinSueño = SleepWindow(bedtimeMinuteOfDay = 0, wakeMinuteOfDay = 0)

        val resultado = FlexibleDoseCalculator.nextDoseAfterTaking(
            lastTakenAt = at(19, 20, 0),
            intervalMinutes = 8 * 60,
            sleepWindow = sinSueño,
            timeZone = utc,
        )

        assertEquals(describir(at(20, 4, 0)), describir(resultado))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un intervalo de cero es un error de programacion, no un caso valido`() {
        FlexibleDoseCalculator.nextDoseAfterTaking(at(19, 8, 0), 0, sueño, utc)
    }

    // --- reanudacion tras una toma perdida --------------------------------------------

    @Test
    fun `tras una toma perdida por la tarde se retoma a la manana siguiente`() {
        // Se perdió la toma de las 16:00. Con ventana de 22:00 a 7:00, se retoma a las
        // 7:00 del día siguiente. La consecuencia asumida: no vuelve a sonar esa tarde.
        val resultado = FlexibleDoseCalculator.resumeAfterMissed(at(19, 16, 0), sueño, utc)

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
    }

    @Test
    fun `tras una toma perdida de madrugada se retoma el mismo dia al despertar`() {
        // No debería ocurrir en permisivo, porque nunca se arma de madrugada, pero si un
        // cambio de la ventana de sueño deja una toma ahí, lo correcto es retomar a las
        // 7:00 de ese mismo día, no esperar 24 horas.
        val resultado = FlexibleDoseCalculator.resumeAfterMissed(at(19, 3, 0), sueño, utc)

        assertEquals(describir(at(19, 7, 0)), describir(resultado))
    }

    @Test
    fun `una toma perdida justo a la hora de despertar espera al dia siguiente`() {
        // Límite estricto: retomar en el mismo instante en que se perdió significaría
        // armar una alarma para ahora mismo y volver a fallar de inmediato.
        val resultado = FlexibleDoseCalculator.resumeAfterMissed(at(19, 7, 0), sueño, utc)

        assertEquals(describir(at(20, 7, 0)), describir(resultado))
    }

    @Test
    fun `la reanudacion siempre es estrictamente futura`() {
        listOf(at(19, 0, 0), at(19, 7, 0), at(19, 12, 0), at(19, 22, 0), at(19, 23, 59))
            .forEach { perdida ->
                val resultado = FlexibleDoseCalculator.resumeAfterMissed(perdida, sueño, utc)
                assertTrue(
                    "reanudar en ${describir(resultado)} no es futuro respecto a ${describir(perdida)}",
                    resultado > perdida,
                )
            }
    }

    @Test
    fun `minuteOfDay convierte un instante a minuto del dia`() {
        assertEquals(0, FlexibleDoseCalculator.minuteOfDay(at(19, 0, 0), utc))
        assertEquals(22 * 60 + 30, FlexibleDoseCalculator.minuteOfDay(at(19, 22, 30), utc))
    }
}
