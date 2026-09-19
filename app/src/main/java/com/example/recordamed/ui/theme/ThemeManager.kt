package com.example.recordamed.ui.theme

import android.content.Context
import com.example.recordamed.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Expone el tema a la interfaz como un [StateFlow].
 *
 * Guardaba la preferencia por su cuenta con `SharedPreferences`. Ahora es un adaptador
 * fino sobre [UserPreferences], que es donde viven todas las preferencias de la persona:
 * la de tema y las horas de sueño que necesita el esquema permisivo. La API pública no
 * cambia, así que la interfaz sigue leyendo [isDarkMode] igual que antes.
 */
object ThemeManager {

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preferences: UserPreferences? = null

    /**
     * Se llama desde `Application.onCreate()`.
     *
     * La primera lectura es bloqueante a propósito. DataStore es asíncrono, y leer el
     * tema de forma diferida haría que la app pintara un primer fotograma en claro para
     * cambiar a oscuro un instante después: un parpadeo visible cada vez que se abre.
     * Es una sola lectura de un archivo pequeño, exactamente lo que ya hacía
     * `SharedPreferences` aquí mismo, así que no empeora nada respecto a antes.
     *
     * La forma limpia de quitar este bloqueo es retener la pantalla de inicio hasta que
     * la preferencia esté cargada; queda como mejora aparte para no mezclarla con este
     * cambio.
     */
    fun init(context: Context) {
        val prefs = UserPreferences(context.applicationContext)
        preferences = prefs
        _isDarkMode.value = runBlocking { prefs.darkMode.first() }

        // A partir de aquí, cualquier cambio escrito en DataStore se refleja solo.
        scope.launch {
            prefs.darkMode.collect { _isDarkMode.value = it }
        }
    }

    fun toggleTheme(context: Context) {
        val prefs = preferences ?: UserPreferences(context.applicationContext).also { preferences = it }
        val nuevoValor = !_isDarkMode.value
        // Se refleja de inmediato para que el toque se sienta instantáneo; la escritura
        // en disco va detrás y el colector de init() confirmará el mismo valor.
        _isDarkMode.value = nuevoValor
        scope.launch { prefs.setDarkMode(nuevoValor) }
    }
}
