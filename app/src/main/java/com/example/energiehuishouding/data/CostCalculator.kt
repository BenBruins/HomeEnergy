package com.example.energiehuishouding.data

import com.example.energiehuishouding.data.local.DailySummary
import com.example.energiehuishouding.data.local.RatePeriod
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.round

data class CostBreakdown(
    val startDate: String,
    val endDate: String,
    val totalImportKwh: Double,
    val totalExportKwh: Double,
    val totalGasM3: Double = 0.0,
    val netKwh: Double,
    val energyCost: Double,      // Variable energy part
    val gasCost: Double = 0.0,   // Variable gas part
    val fixedEnergyCost: Double = 0.0,
    val fixedGasCost: Double = 0.0,
    val totalCost: Double,
    val solarProfit: Double = 0.0,
    val batteryProfit: Double = 0.0,
    val days: Int,
    val label: String = ""
)

class CostCalculator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun toIso(date: String): String {
        return try {
            val clean = date.trim().split(" ")[0].replace(".", "-").replace("/", "-")
            if (clean.isEmpty()) return ""
            val parts = clean.split("-")
            if (parts.size == 3) {
                if (parts[0].length == 4) "${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
                else {
                    val y = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
                    "$y-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
                }
            } else clean
        } catch (_: Exception) { date }
    }

    private fun roundPrice(value: Double): Double = round(value * 100.0) / 100.0

    fun calculate(
        requestedStart: String,
        requestedEnd: String,
        rates: List<RatePeriod>,
        dailySummaries: List<DailySummary>
    ): List<CostBreakdown> {
        val startIso = toIso(requestedStart)
        val endIso = toIso(requestedEnd)
        
        if (rates.isEmpty() || dailySummaries.isEmpty()) return emptyList()

        val sortedRates = rates.map { it.copy(endDate = toIso(it.endDate)) }.sortedBy { it.endDate }
        val allSummaries = dailySummaries.sortedBy { it.date }
        val maxDbDate = allSummaries.lastOrNull()?.date ?: dateFormat.format(Date())
        val effectiveEnd = if (endIso > maxDbDate) maxDbDate else endIso

        val segmentSpecs = mutableListOf<Triple<String, String, RatePeriod>>()
        var current = startIso
        var iterations = 0
        while (current <= effectiveEnd && iterations < 500) {
            iterations++
            val rate = sortedRates.find { it.endDate >= current } ?: sortedRates.last()
            var segmentEnd = if (rate.endDate < effectiveEnd) rate.endDate else effectiveEnd
            val yearEnd = "${current.take(4)}-12-31"
            if (yearEnd < segmentEnd) segmentEnd = yearEnd
            val contractEndStr = if (current <= "${current.take(4)}-10-17") "${current.take(4)}-10-17" else "${current.take(4).toInt() + 1}-10-17"
            if (contractEndStr < segmentEnd) segmentEnd = contractEndStr

            segmentSpecs.add(Triple(current, segmentEnd, rate))
            val nextDay = getNextDay(segmentEnd)
            if (nextDay <= segmentEnd) break
            current = nextDay
        }

        val results = mutableListOf<CostBreakdown>()
        val yearlyDiscountPot = mutableMapOf<String, Double>()
        val yearlyDiscountPct = mutableMapOf<String, Double>()
        val yearlyNetKwhPot = mutableMapOf<String, Double>()

        val startBaseline = findClosestStand(allSummaries, startIso)
        var lastImp = startBaseline.first
        var lastExp = startBaseline.second
        var lastGas = startBaseline.third

        segmentSpecs.forEach { (start, end, rate) ->
            val isPost2027 = start >= "2027-01-01"
            val endPoint = findClosestStand(allSummaries, getNextDay(end))
            val segmentSummaries = allSummaries.filter { it.date in start..end }
            
            val hasAbsStands = endPoint.first > 0.001 || lastImp > 0.001 || endPoint.third > 0.001 || lastGas > 0.001
            
            val sImp = if (hasAbsStands) (endPoint.first - lastImp).coerceAtLeast(0.0) else segmentSummaries.sumOf { it.gridImportKwh }
            val sExp = if (hasAbsStands) (endPoint.second - lastExp).coerceAtLeast(0.0) else segmentSummaries.sumOf { it.gridExportKwh }
            val sGas = if (hasAbsStands) (endPoint.third - lastGas).coerceAtLeast(0.0) else segmentSummaries.sumOf { it.gasM3 }
            
            lastImp = endPoint.first; lastExp = endPoint.second; lastGas = endPoint.third

            val days = calculateDaysBetween(start, end) + 1
            val isAbs = rate.useAbsoluteCalculation || isPost2027

            val totalSolar = segmentSummaries.sumOf { it.solarKwh }
            val totalBatIn = segmentSummaries.sumOf { it.batteryInKwh }
            val totalBatOut = segmentSummaries.sumOf { it.batteryOutKwh }

            val varEnergyRow: Double
            val fixedTLK: Double
            val varTLK: Double
            val exportCredit: Double

            val energyTariff = if (isAbs) sImp * rate.tariff else (sImp - sExp) * rate.tariff
            if (isAbs) {
                val energyTax = sImp * rate.energyTax
                exportCredit = sExp * rate.compensation
                varTLK = sExp * rate.tlkRate
                fixedTLK = 0.0
                varEnergyRow = energyTariff + energyTax - exportCredit + varTLK
            } else {
                val energyTax = (sImp - sExp) * rate.energyTax
                exportCredit = 0.0
                varTLK = 0.0
                fixedTLK = days * rate.tlkRate
                varEnergyRow = energyTariff + energyTax
            }

            fun calcEnergyCostOnly(imp: Double, exp: Double): Double {
                return if (isAbs) {
                    (imp * (rate.tariff + rate.energyTax)) - (exp * rate.compensation) + (exp * rate.tlkRate)
                } else {
                    ((imp - exp) * (rate.tariff + rate.energyTax)) + (days * rate.tlkRate)
                }
            }

            val houseUse = (totalSolar + sImp - sExp - totalBatIn + totalBatOut).coerceAtLeast(0.0)
            
            // Scenario B (no solar, no battery)
            val impB = houseUse
            val costB = if (isAbs) impB * (rate.tariff + rate.energyTax) else impB * (rate.tariff + rate.energyTax)

            // Scenario A (solar, no battery)
            val impA = (houseUse - totalSolar).coerceAtLeast(0.0)
            val expA = (totalSolar - houseUse).coerceAtLeast(0.0)
            val costA = calcEnergyCostOnly(impA, expA)

            // Scenario C (actual with battery)
            val costC = calcEnergyCostOnly(sImp, sExp)

            val sProfit = roundPrice(costB - costA)
            val bProfit = roundPrice(costA - costC)

            val gasTariffPart = sGas * rate.gasTariff
            val gasTaxPart = sGas * rate.gasTax
            
            val fixedAE = days * rate.gridConnection
            val fixedVLK = days * rate.fixedDeliveryCost
            val reductionVEB = days * abs(rate.taxReduction)
            
            val gasFixed = days * rate.gasConnection
            val gasVLK = days * rate.variableGasDeliveryCost 

            val fixedElek = fixedAE + fixedVLK - reductionVEB + fixedTLK + varTLK
            val fixedGas = gasFixed + gasVLK
            val varGasRow = gasTariffPart + gasTaxPart

            results.add(CostBreakdown(
                startDate = start, endDate = end,
                totalImportKwh = sImp, totalExportKwh = sExp, totalGasM3 = sGas, netKwh = sImp - sExp,
                energyCost = roundPrice(varEnergyRow), 
                gasCost = roundPrice(varGasRow), 
                fixedEnergyCost = roundPrice(fixedElek),
                fixedGasCost = roundPrice(fixedGas),
                totalCost = roundPrice(varEnergyRow + fixedElek + varGasRow + fixedGas),
                solarProfit = sProfit,
                batteryProfit = bProfit,
                days = days, label = "Segment ${start.substring(5)} t/m ${end.substring(5)}"
            ))

            val contractYear = if (start.substring(5) >= "10-18") start.take(4) else (start.take(4).toInt() - 1).toString()
            yearlyNetKwhPot[contractYear] = (yearlyNetKwhPot[contractYear] ?: 0.0) + (sImp - sExp)
            val calendarYear = start.take(4)
            val thuisvoordeelKey = "${contractYear}_$calendarYear"
            if (rate.discountPercentage > 0) {
                yearlyDiscountPot[thuisvoordeelKey] = (yearlyDiscountPot[thuisvoordeelKey] ?: 0.0) + energyTariff
                yearlyDiscountPct[thuisvoordeelKey] = rate.discountPercentage
            }
        }

        yearlyNetKwhPot.keys.sorted().forEach { contractYear ->
            val netKwhYear = yearlyNetKwhPot[contractYear] ?: 0.0
            if (netKwhYear < -0.01 && contractYear < "2027") {
                val cEnd = "$contractYear-10-17"
                val lastRate = sortedRates.findLast { it.endDate >= cEnd } ?: sortedRates.last()
                val currentBasisYear = yearlyDiscountPot.filterKeys { it.startsWith("${contractYear}_") }.values.sum()
                val targetBasisYear = roundPrice(abs(netKwhYear) * lastRate.compensation)
                val correction = targetBasisYear - currentBasisYear
                results.add(CostBreakdown(
                    startDate = "$contractYear-10-18", endDate = "${contractYear.toInt()+1}-10-17",
                    totalImportKwh = 0.0, totalExportKwh = 0.0, netKwh = 0.0,
                    energyCost = correction, totalCost = correction, days = 0, label = "Correctie Overschot Contractjaar $contractYear"
                ))
                val lastPartKey = "${contractYear}_${contractYear.toInt() + 1}"
                yearlyDiscountPot[lastPartKey] = (yearlyDiscountPot[lastPartKey] ?: 0.0) + correction
            }
        }

        yearlyDiscountPot.keys.sorted().forEach { key ->
            val basis = yearlyDiscountPot[key] ?: 0.0
            val pct = yearlyDiscountPct[key] ?: 0.0
            if (pct > 0 && basis > 0.001) {
                val parts = key.split("_"); val cYear = parts[0]; val calYear = parts[1]
                val discount = roundPrice(basis * (pct / 100.0))
                val finalDiscount = if (discount > 0) -discount else 0.0
                if (finalDiscount < 0) {
                    val label = if (calYear == cYear) "Thuisvoordeel ($calYear: 18/10 - 31/12)" else "Thuisvoordeel ($calYear: 01/01 - 17/10)"
                    results.add(CostBreakdown(
                        startDate = "", endDate = "", totalImportKwh = 0.0, totalExportKwh = 0.0, netKwh = 0.0,
                        energyCost = finalDiscount, totalCost = finalDiscount, days = 0, label = label
                    ))
                }
            }
        }
        return results
    }

    private fun findClosestStand(data: List<DailySummary>, date: String): Triple<Double, Double, Double> {
        val targetIso = toIso(date)
        val sortedData = data.map { it.copy(date = toIso(it.date)) }.sortedBy { it.date }
        val record = sortedData.findLast { it.date <= targetIso && (it.absGridImportKwh > 0 || it.absGasM3 > 0) }
            ?: sortedData.firstOrNull { it.absGridImportKwh > 0 || it.absGasM3 > 0 }
            ?: return Triple(0.0, 0.0, 0.0)
        return Triple(record.absGridImportKwh, record.absGridExportKwh, record.absGasM3)
    }

    private fun getNextDay(date: String): String {
        return try {
            val d = dateFormat.parse(date) ?: return date
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d; add(Calendar.DAY_OF_YEAR, 1) }
            dateFormat.format(cal.time)
        } catch (_: Exception) { date }
    }

    private fun calculateDaysBetween(start: String, end: String): Int {
        return try {
            val d1 = dateFormat.parse(start) ?: return 0
            val d2 = dateFormat.parse(end) ?: return 0
            val diff = abs(d2.time - d1.time)
            round(diff.toDouble() / (1000 * 60 * 60 * 24)).toInt()
        } catch (_: Exception) { 0 }
    }
}
