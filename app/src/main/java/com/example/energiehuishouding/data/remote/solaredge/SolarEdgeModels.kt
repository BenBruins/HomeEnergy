package com.example.energiehuishouding.data.remote.solaredge

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SolarEdgeEnergyResponse(
    @Json(name = "energy") val energy: EnergyData
)

@JsonClass(generateAdapter = true)
data class EnergyData(
    @Json(name = "timeUnit") val timeUnit: String,
    @Json(name = "unit") val unit: String,
    @Json(name = "values") val values: List<EnergyValue>
)

@JsonClass(generateAdapter = true)
data class EnergyValue(
    @Json(name = "date") val date: String,
    @Json(name = "value") val value: Double?
)

@JsonClass(generateAdapter = true)
data class SolarEdgeOverviewResponse(
    @Json(name = "overview") val overview: OverviewData
)

@JsonClass(generateAdapter = true)
data class OverviewData(
    @Json(name = "lastUpdateTime") val lastUpdateTime: String,
    @Json(name = "lifeTimeData") val lifeTimeData: EnergyAmount,
    @Json(name = "lastYearData") val lastYearData: EnergyAmount,
    @Json(name = "lastMonthData") val lastMonthData: EnergyAmount,
    @Json(name = "lastDayData") val lastDayData: EnergyAmount,
    @Json(name = "currentPower") val currentPower: PowerAmount
)

@JsonClass(generateAdapter = true)
data class EnergyAmount(
    @Json(name = "energy") val energy: Double
)

@JsonClass(generateAdapter = true)
data class PowerAmount(
    @Json(name = "power") val power: Double
)
