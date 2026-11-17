package com.yatharth.yool.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yatharth.yool.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles alarm firings (check state, maybe notify, schedule next day) and the system events that
 * invalidate alarms (boot, time/zone change, app update), which just reschedule everything.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == ReminderScheduler.ACTION_FIRE) {
                    handleFire(app, intent)
                } else {
                    ReminderScheduler.rescheduleAll(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleFire(context: Context, intent: Intent) {
        val type = ReminderType.fromId(intent.getIntExtra(ReminderScheduler.EXTRA_TYPE, -1)) ?: return
        val setting = ReminderPrefs(context).current().getValue(type)
        if (!setting.enabled) return
        // Always queue tomorrow first so a failure below can't end the chain.
        ReminderScheduler.schedule(context, type, setting)
        val day = runCatching { context.appContainer.repository.observeDay(LocalDate.now()).first() }.getOrNull()
        if (shouldFire(type, day)) ReminderNotifier.show(context, type)
    }
}
