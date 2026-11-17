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

    /** Finished workouts in the in-app workout log. Never stored in imported_value; derived from workout. */
    WORKOUT_LOG(1),

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

/** How an exercise is logged; decides which set fields are shown. */
enum class ExerciseType(val label: String, val usesWeight: Boolean, val usesReps: Boolean, val usesDuration: Boolean, val usesDistance: Boolean) {
    BARBELL("Barbell", true, true, false, false),
    DUMBBELL("Dumbbell", true, true, false, false),
    MACHINE("Machine / Cable", true, true, false, false),
    WEIGHTED_BODYWEIGHT("Weighted bodyweight", true, true, false, false),
    /** Weight is the assistance (subtracted). */
    ASSISTED_BODYWEIGHT("Assisted bodyweight", true, true, false, false),
    REPS_ONLY("Reps only", false, true, false, false),
    CARDIO("Cardio", false, false, true, true),
    DURATION("Duration", false, false, true, false),
}

enum class BodyPart(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), BICEPS("Biceps"), TRICEPS("Triceps"),
    FOREARMS("Forearms"), LEGS("Legs"), CORE("Core"), NECK("Neck"), FULL_BODY("Full body"), CARDIO("Cardio"), OTHER("Other"),
}

/** Strong set types. Only NORMAL and FAILURE count towards records and best sets. */
enum class SetType(val short: String, val label: String) {
    NORMAL("", "Normal"), WARMUP("W", "Warm-up"), DROP("D", "Drop set"), FAILURE("F", "Failure"),
}

enum class WorkoutSource { USER, STRONG_IMPORT }

/** Body measurements (Strong's list). [max] rejects obvious typos. Order = display order. */
enum class MeasurementPart(val label: String, val unit: String, val group: String, val max: Double = 300.0) {
    BODY_FAT("Body fat", "%", "Body", 70.0),
    NECK("Neck", "cm", "Upper body"),
    SHOULDERS("Shoulders", "cm", "Upper body"),
    CHEST("Chest", "cm", "Upper body"),
    LEFT_BICEP("Left bicep", "cm", "Arms"),
    RIGHT_BICEP("Right bicep", "cm", "Arms"),
    LEFT_FOREARM("Left forearm", "cm", "Arms"),
    RIGHT_FOREARM("Right forearm", "cm", "Arms"),
    UPPER_ABS("Upper abs", "cm", "Core"),
    WAIST("Waist", "cm", "Core"),
    LOWER_ABS("Lower abs", "cm", "Core"),
    HIPS("Hips", "cm", "Core"),
    LEFT_THIGH("Left thigh", "cm", "Legs"),
    RIGHT_THIGH("Right thigh", "cm", "Legs"),
    LEFT_CALF("Left calf", "cm", "Legs"),
    RIGHT_CALF("Right calf", "cm", "Legs"),
}
