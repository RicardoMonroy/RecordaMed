package com.example.recordamed

import android.app.Application
import com.example.recordamed.data.local.RecordaMedDatabase
import com.example.recordamed.data.preferences.UserPreferences
import com.example.recordamed.data.repository.MedicationRepository
import com.example.recordamed.service.AlarmScheduler
import com.example.recordamed.service.NotificationHelper

class RecordaMedApp : Application() {

    val database by lazy { RecordaMedDatabase.getDatabase(this) }
    val repository by lazy {
        MedicationRepository(
            medicationDao = database.medicationDao(),
            scheduleDao = database.doseScheduleDao(),
            doseLogDao = database.doseLogDao(),
            userPreferences = UserPreferences(this)
        )
    }
    val alarmScheduler by lazy { AlarmScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.recordamed.ui.theme.ThemeManager.init(this)
        NotificationHelper.createAlarmNotificationChannel(this)
    }

    companion object {
        lateinit var instance: RecordaMedApp
            private set
    }
}
