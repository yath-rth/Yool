package com.yatharth.yool.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Schedules one inexact, idle-allowed alarm per enabled reminder; each firing schedules the next day's. */
object ReminderScheduler {
    const val ACTION_FIRE = "com.yatharth.yool.action.REMINDER_FIRE"
    const val EXTRA_TYPE = "reminder_type"

    private fun pendingIntent(context: Context, type: ReminderType, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_TYPE, type.id)
        return PendingIntent.getBroadcast(context, type.id, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancel(context: Context, type: ReminderType) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        pendingIntent(context, type, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    /** Schedule [type] for its next occurrence (or cancel it when disabled). */
    fun schedule(context: Context, type: ReminderType, setting: ReminderSetting) {
        if (!setting.enabled) {
            cancel(context, type)
            return
        }
        val at = nextTrigger(ZonedDateTime.now(), setting.time)
        val pi = pendingIntent(context, type, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
    }

    suspend fun rescheduleAll(context: Context) {
        ReminderPrefs(context).current().forEach { (type, setting) -> schedule(context, type, setting) }
    }

    /** Fire-and-forget variant for Application.onCreate. */
    fun rescheduleAllAsync(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { rescheduleAll(app) }
    }
}
