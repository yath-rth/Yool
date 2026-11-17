package com.yatharth.yool.ui.workout.session

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yatharth.yool.MainActivity
import com.yatharth.yool.R
import com.yatharth.yool.reminders.ReminderNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single rest countdown. State lives in SharedPreferences (survives process death) and is mirrored in a
 * StateFlow for the UI. An AlarmManager alarm fires [RestTimerReceiver] at the end, which posts the
 * "rest over" notification; while running an ongoing notification shows a countdown chronometer.
 */
object RestTimer {
    const val ACTION_FIRE = "com.yatharth.yool.action.REST_TIMER_FIRE"
    private const val PREFS = "rest_timer"
    private const val ALARM_CODE = 7001
    private const val ONGOING_ID = 7002
    private const val DONE_ID = 7003
    private const val ONGOING_CHANNEL = "workout_rest_running"

    private val flow = MutableStateFlow<RestState?>(null)
    private var loaded = false

    /** Current timer (null when none is running). Loads persisted state on first use. */
    fun state(context: Context): StateFlow<RestState?> {
        ensureLoaded(context)
        return flow.asStateFlow()
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val end = p.getLong("endAt", 0L)
        if (end > System.currentTimeMillis()) {
            flow.value = RestState(end, p.getInt("totalSec", 0), p.getString("label", "") ?: "", p.getBoolean("sound", true), p.getBoolean("vibrate", true))
        } else {
            p.edit().clear().apply()
        }
    }

    private fun save(context: Context, s: RestState?) {
        val e = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (s == null) e.clear() else e.putLong("endAt", s.endAt).putInt("totalSec", s.totalSec).putString("label", s.label)
            .putBoolean("sound", s.sound).putBoolean("vibrate", s.vibrate)
        e.apply()
        flow.value = s
    }

    fun start(context: Context, seconds: Int, label: String, sound: Boolean, vibrate: Boolean) {
        val app = context.applicationContext
        ensureLoaded(app)
        cancelDone(app)
        val s = RestState.start(seconds.coerceAtLeast(1), label, System.currentTimeMillis(), sound, vibrate)
        save(app, s)
        schedule(app, s)
        postOngoing(app, s)
    }

    /** Add/remove seconds on the running timer; ending it if nothing is left. */
    fun adjust(context: Context, deltaSec: Int) {
        val app = context.applicationContext
        ensureLoaded(app)
        val cur = flow.value ?: return
        val next = cur.adjusted(deltaSec, System.currentTimeMillis())
        if (next.remainingSec(System.currentTimeMillis()) <= 0) {
            stop(app)
            return
        }
        save(app, next)
        schedule(app, next)
        postOngoing(app, next)
    }

    /** Skip/cancel: clears state, the alarm and both notifications. */
    fun stop(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        cancelAlarm(app)
        val nm = NotificationManagerCompat.from(app)
        nm.cancel(ONGOING_ID)
        nm.cancel(DONE_ID)
        save(app, null)
    }

    private fun cancelDone(app: Context) = NotificationManagerCompat.from(app).cancel(DONE_ID)

    // ── Alarm ────────────────────────────────────────────────────────────────

    private fun alarmIntent(app: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(app, ALARM_CODE, Intent(app, RestTimerReceiver::class.java).setAction(ACTION_FIRE), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelAlarm(app: Context) {
        val am = app.getSystemService(AlarmManager::class.java)
        alarmIntent(app, PendingIntent.FLAG_NO_CREATE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    private fun schedule(app: Context, s: RestState) {
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.endAt, pi)
        }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private fun openApp(app: Context): PendingIntent {
        val open = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(app, ONGOING_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun postOngoing(app: Context, s: RestState) {
        if (!ReminderNotifier.hasPermission(app)) return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, "Workout rest timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown while resting between sets"
                setSound(null, null)
                enableVibration(false)
            },
        )
        val n = NotificationCompat.Builder(app, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Resting")
            .setContentText(s.label)
            .setContentIntent(openApp(app))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(true)
            .setWhen(s.endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((s.endAt - System.currentTimeMillis() + 60_000).coerceAtLeast(1))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(app).notify(ONGOING_ID, n)
    }

    /** Called by the alarm: ends the timer and posts the high-priority "rest over" notification. */
    fun onFired(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        val s = flow.value ?: return
        // Ignore a stale alarm (the timer was extended after it was set).
        if (System.currentTimeMillis() < s.endAt - 1500) return
        NotificationManagerCompat.from(app).cancel(ONGOING_ID)
        save(app, null)
        if (!ReminderNotifier.hasPermission(app)) return
        val channelId = "workout_rest_done_${if (s.sound) "s" else "n"}${if (s.vibrate) "v" else "n"}"
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Workout rest over", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rest timer finished"
                if (!s.sound) setSound(null, null)
                enableVibration(s.vibrate)
                if (s.vibrate) vibrationPattern = longArrayOf(0, 350, 150, 350)
            },
        )
        val n = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest over")
            .setContentText("Time for your next set${if (s.label.isNotEmpty()) " of ${s.label}" else ""}")
            .setContentIntent(openApp(app))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setTimeoutAfter(5 * 60_000L)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(app).notify(DONE_ID, n)
    }
}

/** Receives the rest-timer alarm. */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RestTimer.ACTION_FIRE) RestTimer.onFired(context)
    }
}
