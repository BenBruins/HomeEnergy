package com.example.energiehuishouding.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DailySummary::class,
        MonthlySummary::class,
        YearlySummary::class,
        RatePeriod::class
    ], 
    version = 25, 
    exportSchema = false
)
abstract class SolarDatabase : RoomDatabase() {
    abstract fun solarDao(): SolarDao

    companion object {
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN isInclusive INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN variableFeedInCost REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove feedInCost column
                db.execSQL("""
                    CREATE TABLE rate_periods_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        endDate TEXT NOT NULL,
                        tariff REAL NOT NULL,
                        energyTax REAL NOT NULL DEFAULT 0.0,
                        compensation REAL NOT NULL,
                        variableFeedInCost REAL NOT NULL DEFAULT 0.0,
                        gridConnection REAL NOT NULL,
                        taxReduction REAL NOT NULL,
                        fixedDeliveryCost REAL NOT NULL,
                        discountPercentage REAL NOT NULL DEFAULT 0.0,
                        isInclusive INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO rate_periods_new (
                        id, endDate, tariff, energyTax, compensation, variableFeedInCost, 
                        gridConnection, taxReduction, fixedDeliveryCost, discountPercentage, isInclusive
                    )
                    SELECT 
                        id, endDate, tariff, energyTax, compensation, variableFeedInCost, 
                        gridConnection, taxReduction, fixedDeliveryCost, discountPercentage, isInclusive
                    FROM rate_periods
                """.trimIndent())
                
                db.execSQL("DROP TABLE rate_periods")
                db.execSQL("ALTER TABLE rate_periods_new RENAME TO rate_periods")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename variableFeedInCost to tlkRate
                db.execSQL("""
                    CREATE TABLE rate_periods_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        endDate TEXT NOT NULL,
                        tariff REAL NOT NULL,
                        energyTax REAL NOT NULL DEFAULT 0.0,
                        compensation REAL NOT NULL,
                        tlkRate REAL NOT NULL DEFAULT 0.0,
                        gridConnection REAL NOT NULL,
                        taxReduction REAL NOT NULL,
                        fixedDeliveryCost REAL NOT NULL,
                        discountPercentage REAL NOT NULL DEFAULT 0.0,
                        isInclusive INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO rate_periods_new (
                        id, endDate, tariff, energyTax, compensation, tlkRate, 
                        gridConnection, taxReduction, fixedDeliveryCost, discountPercentage, isInclusive
                    )
                    SELECT 
                        id, endDate, tariff, energyTax, compensation, variableFeedInCost, 
                        gridConnection, taxReduction, fixedDeliveryCost, discountPercentage, isInclusive
                    FROM rate_periods
                """.trimIndent())
                
                db.execSQL("DROP TABLE rate_periods")
                db.execSQL("ALTER TABLE rate_periods_new RENAME TO rate_periods")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN useAbsoluteCalculation INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN gasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN absGasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE monthly_summaries ADD COLUMN gasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE monthly_summaries ADD COLUMN absGasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE yearly_summaries ADD COLUMN gasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE yearly_summaries ADD COLUMN absGasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN gasTariff REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN gasTax REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN gasConnection REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rate_periods ADD COLUMN fixedGasDeliveryCost REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename fixedGasDeliveryCost to variableGasDeliveryCost
                db.execSQL("ALTER TABLE rate_periods RENAME COLUMN fixedGasDeliveryCost TO variableGasDeliveryCost")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN rawGridImportKwh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN rawGridExportKwh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN rawGasM3 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN gridImportOffsetKwh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN gridExportOffsetKwh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN gasOffsetM3 REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN p1SampleTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN p1DayBaselineTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN p1DayStartImportAbsKwh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN p1DayStartExportAbsKwh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN p1DayFinalized INTEGER NOT NULL DEFAULT 0")
                // Old P1 day deltas were inferred from possibly stale once-a-day counters.
                // Do not present those as reliable calendar-day totals.
                db.execSQL("UPDATE daily_summaries SET gridImportKwh = 0.0 WHERE rawGridImportKwh > 0.001")
                db.execSQL("UPDATE daily_summaries SET gridExportKwh = 0.0 WHERE rawGridExportKwh > 0.001")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_summaries_timestamp ON daily_summaries (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_summaries_date ON daily_summaries (date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_summaries_timestamp ON monthly_summaries (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_summaries_month ON monthly_summaries (month)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_yearly_summaries_timestamp ON yearly_summaries (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_yearly_summaries_year ON yearly_summaries (year)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rate_periods_endDate ON rate_periods (endDate)")
            }
        }

        @Volatile
        private var INSTANCE: SolarDatabase? = null

        fun getDatabase(context: Context): SolarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SolarDatabase::class.java,
                    "solar_database"
                )
                .addMigrations(
                    MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, 
                    MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                    MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
