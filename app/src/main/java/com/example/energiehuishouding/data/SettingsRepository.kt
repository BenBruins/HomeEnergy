package com.example.energiehuishouding.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val INVERTER_IP_KEY = stringPreferencesKey("inverter_ip")
        private val INVERTER_BRAND_KEY = stringPreferencesKey("inverter_brand")
        private val APP_KEY = stringPreferencesKey("app_key")
        private val APP_SECRET_KEY = stringPreferencesKey("app_secret")
        private val PLANT_ID_KEY = stringPreferencesKey("plant_id")
        private val API_KEY = stringPreferencesKey("api_key")
        private val SOLPLANET_TOKEN_KEY = stringPreferencesKey("solplanet_token")

        private val SOLAREDGE_API_KEY = stringPreferencesKey("solaredge_api_key")
        private val SOLAREDGE_SITE_ID_KEY = stringPreferencesKey("solaredge_site_id")

        private val HOMEWIZARD_P1_IP_KEY = stringPreferencesKey("p1_ip")
        private val HOMEWIZARD_BATTERY_IP_KEY = stringPreferencesKey("battery_ip")
        private val HOMEWIZARD_BATTERY_LOCAL_TOKEN_KEY = stringPreferencesKey("battery_local_token")
        
        private val LAST_SYNC_TIMESTAMP_KEY = longPreferencesKey("last_sync_timestamp")

        private val P1_BASELINE_DATE_KEY = stringPreferencesKey("p1_baseline_date")
        private val P1_IMPORT_BASELINE_KEY = doublePreferencesKey("p1_import_baseline")
        private val P1_EXPORT_BASELINE_KEY = doublePreferencesKey("p1_export_baseline")

        private val BATTERY_BASELINE_DATE_KEY = stringPreferencesKey("battery_baseline_date")
        private val BATTERY_IN_BASELINE_KEY = doublePreferencesKey("battery_in_baseline")
        private val BATTERY_OUT_BASELINE_KEY = doublePreferencesKey("battery_out_baseline")

        private val P1_LAST_TOTAL_IMPORT_KEY = doublePreferencesKey("p1_last_total_import")
        private val P1_LAST_TOTAL_EXPORT_KEY = doublePreferencesKey("p1_last_total_export")
        private val BATTERY_LAST_TOTAL_IN_KEY = doublePreferencesKey("battery_last_total_in")
        private val BATTERY_LAST_TOTAL_OUT_KEY = doublePreferencesKey("battery_last_total_out")
    }

    val inverterIp: Flow<String?> = context.dataStore.data.map { it[INVERTER_IP_KEY] }
    val inverterBrand: Flow<String?> = context.dataStore.data.map { it[INVERTER_BRAND_KEY] ?: "Solplanet" }
    val appKey: Flow<String?> = context.dataStore.data.map { it[APP_KEY] }
    val appSecret: Flow<String?> = context.dataStore.data.map { it[APP_SECRET_KEY] }
    val plantId: Flow<String?> = context.dataStore.data.map { it[PLANT_ID_KEY] }
    val apiKey: Flow<String?> = context.dataStore.data.map { it[API_KEY] }
    val solplanetToken: Flow<String?> = context.dataStore.data.map { it[SOLPLANET_TOKEN_KEY] }

    val solarEdgeApiKey: Flow<String?> = context.dataStore.data.map { it[SOLAREDGE_API_KEY] }
    val solarEdgeSiteId: Flow<String?> = context.dataStore.data.map { it[SOLAREDGE_SITE_ID_KEY] }

    val p1Ip: Flow<String?> = context.dataStore.data.map { it[HOMEWIZARD_P1_IP_KEY] }
    val batteryIp: Flow<String?> = context.dataStore.data.map { it[HOMEWIZARD_BATTERY_IP_KEY] }
    val batteryLocalToken: Flow<String?> = context.dataStore.data.map { it[HOMEWIZARD_BATTERY_LOCAL_TOKEN_KEY] }
    
    val lastSyncTimestamp: Flow<Long> = context.dataStore.data.map { it[LAST_SYNC_TIMESTAMP_KEY] ?: 0L }

    val p1BaselineDate: Flow<String?> = context.dataStore.data.map { it[P1_BASELINE_DATE_KEY] }
    val p1ImportBaseline: Flow<Double?> = context.dataStore.data.map { it[P1_IMPORT_BASELINE_KEY] }
    val p1ExportBaseline: Flow<Double?> = context.dataStore.data.map { it[P1_EXPORT_BASELINE_KEY] }

    val batteryBaselineDate: Flow<String?> = context.dataStore.data.map { it[BATTERY_BASELINE_DATE_KEY] }
    val batteryInBaseline: Flow<Double?> = context.dataStore.data.map { it[BATTERY_IN_BASELINE_KEY] }
    val batteryOutBaseline: Flow<Double?> = context.dataStore.data.map { it[BATTERY_OUT_BASELINE_KEY] }

    val p1LastTotalImport: Flow<Double?> = context.dataStore.data.map { it[P1_LAST_TOTAL_IMPORT_KEY] }
    val p1LastTotalExport: Flow<Double?> = context.dataStore.data.map { it[P1_LAST_TOTAL_EXPORT_KEY] }
    val batteryLastTotalIn: Flow<Double?> = context.dataStore.data.map { it[BATTERY_LAST_TOTAL_IN_KEY] }
    val batteryLastTotalOut: Flow<Double?> = context.dataStore.data.map { it[BATTERY_LAST_TOTAL_OUT_KEY] }

    val settingsFlow: Flow<Array<Any?>> = combine<Any?, Array<Any?>>(
        inverterIp, p1Ip, batteryIp, batteryLocalToken, solplanetToken,
        appKey, appSecret, plantId, apiKey, lastSyncTimestamp, inverterBrand,
        solarEdgeApiKey, solarEdgeSiteId
    ) { it }

    suspend fun saveInverterIp(ip: String) {
        context.dataStore.edit { it[INVERTER_IP_KEY] = ip }
    }

    suspend fun saveInverterBrand(brand: String) {
        context.dataStore.edit { it[INVERTER_BRAND_KEY] = brand }
    }

    suspend fun saveCloudSettings(appKey: String, appSecret: String, plantId: String, apiKey: String) {
        context.dataStore.edit {
            it[APP_KEY] = appKey
            it[APP_SECRET_KEY] = appSecret
            it[PLANT_ID_KEY] = plantId
            it[API_KEY] = apiKey
        }
    }

    suspend fun saveSolarEdgeSettings(apiKey: String, siteId: String) {
        context.dataStore.edit {
            it[SOLAREDGE_API_KEY] = apiKey
            it[SOLAREDGE_SITE_ID_KEY] = siteId
        }
    }

    suspend fun saveSolplanetToken(token: String) {
        context.dataStore.edit { it[SOLPLANET_TOKEN_KEY] = token }
    }

    suspend fun saveBatteryLocalToken(token: String) {
        context.dataStore.edit { it[HOMEWIZARD_BATTERY_LOCAL_TOKEN_KEY] = token }
    }

    suspend fun saveHomeWizardSettings(p1Ip: String, batteryIp: String) {
        context.dataStore.edit {
            it[HOMEWIZARD_P1_IP_KEY] = p1Ip
            it[HOMEWIZARD_BATTERY_IP_KEY] = batteryIp
        }
    }

    suspend fun saveLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_SYNC_TIMESTAMP_KEY] = timestamp }
    }

    suspend fun resetP1Baselines(date: String, import: Double, export: Double) {
        context.dataStore.edit {
            it[P1_BASELINE_DATE_KEY] = date
            it[P1_IMPORT_BASELINE_KEY] = import
            it[P1_EXPORT_BASELINE_KEY] = export
        }
    }

    suspend fun resetBatteryBaselines(date: String, energyIn: Double, energyOut: Double) {
        context.dataStore.edit {
            it[BATTERY_BASELINE_DATE_KEY] = date
            it[BATTERY_IN_BASELINE_KEY] = energyIn
            it[BATTERY_OUT_BASELINE_KEY] = energyOut
        }
    }

    suspend fun saveLastP1Totals(import: Double, export: Double) {
        context.dataStore.edit {
            it[P1_LAST_TOTAL_IMPORT_KEY] = import
            it[P1_LAST_TOTAL_EXPORT_KEY] = export
        }
    }

    suspend fun saveLastBatteryTotals(energyIn: Double, energyOut: Double) {
        context.dataStore.edit {
            it[BATTERY_LAST_TOTAL_IN_KEY] = energyIn
            it[BATTERY_LAST_TOTAL_OUT_KEY] = energyOut
        }
    }
}
