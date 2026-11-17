package com.yatharth.yool.data.model

/** Metrics that can come from an external source (and be overridden manually). */
enum class Metric {
    WEIGHT_KG,
    CALORIES,
    PROTEIN_G,
    STEPS,
    SLEEP_HOURS,

    /** 1.0 = a workout happened that day. */
    WORKOUT,
}

/**
 * Where a value came from. Lower [priority] wins when several sources have a value for the same day+metric.
 * Agreed rule: typed by user > yool food log > MyFitnessPal > other Health Connect > one-off migrated history.
 * Priorities are code-only (Room stores the enum name), so they can be renumbered freely.
 */
enum class Source(val priority: Int) {
    MANUAL(0),

    /** Totals of the in-app food diary. Never stored in imported_value; derived from food_log_entry. */
    FOOD_LOG(1),

    /** MyFitnessPal — live via Health Connect (dataOrigin com.myfitnesspal.android) or migrated MFP history. */
    MFP(2),

    /** Any other Health Connect origin (Google Fit steps, Mi Fitness sleep, exercise sessions). */
    HEALTH_CONNECT(3),

    /** Strong CSV import (workout done flag). */
    STRONG_CSV(3),

    /** One-off migrated history other than MFP (e.g. Google Takeout steps). Only fills gaps. */
    MIGRATED(4),
}

/** Diary meal sections, in display order. */
enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks"),
}

/** Where a library food came from. */
enum class FoodSource { USER, MFP_IMPORT }

/** Metrics that can have a target. Targets are versioned by effective date. */
enum class TargetMetric(val label: String, val unit: String) {
    WEIGHT_GOAL("Goal weight", "kg"),
    CALORIES("Calories", "kcal"),
    PROTEIN("Protein", "g"),
    STEPS("Steps", "steps"),
    SLEEP_HOURS("Sleep", "h"),
}

/** Preset binge trigger tags; users can also type a custom one. */
val DefaultBingeTriggers = listOf("Stress", "Social", "Boredom", "Tired")
