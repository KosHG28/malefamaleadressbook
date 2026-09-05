package com.koshg.interlude.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.koshg.interlude.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "cycle-reminders"

/** Local hour the daily reminder check prefers to run at, so a notification (if any fires that
 *  day) lands at a sensible time rather than whenever the setting happened to be toggled on. */
private const val PREFERRED_HOUR = 9

const val REMINDER_CHANNEL_ID = "cycle_reminders"

// Shared between ReminderWorker (posts these notifications) and MarkPeriodStartReceiver (needs
// NOTIFICATION_ID_PERIOD to dismiss the one its action button was tapped on).
const val NOTIFICATION_ID_PERIOD = 1001
const val NOTIFICATION_ID_OVULATION = 1002

/** Schedules (or cancels) the daily WorkManager check behind the opt-in reminders setting --
 *  see [ReminderWorker] for what it actually checks and notifies about. Everything here is local:
 *  no server, no push service, just an on-device periodic job. */
object ReminderScheduler {

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Enables or disables the daily check. Safe to call redundantly (e.g. on every app start, to
     *  self-heal after an upgrade or a permission change) -- enqueueing with
     *  [ExistingPeriodicWorkPolicy.UPDATE] is idempotent and won't reset an already-scheduled run. */
    fun sync(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofHours(24))
            .setInitialDelay(minutesUntilPreferredHour(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun minutesUntilPreferredHour(): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(PREFERRED_HOUR, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes().coerceAtLeast(1)
    }
}
