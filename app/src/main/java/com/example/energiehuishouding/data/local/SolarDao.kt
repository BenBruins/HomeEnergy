package com.example.energiehuishouding.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SolarDao {

    // NEW SUMMARY ARCHITECTURE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummaries(summaries: List<DailySummary>)

    @Query("SELECT * FROM daily_summaries WHERE date = :date LIMIT 1")
    suspend fun getDailySummary(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date = :date LIMIT 1")
    fun getDailySummaryFlow(date: String): Flow<DailySummary?>

    @Query("SELECT * FROM daily_summaries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getDailySummaries(start: Long, end: Long): Flow<List<DailySummary>>

    @Query("SELECT * FROM daily_summaries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getDailySummariesOnce(start: Long, end: Long): List<DailySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlySummaries(summaries: List<MonthlySummary>)

    @Query("SELECT * FROM monthly_summaries WHERE month = :month LIMIT 1")
    suspend fun getMonthlySummary(month: String): MonthlySummary?

    @Query("SELECT * FROM monthly_summaries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getMonthlySummaries(start: Long, end: Long): Flow<List<MonthlySummary>>

    @Query("SELECT * FROM monthly_summaries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getMonthlySummariesOnce(start: Long, end: Long): List<MonthlySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYearlySummaries(summaries: List<YearlySummary>)

    @Query("SELECT * FROM yearly_summaries WHERE year = :year LIMIT 1")
    suspend fun getYearlySummary(year: String): YearlySummary?

    @Query("SELECT * FROM yearly_summaries ORDER BY timestamp ASC")
    fun getYearlySummaries(): Flow<List<YearlySummary>>

    @Query("SELECT * FROM yearly_summaries ORDER BY timestamp ASC")
    suspend fun getAllYearlySummariesOnce(): List<YearlySummary>

    @Query("SELECT * FROM daily_summaries WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDailySummaryBefore(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND (absGridImportKwh > 0 OR absGasM3 > 0) ORDER BY date DESC LIMIT 1")
    fun getLatestAbsoluteStandBefore(date: String): Flow<DailySummary?>

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND (absGridImportKwh > 0 OR absGasM3 > 0) ORDER BY date DESC LIMIT 1")
    fun getLatestAbsoluteStandBeforeOnce(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND absGasM3 > 0.001 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestGasStandBefore(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND absGridImportKwh > 0.001 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestImportStandBefore(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND absGridExportKwh > 0.001 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestExportStandBefore(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND absBatteryInKwh > 0.001 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestBatteryInStandBefore(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries WHERE date < :date AND absBatteryOutKwh > 0.001 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestBatteryOutStandBefore(date: String): DailySummary?

    // FULL CLEANUP
    @androidx.room.Transaction
    suspend fun clearEnergyData() {
        deleteAllDailySummaries()
        deleteAllMonthlySummaries()
        deleteAllYearlySummaries()
    }

    @androidx.room.Transaction
    suspend fun clearAllData() {
        deleteAllDailySummaries()
        deleteAllMonthlySummaries()
        deleteAllYearlySummaries()
    }

    @Query("DELETE FROM daily_summaries WHERE date > :today")
    suspend fun deleteFutureDailySummaries(today: String)

    // CLEANUP
    @Query("DELETE FROM daily_summaries")
    suspend fun deleteAllDailySummaries()

    @Query("DELETE FROM monthly_summaries")
    suspend fun deleteAllMonthlySummaries()

    @Query("DELETE FROM yearly_summaries")
    suspend fun deleteAllYearlySummaries()

    // RATE PERIODS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatePeriod(period: RatePeriod)

    @Query("SELECT * FROM rate_periods ORDER BY endDate ASC")
    fun getAllRatePeriods(): Flow<List<RatePeriod>>

    @Query("SELECT * FROM rate_periods WHERE endDate = :endDate LIMIT 1")
    suspend fun getRatePeriodByDate(endDate: String): RatePeriod?

    @Query("DELETE FROM rate_periods WHERE id = :id")
    suspend fun deleteRatePeriod(id: Long)

    @Query("SELECT * FROM daily_summaries WHERE length(:start) >= 0 AND length(:end) >= 0")
    suspend fun getDailySummariesRangeOnce(start: String, end: String): List<DailySummary>
}
