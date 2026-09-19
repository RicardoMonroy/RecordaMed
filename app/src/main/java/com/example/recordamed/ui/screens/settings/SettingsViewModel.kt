package com.example.recordamed.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recordamed.data.preferences.UserPreferences
import com.example.recordamed.domain.schedule.SleepWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sleepWindow: SleepWindow = SleepWindow.DEFAULT,
    /**
     * Si la persona todavía no configuró sus horas, lo que se muestra es una suposición
     * de la app. Conviene decirlo en pantalla en vez de presentarlo como un dato suyo.
     */
    val isSleepWindowAssumed: Boolean = true,
    val isDarkMode: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.sleepWindow,
                preferences.hasCustomSleepWindow,
                preferences.darkMode,
            ) { window, configurada, oscuro ->
                SettingsUiState(
                    sleepWindow = window,
                    isSleepWindowAssumed = !configurada,
                    isDarkMode = oscuro,
                )
            }.collect { nuevo -> _uiState.update { nuevo } }
        }
    }

    fun updateBedtime(hour: Int, minute: Int) {
        val actual = _uiState.value.sleepWindow
        guardar(SleepWindow.of(hour * 60 + minute, actual.wakeMinuteOfDay))
    }

    fun updateWakeTime(hour: Int, minute: Int) {
        val actual = _uiState.value.sleepWindow
        guardar(SleepWindow.of(actual.bedtimeMinuteOfDay, hour * 60 + minute))
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkMode(enabled) }
    }

    private fun guardar(window: SleepWindow) {
        viewModelScope.launch { preferences.setSleepWindow(window) }
    }
}
