package com.yatharth.yool.data

import androidx.room.withTransaction
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.FoodLogEntry
import com.yatharth.yool.data.db.SavedMeal
import com.yatharth.yool.data.db.SavedMealItem
import com.yatharth.yool.data.db.SavedMealWithItems
import com.yatharth.yool.data.db.YoolDatabase
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.domain.FoodMath
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Food library, food diary and saved meals. Daily totals feed [TrackerRepository.observeDays] as source FOOD_LOG. */
class FoodRepository(private val db: YoolDatabase) {
    private val dao = db.foodDao()

    // ── Library ──────────────────────────────────────────────────────────────

    fun search(query: String, limit: Int = 100): Flow<List<Food>> = dao.search(query.trim(), limit)
    fun observeAllFoods(): Flow<List<Food>> = dao.observeAll()
    fun observeRecent(limit: Int = 15): Flow<List<Food>> = dao.observeRecent(limit)
    fun observeFrequent(limit: Int = 15, days: Long = 60): Flow<List<Food>> =
        dao.observeFrequent(LocalDate.now().minusDays(days), limit)
    fun observeFood(id: Long): Flow<Food?> = dao.observe(id)
    suspend fun getFood(id: Long): Food? = dao.get(id)

    /** Insert (id 0) or update a food. Returns its id. Throws [DuplicateFoodException] if name+serving already exists. */
    suspend fun saveFood(food: Food): Long {
        val clean = food.copy(name = food.name.trim(), brand = food.brand?.trim()?.ifEmpty { null }, servingLabel = food.servingLabel.trim())
        val clash = dao.findByKey(clean.name, clean.servingLabel)
        if (clash != null && clash.id != clean.id) throw DuplicateFoodException(clean.name, clean.servingLabel)
        return if (clean.id == 0L) dao.insert(clean) else clean.id.also { dao.update(clean) }
    }

    /** Foods that were ever logged are archived (history keeps its snapshot); unused foods are deleted. */
    suspend fun removeFood(id: Long) {
        val food = dao.get(id) ?: return
        if (dao.logCount(id) > 0) dao.update(food.copy(archived = true)) else dao.delete(food)
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.get(id)?.let { dao.update(it.copy(archived = archived)) }
    }

    // ── Diary ────────────────────────────────────────────────────────────────

    fun observeLog(date: LocalDate): Flow<List<FoodLogEntry>> = dao.observeLog(date)

    /** Dates with any food logged in the last [days] days (newest first). */
    fun observeLoggedDates(days: Long = 120): Flow<List<LocalDate>> =
        dao.observeLoggedDates(LocalDate.now().minusDays(days), LocalDate.now().plusDays(1))

    suspend fun log(food: Food, date: LocalDate, meal: Meal, servings: Double): Long =
        dao.insertEntry(FoodMath.entryFor(food, date, meal, servings))

    suspend fun setServings(entryId: Long, servings: Double) {
        dao.getEntry(entryId)?.let { dao.updateEntry(FoodMath.rescale(it, servings)) }
    }

    suspend fun moveEntry(entryId: Long, meal: Meal) {
        dao.getEntry(entryId)?.let { dao.updateEntry(it.copy(meal = meal)) }
    }

    suspend fun deleteEntry(entryId: Long) = dao.deleteEntry(entryId)

    /** Append a copy of [fromDate]'s [fromMeal] to [toDate]/[toMeal]. Returns how many entries were copied. */
    suspend fun copyMeal(fromDate: LocalDate, fromMeal: Meal, toDate: LocalDate, toMeal: Meal): Int {
        val src = dao.mealEntries(fromDate, fromMeal)
        if (src.isNotEmpty()) dao.insertEntries(FoodMath.copyTo(src, toDate, toMeal))
        return src.size
    }

    // ── Saved meals ──────────────────────────────────────────────────────────

    fun observeSavedMeals(): Flow<List<SavedMealWithItems>> = dao.observeSavedMeals()
    suspend fun getSavedMeal(id: Long): SavedMealWithItems? = dao.getSavedMeal(id)

    /** Create (id 0) or replace a saved meal and its items. Returns its id. */
    suspend fun saveSavedMeal(id: Long, name: String, items: List<Pair<Long, Double>>): Long = db.withTransaction {
        val mealId = if (id == 0L) dao.insertSavedMeal(SavedMeal(name = name.trim()))
        else id.also { dao.updateSavedMeal(SavedMeal(id = id, name = name.trim())) }
        dao.clearSavedMealItems(mealId)
        dao.insertSavedMealItems(items.map { (foodId, servings) -> SavedMealItem(mealId = mealId, foodId = foodId, servings = servings) })
        mealId
    }

    /** Save a diary meal section as a saved meal (entries whose food no longer exists are skipped). */
    suspend fun saveMealFromDiary(date: LocalDate, meal: Meal, name: String): Long {
        val items = dao.mealEntries(date, meal).mapNotNull { e -> e.foodId?.takeIf { dao.get(it) != null }?.let { it to e.servings } }
        return saveSavedMeal(0, name, items)
    }

    suspend fun deleteSavedMeal(id: Long) = dao.deleteSavedMeal(id)

    /** Log every item of a saved meal into [date]/[meal]. */
    suspend fun logSavedMeal(savedMealId: Long, date: LocalDate, meal: Meal) {
        val saved = dao.getSavedMeal(savedMealId) ?: return
        val now = System.currentTimeMillis()
        dao.insertEntries(saved.items.mapIndexed { i, it -> FoodMath.entryFor(it.food, date, meal, it.item.servings, now + i) })
    }

    // ── Backup ───────────────────────────────────────────────────────────────

    suspend fun allFoods() = dao.all()
    suspend fun allLogEntries() = dao.allEntries()
    suspend fun allSavedMeals() = dao.allSavedMeals()

    /**
     * Merge a backup. Foods are matched by (name, servingLabel) and backup ids remapped to local ids.
     * Log entries are only added for dates that have no food logged yet, so restoring twice never duplicates.
     */
    suspend fun restore(foods: List<Food>, log: List<FoodLogEntry>, savedMeals: List<Pair<String, List<Pair<Long, Double>>>>) {
        db.withTransaction {
            val idMap = HashMap<Long, Long>()
            for (f in foods) {
                val existing = dao.findByKey(f.name, f.servingLabel)
                idMap[f.id] = existing?.id ?: dao.insert(f.copy(id = 0))
            }
            val logged = dao.loggedDates().toSet()
            dao.insertEntries(log.filter { it.date !in logged }.map { it.copy(id = 0, foodId = it.foodId?.let(idMap::get)) })
            val existingNames = dao.allSavedMeals().map { it.meal.name }.toSet()
            for ((name, items) in savedMeals) {
                if (name in existingNames) continue
                val mapped = items.mapNotNull { (fid, s) -> idMap[fid]?.let { it to s } }
                val mealId = dao.insertSavedMeal(SavedMeal(name = name))
                dao.insertSavedMealItems(mapped.map { (fid, s) -> SavedMealItem(mealId = mealId, foodId = fid, servings = s) })
            }
        }
    }
}

class DuplicateFoodException(name: String, serving: String) : Exception("\"$name\" ($serving) is already in your foods.")
