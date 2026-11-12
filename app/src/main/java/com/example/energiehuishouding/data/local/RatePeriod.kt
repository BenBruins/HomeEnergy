package com.example.energiehuishouding.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rate_periods")
data class RatePeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endDate: String,   // yyyy-MM-dd
    val tariff: Double,            // Tarief
    val energyTax: Double = 0.0,   // Energie Belasting (EB) per kWh
    val gasTariff: Double = 0.0,   // Gas Tarief per m3
    val gasTax: Double = 0.0,      // Gas Belasting per m3
    val compensation: Double,      // VBV over netto overschot OF TLV over totale export (€/kWh)
    val tlkRate: Double = 0.0,     // TLK per dag OF TLK per kWh export (€)
    val gridConnection: Double,    // Aansluiting Enexis (AE) per dag
    val gasConnection: Double = 0.0, // Gas Aansluiting per dag
    val taxReduction: Double,      // Vermindering Energie Belasting (VEB)
    val fixedDeliveryCost: Double, // Vaste Leverings Kosten (VLK)
    val variableGasDeliveryCost: Double = 0.0, // Vaste Gas Levering per dag
    val discountPercentage: Double = 0.0, // Thuisvoordeel percentage (0-100)
    val isInclusive: Boolean = true, // Of de einddatum moet worden meegeteld (+1 dag)
    val useAbsoluteCalculation: Boolean = false // false = salderen (netto), true = geen salderen (absoluut)
)
