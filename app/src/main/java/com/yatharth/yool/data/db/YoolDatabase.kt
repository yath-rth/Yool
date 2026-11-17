package com.yatharth.yool.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
}

@Dao
interface DailyEntryDao {
    @Query("SELECT * FROM daily_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailyEntry>>

    @Query("SELECT * FROM daily_entry WHERE date = :date")
    fun observe(date: LocalDate): Flow<DailyEntry?>

    @Query("SELECT * FROM daily_entry WHERE date = :date")
    suspend fun get(date: LocalDate): DailyEntry?

    @Query("SELECT * FROM daily_entry ORDER BY date")
    suspend fun all(): List<DailyEntry>

    @Upsert suspend fun upsert(entry: DailyEntry)
    @Upsert suspend fun upsertAll(entries: List<DailyEntry>)
}

@Dao
interface ImportedValueDao {
    @Query("SELECT * FROM imported_value WHERE date BETWEEN :from AND :to")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ImportedValue>>

    @Query("SELECT * FROM imported_value ORDER BY date")
    suspend fun all(): List<ImportedValue>

    @Upsert suspend fun upsertAll(values: List<ImportedValue>)

    @Query("DELETE FROM imported_value WHERE source = :source AND metric = :metric AND date BETWEEN :from AND :to")
    suspend fun deleteRange(source: Source, metric: Metric, from: LocalDate, to: LocalDate)
}

@Dao
interface LiftDao {
    @Query("SELECT * FROM lift_best_set WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<LiftBestSet>>

    /** Most frequently trained first. */
    @Query("SELECT exercise FROM lift_best_set GROUP BY exercise ORDER BY COUNT(*) DESC, exercise")
    fun observeExercises(): Flow<List<String>>

    @Query("SELECT * FROM lift_best_set ORDER BY date")
    suspend fun all(): List<LiftBestSet>

    @Upsert suspend fun upsertAll(sets: List<LiftBestSet>)
}

@Dao
interface TargetDao {
    @Query("SELECT * FROM target ORDER BY metric, effectiveFrom")
    fun observeAll(): Flow<List<TargetEntry>>

    @Query("SELECT * FROM target ORDER BY metric, effectiveFrom")
    suspend fun all(): List<TargetEntry>

    @Upsert suspend fun upsert(target: TargetEntry)
    @Upsert suspend fun upsertAll(targets: List<TargetEntry>)

    @Query("DELETE FROM target WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [
        DailyEntry::class, ImportedValue::class, LiftBestSet::class, TargetEntry::class,
        Food::class, FoodLogEntry::class, SavedMeal::class, SavedMealItem::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)], // v2: food library, food log, saved meals
)
@TypeConverters(Converters::class)
abstract class YoolDatabase : RoomDatabase() {
    abstract fun dailyEntryDao(): DailyEntryDao
    abstract fun importedValueDao(): ImportedValueDao
    abstract fun liftDao(): LiftDao
    abstract fun targetDao(): TargetDao
    abstract fun foodDao(): FoodDao

    companion object {
        fun build(context: Context): YoolDatabase =
            Room.databaseBuilder(context, YoolDatabase::class.java, "yool.db").build()
    }
}
