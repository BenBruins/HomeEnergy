package com.example.energiehuishouding.data

import com.example.energiehuishouding.data.local.DailySummary
import org.junit.Assert.assertEquals
import org.junit.Test

class GasHistoryTest {

    @Test
    fun testGasDeltaCalculationWithSolarOnlyGapRecords() {
        // Simulates a sequence of daily summaries where intermediate days had solar/grid updates but NO gas reading
        val day1 = DailySummary(date = "2024-01-01", timestamp = 1000L, solarKwh = 5.0, absGasM3 = 1000.0)
        val day2SolarOnly = DailySummary(date = "2024-01-02", timestamp = 2000L, solarKwh = 6.0, absGasM3 = 0.0)
        val day3WithGas = DailySummary(date = "2024-01-03", timestamp = 3000L, solarKwh = 4.0, absGasM3 = 1005.0)

        val allDaily = listOf(day1, day2SolarOnly, day3WithGas)

        val updatedDaily = mutableListOf<DailySummary>()
        for (i in allDaily.indices) {
            val cur = allDaily[i]
            val prevGas = if (cur.absGasM3 > 0.001) allDaily.take(i).lastOrNull { it.absGasM3 > 0.001 } else null

            val dGas = if (prevGas != null && prevGas.absGasM3 > 0.001) {
                val diff = cur.absGasM3 - prevGas.absGasM3
                if (diff < 0 || diff > 50.0) cur.gasM3 else diff
            } else cur.gasM3

            updatedDaily.add(cur.copy(gasM3 = dGas))
        }

        // Day 1: no previous gas stand -> gasM3 = 0.0
        assertEquals(0.0, updatedDaily[0].gasM3, 0.001)
        
        // Day 2 (solar only): absGasM3 = 0.0 -> gasM3 preserved as 0.0
        assertEquals(0.0, updatedDaily[1].gasM3, 0.001)

        // Day 3 (gas stand 1005.0): prevGas found as Day 1 (1000.0) -> gasM3 = 5.0
        assertEquals(5.0, updatedDaily[2].gasM3, 0.001)

        // Monthly gas total should be 5.0 m3
        val monthlyTotalGas = updatedDaily.sumOf { it.gasM3 }
        assertEquals(5.0, monthlyTotalGas, 0.001)
    }

    @Test
    fun testGasDailyVerbruikImportPreserved() {
        // Simulates importing gas CSV with daily usage values (deltas) like 2.5, 3.1, 1.8
        val day1 = DailySummary(date = "2024-01-01", timestamp = 1000L, solarKwh = 0.0, gasM3 = 2.5, absGasM3 = 0.0)
        val day2 = DailySummary(date = "2024-01-02", timestamp = 2000L, solarKwh = 0.0, gasM3 = 3.1, absGasM3 = 0.0)
        val day3 = DailySummary(date = "2024-01-03", timestamp = 3000L, solarKwh = 0.0, gasM3 = 1.8, absGasM3 = 0.0)

        val allDaily = listOf(day1, day2, day3)

        val updatedDaily = mutableListOf<DailySummary>()
        for (i in allDaily.indices) {
            val cur = allDaily[i]
            val prevGas = if (cur.absGasM3 > 0.001) allDaily.take(i).lastOrNull { it.absGasM3 > 0.001 } else null

            val dGas = if (prevGas != null && prevGas.absGasM3 > 0.001) {
                val diff = cur.absGasM3 - prevGas.absGasM3
                if (diff < 0 || diff > 50.0) cur.gasM3 else diff
            } else cur.gasM3

            updatedDaily.add(cur.copy(gasM3 = dGas))
        }

        assertEquals(2.5, updatedDaily[0].gasM3, 0.001)
        assertEquals(3.1, updatedDaily[1].gasM3, 0.001)
        assertEquals(1.8, updatedDaily[2].gasM3, 0.001)

        val monthlyGas = updatedDaily.sumOf { it.gasM3 }
        assertEquals(7.4, monthlyGas, 0.001)
    }
}
