package com.example.energiehuishouding.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summaries")
data class DailySummary(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val timestamp: Long,
    val solarKwh: Double,
    val gridImportKwh: Double = 0.0,
    val gridExportKwh: Double = 0.0,
    val batteryInKwh: Double = 0.0,
    val batteryOutKwh: Double = 0.0,
    val gasM3: Double = 0.0,
    // Absolute Lifetime Stands
    val absSolarKwh: Double = 0.0,
    val absGridImportKwh: Double = 0.0,
    val absGridExportKwh: Double = 0.0,
    val absBatteryInKwh: Double = 0.0,
    val absBatteryOutKwh: Double = 0.0,
    val absGasM3: Double = 0.0,
    // Raw readings and accumulated offsets let cumulative meters restart at zero
    // without breaking lifetime totals when a meter is replaced.
    val rawGridImportKwh: Double = 0.0,
    val rawGridExportKwh: Double = 0.0,
    val rawGasM3: Double = 0.0,
    val gridImportOffsetKwh: Double = 0.0,
    val gridExportOffsetKwh: Double = 0.0,
    val gasOffsetM3: Double = 0.0,
    // Exact P1 sample time and the absolute counters at the start of this local day.
    // A zero baseline timestamp means the daily grid totals are not yet trustworthy.
    val p1SampleTimestamp: Long = 0,
    val p1DayBaselineTimestamp: Long = 0,
    val p1DayStartImportAbsKwh: Double = 0.0,
    val p1DayStartExportAbsKwh: Double = 0.0,
    val p1DayFinalized: Boolean = false
)

@Entity(tableName = "monthly_summaries")
data class MonthlySummary(
    @PrimaryKey val month: String, // yyyy-MM
    val timestamp: Long,
    val solarKwh: Double,
    val gridImportKwh: Double = 0.0,
    val gridExportKwh: Double = 0.0,
    val batteryInKwh: Double = 0.0,
    val batteryOutKwh: Double = 0.0,
    val gasM3: Double = 0.0,
    // Absolute Lifetime Stands (as of end of month)
    val absSolarKwh: Double = 0.0,
    val absGridImportKwh: Double = 0.0,
    val absGridExportKwh: Double = 0.0,
    val absBatteryInKwh: Double = 0.0,
    val absBatteryOutKwh: Double = 0.0,
    val absGasM3: Double = 0.0
)

@Entity(tableName = "yearly_summaries")
data class YearlySummary(
    @PrimaryKey val year: String, // yyyy
    val timestamp: Long,
    val solarKwh: Double,
    val gridImportKwh: Double = 0.0,
    val gridExportKwh: Double = 0.0,
    val batteryInKwh: Double = 0.0,
    val batteryOutKwh: Double = 0.0,
    val gasM3: Double = 0.0,
    // Absolute Lifetime Stands (as of end of year)
    val absSolarKwh: Double = 0.0,
    val absGridImportKwh: Double = 0.0,
    val absGridExportKwh: Double = 0.0,
    val absBatteryInKwh: Double = 0.0,
    val absBatteryOutKwh: Double = 0.0,
    val absGasM3: Double = 0.0
)
