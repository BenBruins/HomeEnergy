package com.example.energiehuishouding.data

import com.example.energiehuishouding.data.local.DailySummary
import com.example.energiehuishouding.data.local.RatePeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class CostCalculatorTest {

    private val calculator = CostCalculator()

    @Test
    fun test3ScenarioProfitCalculation() {
        // Setup: 1 day with 8 kWh House Use
        // Solar produces 10 kWh
        // Battery stores 10 kWh and releases 8 kWh later (avoiding all import/export)
        
        // h = 10 (Solar) + 0 (NetImp) - 0 (NetExp) - 10 (BatIn) + 8 (BatOut) = 8 kWh.
        
        // Scenario B (Basis): Imp = 8 -> Cost = 8 * 0.40 = 3.20 (No TLK)
        // Scenario A (Alleen Solar): Solar=10. Net = 8 - 10 = -2. Exp = 2. 
        // Pre-2027: Cost A = -2 * 0.40 + 0.84 TLK(fixed) = -0.80 + 0.84 = 0.04
        // Scenario C (Actueel): Net=0. Exp=0. -> Cost = 0 + 0.84 TLK(fixed) = 0.84
        
        // Solar Profit = B - A = 3.20 - 0.04 = 3.16
        // Battery Profit = A - C = 0.04 - 0.84 = -0.80
        
        val rates = listOf(
            RatePeriod(
                id = 1,
                endDate = "2024-12-31",
                tariff = 0.25,
                energyTax = 0.15, // Total price 0.40
                compensation = 0.15,
                tlkRate = 0.84, // 0.84 per day Fixed TLK before 2027
                gridConnection = 0.0,
                taxReduction = 0.0,
                fixedDeliveryCost = 0.0,
                discountPercentage = 0.0,
                isInclusive = true,
                useAbsoluteCalculation = false // Netting mode
            )
        )
        
        val dailySummaries = listOf(
            DailySummary(
                date = "2024-10-20",
                timestamp = 0L,
                solarKwh = 10.0,
                gridImportKwh = 0.0,
                gridExportKwh = 0.0,
                batteryInKwh = 10.0,
                batteryOutKwh = 8.0
            )
        )
        
        val results = calculator.calculate("2024-10-20", "2024-10-20", rates, dailySummaries)
        
        assertEquals(1, results.size)
        val segment = results[0]
        
        assertEquals(3.16, segment.solarProfit, 0.001)
        assertEquals(-0.80, segment.batteryProfit, 0.001)
    }

    @Test
    fun testAbsoluteCalculationMode() {
        val rates = listOf(
            RatePeriod(
                id = 1,
                endDate = "2024-12-31",
                tariff = 0.25,
                energyTax = 0.15,
                compensation = 0.15, // Used as TLV
                tlkRate = 0.10, // Used as TLK per kWh export
                gridConnection = 0.0,
                taxReduction = 0.0,
                fixedDeliveryCost = 0.0,
                discountPercentage = 0.0,
                isInclusive = true,
                useAbsoluteCalculation = true // ABSOLUTE mode
            )
        )
        
        val dailySummaries = listOf(
            DailySummary(
                date = "2024-06-20",
                timestamp = 0L,
                solarKwh = 15.0,
                gridImportKwh = 2.0,
                gridExportKwh = 3.0,
                batteryInKwh = 0.0,
                batteryOutKwh = 0.0
            )
        )
        // Scenario C (Actueel): Imp=2, Exp=3.
        // Price = 0.40. TLV=0.15. TLK(kWh)=0.10.
        // Cost C = (2 * 0.40) - (3 * 0.15) + (3 * 0.10) = 0.80 - 0.45 + 0.30 = 0.65.
        
        val results = calculator.calculate("2024-06-20", "2024-06-20", rates, dailySummaries)
        
        val segment = results[0]
        assertEquals(0.65, segment.energyCost, 0.001)
    }

    @Test
    fun testSalderingStillActiveIn2026() {
        // Full saldering 1-op-1 should still work in 2026
        val rates = listOf(
            RatePeriod(
                id = 1,
                endDate = "2026-12-31",
                tariff = 0.25,
                energyTax = 0.15,
                compensation = 0.05, 
                gridConnection = 0.0,
                taxReduction = 0.0,
                fixedDeliveryCost = 0.0,
                discountPercentage = 0.0,
                isInclusive = true,
                useAbsoluteCalculation = false // Netting mode
            )
        )
        
        val daily = listOf(
            DailySummary(date = "2026-05-01", timestamp = 0L, solarKwh = 200.0, gridImportKwh = 0.0, gridExportKwh = 100.0)
        )
        // h = 200 - 100 = 100.
        // Net = 0 - 100 = -100.
        // Cost = -100 * 0.40 = -40.00
        
        val results = calculator.calculate("2026-05-01", "2026-05-01", rates, daily)
        assertEquals(-40.00, results[0].energyCost, 0.001)
    }
}
