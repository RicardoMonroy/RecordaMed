package com.example.recordamed.domain.schedule

/**
 * Los dos esquemas con los que puede tomarse un medicamento.
 *
 * La distinción viene de cómo funciona esto en la práctica clínica, no de una
 * preferencia de la app.
 */
enum class ScheduleMode {

    /**
     * La hora de reloj manda. Es el esquema hospitalario: la toma de las 14:00 es a las
     * 14:00, y debe avisar aunque sean las tres de la madrugada.
     */
    STRICT,

    /**
     * Lo que manda es el tiempo entre tomas, no la hora absoluta. Los médicos suelen
     * recomendar no despertar al paciente de noche: la toma de la mañana ancla el día y
     * a partir de ahí se respeta el intervalo indicado.
     */
    FLEXIBLE;

    companion object {

        /** Valor por defecto: el único comportamiento que la app tenía antes. */
        val DEFAULT = STRICT

        /**
         * Convierte el texto guardado en la base. Un valor desconocido —por ejemplo de
         * una versión futura instalada y luego revertida— cae en [DEFAULT] en lugar de
         * romper: es preferible avisar de más que dejar a alguien sin su recordatorio.
         */
        fun fromStorage(value: String?): ScheduleMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
