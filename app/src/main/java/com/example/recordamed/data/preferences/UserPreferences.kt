package com.example.recordamed.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.recordamed.domain.schedule.SleepWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val LEGACY_THEME_PREFS = "recordamed_theme_prefs"
private const val LEGACY_KEY_DARK_MODE = "key_dark_mode"

/**
 * DataStore de la app, con migración automática desde el `SharedPreferences` que usaba
 * `ThemeManager`. Sin esa migración, quien ya tuviera elegido el tema oscuro lo vería
 * volver a claro al actualizar — un detalle pequeño, pero gratuito de evitar.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recordamed_preferences",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_THEME_PREFS))
    },
)

/**
 * Preferencias de la persona que usa la app.
 *
 * Antes solo existía una (el tema), guardada con `SharedPreferences` leído de forma
 * síncrona en el hilo principal desde un `object` en la capa de interfaz. Al aparecer la
 * segunda —las horas de sueño, que el esquema permisivo necesita— tenía poco sentido
 * repetir aquel mecanismo, así que se unifican aquí sobre DataStore, que es asíncrono y
 * transaccional.
 */
class UserPreferences(private val context: Context) {

    /** Tema oscuro. Por defecto claro, como hasta ahora. */
    val darkMode: Flow<Boolean> = context.dataStore.data
        .sinFallarAnteCorrupcion()
        .map { it[KEY_DARK_MODE] ?: false }

    /**
     * Horas de sueño de la persona. Si aún no las ha configurado se usa
     * [SleepWindow.DEFAULT], que es una suposición razonable y no un dato real.
     */
    val sleepWindow: Flow<SleepWindow> = context.dataStore.data
        .sinFallarAnteCorrupcion()
        .map { prefs ->
            val bedtime = prefs[KEY_SLEEP_BEDTIME]
            val wake = prefs[KEY_SLEEP_WAKE]
            if (bedtime == null || wake == null) {
                SleepWindow.DEFAULT
            } else {
                SleepWindow.of(bedtime, wake)
            }
        }

    /** ¿La persona configuró sus horas de sueño, o seguimos con la suposición? */
    val hasCustomSleepWindow: Flow<Boolean> = context.dataStore.data
        .sinFallarAnteCorrupcion()
        .map { it[KEY_SLEEP_BEDTIME] != null && it[KEY_SLEEP_WAKE] != null }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setSleepWindow(window: SleepWindow) {
        context.dataStore.edit {
            it[KEY_SLEEP_BEDTIME] = window.bedtimeMinuteOfDay
            it[KEY_SLEEP_WAKE] = window.wakeMinuteOfDay
        }
    }

    /**
     * Un archivo de preferencias dañado no debe impedir que la app arranque: se cae a los
     * valores por defecto. Para una app de recordatorios, quedarse sin abrir es peor que
     * perder la elección de tema.
     */
    private fun Flow<Preferences>.sinFallarAnteCorrupcion(): Flow<Preferences> =
        catch { causa ->
            if (causa is IOException) emit(emptyPreferences()) else throw causa
        }

    private companion object {
        val KEY_DARK_MODE = booleanPreferencesKey(LEGACY_KEY_DARK_MODE)
        val KEY_SLEEP_BEDTIME = intPreferencesKey("sleep_bedtime_minute_of_day")
        val KEY_SLEEP_WAKE = intPreferencesKey("sleep_wake_minute_of_day")
    }
}
