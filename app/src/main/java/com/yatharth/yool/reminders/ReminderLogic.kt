package com.yatharth.yool.reminders

import com.yatharth.yool.domain.ResolvedDay
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The four reminders. [id] is stable (used for prefs keys and PendingIntent request codes). */
enum class ReminderType(
    val id: Int,
    val label: String,
    val title: String,
    val body: String,
    val defaultTime: LocalTime,
) {
    WEIGHT(1, "Log your weight", "Log your weight", "Step on the scale and log today's weight.", LocalTime.of(7, 30)),
    WEIGHT_FOLLOW_UP(2, "Weight follow-up", "Weight still missing", "You haven't logged today's weight yet.", LocalTime.of(10, 0)),
    CHECK_IN(3, "Evening check-in", "Evening check-in", "Log your binge and mood for today.", LocalTime.of(19, 0)),
    CHECK_IN_FOLLOW_UP(4, "Check-in follow-up", "Check-in still open", "Today's check-in isn't done yet.", LocalTime.of(21, 30));

    /** True for the follow-ups that only make sense as a nudge for something still missing. */
    val isFollowUp: Boolean get() = this == WEIGHT_FOLLOW_UP || this == CHECK_IN_FOLLOW_UP

    companion object {
        fun fromId(id: Int): ReminderType? = entries.firstOrNull { it.id == id }
    }
}

/** Whether [type] should actually notify given today's resolved state (null = nothing known, nothing logged). */
fun shouldFire(type: ReminderType, today: ResolvedDay?): Boolean = when (type) {
    ReminderType.WEIGHT, ReminderType.WEIGHT_FOLLOW_UP -> today?.weightKg == null
    ReminderType.CHECK_IN, ReminderType.CHECK_IN_FOLLOW_UP -> today?.checkInDone != true
}

/**
 * Next instant strictly after [now] at which the wall clock reads [time] in [now]'s zone.
 * Built from LocalDate so DST shifts are handled (a time in a spring-forward gap lands just after the gap).
 */
fun nextTrigger(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val zone: ZoneId = now.zone
    val today = now.toLocalDate().atTime(time).atZone(zone)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
}
