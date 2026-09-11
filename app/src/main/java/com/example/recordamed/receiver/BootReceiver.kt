package com.example.recordamed.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.recordamed.RecordaMedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as? RecordaMedApp ?: return
            val repository = app.repository
            val scheduler = app.alarmScheduler

            CoroutineScope(Dispatchers.IO).launch {
                val schedules = app.database.doseScheduleDao().getAllSchedulesSync()
                for (schedule in schedules) {
                    val med = repository.getMedicationById(schedule.medicationId)
                    if (med != null && med.isActive) {
                        scheduler.scheduleDoseAlarm(
                            medicationId = med.id,
                            medicationName = med.name,
                            dosage = med.dosage,
                            hour = schedule.timeHour,
                            minute = schedule.timeMinute,
                            voiceNotePath = med.voiceNotePath,
                            soundType = med.soundType
                        )
                    }
                }
            }
        }
    }
}
