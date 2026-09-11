package com.example.recordamed.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recordamed.RecordaMedApp
import com.example.recordamed.data.local.entities.DoseLogEntity
import com.example.recordamed.data.local.entities.MedicationEntity
import com.example.recordamed.domain.model.DayAdherence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val medications: List<MedicationEntity> = emptyList(),
    val selectedMedicationId: Long? = null,
    val heatmap: List<DayAdherence> = emptyList(),
    val recentLogs: List<DoseLogEntity> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RecordaMedApp).repository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getActiveMedications().collect { meds ->
                val currentSelection = _uiState.value.selectedMedicationId
                val stillValid = meds.any { it.id == currentSelection }
                val selected = if (stillValid) currentSelection else meds.firstOrNull()?.id

                _uiState.update { it.copy(medications = meds, selectedMedicationId = selected) }

                if (selected != null && selected != currentSelection) {
                    loadDataFor(selected)
                } else if (selected == null) {
                    _uiState.update { it.copy(heatmap = emptyList(), recentLogs = emptyList(), isLoading = false) }
                }
            }
        }
    }

    fun selectMedication(medicationId: Long) {
        if (medicationId == _uiState.value.selectedMedicationId) return
        _uiState.update { it.copy(selectedMedicationId = medicationId) }
        loadDataFor(medicationId)
    }

    private fun loadDataFor(medicationId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val heatmap = repository.getAdherenceHeatmap(medicationId)
            val logs = repository.getRecentLogsForMedication(medicationId)
            // Si el usuario ya cambió de pestaña mientras esto cargaba, no pisar esa selección.
            if (_uiState.value.selectedMedicationId == medicationId) {
                _uiState.update { it.copy(heatmap = heatmap, recentLogs = logs, isLoading = false) }
            }
        }
    }
}
