package com.example.energiehuishouding.data

import android.util.Log
import com.example.energiehuishouding.data.local.*
import com.example.energiehuishouding.data.remote.ZeversolarApi
import com.example.energiehuishouding.data.remote.homewizard.*
import com.example.energiehuishouding.data.remote.parseZeversolarResponse
import com.example.energiehuishouding.data.remote.solaredge.SolarEdgeApi
import com.example.energiehuishouding.data.remote.solplanet.*
import com.example.energiehuishouding.ui.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class InverterRepository(
    private val solarDao: SolarDao,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        const val NEW_METER_READING_DIVISOR = 1000.0

        fun normalizeGridReading(date: String, reading: Double): Double = reading
    }

    private data class MeterStands(
        val importKwh: Double? = null,
        val exportKwh: Double? = null,
        val gasM3: Double? = null,
        val batteryInKwh: Double? = null,
        val batteryOutKwh: Double? = null
    )

    private data class StandContext(val before: MeterStands, val after: MeterStands)

    val p1Data = MutableStateFlow<HomeWizardData?>(null)
    val batteryData = MutableStateFlow<HomeWizardData?>(null)
    val liveSolarWatts = MutableStateFlow(0)
    val connectionStatus = MutableStateFlow<Map<String, String>>(emptyMap())

    private val localClient = createOkHttpClient()

    private fun createOkHttpClient(timeoutSec: Long = 10): OkHttpClient {
        // Keep OkHttp's platform TLS certificate and hostname checks enabled for cloud APIs.
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .build()
    }

    private fun createHomeWizardApi(baseUrl: String): HomeWizardApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(localClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(HomeWizardApi::class.java)
    }

    private fun createZeversolarApi(ip: String): ZeversolarApi {
        return Retrofit.Builder()
            .baseUrl("http://$ip:80/")
            .client(localClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(ZeversolarApi::class.java)
    }

    private fun createSolplanetApi(): SolplanetApi {
        return Retrofit.Builder()
            .baseUrl("https://eu-api-genergal.aisweicloud.com/")
            .client(localClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SolplanetApi::class.java)
    }

    private fun createLocalSolplanetApi(ip: String): SolplanetApi {
        val host = ip.removePrefix("http://").removePrefix("https://").removeSuffix("/").removeSuffix(":8484")
        return Retrofit.Builder()
            .baseUrl("http://$host:8484/")
            .client(localClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SolplanetApi::class.java)
    }

    private fun createSolarEdgeApi(): SolarEdgeApi {
        return Retrofit.Builder()
            .baseUrl("https://monitoringapi.solaredge.com/")
            .client(localClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SolarEdgeApi::class.java)
    }

    suspend fun syncCloudHistory(
        range: TimeRange, 
        date: Long, 
        brand: String,
        ak: String, sec: String, pid: String, apiKey: String, token: String,
        seApiKey: String, seSiteId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cleanBrand = brand.trim()
        
        if (cleanBrand.equals("SolarEdge", ignoreCase = true)) {
            val res = performSolarEdgeSync(range, date, seApiKey.trim(), seSiteId.trim())
            return@withContext res
        }

        if (ak.isBlank()) {
            updateStatus("Solar", "Offline")
            Log.w("Energy-Net", "Solplanet sync skipped: app key is missing")
            return@withContext Result.failure(Exception("Sleutels missen."))
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(date))
        val dateMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(date))
        
        val syncTasks = if (range == TimeRange.MONTH) {
            listOf("bymonth" to dateMonth, "bydays" to dateStr)
        } else {
            val period = when (range) {
                TimeRange.YEAR -> "byyear"
                TimeRange.TOTAL -> "bytotal"
                else -> "bydays"
            }
            val qDate = when(range) {
                TimeRange.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(date))
                TimeRange.TOTAL -> ""
                else -> dateStr
            }
            listOf(period to qDate)
        }

        var totalImported = 0
        for (task in syncTasks) {
            val res = performCloudSync(task.first, task.second, range, ak, sec, pid, apiKey, token)
            totalImported += res.getOrDefault(0)
        }
        
        if (totalImported > 0) {
            settingsRepository.saveLastSyncTimestamp(System.currentTimeMillis())
            Result.success(totalImported)
        } else {
            Result.failure(Exception("Geen data."))
        }
    }

    private suspend fun performSolarEdgeSync(range: TimeRange, date: Long, apiKey: String, siteId: String): Result<Int> {
        if (apiKey.isBlank() || siteId.isBlank()) return Result.failure(Exception("SolarEdge keys missen."))
        
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }
        val startDate: String
        val endDate: String
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        when (range) {
            TimeRange.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                startDate = sdf.format(cal.time)
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.DAY_OF_YEAR, -1)
                endDate = sdf.format(cal.time)
            }
            TimeRange.YEAR -> {
                cal.set(Calendar.MONTH, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                startDate = sdf.format(cal.time)
                cal.add(Calendar.YEAR, 1)
                cal.add(Calendar.DAY_OF_YEAR, -1)
                endDate = sdf.format(cal.time)
            }
            TimeRange.TOTAL -> {
                cal.add(Calendar.YEAR, -1)
                startDate = sdf.format(cal.time)
                endDate = sdf.format(Date())
            }
            else -> return Result.failure(Exception("Onbekende range"))
        }

        try {
            val api = createSolarEdgeApi()
            val overviewResp = try { api.getOverview(siteId, apiKey) } catch (_: Exception) { null }
            if (overviewResp != null && overviewResp.isSuccessful) {
                val overview = overviewResp.body()?.overview
                if (overview != null) {
                    liveSolarWatts.value = overview.currentPower.power.toInt()
                    updateStatus("Solar", "OK")
                    val todayKwh = overview.lastDayData.energy / 1000.0
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    upsertDailySolar(todayStr, System.currentTimeMillis(), todayKwh, isCloudSync = true)
                } else {
                    updateStatus("Solar", "Geen livewaarde")
                }
            } else {
                updateStatus("Solar", "Geen livewaarde")
            }

            val resp = api.getEnergy(siteId, apiKey, startDate, endDate)
            if (resp.isSuccessful) {
                val data = resp.body()?.energy?.values ?: emptyList()
                var imported = 0
                data.forEach { value ->
                    val rawVal = value.value ?: 0.0
                    val isWh = resp.body()?.energy?.unit?.lowercase()?.contains("kwh") != true
                    val finalKwh = if (isWh) rawVal / 1000.0 else rawVal
                    
                    val dateStr = value.date.take(10)
                    val ts = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)?.time ?: 0L
                    if (ts > 0) {
                        upsertDailySolar(dateStr, ts, finalKwh, isCloudSync = true)
                        imported++
                    }
                }
                settingsRepository.saveLastSyncTimestamp(System.currentTimeMillis())
                return Result.success(imported)
            } else {
                Log.e("SolarEdge", "API Error: ${resp.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SolarEdge", "Sync failed", e)
        }
        return Result.failure(Exception("SolarEdge sync mislukt"))
    }

    private suspend fun performCloudSync(period: String, qDate: String, range: TimeRange, ak: String, sec: String, pid: String, apiKey: String, token: String): Result<Int> {
        val host = "https://eu-api-genergal.aisweicloud.com/"
        val path = "/getPlantOutput"
        val query = mutableMapOf("plantId" to pid, "key" to apiKey, "period" to period, "date" to qDate, "token" to token)

        try {
            val timestamp = System.currentTimeMillis().toString()
            val nonce = UUID.randomUUID().toString()
            val sig = SolplanetSigner.generateSignature("GET", path, query, ak, sec, timestamp, nonce)
            
            val headers = mapOf(
                "X-Ca-Key" to ak, "X-Ca-Signature" to sig, "X-Ca-Timestamp" to timestamp, "X-Ca-Nonce" to nonce,
                "X-Ca-Signature-Headers" to "X-Ca-Key,X-Ca-Nonce,X-Ca-Timestamp", "X-Ca-Stage" to "RELEASE", "Accept" to "application/json"
            )

            val sortedParams = query.toSortedMap()
            val queryStr = sortedParams.entries.joinToString("&") { if (it.value.isEmpty()) it.key else "${it.key}=${it.value}" }
            val url = "${host.removeSuffix("/")}$path?$queryStr"

            val resp = createSolplanetApi().getGateway(url, headers)
            if (resp.isSuccessful) {
                val raw = resp.body()?.string() ?: ""
                val summaries = parseSummaryResponse(raw, range)
                if (summaries.isNotEmpty()) {
                    Log.d("Energy-Net", "Solplanet sync parsed ${summaries.size} ${range.name.lowercase()} summaries")
                    summaries.forEach { s ->
                        when (s) {
                            is DailySummary -> upsertDailySolar(s.date, s.timestamp, s.solarKwh, isCloudSync = true)
                            is MonthlySummary -> upsertMonthlySolar(s.month, s.timestamp, s.solarKwh)
                            is YearlySummary -> upsertYearlySolar(s.year, s.timestamp, s.solarKwh)
                        }
                    }
                    return Result.success(summaries.size)
                }
                Log.w("Energy-Net", "Solplanet sync returned no parseable summaries for $period (payload length ${raw.length})")
            } else {
                Log.e("Energy-Net", "Solplanet API returned HTTP ${resp.code()} for $period")
            }
        } catch (e: Exception) { Log.e("Energy-Net", "Sync Error period=$period", e) }
        return Result.failure(Exception("Fout"))
    }

    private fun parseSummaryResponse(raw: String, range: TimeRange): List<Any> {
        val result = mutableListOf<Any>()
        try {
            val json = JSONObject(raw)
            val listArray = json.optJSONObject("data")?.optJSONArray("list") ?: json.optJSONArray("data") ?: json.optJSONArray("list") ?: JSONArray()
            val unit = json.optString("dataunit", "").uppercase()

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

            for (i in 0 until listArray.length()) {
                val item = listArray.getJSONObject(i)
                val rawTime = item.optString("time", item.optString("timestamp", ""))
                if (rawTime.equals("Total", ignoreCase = true) || rawTime.isBlank()) continue

                val solarKwh = when {
                    item.has("value") -> item.optDouble("value", 0.0)
                    item.has("energy") -> item.optDouble("energy", 0.0)
                    item.has("yield") -> item.optDouble("yield", 0.0)
                    item.has("etd") -> item.optDouble("etd", 0.0)
                    else -> item.optDouble("pac", 0.0)
                }.let { if (unit == "MWH") it * 1000 else it }

                try {
                    val parsedDate: Date = when {
                        rawTime.length >= 10 -> SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(rawTime.take(10))!!
                        rawTime.contains("/") -> SimpleDateFormat("yyyy/MM/dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(rawTime)!!
                        rawTime.length == 7 -> SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(rawTime)!!
                        rawTime.length == 4 -> SimpleDateFormat("yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(rawTime)!!
                        else -> throw Exception("Unknown date format")
                    }
                    
                    cal.time = parsedDate
                    cal.set(Calendar.HOUR_OF_DAY, 12); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    val ts = cal.timeInMillis
                    
                    val key = when(range) {
                        TimeRange.MONTH -> SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsedDate)
                        TimeRange.YEAR -> SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsedDate)
                        TimeRange.TOTAL -> SimpleDateFormat("yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsedDate)
                        else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsedDate)
                    }

                    when (range) {
                        TimeRange.MONTH -> result.add(DailySummary(date = key, timestamp = ts, solarKwh = solarKwh))
                        TimeRange.YEAR -> result.add(MonthlySummary(month = key, timestamp = ts, solarKwh = solarKwh))
                        TimeRange.TOTAL -> result.add(YearlySummary(year = key, timestamp = ts, solarKwh = solarKwh))
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun refreshLocalOnlyData(invIp: String, p1Ip: String, batIp: String, updateLiveFlows: Boolean = true) = coroutineScope {
        val brand = settingsRepository.inverterBrand.first() ?: "Solplanet"
        val batToken = settingsRepository.batteryLocalToken.first() ?: ""
        val now = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

        if (invIp.isNotBlank() && (brand.equals("Zeversolar", ignoreCase = true) || brand.equals("Solplanet", ignoreCase = true))) launch(Dispatchers.IO) {
            var inverterStatus: com.example.energiehuishouding.data.remote.InverterStatus? = null
            try {
                inverterStatus = parseZeversolarResponse(createZeversolarApi(invIp).getInverterData().string())
            } catch (error: Exception) {
                Log.w("InverterRepository", "Local inverter home.cgi request failed", error)
            }

            if (inverterStatus == null && brand.equals("Solplanet", ignoreCase = true)) {
                try {
                    val serialNumber = settingsRepository.plantId.first().orEmpty()
                    val api = createLocalSolplanetApi(invIp)
                    val info = JSONObject(api.getLocalInverterInfo().string())
                    val infoPower = info.optJSONArray("inv")?.optJSONObject(0)
                        ?.takeIf { it.has("pac") && !it.isNull("pac") }
                    val live = infoPower ?: if (serialNumber.isNotBlank()) {
                        JSONObject(api.getLocalInverterData(serialNumber = serialNumber.trim()).string())
                    } else null
                    if (live != null && live.has("pac") && !live.isNull("pac")) {
                        inverterStatus = com.example.energiehuishouding.data.remote.InverterStatus(
                            currentWattage = live.optDouble("pac").toInt(),
                            todayKwh = if (live.has("etd") && !live.isNull("etd")) live.optDouble("etd") / 10.0 else 0.0
                        )
                    }
                } catch (error: Exception) {
                    Log.w("InverterRepository", "Local Solplanet data endpoint failed", error)
                }
            }

            if (inverterStatus != null) {
                if (updateLiveFlows) {
                    liveSolarWatts.value = inverterStatus!!.currentWattage
                    updateStatus("Solar", "OK")
                }
                upsertDailySolar(todayStr, now, inverterStatus!!.todayKwh, isCloudSync = false)
            } else if (updateLiveFlows) {
                updateStatus("Solar", "Offline")
            }
        }

        if (p1Ip.isNotBlank()) launch(Dispatchers.IO) {
            val data = try {
                val api = createHomeWizardApi("http://${p1Ip.removePrefix("http://")}/")
                api.getLocalDataV1()
            } catch (error: Exception) {
                if (updateLiveFlows) updateStatus("P1 Meter", "Offline")
                Log.w("InverterRepository", "P1 meter request failed", error)
                return@launch
            }

            if (updateLiveFlows) {
                p1Data.value = data
                updateStatus("P1 Meter", "OK")
            }

            try {
                val currentTotalImp = data.totalImportKwh ?: (data.importT1Kwh ?: 0.0) + (data.importT2Kwh ?: 0.0)
                val currentTotalExp = data.totalExportKwh ?: (data.exportT1Kwh ?: 0.0) + (data.exportT2Kwh ?: 0.0)
                val currentGas = data.totalGasM3 ?: 0.0
                val hasImportReading = data.totalImportKwh != null || data.importT1Kwh != null || data.importT2Kwh != null
                val hasExportReading = data.totalExportKwh != null || data.exportT1Kwh != null || data.exportT2Kwh != null
                upsertDailyGrid(todayStr, now, currentTotalImp, currentTotalExp, absGas = currentGas, hasP1ImportReading = hasImportReading, hasP1ExportReading = hasExportReading)
            } catch (error: Exception) {
                // The meter is reachable even if saving its latest values fails.
                Log.w("InverterRepository", "P1 data received but could not be saved", error)
            }
        }

        if (batIp.isNotBlank() && batToken.isNotBlank()) launch(Dispatchers.IO) {
            try {
                val api = createHomeWizardApi("https://${batIp.removePrefix("http://").removePrefix("https://").removeSuffix("/")}/")
                val data = api.getLocalDataV2("Bearer $batToken")
                if (updateLiveFlows) {
                    batteryData.value = data
                    updateStatus("Battery", "OK")
                }
                updateBatterySummary(data, todayStr, now)
            } catch (_: Exception) { if (updateLiveFlows) updateStatus("Battery", "Offline") }
        }
    }

    private suspend fun updateBatterySummary(data: HomeWizardData, date: String, ts: Long) {
        val currentIn = data.energyInKwh ?: data.totalImportKwh ?: 0.0
        val currentOut = data.energyOutKwh ?: data.totalExportKwh ?: 0.0
        upsertDailyBattery(date, ts, currentIn, currentOut)
    }

    private fun updateStatus(key: String, status: String) {
        connectionStatus.update { current ->
            val next = current.toMutableMap()
            next[key] = status
            next
        }
    }

    fun getDailySummaries(start: Long, end: Long) = solarDao.getDailySummaries(start, end)
    fun getMonthlySummaries(start: Long, end: Long) = solarDao.getMonthlySummaries(start, end)
    fun getYearlySummaries() = solarDao.getYearlySummaries()
    fun getTodaySummaryFlow(date: String) = solarDao.getDailySummaryFlow(date)

    suspend fun upsertDailyGrid(date: String, ts: Long, absImp: Double, absExp: Double, absGas: Double = 0.0, manualDeltaImp: Double? = null, manualDeltaExp: Double? = null, hasP1ImportReading: Boolean = absImp > 0.0, hasP1ExportReading: Boolean = absExp > 0.0) = withContext(Dispatchers.IO) {
        val anchoredTs = anchorToNoon(date, ts)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val dayStartMs = dateFormat.parse(date)?.time ?: ts
        val previousDate = Calendar.getInstance().apply {
            timeInMillis = dayStartMs
            add(Calendar.DAY_OF_YEAR, -1)
        }.let { dateFormat.format(it.time) }
        val baselineWindowStart = dayStartMs - TimeUnit.MINUTES.toMillis(30)
        val prevImp = if (hasP1ImportReading) solarDao.getLatestImportStandBefore(date) else null
        val prevExp = if (hasP1ExportReading) solarDao.getLatestExportStandBefore(date) else null
        val prevGas = if (absGas > 0) solarDao.getLatestGasStandBefore(date) else null
        val readingImp = normalizeGridReading(date, absImp)
        val readingExp = normalizeGridReading(date, absExp)
        val previousRawImp = prevImp?.let { normalizeGridReading(it.date, it.rawGridImportKwh.takeIf { raw -> it.p1SampleTimestamp > 0 || raw > 0.0 } ?: it.absGridImportKwh) } ?: 0.0
        val previousRawExp = prevExp?.let { normalizeGridReading(it.date, it.rawGridExportKwh.takeIf { raw -> it.p1SampleTimestamp > 0 || raw > 0.0 } ?: it.absGridExportKwh) } ?: 0.0
        
        fun counter(raw: Double, hasReading: Boolean, previous: DailySummary?, rawPrevious: Double, previousAbs: Double, previousOffset: Double): Pair<Double, Double> {
            if (!hasReading) return previousAbs to previousOffset
            val reset = previous != null && if (rawPrevious > 0.0) raw < rawPrevious else raw < previousAbs
            val offset = if (reset) previousOffset + (if (rawPrevious > 0.0) rawPrevious else previousAbs - previousOffset) else previousOffset
            return (offset + raw) to offset
        }
        val (totalImp, offsetImp) = counter(readingImp, hasP1ImportReading, prevImp, previousRawImp, prevImp?.absGridImportKwh ?: 0.0, prevImp?.gridImportOffsetKwh ?: 0.0)
        val (totalExp, offsetExp) = counter(readingExp, hasP1ExportReading, prevExp, previousRawExp, prevExp?.absGridExportKwh ?: 0.0, prevExp?.gridExportOffsetKwh ?: 0.0)
        val (totalGas, offsetGas) = counter(absGas, absGas > 0.0, prevGas, prevGas?.rawGasM3 ?: 0.0, prevGas?.absGasM3 ?: 0.0, prevGas?.gasOffsetM3 ?: 0.0)
        val existing = solarDao.getDailySummary(date)
        val midnightImp = prevImp?.takeIf {
            it.date == previousDate && it.p1SampleTimestamp in baselineWindowStart until dayStartMs
        } ?: prevImp?.takeIf { it.date == previousDate }
        val midnightExp = prevExp?.takeIf {
            it.date == previousDate && it.p1SampleTimestamp in baselineWindowStart until dayStartMs
        } ?: prevExp?.takeIf { it.date == previousDate }

        val freshMidnightBaseline = hasP1ImportReading && hasP1ExportReading && (midnightImp != null || prevImp != null) && (midnightExp != null || prevExp != null)
        val dayBaselineTimestamp = existing?.p1DayBaselineTimestamp?.takeIf { it > 0 }
            ?: if (freshMidnightBaseline) {
                val bestImp = midnightImp ?: prevImp!!
                val bestExp = midnightExp ?: prevExp!!
                val minTs = minOf(bestImp.p1SampleTimestamp, bestExp.p1SampleTimestamp)
                if (minTs > 0) minTs else ts
            } else 0L

        val midnightImpRaw = midnightImp?.let { if (it.rawGridImportKwh > 0.0) it.rawGridImportKwh else (it.absGridImportKwh - it.gridImportOffsetKwh).coerceAtLeast(0.0) }
        val prevImpRaw = prevImp?.let { if (it.rawGridImportKwh > 0.0) it.rawGridImportKwh else (it.absGridImportKwh - it.gridImportOffsetKwh).coerceAtLeast(0.0) }

        val midnightExpRaw = midnightExp?.let { if (it.rawGridExportKwh > 0.0) it.rawGridExportKwh else (it.absGridExportKwh - it.gridExportOffsetKwh).coerceAtLeast(0.0) }
        val prevExpRaw = prevExp?.let { if (it.rawGridExportKwh > 0.0) it.rawGridExportKwh else (it.absGridExportKwh - it.gridExportOffsetKwh).coerceAtLeast(0.0) }

        val dayStartImportAbs = existing?.p1DayBaselineTimestamp?.takeIf { it > 0 }
            ?.let { existing.p1DayStartImportAbsKwh }
            ?.takeIf { it > 0 }
            ?: midnightImpRaw?.takeIf { readingImp >= it && (readingImp - it) <= 50.0 }
            ?: prevImpRaw?.takeIf { readingImp >= it && (readingImp - it) <= 50.0 }
            ?: readingImp

        val dayStartExportAbs = existing?.p1DayBaselineTimestamp?.takeIf { it > 0 }
            ?.let { existing.p1DayStartExportAbsKwh }
            ?.takeIf { it > 0 }
            ?: midnightExpRaw?.takeIf { readingExp >= it && (readingExp - it) <= 50.0 }
            ?: prevExpRaw?.takeIf { readingExp >= it && (readingExp - it) <= 50.0 }
            ?: readingExp

        val previousDaySummary = solarDao.getDailySummary(previousDate)
        if (previousDaySummary != null && !previousDaySummary.p1DayFinalized) {
            val startImp = if (previousDaySummary.p1DayStartImportAbsKwh > 0) previousDaySummary.p1DayStartImportAbsKwh else (prevImp?.absGridImportKwh ?: previousDaySummary.absGridImportKwh)
            val startExp = if (previousDaySummary.p1DayStartExportAbsKwh > 0) previousDaySummary.p1DayStartExportAbsKwh else (prevExp?.absGridExportKwh ?: previousDaySummary.absGridExportKwh)
            val finalizedPreviousDay = previousDaySummary.copy(
                gridImportKwh = (previousDaySummary.absGridImportKwh - startImp).coerceAtLeast(previousDaySummary.gridImportKwh),
                gridExportKwh = (previousDaySummary.absGridExportKwh - startExp).coerceAtLeast(previousDaySummary.gridExportKwh),
                p1DayFinalized = true
            )
            solarDao.insertDailySummaries(listOf(finalizedPreviousDay))
            updateAggregates(previousDate, previousDaySummary.timestamp)
        }
        val resetGas = prevGas != null && if (prevGas.rawGasM3 > 0.0) absGas < prevGas.rawGasM3 else absGas > 0.0 && absGas < prevGas.absGasM3

        val deltaGas = if (resetGas && prevGas != null) {
            (existing?.let { (it.absGasM3 - prevGas.absGasM3).coerceAtLeast(0.0) } ?: 0.0) + absGas
        } else if (prevGas != null && totalGas > prevGas.absGasM3) {
            (totalGas - prevGas.absGasM3).coerceAtLeast(0.0)
        } else 0.0

        val finalDeltaImp = manualDeltaImp?.takeIf { it <= 5000.0 } ?: 0.0
        val finalDeltaExp = manualDeltaExp?.takeIf { it <= 5000.0 } ?: 0.0
        val finalDeltaGas = if (deltaGas > 50.0) 0.0 else deltaGas

        val hasChanges = existing == null || (hasP1ImportReading && existing.absGridImportKwh != totalImp) || (hasP1ExportReading && existing.absGridExportKwh != totalExp) || (absGas > 0 && existing.absGasM3 != totalGas)

        if (existing != null) {
            solarDao.insertDailySummaries(listOf(existing.copy(
                timestamp = anchoredTs,
                gridImportKwh = if (hasP1ImportReading) finalDeltaImp else existing.gridImportKwh,
                gridExportKwh = if (hasP1ExportReading) finalDeltaExp else existing.gridExportKwh,
                gasM3 = if (absGas > 0) finalDeltaGas else (if (existing.gasM3 > 50.0) 0.0 else existing.gasM3),
                absGridImportKwh = if (hasP1ImportReading) totalImp else existing.absGridImportKwh,
                absGridExportKwh = if (hasP1ExportReading) totalExp else existing.absGridExportKwh,
                absGasM3 = if (absGas > 0) totalGas else existing.absGasM3,
                rawGridImportKwh = if (hasP1ImportReading) readingImp else existing.rawGridImportKwh,
                rawGridExportKwh = if (hasP1ExportReading) readingExp else existing.rawGridExportKwh,
                rawGasM3 = if (absGas > 0) absGas else existing.rawGasM3,
                gridImportOffsetKwh = if (hasP1ImportReading) offsetImp else existing.gridImportOffsetKwh,
                gridExportOffsetKwh = if (hasP1ExportReading) offsetExp else existing.gridExportOffsetKwh,
                gasOffsetM3 = if (absGas > 0) offsetGas else existing.gasOffsetM3,
                p1SampleTimestamp = if (hasP1ImportReading && hasP1ExportReading) ts else existing.p1SampleTimestamp,
                p1DayBaselineTimestamp = dayBaselineTimestamp,
                p1DayStartImportAbsKwh = dayStartImportAbs,
                p1DayStartExportAbsKwh = dayStartExportAbs,
                p1DayFinalized = false
            )))
        } else {
            solarDao.insertDailySummaries(listOf(DailySummary(
                date = date, timestamp = anchoredTs, solarKwh = 0.0,
                gridImportKwh = finalDeltaImp, gridExportKwh = finalDeltaExp,
                gasM3 = finalDeltaGas,
                absGridImportKwh = totalImp, absGridExportKwh = totalExp,
                absGasM3 = totalGas,
                rawGridImportKwh = readingImp, rawGridExportKwh = readingExp, rawGasM3 = absGas,
                gridImportOffsetKwh = offsetImp, gridExportOffsetKwh = offsetExp, gasOffsetM3 = offsetGas,
                p1SampleTimestamp = if (hasP1ImportReading && hasP1ExportReading) ts else 0L,
                p1DayBaselineTimestamp = dayBaselineTimestamp,
                p1DayStartImportAbsKwh = dayStartImportAbs,
                p1DayStartExportAbsKwh = dayStartExportAbs,
                p1DayFinalized = false
            )))
        }
        if (hasChanges) updateAggregates(date, anchoredTs)
    }

    suspend fun upsertDailySolar(date: String, ts: Long, solar: Double, isCloudSync: Boolean = false) = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (isCloudSync && date > todayStr) return@withContext
        val anchoredTs = anchorToNoon(date, ts)
        val existing = solarDao.getDailySummary(date)

        if (existing != null) {
            if (isCloudSync && solar <= 0.0 && existing.solarKwh > 0.0) return@withContext
            if (isCloudSync && date == todayStr && solar <= existing.solarKwh) return@withContext
            val hasChanges = existing.solarKwh != solar
            solarDao.insertDailySummaries(listOf(existing.copy(solarKwh = solar, timestamp = anchoredTs)))
            if (hasChanges) updateAggregates(date, anchoredTs)
        } else {
            if (isCloudSync && date > todayStr) return@withContext
            if (isCloudSync && solar <= 0.0) return@withContext
            solarDao.insertDailySummaries(listOf(DailySummary(date = date, timestamp = anchoredTs, solarKwh = solar)))
            updateAggregates(date, anchoredTs)
        }
    }

    suspend fun upsertDailyBattery(date: String, ts: Long, absIn: Double, absOut: Double, manualDeltaIn: Double? = null, manualDeltaOut: Double? = null) = withContext(Dispatchers.IO) {
        val anchoredTs = anchorToNoon(date, ts)
        val prevBIn = if (absIn > 0.001) solarDao.getLatestBatteryInStandBefore(date) else null
        val prevBOut = if (absOut > 0.001) solarDao.getLatestBatteryOutStandBefore(date) else null

        val deltaIn = manualDeltaIn ?: run {
            if (prevBIn != null && prevBIn.absBatteryInKwh > 0 && absIn > prevBIn.absBatteryInKwh) {
                (absIn - prevBIn.absBatteryInKwh).coerceAtLeast(0.0)
            } else 0.0
        }
        val deltaOut = manualDeltaOut ?: run {
            if (prevBOut != null && prevBOut.absBatteryOutKwh > 0 && absOut > prevBOut.absBatteryOutKwh) {
                (absOut - prevBOut.absBatteryOutKwh).coerceAtLeast(0.0)
            } else 0.0
        }

        val finalDeltaIn = if (deltaIn > 5000.0) 0.0 else deltaIn
        val finalDeltaOut = if (deltaOut > 5000.0) 0.0 else deltaOut

        val existing = solarDao.getDailySummary(date)
        val hasChanges = existing == null || (absIn > 0 && existing.absBatteryInKwh != absIn) || (absOut > 0 && existing.absBatteryOutKwh != absOut)

        if (existing != null) {
            solarDao.insertDailySummaries(listOf(existing.copy(
                timestamp = anchoredTs,
                batteryInKwh = if (absIn > 0) finalDeltaIn else existing.batteryInKwh,
                batteryOutKwh = if (absOut > 0) finalDeltaOut else existing.batteryOutKwh,
                absBatteryInKwh = if (absIn > 0) absIn else existing.absBatteryInKwh,
                absBatteryOutKwh = if (absOut > 0) absOut else existing.absBatteryOutKwh
            )))
        } else {
            solarDao.insertDailySummaries(listOf(DailySummary(
                date = date, timestamp = anchoredTs, solarKwh = 0.0,
                batteryInKwh = finalDeltaIn, batteryOutKwh = finalDeltaOut,
                absBatteryInKwh = absIn, absBatteryOutKwh = absOut
            )))
        }
        if (hasChanges) updateAggregates(date, anchoredTs)
    }

    suspend fun getLatestAbsoluteStandBefore(date: String): DailySummary? = withContext(Dispatchers.IO) {
        solarDao.getLatestAbsoluteStandBeforeOnce(date)
    }

    fun getLatestAbsoluteStandBeforeFlow(date: String): Flow<DailySummary?> = solarDao.getLatestAbsoluteStandBefore(date)

    private fun anchorToNoon(date: String, fallbackTs: Long): Long {
        return try {
            val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = utcSdf.parse(date)!!
                set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) { fallbackTs }
    }

    suspend fun upsertMonthlySolar(month: String, ts: Long, solar: Double) = withContext(Dispatchers.IO) {
        val existing = solarDao.getMonthlySummary(month)
        if (existing != null) {
            solarDao.insertMonthlySummaries(listOf(existing.copy(solarKwh = solar)))
        } else {
            solarDao.insertMonthlySummaries(listOf(MonthlySummary(month = month, timestamp = ts, solarKwh = solar)))
        }
    }

    suspend fun upsertYearlySolar(year: String, ts: Long, solar: Double) = withContext(Dispatchers.IO) {
        val existing = solarDao.getYearlySummary(year)
        if (existing != null) {
            solarDao.insertYearlySummaries(listOf(existing.copy(solarKwh = solar)))
        } else {
            solarDao.insertYearlySummaries(listOf(YearlySummary(year = year, timestamp = ts, solarKwh = solar)))
        }
    }

    private suspend fun updateAggregates(date: String, ts: Long) {
        val month = date.substring(0, 7)
        val year = date.substring(0, 4)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ts }
        
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startMonth = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        val endMonth = cal.timeInMillis
        
        val daily = solarDao.getDailySummariesOnce(startMonth, endMonth)
        if (daily.isNotEmpty()) {
            val lastDay = daily.last()
            
            // Monthly total = Sum of daily values
            val netImp = daily.sumOf { it.gridImportKwh }
            val netExp = daily.sumOf { it.gridExportKwh }

            solarDao.insertMonthlySummaries(listOf(MonthlySummary(
                month = month,
                timestamp = ts,
                solarKwh = daily.sumOf { it.solarKwh },
                gridImportKwh = netImp,
                gridExportKwh = netExp,
                batteryInKwh = daily.sumOf { it.batteryInKwh },
                batteryOutKwh = daily.sumOf { it.batteryOutKwh },
                gasM3 = daily.sumOf { it.gasM3 },
                absSolarKwh = lastDay.absSolarKwh,
                absGridImportKwh = lastDay.absGridImportKwh,
                absGridExportKwh = lastDay.absGridExportKwh,
                absBatteryInKwh = lastDay.absBatteryInKwh,
                absBatteryOutKwh = lastDay.absBatteryOutKwh,
                absGasM3 = lastDay.absGasM3
            )))
        }
        
        cal.set(Calendar.MONTH, 0); cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        val startYear = cal.timeInMillis
        cal.set(Calendar.MONTH, 11); cal.set(Calendar.DAY_OF_MONTH, 31); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        val endYear = cal.timeInMillis
        
        val monthly = solarDao.getMonthlySummariesOnce(startYear, endYear)
        if (monthly.isNotEmpty()) {
            val lastMonth = monthly.last()
            solarDao.insertYearlySummaries(listOf(YearlySummary(
                year = year,
                timestamp = ts,
                solarKwh = monthly.sumOf { it.solarKwh },
                gridImportKwh = monthly.sumOf { it.gridImportKwh },
                gridExportKwh = monthly.sumOf { it.gridExportKwh },
                batteryInKwh = monthly.sumOf { it.batteryInKwh },
                batteryOutKwh = monthly.sumOf { it.batteryOutKwh },
                gasM3 = monthly.sumOf { it.gasM3 },
                absSolarKwh = lastMonth.absSolarKwh,
                absGridImportKwh = lastMonth.absGridImportKwh,
                absGridExportKwh = lastMonth.absGridExportKwh,
                absBatteryInKwh = lastMonth.absBatteryInKwh,
                absBatteryOutKwh = lastMonth.absBatteryOutKwh,
                absGasM3 = lastMonth.absGasM3
            )))
        }
    }

    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        solarDao.clearAllData()
    }

    suspend fun clearEnergyDataOnly() = withContext(Dispatchers.IO) {
        solarDao.clearEnergyData()
    }

    suspend fun insertBulkDailySummaries(list: List<DailySummary>) = withContext(Dispatchers.IO) {
        solarDao.insertDailySummaries(list)
        triggerFullRecalculation()
    }

    private fun calculateDaysBetween(d1Str: String, d2Str: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val d1 = sdf.parse(d1Str)
            val d2 = sdf.parse(d2Str)
            if (d1 != null && d2 != null) {
                val diff = abs(d2.time - d1.time)
                (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            } else 1
        } catch (_: Exception) { 1 }
    }

    private fun firstMeterStands(records: List<DailySummary>) = MeterStands(
        importKwh = records.firstOrNull { it.absGridImportKwh > 0.1 }?.absGridImportKwh,
        exportKwh = records.firstOrNull { it.absGridExportKwh > 0.1 }?.absGridExportKwh,
        gasM3 = records.firstOrNull { it.absGasM3 > 0.001 }?.absGasM3,
        batteryInKwh = records.firstOrNull { it.absBatteryInKwh > 0.1 }?.absBatteryInKwh,
        batteryOutKwh = records.firstOrNull { it.absBatteryOutKwh > 0.1 }?.absBatteryOutKwh
    )

    private fun lastMeterStands(records: List<DailySummary>) = MeterStands(
        importKwh = records.lastOrNull { it.absGridImportKwh > 0.1 }?.absGridImportKwh,
        exportKwh = records.lastOrNull { it.absGridExportKwh > 0.1 }?.absGridExportKwh,
        gasM3 = records.lastOrNull { it.absGasM3 > 0.001 }?.absGasM3,
        batteryInKwh = records.lastOrNull { it.absBatteryInKwh > 0.1 }?.absBatteryInKwh,
        batteryOutKwh = records.lastOrNull { it.absBatteryOutKwh > 0.1 }?.absBatteryOutKwh
    )

    private fun mergeStands(preferred: MeterStands, fallback: MeterStands) = MeterStands(
        importKwh = preferred.importKwh ?: fallback.importKwh,
        exportKwh = preferred.exportKwh ?: fallback.exportKwh,
        gasM3 = preferred.gasM3 ?: fallback.gasM3,
        batteryInKwh = preferred.batteryInKwh ?: fallback.batteryInKwh,
        batteryOutKwh = preferred.batteryOutKwh ?: fallback.batteryOutKwh
    )

    private fun buildStandContexts(periods: Map<String, List<DailySummary>>): Map<String, StandContext> {
        val keys = periods.keys.sorted()
        val before = mutableMapOf<String, MeterStands>()
        var previous = MeterStands()
        keys.forEach { key ->
            before[key] = previous
            previous = mergeStands(lastMeterStands(periods.getValue(key)), previous)
        }

        val after = mutableMapOf<String, MeterStands>()
        var next = MeterStands()
        keys.asReversed().forEach { key ->
            after[key] = next
            next = mergeStands(firstMeterStands(periods.getValue(key)), next)
        }

        return keys.associateWith { key -> StandContext(before.getValue(key), after.getValue(key)) }
    }

    private suspend fun recalculateAllAggregatesWithData(allDaily: List<DailySummary>) {
        if (allDaily.isEmpty()) return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun legacyGridDayDeltaNext(current: DailySummary, next: DailySummary?, importing: Boolean): Double {
            val currentStand = if (importing) current.absGridImportKwh else current.absGridExportKwh
            val nextStand = next?.let { if (importing) it.absGridImportKwh else it.absGridExportKwh }
            if (next == null || nextStand == null || nextStand < currentStand) return 0.0

            val diffDays = calculateDaysBetween(current.date, next.date).coerceAtLeast(1)
            val avgDelta = (nextStand - currentStand) / diffDays
            return avgDelta.takeIf { it <= 100.0 } ?: 0.0
        }

        fun legacyGridDayDelta(current: DailySummary, previous: DailySummary?, importing: Boolean): Double {
            val raw = if (importing) current.rawGridImportKwh else current.rawGridExportKwh
            val currentStand = if (importing) current.absGridImportKwh else current.absGridExportKwh
            val previousStand = previous?.let { if (importing) it.absGridImportKwh else it.absGridExportKwh }
            if (raw <= 0.001 || previous == null || previousStand == null || currentStand < previousStand) return 0.0

            val diffDays = calculateDaysBetween(previous.date, current.date).coerceAtLeast(1)
            val avgDelta = (currentStand - previousStand) / diffDays
            return avgDelta.takeIf { it <= 100.0 } ?: 0.0
        }

        val n = allDaily.size
        val nextImpArr = arrayOfNulls<DailySummary>(n)
        val nextExpArr = arrayOfNulls<DailySummary>(n)
        val nextGasArr = arrayOfNulls<DailySummary>(n)
        val nextBInArr = arrayOfNulls<DailySummary>(n)
        val nextBOutArr = arrayOfNulls<DailySummary>(n)

        var rImp: DailySummary? = null
        var rExp: DailySummary? = null
        var rGas: DailySummary? = null
        var rBIn: DailySummary? = null
        var rBOut: DailySummary? = null

        for (i in n - 1 downTo 0) {
            val item = allDaily[i]
            nextImpArr[i] = rImp
            nextExpArr[i] = rExp
            nextGasArr[i] = rGas
            nextBInArr[i] = rBIn
            nextBOutArr[i] = rBOut

            if (item.absGridImportKwh > 0.1) rImp = item
            if (item.absGridExportKwh > 0.1) rExp = item
            if (item.absGasM3 > 0.001) rGas = item
            if (item.absBatteryInKwh > 0.1) rBIn = item
            if (item.absBatteryOutKwh > 0.1) rBOut = item
        }

        val updatedDaily = ArrayList<DailySummary>(n)
        var lastImpRec: DailySummary? = null
        var lastExpRec: DailySummary? = null
        var lastGasRec: DailySummary? = null
        var lastBInRec: DailySummary? = null
        var lastBOutRec: DailySummary? = null

        for (i in 0 until n) {
            val cur = allDaily[i]
            val nextImpRec = nextImpArr[i]
            val nextExpRec = nextExpArr[i]
            val nextGasRec = nextGasArr[i]
            val nextBInRec = nextBInArr[i]
            val nextBOutRec = nextBOutArr[i]

            val curRawImp = if (cur.rawGridImportKwh > 0.0) cur.rawGridImportKwh else (cur.absGridImportKwh - cur.gridImportOffsetKwh).coerceAtLeast(0.0)
            val curRawExp = if (cur.rawGridExportKwh > 0.0) cur.rawGridExportKwh else (cur.absGridExportKwh - cur.gridExportOffsetKwh).coerceAtLeast(0.0)

            // Grid Import Delta
            val dImp = if (cur.p1SampleTimestamp > 0L) {
                if (cur.p1DayBaselineTimestamp > 0L) (curRawImp - cur.p1DayStartImportAbsKwh).coerceAtLeast(0.0).coerceAtMost(100.0)
                else (if (nextImpRec != null) legacyGridDayDeltaNext(cur, nextImpRec, importing = true) else legacyGridDayDelta(cur, lastImpRec, importing = true))
            } else if (cur.rawGridImportKwh > 0.001) {
                if (nextImpRec != null) legacyGridDayDeltaNext(cur, nextImpRec, importing = true) else legacyGridDayDelta(cur, lastImpRec, importing = true)
            } else if (nextImpRec != null && nextImpRec.absGridImportKwh >= cur.absGridImportKwh && cur.absGridImportKwh > 0.1) {
                val diff = nextImpRec.absGridImportKwh - cur.absGridImportKwh
                val days = calculateDaysBetween(cur.date, nextImpRec.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.gridImportKwh > maxDiff) 0.0 else cur.gridImportKwh)
            } else if (lastImpRec != null && cur.absGridImportKwh >= lastImpRec.absGridImportKwh && lastImpRec.absGridImportKwh > 0.1) {
                val diff = cur.absGridImportKwh - lastImpRec.absGridImportKwh
                val days = calculateDaysBetween(lastImpRec.date, cur.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.gridImportKwh > maxDiff) 0.0 else cur.gridImportKwh)
            } else (if (cur.gridImportKwh > 100.0) 0.0 else cur.gridImportKwh)

            // Grid Export Delta
            val dExp = if (cur.p1SampleTimestamp > 0L) {
                if (cur.p1DayBaselineTimestamp > 0L) (curRawExp - cur.p1DayStartExportAbsKwh).coerceAtLeast(0.0).coerceAtMost(100.0)
                else (if (nextExpRec != null) legacyGridDayDeltaNext(cur, nextExpRec, importing = false) else legacyGridDayDelta(cur, lastExpRec, importing = false))
            } else if (cur.rawGridExportKwh > 0.001) {
                if (nextExpRec != null) legacyGridDayDeltaNext(cur, nextExpRec, importing = false) else legacyGridDayDelta(cur, lastExpRec, importing = false)
            } else if (nextExpRec != null && nextExpRec.absGridExportKwh >= cur.absGridExportKwh && cur.absGridExportKwh > 0.1) {
                val diff = nextExpRec.absGridExportKwh - cur.absGridExportKwh
                val days = calculateDaysBetween(cur.date, nextExpRec.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.gridExportKwh > maxDiff) 0.0 else cur.gridExportKwh)
            } else if (lastExpRec != null && cur.absGridExportKwh >= lastExpRec.absGridExportKwh && lastExpRec.absGridExportKwh > 0.1) {
                val diff = cur.absGridExportKwh - lastExpRec.absGridExportKwh
                val days = calculateDaysBetween(lastExpRec.date, cur.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.gridExportKwh > maxDiff) 0.0 else cur.gridExportKwh)
            } else (if (cur.gridExportKwh > 100.0) 0.0 else cur.gridExportKwh)

            // Gas Delta
            val dGas = if (nextGasRec != null && nextGasRec.absGasM3 >= cur.absGasM3 && cur.absGasM3 > 0.001) {
                val diff = nextGasRec.absGasM3 - cur.absGasM3
                val days = calculateDaysBetween(cur.date, nextGasRec.date)
                val maxDiff = maxOf(50.0, days * 50.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.gasM3 > maxDiff) 0.0 else cur.gasM3)
            } else if (lastGasRec != null && cur.absGasM3 >= lastGasRec.absGasM3 && lastGasRec.absGasM3 > 0.001) {
                val diff = cur.absGasM3 - lastGasRec.absGasM3
                val days = calculateDaysBetween(lastGasRec.date, cur.date)
                val maxDiff = maxOf(50.0, days * 50.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.gasM3 > maxDiff) 0.0 else cur.gasM3)
            } else (if (cur.gasM3 > 50.0) 0.0 else cur.gasM3)

            // Battery In Delta
            val dBIn = if (lastBInRec != null && cur.absBatteryInKwh >= lastBInRec.absBatteryInKwh && lastBInRec.absBatteryInKwh > 0.1) {
                val diff = cur.absBatteryInKwh - lastBInRec.absBatteryInKwh
                val days = calculateDaysBetween(lastBInRec.date, cur.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.batteryInKwh > maxDiff) 0.0 else cur.batteryInKwh)
            } else if (lastBInRec == null && nextBInRec != null && nextBInRec.absBatteryInKwh >= cur.absBatteryInKwh && cur.absBatteryInKwh > 0.1) {
                val diff = nextBInRec.absBatteryInKwh - cur.absBatteryInKwh
                val days = calculateDaysBetween(cur.date, nextBInRec.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.batteryInKwh > maxDiff) 0.0 else cur.batteryInKwh)
            } else (if (cur.batteryInKwh > 100.0) 0.0 else cur.batteryInKwh)

            // Battery Out Delta
            val dBOut = if (lastBOutRec != null && cur.absBatteryOutKwh >= lastBOutRec.absBatteryOutKwh && lastBOutRec.absBatteryOutKwh > 0.1) {
                val diff = cur.absBatteryOutKwh - lastBOutRec.absBatteryOutKwh
                val days = calculateDaysBetween(lastBOutRec.date, cur.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.batteryOutKwh > maxDiff) 0.0 else cur.batteryOutKwh)
            } else if (lastBOutRec == null && nextBOutRec != null && nextBOutRec.absBatteryOutKwh >= cur.absBatteryOutKwh && cur.absBatteryOutKwh > 0.1) {
                val diff = nextBOutRec.absBatteryOutKwh - cur.absBatteryOutKwh
                val days = calculateDaysBetween(cur.date, nextBOutRec.date)
                val maxDiff = maxOf(100.0, days * 100.0)
                if (diff in 0.0..maxDiff) diff else (if (cur.batteryOutKwh > maxDiff) 0.0 else cur.batteryOutKwh)
            } else (if (cur.batteryOutKwh > 100.0) 0.0 else cur.batteryOutKwh)

            if (cur.absGridImportKwh > 0.1 && (nextImpRec == null || nextImpRec.absGridImportKwh >= cur.absGridImportKwh || cur.p1SampleTimestamp == 0L)) lastImpRec = cur
            if (cur.absGridExportKwh > 0.1 && (nextExpRec == null || nextExpRec.absGridExportKwh >= cur.absGridExportKwh || cur.p1SampleTimestamp == 0L)) lastExpRec = cur
            if (cur.absGasM3 > 0.001 && (nextGasRec == null || nextGasRec.absGasM3 >= cur.absGasM3 || cur.p1SampleTimestamp == 0L)) lastGasRec = cur
            if (cur.absBatteryInKwh > 0.1 && (nextBInRec == null || nextBInRec.absBatteryInKwh >= cur.absBatteryInKwh || cur.p1SampleTimestamp == 0L)) lastBInRec = cur
            if (cur.absBatteryOutKwh > 0.1 && (nextBOutRec == null || nextBOutRec.absBatteryOutKwh >= cur.absBatteryOutKwh || cur.p1SampleTimestamp == 0L)) lastBOutRec = cur

            updatedDaily.add(cur.copy(gridImportKwh = dImp, gridExportKwh = dExp, gasM3 = dGas, batteryInKwh = dBIn, batteryOutKwh = dBOut))
        }
        solarDao.insertDailySummaries(updatedDaily)

        // Group once so each period does not repeatedly scan the full daily history.
        val dailyByMonth = updatedDaily.groupBy { it.date.substring(0, 7) }
        val months = dailyByMonth.keys.sorted()
        val monthStandContexts = buildStandContexts(dailyByMonth)
        val existingMonths = solarDao.getMonthlySummariesOnce(0, Long.MAX_VALUE).associateBy { it.month }
        
        val monthlyToInsert = months.map { month ->
            val daysInMonth = dailyByMonth.getValue(month)
            val firstDay = daysInMonth.first()
            val lastDay = daysInMonth.last()
            val existing = existingMonths[month]

            val calculatedSolar = daysInMonth.sumOf { it.solarKwh }
            val finalSolar = if (existing != null) maxOf(existing.solarKwh, calculatedSolar) else calculatedSolar

            MonthlySummary(
                month = month, timestamp = firstDay.timestamp,
                solarKwh = finalSolar, 
                gridImportKwh = daysInMonth.sumOf { it.gridImportKwh }, 
                gridExportKwh = daysInMonth.sumOf { it.gridExportKwh },
                batteryInKwh = daysInMonth.sumOf { it.batteryInKwh }, 
                batteryOutKwh = daysInMonth.sumOf { it.batteryOutKwh },
                gasM3 = daysInMonth.sumOf { it.gasM3 }, 
                absSolarKwh = lastDay.absSolarKwh, absGridImportKwh = lastDay.absGridImportKwh,
                absGridExportKwh = lastDay.absGridExportKwh, absBatteryInKwh = lastDay.absBatteryInKwh,
                absBatteryOutKwh = lastDay.absBatteryOutKwh, absGasM3 = lastDay.absGasM3
            )
        }
        solarDao.insertMonthlySummaries(monthlyToInsert)

        // And update all yearly summaries with the same protection
        val dailyByYear = updatedDaily.groupBy { it.date.substring(0, 4) }
        val monthlyByYear = monthlyToInsert.groupBy { it.month.substring(0, 4) }
        val years = dailyByYear.keys.sorted()
        val existingYears = solarDao.getAllYearlySummariesOnce().associateBy { it.year }

        val yearlyToInsert = years.map { year ->
            val daysInYear = dailyByYear.getValue(year)
            val monthsInYear = monthlyByYear[year].orEmpty()
            val firstDay = daysInYear.first()
            val lastDay = daysInYear.last()
            val existing = existingYears[year]

            val calculatedSolar = daysInYear.sumOf { it.solarKwh }
            val finalSolar = if (existing != null) maxOf(existing.solarKwh, calculatedSolar) else calculatedSolar

            YearlySummary(
                year = year, timestamp = firstDay.timestamp,
                solarKwh = finalSolar, 
                gridImportKwh = monthsInYear.sumOf { it.gridImportKwh }, 
                gridExportKwh = monthsInYear.sumOf { it.gridExportKwh },
                batteryInKwh = monthsInYear.sumOf { it.batteryInKwh }, 
                batteryOutKwh = monthsInYear.sumOf { it.batteryOutKwh },
                gasM3 = monthsInYear.sumOf { it.gasM3 }, 
                absSolarKwh = lastDay.absSolarKwh, absGridImportKwh = lastDay.absGridImportKwh,
                absGridExportKwh = lastDay.absGridExportKwh, absBatteryInKwh = lastDay.absBatteryInKwh,
                absBatteryOutKwh = lastDay.absBatteryOutKwh, absGasM3 = lastDay.absGasM3
            )
        }
        solarDao.insertYearlySummaries(yearlyToInsert)
    }

    private fun getPreviousDate(date: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(date)!!
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d; add(Calendar.DAY_OF_YEAR, -1) }
            sdf.format(cal.time)
        } catch (_: Exception) { date }
    }

    suspend fun triggerFullRecalculation() = withContext(Dispatchers.IO) {
        val allDailyFull = solarDao.getDailySummariesRangeOnce("2000-01-01", "2100-12-31")
        if (allDailyFull.isEmpty()) return@withContext
        val sortedDaily = allDailyFull.sortedBy { it.date }
        recalculateAllAggregatesWithData(sortedDaily)
    }

    fun getAllRatePeriods() = solarDao.getAllRatePeriods()
    suspend fun insertRatePeriod(period: RatePeriod) = withContext(Dispatchers.IO) {
        val existing = solarDao.getRatePeriodByDate(period.endDate)
        if (existing != null) {
            solarDao.insertRatePeriod(period.copy(id = existing.id))
        } else {
            solarDao.insertRatePeriod(period)
        }
    }
    suspend fun deleteRatePeriod(id: Long) = solarDao.deleteRatePeriod(id)
    suspend fun getDailySummaryOnce(date: String): DailySummary? = withContext(Dispatchers.IO) {
        solarDao.getDailySummary(date)
    }

    suspend fun getDailySummariesRangeOnce(start: String, end: String) = solarDao.getDailySummariesRangeOnce(start, end)
}
