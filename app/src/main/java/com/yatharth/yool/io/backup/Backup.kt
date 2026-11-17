package com.yatharth.yool.io.backup

import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.data.model.TargetMetric
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** Versioned JSON snapshot of all four tables. Schema documented in docs/backup-format.md. */
@Serializable
data class BackupFile(
    val formatVersion: Int = Backup.FORMAT_VERSION,
    val exportedAt: String,
    val entries: List<EntryDto> = emptyList(),
    val imported: List<ImportedDto> = emptyList(),
    val lifts: List<LiftDto> = emptyList(),
    val targets: List<TargetDto> = emptyList(),
)

@Serializable
data class EntryDto(
    val date: String,
    val weightKg: Double? = null,
    val calories: Int? = null,
    val proteinG: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val workoutDone: Boolean? = null,
    val binge: Boolean? = null,
    val bingeTrigger: String? = null,
    val bingeNote: String? = null,
    val mood: Int? = null,
    val note: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class ImportedDto(
    val date: String,
    val metric: String,
    val source: String,
    val value: Double,
    val importedAt: Long = 0,
)

@Serializable
data class LiftDto(val date: String, val exercise: String, val weightKg: Double, val reps: Int)

@Serializable
data class TargetDto(
    val metric: String,
    val value: Double,
    val effectiveFrom: String,
    val targetDate: String? = null,
)

/** Domain-side contents of a backup. */
data class BackupData(
    val entries: List<DailyEntry>,
    val imported: List<ImportedValue>,
    val lifts: List<LiftBestSet>,
    val targets: List<TargetEntry>,
)

class BackupException(message: String) : Exception(message)

object Backup {
    const val FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(data: BackupData, exportedAt: Instant = Instant.now()): String {
        val file = BackupFile(
            exportedAt = exportedAt.toString(),
            entries = data.entries.sortedBy { it.date }.map {
                EntryDto(
                    it.date.toString(), it.weightKg, it.calories, it.proteinG, it.steps, it.sleepHours,
                    it.workoutDone, it.binge, it.bingeTrigger, it.bingeNote, it.mood, it.note, it.updatedAt,
                )
            },
            imported = data.imported.sortedWith(compareBy({ it.date }, { it.metric }, { it.source })).map {
                ImportedDto(it.date.toString(), it.metric.name, it.source.name, it.value, it.importedAt)
            },
            lifts = data.lifts.sortedWith(compareBy({ it.date }, { it.exercise })).map {
                LiftDto(it.date.toString(), it.exercise, it.weightKg, it.reps)
            },
            targets = data.targets.sortedWith(compareBy({ it.metric }, { it.effectiveFrom })).map {
                TargetDto(it.metric.name, it.value, it.effectiveFrom.toString(), it.targetDate?.toString())
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    fun decode(text: String): BackupData {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text.removePrefix("﻿"))
        } catch (e: Exception) {
            throw BackupException("Not a valid yool backup file (${e.message?.lineSequence()?.firstOrNull() ?: "parse error"}).")
        }
        if (file.formatVersion > FORMAT_VERSION) {
            throw BackupException("Backup format ${file.formatVersion} is newer than this app understands ($FORMAT_VERSION).")
        }
        try {
            return BackupData(
                entries = file.entries.map {
                    DailyEntry(
                        LocalDate.parse(it.date), it.weightKg, it.calories, it.proteinG, it.steps, it.sleepHours,
                        it.workoutDone, it.binge, it.bingeTrigger, it.bingeNote, it.mood, it.note, it.updatedAt,
                    )
                },
                imported = file.imported.map {
                    ImportedValue(LocalDate.parse(it.date), Metric.valueOf(it.metric), Source.valueOf(it.source), it.value, it.importedAt)
                },
                lifts = file.lifts.map { LiftBestSet(LocalDate.parse(it.date), it.exercise, it.weightKg, it.reps) },
                targets = file.targets.map {
                    TargetEntry(
                        metric = TargetMetric.valueOf(it.metric),
                        value = it.value,
                        effectiveFrom = LocalDate.parse(it.effectiveFrom),
                        targetDate = it.targetDate?.let(LocalDate::parse),
                    )
                },
            )
        } catch (e: IllegalArgumentException) {
            throw BackupException("Backup contains an invalid value: ${e.message}")
        } catch (e: java.time.format.DateTimeParseException) {
            throw BackupException("Backup contains an invalid date: ${e.parsedString}")
        }
    }
}
