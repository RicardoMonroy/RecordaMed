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

            // Un solo punto de re-armado, compartido con AlarmReceiver: antes cada
            // receiver tenía su propio bucle y no calculaban el instante igual.
            CoroutineScope(Dispatchers.IO).launch {
                scheduler.rescheduleAll(repository)
            }
        }
    }
}
