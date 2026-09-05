package com.koshg.interlude.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.koshg.interlude.data.AppDatabase
import com.koshg.interlude.data.CycleRepository
import com.koshg.interlude.data.PeriodEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Handles the "Отметить начало" action button on the period-approaching reminder notification --
 * logs today as a new period start directly, with no need to open the app first. A
 * [BroadcastReceiver] has no coroutine scope of its own, so this pairs [goAsync] (which keeps the
 * process alive past [onReceive] returning) with a short-lived [CoroutineScope] that's closed in
 * a `finally`, the standard pattern for suspending work from a receiver.
 */
class MarkPeriodStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = CycleRepository(AppDatabase.getInstance(appContext).periodDao())
                repository.save(
                    PeriodEntry(id = UUID.randomUUID().toString(), startDate = LocalDate.now().toString(), notes = "")
                )
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID_PERIOD)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
