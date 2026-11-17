# yool backup format (version 4)

A single UTF-8 JSON document. Used by Settings > Data > Backup / Restore and for one-off
history migration (e.g. MyFitnessPal / Google Takeout converted to this schema).

Restore calls `repository.restore(..., overwriteManual = false)`:

- `entries`: only inserted for dates that have **no** manual entry yet (typed data is never clobbered).
- `imported`: upserted by (`date`, `metric`, `source`).
- `lifts`: upserted by (`date`, `exercise`).
- `targets`: inserted unless a target with the same (`metric`, `effectiveFrom`) already exists.
- `foods`: matched by (`name`, `servingLabel`); new ones inserted. File `id`s are remapped to local ids.
- `foodLog`: only inserted for dates that have **no** food logged yet (restoring twice never duplicates).
- `savedMeals`: inserted unless a saved meal with the same `name` exists.
- `exercises`: matched by `name` (case-insensitive); existing exercises are kept as they are.
- `templates`: inserted unless a template with the same `name` exists. Folders are matched by name.
- `workouts`: inserted unless a workout with the same `startedAt` exists (restoring twice never duplicates).

## Top level

| field           | type   | notes                                                                 |
|-----------------|--------|-----------------------------------------------------------------------|
| `formatVersion` | int    | `4` (v1–v3 files still restore). Higher versions are rejected.        |
| `exportedAt`    | string | ISO-8601 instant, informational.                                      |
| `entries`       | array  | optional (default empty). Things the user typed. Objects below.       |
| `imported`      | array  | optional. Externally sourced values.                                  |
| `lifts`         | array  | optional. Best set per exercise per day.                              |
| `targets`       | array  | optional. Versioned targets.                                          |
| `foods`         | array  | optional (v2). Food library.                                          |
| `foodLog`       | array  | optional (v2). Food diary entries.                                    |
| `savedMeals`    | array  | optional (v2). Saved meals.                                           |
| `exercises`     | array  | optional (v3). Exercise library.                                      |
| `templates`     | array  | optional (v3). Workout templates.                                     |
| `workouts`      | array  | optional (v3). Finished workouts, every set.                          |
| `measurements`  | array  | optional (v4). Body measurements.                                     |

Unknown keys are ignored. All dates are ISO `yyyy-MM-dd`. Enums are their exact upper-case names.

## `entries[]` (DailyEntry)

`date` (required); everything else optional/null: `weightKg` (double), `calories` (int), `proteinG` (double),
`steps` (int), `sleepHours` (double), `workoutDone` (bool), `binge` (bool), `bingeTrigger` (string),
`bingeNote` (string), `mood` (int 1..5), `note` (string, max 200), `updatedAt` (epoch millis, default 0).

## `imported[]` (ImportedValue)

| field        | type   | notes                                                                       |
|--------------|--------|-----------------------------------------------------------------------------|
| `date`       | string | required                                                                    |
| `metric`     | string | `WEIGHT_KG`, `CALORIES`, `PROTEIN_G`, `STEPS`, `SLEEP_HOURS`, `WORKOUT`     |
| `source`     | string | `MANUAL`, `MFP`, `HEALTH_CONNECT`, `STRONG_CSV`, `MIGRATED`                 |
| `value`      | number | kg / kcal / g / steps / hours; `WORKOUT` is `1.0`                           |
| `importedAt` | long   | epoch millis, default 0                                                     |

`FOOD_LOG` and `WORKOUT_LOG` are never stored here: they are computed from `foodLog` and `workouts`.

Source priority when several exist for one day+metric: typed (`entries`) > food diary > `MFP` > `HEALTH_CONNECT`/`STRONG_CSV` > `MIGRATED`.
For MFP history use `source: "MFP"`; for anything else (Takeout steps etc.) use `"MIGRATED"` so it only fills gaps.

## `lifts[]` (LiftBestSet)

`date`, `exercise` (string), `weightKg` (double, 0 for bodyweight), `reps` (int). Unique per (`date`, `exercise`).

## `targets[]` (TargetEntry)

`metric` (`WEIGHT_GOAL`, `CALORIES`, `PROTEIN`, `STEPS`, `SLEEP_HOURS`), `value` (double),
`effectiveFrom` (date), `targetDate` (date, optional, only for `WEIGHT_GOAL`).

