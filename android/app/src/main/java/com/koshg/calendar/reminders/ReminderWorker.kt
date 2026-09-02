package com.koshg.calendar.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.koshg.calendar.R
import com.koshg.calendar.data.AppDatabase
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.settings.CyclePreferences
import com.koshg.calendar.util.computeCycleStats
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How many days before the predicted period start the reminder fires. */
private const val PERIOD_REMINDER_LEAD_DAYS = 2L

private const val NOTIFICATION_ID_PERIOD = 1001
private const val NOTIFICATION_ID_OVULATION = 1002

/**
 * Runs roughly once a day (see [ReminderScheduler]) and checks today's date against the same
 * EWMA-based prediction the calendar itself shows -- no separate model, so a reminder never
 * disagrees with what's on screen. Entirely on-device: reads the local Room database, posts a
 * local notification, nothing leaves the phone.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferences = CyclePreferences(applicationContext)
        if (!preferences.remindersEnabled) return Result.success()

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val repository = CycleRepository(AppDatabase.getInstance(applicationContext).periodDao())
        val periods = repository.periods.first()
        val today = LocalDate.now()
        val stats = computeCycleStats(periods, today = today, lutealPhaseDays = preferences.lutealPhaseDays)
        val notifier = NotificationManagerCompat.from(applicationContext)
        val todayEpochDay = today.toEpochDay()

        stats.predictedNextPeriod?.let { predicted ->
            val daysUntil = ChronoUnit.DAYS.between(today, predicted)
            if (daysUntil == PERIOD_REMINDER_LEAD_DAYS && preferences.lastPeriodReminderEpochDay != todayEpochDay) {
                notifier.notify(
                    NOTIFICATION_ID_PERIOD,
                    buildNotification("Месячные скоро", "По прогнозу — через $PERIOD_REMINDER_LEAD_DAYS дня")
                )
                preferences.lastPeriodReminderEpochDay = todayEpochDay
            }
        }

        stats.predictedOvulation?.let { predicted ->
            if (predicted == today && preferences.lastOvulationReminderEpochDay != todayEpochDay) {
                notifier.notify(
                    NOTIFICATION_ID_OVULATION,
                    buildNotification("День овуляции", "Сегодня примерный день овуляции, по текущему прогнозу")
                )
                preferences.lastOvulationReminderEpochDay = todayEpochDay
            }
        }

        return Result.success()
    }

    private fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
}
