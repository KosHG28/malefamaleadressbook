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

/** How many days before the predicted period start the reminder fires. Also the width of the
 *  catch-up window: a run missed on the lead day still notifies on any later day up to the
 *  predicted date itself, rather than skipping the cycle. */
private const val PERIOD_REMINDER_LEAD_DAYS = 2L

/** How late an ovulation reminder may still be posted after the predicted day, when the worker
 *  didn't get to run on the day itself. One day: past that the notification stops being useful. */
private const val OVULATION_REMINDER_CATCH_UP_DAYS = 1L

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

        // Both checks look at a window rather than one exact day, and de-duplicate on the
        // predicted date rather than on today: a periodic worker is not guaranteed to run on any
        // given day (doze, battery saver, the phone simply off), and an "is it exactly two days
        // out?" test silently skips the whole cycle whenever that day's run is missed.
        stats.predictedNextPeriod?.let { predicted ->
            val daysUntil = ChronoUnit.DAYS.between(today, predicted)
            val inWindow = daysUntil in 0..PERIOD_REMINDER_LEAD_DAYS
            if (inWindow && preferences.periodReminderNotifiedForEpochDay != predicted.toEpochDay()) {
                val text = when (daysUntil) {
                    0L -> "По прогнозу — сегодня"
                    1L -> "По прогнозу — завтра"
                    else -> "По прогнозу — через $daysUntil дня"
                }
                notifier.notify(NOTIFICATION_ID_PERIOD, buildNotification("Месячные скоро", text))
                preferences.periodReminderNotifiedForEpochDay = predicted.toEpochDay()
            }
        }

        stats.predictedOvulation?.let { predicted ->
            val daysSince = ChronoUnit.DAYS.between(predicted, today)
            val inWindow = daysSince in 0..OVULATION_REMINDER_CATCH_UP_DAYS
            if (inWindow && preferences.ovulationReminderNotifiedForEpochDay != predicted.toEpochDay()) {
                val text = if (daysSince == 0L) {
                    "Сегодня примерный день овуляции, по текущему прогнозу"
                } else {
                    "Вчера был примерный день овуляции, по текущему прогнозу"
                }
                notifier.notify(NOTIFICATION_ID_OVULATION, buildNotification("День овуляции", text))
                preferences.ovulationReminderNotifiedForEpochDay = predicted.toEpochDay()
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