## `foods[]` (Food) — v2

Nutrients are **per serving**. `id` (long, required) is only a key that `foodLog`/`savedMeals` refer to within the file.
`name`, `servingLabel` (e.g. `"100 g"`, `"1 cup"`), `kcal` required; optional `brand`, `servingGrams` (grams in one serving,
enables logging by weight), `proteinG`, `carbsG`, `fatG`, `fiberG` (default 0), `archived` (bool), `createdAt` (epoch millis),
`source` (`USER` | `MFP_IMPORT`, default `USER`).

## `foodLog[]` (FoodLogEntry) — v2

`date`, `meal` (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACKS`), `name`, `kcal` required; optional `foodId` (a `foods[].id`),
`servingLabel`, `servings` (default 1), `proteinG`, `carbsG`, `fatG`, `fiberG`, `createdAt` (orders entries within a meal).
Nutrients are the **entry totals** (already multiplied by `servings`) as they were when logged.

## `savedMeals[]` — v2

`name`, `items: [{ "foodId": <foods[].id>, "servings": <double> }]`.

## Example

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-11-17T09:30:00Z",
  "entries": [
    { "date": "2026-11-16", "weightKg": 71.4, "binge": false, "mood": 4, "note": "Good day" }
  ],
  "imported": [
    { "date": "2019-05-04", "metric": "CALORIES", "source": "MFP", "value": 2100 },
    { "date": "2019-05-04", "metric": "STEPS", "source": "MIGRATED", "value": 8421 }
  ],
  "lifts": [
    { "date": "2026-11-15", "exercise": "Bench Press", "weightKg": 80.0, "reps": 8 }
  ],
  "targets": [
    { "metric": "CALORIES", "value": 2000, "effectiveFrom": "2026-01-01" },
    { "metric": "WEIGHT_GOAL", "value": 68, "effectiveFrom": "2026-01-01", "targetDate": "2026-06-01" }
  ]
}
```

## Workouts (v3)

`exercises[]`: `name` (required, unique), `type` (`BARBELL`, `DUMBBELL`, `MACHINE`, `WEIGHTED_BODYWEIGHT`,
`ASSISTED_BODYWEIGHT`, `REPS_ONLY`, `CARDIO`, `DURATION`), `bodyPart` (`CHEST`, `BACK`, `SHOULDERS`, `BICEPS`,
`TRICEPS`, `FOREARMS`, `LEGS`, `CORE`, `NECK`, `FULL_BODY`, `CARDIO`, `OTHER`), `restSec` (default rest, optional),
`note`, `archived`, `createdAt`, `source` (`USER`, `STRONG_IMPORT`).

`templates[]`: `name`, `folder` (folder name, optional), `note`, `exercises[]`.

`workouts[]`: `name`, `startedAt`, `endedAt` (epoch millis; the date is derived in the device time zone),
`note`, `source`, `exercises[]`. Only finished workouts are written.

Exercise entries (`exercises[]` inside templates and workouts): `exercise` (exercise name), `supersetGroup`
(exercises sharing a number form a superset), `note`, `sets[]`.

Sets: `type` (`NORMAL`, `WARMUP`, `DROP`, `FAILURE`), `weightKg`, `reps`, `durationSec`, `distanceM`,
`rpe` (workouts only), `restSec` (rest after the set), `completed` (default true), `completedAt`.
Only completed `NORMAL`/`FAILURE` sets count toward records and the Lifts graph.

## Measurements (v4)

`measurements[]`: `date`, `part` (`BODY_FAT`, `NECK`, `SHOULDERS`, `CHEST`, `LEFT_BICEP`, `RIGHT_BICEP`,
`LEFT_FOREARM`, `RIGHT_FOREARM`, `UPPER_ABS`, `WAIST`, `LOWER_ABS`, `HIPS`, `LEFT_THIGH`, `RIGHT_THIGH`,
`LEFT_CALF`, `RIGHT_CALF`), `value` (cm; % for `BODY_FAT`). One per (`date`, `part`); restore only adds
pairs that don't exist yet.
