package com.yatharth.yool.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yatharth.yool.data.model.Meal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodDao {
    // ── Library ──────────────────────────────────────────────────────────────

    /** Active foods whose name or brand contains [query], most-logged first. */
    @Query(
        """SELECT f.* FROM food f
           LEFT JOIN (SELECT foodId, COUNT(*) AS c FROM food_log_entry GROUP BY foodId) u ON u.foodId = f.id
           WHERE f.archived = 0 AND (f.name LIKE '%' || :query || '%' OR IFNULL(f.brand, '') LIKE '%' || :query || '%')
           ORDER BY IFNULL(u.c, 0) DESC, f.name COLLATE NOCASE
           LIMIT :limit""",
    )
    fun search(query: String, limit: Int): Flow<List<Food>>

    /** Every food (including archived), alphabetical — for library management. */
    @Query("SELECT * FROM food ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Food>>

    /** Foods most recently logged (active only). */
    @Query(
        """SELECT f.* FROM food f JOIN food_log_entry e ON e.foodId = f.id
           WHERE f.archived = 0
           GROUP BY f.id ORDER BY MAX(e.date) DESC, MAX(e.createdAt) DESC LIMIT :limit""",
    )
    fun observeRecent(limit: Int): Flow<List<Food>>

    /** Foods logged most often on or after [since] (active only). */
    @Query(
        """SELECT f.* FROM food f JOIN food_log_entry e ON e.foodId = f.id
           WHERE f.archived = 0 AND e.date >= :since
           GROUP BY f.id ORDER BY COUNT(*) DESC, f.name COLLATE NOCASE LIMIT :limit""",
    )
    fun observeFrequent(since: LocalDate, limit: Int): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE id = :id") suspend fun get(id: Long): Food?
    @Query("SELECT * FROM food WHERE id = :id") fun observe(id: Long): Flow<Food?>
    @Query("SELECT * FROM food WHERE name = :name AND servingLabel = :servingLabel") suspend fun findByKey(name: String, servingLabel: String): Food?
    @Query("SELECT * FROM food ORDER BY id") suspend fun all(): List<Food>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(food: Food): Long
    @Update suspend fun update(food: Food)
    @Delete suspend fun delete(food: Food)

    @Query("SELECT COUNT(*) FROM food_log_entry WHERE foodId = :foodId") suspend fun logCount(foodId: Long): Int

    // ── Log ──────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM food_log_entry WHERE date = :date ORDER BY createdAt, id")
    fun observeLog(date: LocalDate): Flow<List<FoodLogEntry>>

    @Query("SELECT * FROM food_log_entry WHERE date = :date AND meal = :meal ORDER BY createdAt, id")
    suspend fun mealEntries(date: LocalDate, meal: Meal): List<FoodLogEntry>

    @Query("SELECT * FROM food_log_entry WHERE id = :id") suspend fun getEntry(id: Long): FoodLogEntry?

    @Query(
        """SELECT date, SUM(kcal) AS kcal, SUM(proteinG) AS proteinG FROM food_log_entry
           WHERE date BETWEEN :from AND :to GROUP BY date""",
    )
    fun observeTotals(from: LocalDate, to: LocalDate): Flow<List<DayFoodTotals>>

    /** Dates in [from]..[to] that have at least one log entry, newest first (for "copy from" pickers). */
    @Query("SELECT DISTINCT date FROM food_log_entry WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeLoggedDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    @Query("SELECT DISTINCT date FROM food_log_entry") suspend fun loggedDates(): List<LocalDate>
    @Query("SELECT * FROM food_log_entry ORDER BY date, createdAt, id") suspend fun allEntries(): List<FoodLogEntry>

    @Insert suspend fun insertEntry(entry: FoodLogEntry): Long
    @Insert suspend fun insertEntries(entries: List<FoodLogEntry>)
    @Update suspend fun updateEntry(entry: FoodLogEntry)
    @Query("DELETE FROM food_log_entry WHERE id = :id") suspend fun deleteEntry(id: Long)

    // ── Saved meals ──────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM saved_meal ORDER BY name COLLATE NOCASE")
    fun observeSavedMeals(): Flow<List<SavedMealWithItems>>

    @Transaction
    @Query("SELECT * FROM saved_meal WHERE id = :id")
    suspend fun getSavedMeal(id: Long): SavedMealWithItems?

    @Transaction
    @Query("SELECT * FROM saved_meal ORDER BY id")
    suspend fun allSavedMeals(): List<SavedMealWithItems>

    @Insert suspend fun insertSavedMeal(meal: SavedMeal): Long
    @Update suspend fun updateSavedMeal(meal: SavedMeal)
    @Query("DELETE FROM saved_meal WHERE id = :id") suspend fun deleteSavedMeal(id: Long)
    @Insert suspend fun insertSavedMealItems(items: List<SavedMealItem>)
    @Query("DELETE FROM saved_meal_item WHERE mealId = :mealId") suspend fun clearSavedMealItems(mealId: Long)
}
