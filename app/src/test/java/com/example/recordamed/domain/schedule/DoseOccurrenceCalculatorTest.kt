package com.example.recordamed.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

/**
 * Las pruebas fijan la zona horaria a UTC para que los resultados no dependan de la
 * máquina que las ejecuta. El cambio de horario de verano es una limitación conocida y
 * documentada del calculador, y se aborda por separado.
 */
class DoseOccurrenceCalculatorTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun hhmm(hour: Int, minute: Int) = DoseOccurrenceCalculator.minuteOfDay(hour, minute)

    // --- currentOccurrence -------------------------------------------------------

    @Test
    fun `la toma ancla cae el dia de alta a su hora`() {
        val startDate = at(2026, 9, 17, 20, 16) // se guardó a las 8:16 p.m.
        val now = at(2026, 9, 17, 20, 30)

        val result = DoseOccurrenceCalculator.currentOccurrence(
            slotMinuteOfDay = hhmm(18, 0),
            anchorMinuteOfDay = hhmm(18, 0),
            medicationStartDate = startDate,
            now = now,
            timeZone = utc,
        )

        // Las 18:00 de HOY, aunque ya pasaran: debe poder marcarse como atrasada.
        assertEquals(at(2026, 9, 17, 18, 0), result)
        assertTrue("la ocurrencia vigente puede quedar en el pasado", result < now)
    }

    @Test
    fun `una toma posterior del mismo dia sigue siendo de hoy`() {
        val startDate = at(2026, 9, 17, 20, 16)
        val now = at(2026, 9, 17, 20, 30)

        // Horario cada 4 h desde las 18:00; la de las 22:00 aún no llega.
        val result = DoseOccurrenceCalculator.currentOccurrence(
            slotMinuteOfDay = hhmm(22, 0),
            anchorMinuteOfDay = hhmm(18, 0),
            medicationStartDate = startDate,
            now = now,
            timeZone = utc,
        )

        assertEquals(at(2026, 9, 17, 22, 0), result)
    }

    @Test
    fun `una toma que cruza la medianoche cae en el dia siguiente, no en el mismo`() {
        val startDate = at(2026, 9, 17, 20, 16)
        val now = at(2026, 9, 17, 20, 30)

        // Ancla 18:00, toma a las 02:00: pertenece al ciclo que empezó a las 18:00 de
        // hoy, así que son las 02:00 de MAÑANA. Comparar "02:00 contra hoy" daría las
        // 02:00 de esta madrugada, ocho horas antes de que el medicamento existiera.
        val result = DoseOccurrenceCalculator.currentOccurrence(
            slotMinuteOfDay = hhmm(2, 0),
            anchorMinuteOfDay = hhmm(18, 0),
            medicationStartDate = startDate,
            now = now,
            timeZone = utc,
        )

        assertEquals(at(2026, 9, 18, 2, 0), result)
    }

    @Test
    fun `avanza de ciclo cuando pasan los dias`() {
        val startDate = at(2026, 9, 17, 8, 0)
        val now = at(2026, 9, 20, 9, 0) // tres días después

        val result = DoseOccurrenceCalculator.currentOccurrence(
            slotMinuteOfDay = hhmm(8, 0),
            anchorMinuteOfDay = hhmm(8, 0),
            medicationStartDate = startDate,
            now = now,
            timeZone = utc,
        )

        assertEquals(at(2026, 9, 20, 8, 0), result)
    }

    @Test
    fun `antes de T0 no retrocede de ciclo`() {
        val startDate = at(2026, 9, 17, 8, 0)
        val now = at(2026, 9, 17, 7, 0) // una hora antes de la primera toma

        val result = DoseOccurrenceCalculator.currentOccurrence(
            slotMinuteOfDay = hhmm(8, 0),
            anchorMinuteOfDay = hhmm(8, 0),
            medicationStartDate = startDate,
            now = now,
            timeZone = utc,
        )

        assertEquals(at(2026, 9, 17, 8, 0), result)
    }

    // --- nextOccurrenceAfter -----------------------------------------------------

    @Test
    fun `la proxima ocurrencia es siempre estrictamente futura`() {
        val startDate = at(2026, 9, 17, 20, 16)
        val now = at(2026, 9, 17, 20, 30)

        val result = DoseOccurrenceCalculator.nextOccurrenceAfter(
            slotMinuteOfDay = hhmm(18, 0),
            anchorMinuteOfDay = hhmm(18, 0),
            medicationStartDate = startDate,
            now = now,
            timeZone = utc,
        )

        assertEquals(at(2026, 9, 18, 18, 0), result)
        assertTrue(result > now)
    }

    /**
     * Esta es la regresión que motivó extraer el calculador.
     *
     * El identificador de una alarma se deriva de su timestamp. Si el valor con el que
     * se arma no es uno de los que el resto de la app sabe calcular, cancelarla no
     * encuentra nada y la alarma suena aunque la toma ya se haya registrado. La
     * propiedad que lo impide es que la próxima ocurrencia sea siempre la vigente o la
     * vigente más 24 h exactas — nunca un tercer valor.
     */
    @Test
    fun `la proxima ocurrencia coincide con la vigente o con la vigente mas un dia`() {
        val startDate = at(2026, 9, 17, 20, 16)
        val unDia = 24L * 60 * 60 * 1000

        // Se barren todas las tomas de un horario cada 15 min a lo largo de un día
        // entero, que es el caso donde los dos cálculos antiguos más discrepaban.
        var minuto = hhmm(18, 0)
        repeat(96) {
            val slot = minuto % MINUTES_PER_DAY
            listOf(
                at(2026, 9, 17, 20, 30),
                at(2026, 9, 18, 3, 0),
                at(2026, 9, 19, 12, 0),
            ).forEach { now ->
                val vigente = DoseOccurrenceCalculator.currentOccurrence(
                    slot, hhmm(18, 0), startDate, now, utc,
                )
                val proxima = DoseOccurrenceCalculator.nextOccurrenceAfter(
                    slot, hhmm(18, 0), startDate, now, utc,
                )

                assertTrue("la próxima debe ser futura", proxima > now)
                assertTrue(
                    "la próxima ($proxima) debe derivarse de la vigente ($vigente)",
                    proxima == vigente || proxima == vigente + unDia,
                )
            }
            minuto += 15
        }
    }

    @Test
    fun `minuteOfDay convierte hora y minuto`() {
        assertEquals(0, DoseOccurrenceCalculator.minuteOfDay(0, 0))
        assertEquals(1080, DoseOccurrenceCalculator.minuteOfDay(18, 0))
        assertEquals(1439, DoseOccurrenceCalculator.minuteOfDay(23, 59))
    }
}
