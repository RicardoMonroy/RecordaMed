package com.example.recordamed.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica que actualizar la app no destruya los datos de nadie.
 *
 * Durante mucho tiempo la base se construía con `fallbackToDestructiveMigration()`: ante
 * cualquier cambio de esquema Room borraba todo y con ello el historial de tomas, en
 * silencio. En una app de adherencia ese historial es el producto. Estas pruebas son la
 * red que sostiene la decisión de haberlo quitado.
 *
 * No se usa `MigrationTestHelper` a propósito. Arrastra `room-migration`, que lee los JSON
 * del esquema con kotlinx-serialization, y sus serializadores generados chocan con la
 * versión que AndroidX fija mediante una restricción `strictly` en la app. Hacerlos
 * coincidir obligaría a sobrescribir esa restricción en el APK que se publica, lo que
 * pone en riesgo el guardado de estado de `lifecycle` — mal negocio para una app de
 * medicamentos a cambio de una comodidad de prueba.
 *
 * En su lugar se construye la base en la versión 3 con el DDL exacto del esquema
 * exportado en `app/schemas/.../3.json` y se abre con Room aplicando la migración real.
 * A cambio se gana algo: **Room valida el esquema resultante al abrir**, comparando su
 * huella con la compilada. Si la migración dejara las tablas de otra forma, la apertura
 * fallaría, así que la validación sigue cubierta.
 *
 * Se ejecutan con `./gradlew.bat connectedDebugAndroidTest` y requieren dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val databaseName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    /**
     * Crea una base tal y como la dejaba la versión 3, con el DDL literal del esquema
     * exportado, y marca `user_version` para que Room la reconozca como versión 3 y
     * aplique la migración en vez de crearla de cero.
     */
    private fun crearBaseVersion3(poblar: (android.database.sqlite.SQLiteDatabase) -> Unit) {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        db.use {
            it.execSQL(
                "CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `instructions` TEXT NOT NULL, " +
                    "`colorHex` INTEGER NOT NULL, `iconType` TEXT NOT NULL, `isTemporary` INTEGER NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, `endDate` INTEGER, `voiceNotePath` TEXT, " +
                    "`soundType` TEXT NOT NULL, `isActive` INTEGER NOT NULL)"
            )
            it.execSQL(
                "CREATE TABLE IF NOT EXISTS `dose_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`medicationId` INTEGER NOT NULL, `timeHour` INTEGER NOT NULL, `timeMinute` INTEGER NOT NULL, " +
                    "`sequenceIndex` INTEGER NOT NULL, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            it.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_dose_schedules_medicationId` ON `dose_schedules` (`medicationId`)"
            )
            it.execSQL(
                "CREATE TABLE IF NOT EXISTS `dose_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`medicationId` INTEGER NOT NULL, `scheduledTime` INTEGER NOT NULL, `takenTime` INTEGER, " +
                    "`status` TEXT NOT NULL, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            it.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_dose_logs_medicationId` ON `dose_logs` (`medicationId`)"
            )
            it.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_dose_logs_scheduledTime` ON `dose_logs` (`scheduledTime`)"
            )
            // Room guarda su huella de identidad aquí; sin ella rechazaría la base por
            // no poder verificar de qué esquema viene.
            it.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            it.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '$IDENTITY_HASH_V3')"
            )
            it.version = 3
            poblar(it)
        }
    }

    /** Abre la base con Room, lo que dispara la migración y su validación de esquema. */
    private fun abrirConMigracion(): RecordaMedDatabase =
        Room.databaseBuilder(context, RecordaMedDatabase::class.java, databaseName)
            .addMigrations(RecordaMedDatabase.MIGRATION_3_4)
            .build()

    /**
     * El caso que de verdad importa: alguien con tratamiento en curso actualiza la app y
     * su historial sigue ahí.
     */
    @Test
    fun migracion3a4_conservaMedicamentosHorariosEHistorial() {
        crearBaseVersion3 { db ->
            db.execSQL(
                "INSERT INTO medications (id, name, dosage, instructions, colorHex, iconType, " +
                    "isTemporary, startDate, endDate, voiceNotePath, soundType, isActive) VALUES " +
                    "(1, 'Metformina', '850 mg', 'Con la comida', 3050327, 'PILL', 0, " +
                    "1789688400000, NULL, NULL, 'BELLS', 1)"
            )
            db.execSQL(
                "INSERT INTO dose_schedules (id, medicationId, timeHour, timeMinute, sequenceIndex) " +
                    "VALUES (1, 1, 8, 0, 0), (2, 1, 20, 0, 1)"
            )
            db.execSQL(
                "INSERT INTO dose_logs (id, medicationId, scheduledTime, takenTime, status) " +
                    "VALUES (1, 1, 1789688400000, 1789688460000, 'TAKEN')"
            )
        }

        val db = abrirConMigracion()
        try {
            val medicamento = db.openHelper.readableDatabase
                .query("SELECT name, dosage, instructions FROM medications WHERE id = 1")
            medicamento.use { c ->
                assertTrue("el medicamento debe sobrevivir", c.moveToFirst())
                assertEquals("Metformina", c.getString(0))
                assertEquals("850 mg", c.getString(1))
                assertEquals("Con la comida", c.getString(2))
            }

            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM dose_schedules").use { c ->
                c.moveToFirst()
                assertEquals("los horarios deben sobrevivir", 2, c.getInt(0))
            }

            db.openHelper.readableDatabase
                .query("SELECT medicationId, status, takenTime FROM dose_logs").use { c ->
                    assertTrue("el historial de tomas debe sobrevivir", c.moveToFirst())
                    assertEquals(1L, c.getLong(0))
                    assertEquals("TAKEN", c.getString(1))
                    assertEquals(1789688460000L, c.getLong(2))
                }
        } finally {
            db.close()
        }
    }

    /**
     * Los tratamientos ya existentes deben quedar en el esquema estricto, que es el único
     * comportamiento que la app tenía antes de esta versión. Migrarlos a permisivo
     * cambiaría en silencio cómo se toma un medicamento que quizá sí era crítico.
     */
    @Test
    fun migracion3a4_losMedicamentosExistentesQuedanEnEsquemaEstricto() {
        crearBaseVersion3 { db ->
            db.execSQL(
                "INSERT INTO medications (id, name, dosage, instructions, colorHex, iconType, " +
                    "isTemporary, startDate, endDate, voiceNotePath, soundType, isActive) VALUES " +
                    "(7, 'Enalapril', '10 mg', '', 3050327, 'PILL', 0, 1789688400000, NULL, NULL, 'HARP', 1)"
            )
        }

        val db = abrirConMigracion()
        try {
            db.openHelper.readableDatabase
                .query("SELECT scheduleMode, intervalMinutes FROM medications WHERE id = 7").use { c ->
                    assertTrue("el medicamento debe existir tras migrar", c.moveToFirst())
                    assertEquals("STRICT", c.getString(0))
                    assertEquals("intervalo desconocido para los ya existentes", 0, c.getInt(1))
                }
        } finally {
            db.close()
        }
    }

    companion object {
        /** Huella del esquema v3, tomada de `app/schemas/.../3.json`. */
        private const val IDENTITY_HASH_V3 = "7c57ce3c36e5392c7e0230829334fd84"
    }
}
