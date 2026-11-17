package com.yatharth.yool.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.yatharth.yool.data.model.MeasurementPart
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** One body measurement. At most one per (date, part); logging again the same day replaces it. */
@Entity(tableName = "body_measurement", indices = [Index(value = ["date", "part"], unique = true), Index("part")])
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val part: MeasurementPart,
    /** cm, or % for body fat (see [MeasurementPart.unit]). */
    val value: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM body_measurement ORDER BY date, part")
    fun observeAll(): Flow<List<BodyMeasurement>>

    @Query("SELECT * FROM body_measurement WHERE part = :part ORDER BY date")
    fun observePart(part: MeasurementPart): Flow<List<BodyMeasurement>>

    @Query("SELECT * FROM body_measurement WHERE date = :date")
    suspend fun onDate(date: LocalDate): List<BodyMeasurement>

    @Query("SELECT * FROM body_measurement WHERE date = :date AND part = :part")
    suspend fun find(date: LocalDate, part: MeasurementPart): BodyMeasurement?

    @Query("SELECT * FROM body_measurement ORDER BY date, part")
    suspend fun all(): List<BodyMeasurement>

    @Upsert suspend fun upsert(m: BodyMeasurement)

    @Query("DELETE FROM body_measurement WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM body_measurement WHERE date = :date AND part = :part")
    suspend fun delete(date: LocalDate, part: MeasurementPart)
}
