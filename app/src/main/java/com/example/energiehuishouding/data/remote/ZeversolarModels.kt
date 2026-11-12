package com.example.energiehuishouding.data.remote

data class InverterStatus(
    val currentWattage: Int,
    val todayKwh: Double
)

fun parseZeversolarResponse(response: String): InverterStatus? {
    val parts = response.trim().split(Regex("\\s+"))
    return if (parts.size >= 13) {
        InverterStatus(
            currentWattage = parts[11].toIntOrNull() ?: 0,
            todayKwh = parts[12].toDoubleOrNull() ?: 0.0
        )
    } else null
}
