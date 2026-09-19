package com.example.recordamed.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWindowTest {

    private fun hhmm(hour: Int, minute: Int = 0) = hour * 60 + minute

    // --- ventana nocturna, el caso normal ------------------------------------------

    private val nocturna = SleepWindow(bedtimeMinuteOfDay = hhmm(22), wakeMinuteOfDay = hhmm(7))

    @Test
    fun `una ventana nocturna cruza la medianoche`() {
        assertTrue(nocturna.crossesMidnight)
        assertEquals(9 * 60, nocturna.durationMinutes)
    }

    @Test
    fun `la madrugada cae dentro de la ventana nocturna`() {
        assertTrue(nocturna.contains(hhmm(23)))
        assertTrue(nocturna.contains(hhmm(0)))
        assertTrue(nocturna.contains(hhmm(3, 30)))
        assertTrue(nocturna.contains(hhmm(6, 59)))
    }

    @Test
    fun `el dia queda fuera de la ventana nocturna`() {
        assertFalse(nocturna.contains(hhmm(8)))
        assertFalse(nocturna.contains(hhmm(14)))
        assertFalse(nocturna.contains(hhmm(21, 59)))
    }

    @Test
    fun `la hora de acostarse esta dentro y la de despertar fuera`() {
        // El inicio es inclusivo y el final exclusivo: una toma justo al despertar no
        // debe diferirse, ya es hora de estar despierto.
        assertTrue("las 22:00 ya son horas de sueño", nocturna.contains(hhmm(22)))
        assertFalse("las 7:00 ya no lo son", nocturna.contains(hhmm(7)))
    }

    // --- ventana diurna, para quien duerme de día ----------------------------------

    @Test
    fun `una ventana que no cruza medianoche funciona igual`() {
        // Por ejemplo alguien que trabaja de noche y duerme de 9:00 a 17:00.
        val diurna = SleepWindow(bedtimeMinuteOfDay = hhmm(9), wakeMinuteOfDay = hhmm(17))

        assertFalse(diurna.crossesMidnight)
        assertEquals(8 * 60, diurna.durationMinutes)
        assertTrue(diurna.contains(hhmm(12)))
        assertFalse(diurna.contains(hhmm(3)))
        assertFalse(diurna.contains(hhmm(20)))
    }

    // --- casos límite ---------------------------------------------------------------

    @Test
    fun `una ventana vacia no contiene nada`() {
        // Acostarse y despertar a la misma hora podría leerse como "el día entero" o
        // como "nada". Se elige "nada": no diferir es más prudente que silenciar todas
        // las tomas de alguien.
        val vacia = SleepWindow(bedtimeMinuteOfDay = hhmm(22), wakeMinuteOfDay = hhmm(22))

        assertFalse(vacia.contains(hhmm(22)))
        assertFalse(vacia.contains(hhmm(3)))
        assertFalse(vacia.contains(hhmm(14)))
        assertEquals(0, vacia.durationMinutes)
    }

    @Test
    fun `of normaliza valores fuera de rango`() {
        val w = SleepWindow.of(bedtimeMinuteOfDay = MINUTES_PER_DAY + hhmm(22), wakeMinuteOfDay = -60)

        assertEquals(hhmm(22), w.bedtimeMinuteOfDay)
        assertEquals(hhmm(23), w.wakeMinuteOfDay)
    }

    @Test
    fun `contains normaliza el minuto consultado`() {
        assertTrue(nocturna.contains(MINUTES_PER_DAY + hhmm(3)))
        assertFalse(nocturna.contains(MINUTES_PER_DAY + hhmm(14)))
    }

    @Test
    fun `la ventana por defecto es de diez de la noche a siete de la manana`() {
        assertEquals(hhmm(22), SleepWindow.DEFAULT.bedtimeMinuteOfDay)
        assertEquals(hhmm(7), SleepWindow.DEFAULT.wakeMinuteOfDay)
    }
}
