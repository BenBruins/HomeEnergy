package com.example.energiehuishouding.data.remote.homewizard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HomeWizardData(
    @Json(name = "active_power_w") val activePowerW: Double?,
    @Json(name = "power_w") val powerW: Double?,
    @Json(name = "total_power_import_kwh") val totalImportKwh: Double?,
    @Json(name = "total_power_import_t1_kwh") val importT1Kwh: Double?,
    @Json(name = "total_power_import_t2_kwh") val importT2Kwh: Double?,
    @Json(name = "total_power_export_kwh") val totalExportKwh: Double?,
    @Json(name = "total_power_export_t1_kwh") val exportT1Kwh: Double?,
    @Json(name = "total_power_export_t2_kwh") val exportT2Kwh: Double?,
    @Json(name = "energy_import_kwh") val energyInKwh: Double?,
    @Json(name = "energy_export_kwh") val energyOutKwh: Double?,
    @Json(name = "battery_state_of_charge_pct") val batterySoc: Double?,
    @Json(name = "state_of_charge_pct") val batterySocV2: Double?,
    @Json(name = "state_of_charge") val stateOfCharge: Double?,
    @Json(name = "total_gas_m3") val totalGasM3: Double?,
    @Json(name = "active_power_l1_w") val activePowerL1W: Double? = null,
    @Json(name = "active_power_l2_w") val activePowerL2W: Double? = null,
    @Json(name = "active_power_l3_w") val activePowerL3W: Double? = null,
    @Json(name = "active_current_l1_a") val activeCurrentL1A: Double? = null,
    @Json(name = "active_current_l2_a") val activeCurrentL2A: Double? = null,
    @Json(name = "active_current_l3_a") val activeCurrentL3A: Double? = null,
    @Json(name = "active_voltage_l1_v") val activeVoltageL1V: Double? = null,
    @Json(name = "active_voltage_l2_v") val activeVoltageL2V: Double? = null,
    @Json(name = "active_voltage_l3_v") val activeVoltageL3V: Double? = null
)

@JsonClass(generateAdapter = true)
data class HomeWizardBattery(
    val id: String?,
    @Json(name = "state_of_charge") val stateOfCharge: Double?,
    @Json(name = "power_w") val powerW: Double?,
    @Json(name = "energy_import_kwh") val energyInKwh: Double?,
    @Json(name = "energy_export_kwh") val energyOutKwh: Double?,
    val mode: String?
)

@JsonClass(generateAdapter = true)
data class HomeWizardBatteryResponseV2(
    val batteries: List<HomeWizardBattery>
)

@JsonClass(generateAdapter = true)
data class HomeWizardBatteryResponse(
    val batteries: List<HomeWizardData>
)

@JsonClass(generateAdapter = true)
data class HomeWizardUserRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "client_name") val clientName: String? = null
)

@JsonClass(generateAdapter = true)
data class HomeWizardUserResponse(
    val token: String? = null
)
