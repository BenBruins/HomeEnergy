package com.example.energiehuishouding.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.energiehuishouding.data.CostBreakdown
import com.example.energiehuishouding.data.CostCalculator
import com.example.energiehuishouding.data.HistoryImporter
import com.example.energiehuishouding.data.InverterRepository
import com.example.energiehuishouding.data.SettingsRepository
import com.example.energiehuishouding.data.local.*
import com.example.energiehuishouding.data.remote.homewizard.HomeWizardData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.seconds

enum class TimeRange { MONTH, YEAR, TOTAL }
enum class ViewMode { ELECTRICITY, GAS, PHASES }

data class LivePhasePoint(
    val timeLabel: String,
    val l1W: Double,
    val l2W: Double,
    val l3W: Double,
    val l1A: Double,
    val l2A: Double,
    val l3A: Double
)

data class ChartDataPoint(val x: Float, val y: Float, val label: String, val seriesName: String)

data class DashboardUiState(
    val solarEnergyTodayKwh: Double = 0.0,
    val periodSolarKwh: Double = 0.0,
    val periodSolarProfit: Double = 0.0,
    val gridImportTodayKwh: Double = 0.0,
    val gridExportTodayKwh: Double = 0.0,
    val gridTodayTotalsReliable: Boolean = false,
    val houseTodayTotalReliable: Boolean = false,
    val batteryInTodayKwh: Double = 0.0,
    val batteryOutTodayKwh: Double = 0.0,
    val batteryEfficiency: Double? = null,
    val gasTodayM3: Double = 0.0,
    val periodGasM3: Double = 0.0,
    val liveSolarPower: Int = 0,
    val gridPowerWatts: Int = 0,
    val batteryPowerWatts: Int = 0,
    val housePowerWatts: Int = 0,
    val connectionStatus: Map<String, String> = emptyMap(),
    val chartData: List<ChartDataPoint> = emptyList(),
    val visibleSeries: Set<String> = setOf("Solar", "Import", "Export", "Bat-In", "Bat-Out"),
    val selectedRange: TimeRange = TimeRange.MONTH,
    val failedRanges: Set<TimeRange> = emptySet(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val lastSyncTimestamp: Long = 0,
    val inverterIp: String = "",
    val p1Ip: String = "",
    val batteryIp: String = "",
    val batteryLocalToken: String = "",
    val appKey: String = "",
    val appSecret: String = "",
    val plantId: String = "",
    val apiKey: String = "",
    val gatewayToken: String = "",
    val inverterBrand: String = "Solplanet",
    val seApiKey: String = "",
    val seSiteId: String = "",
    val ratePeriods: List<RatePeriod> = emptyList(),
    val costResults: List<CostBreakdown> = emptyList(),
    val startDate: Long = System.currentTimeMillis(),
    val minX: Float = 1f,
    val maxX: Float = 31f,
    val viewMode: ViewMode = ViewMode.ELECTRICITY,
    val p1Data: HomeWizardData? = null,
    val batteryData: HomeWizardData? = null,
    val houseEnergyInTodayKwh: Double = 0.0,
    val periodBatteryProfit: Double = 0.0,
    val livePhaseHistory: List<LivePhasePoint> = emptyList()
)

class MainViewModel(
    private val repository: InverterRepository,
    private val settingsRepository: SettingsRepository,
    private val historyImporter: HistoryImporter
) : ViewModel() {

    private val _livePhaseHistory = MutableStateFlow<List<LivePhasePoint>>(emptyList())
    private var lastPhaseUpdateMs: Long = 0L

    private val _selectedRange = MutableStateFlow(TimeRange.MONTH)
    private val _startDate = MutableStateFlow(Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis)
    
    private val _visibleSeries = MutableStateFlow(setOf("Solar", "Import", "Export", "Bat-In", "Bat-Out"))
    private val _isRefreshing = MutableStateFlow(false)
    private val _failedRanges = MutableStateFlow(setOf<TimeRange>())
    private val _costResults = MutableStateFlow<List<CostBreakdown>>(emptyList())
    private val _viewMode = MutableStateFlow(ViewMode.ELECTRICITY)

    private val calculator = CostCalculator()
    private val settingsFlow = settingsRepository.settingsFlow

    init {
        viewModelScope.launch {
            repository.triggerFullRecalculation()
            // Load the selected period and its live Solar overview when the
            // dashboard opens; otherwise Solar can stay at 0 W until the user
            // changes the range or background work happens to run.
            syncHistory()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val summaryDataFlow = combine(_selectedRange, _startDate) { range, start ->
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = start }
        when (range) {
            TimeRange.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1); val s = cal.timeInMillis
                cal.add(Calendar.MONTH, 1); cal.set(Calendar.DAY_OF_MONTH, 1); val endMs = cal.timeInMillis
                repository.getDailySummaries(s, endMs)
            }
            TimeRange.YEAR -> {
                cal.set(Calendar.MONTH, 0); cal.set(Calendar.DAY_OF_MONTH, 1); val s = cal.timeInMillis
                cal.set(Calendar.MONTH, 11); cal.set(Calendar.DAY_OF_MONTH, 31)
                repository.getMonthlySummaries(s, cal.timeInMillis)
            }
            TimeRange.TOTAL -> repository.getYearlySummaries()
        }
    }.flatMapLatest { it }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lastKnownSummaryFlow = flow {
        while(true) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            emit(repository.getLatestAbsoluteStandBefore(todayStr))
            delay(10.seconds)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todaySummaryFlow = flow {
        while (true) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            emitAll(repository.getTodaySummaryFlow(todayStr))
            delay(10.seconds)
        }
    }

    private val livePollingFlow = flow {
        while (true) {
            val s = settingsFlow.first()
            val invIp = s[0] as? String ?: ""; val p1Ip = s[1] as? String ?: ""; val batIp = s[2] as? String ?: ""
            if (p1Ip.isNotBlank() || invIp.isNotBlank() || batIp.isNotBlank()) {
                repository.refreshLocalOnlyData(invIp, p1Ip, batIp, updateLiveFlows = true)
            }
            emit(System.currentTimeMillis())
            delay(10.seconds)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        summaryDataFlow, repository.p1Data, repository.batteryData, repository.liveSolarWatts,
        repository.connectionStatus, settingsFlow, _isRefreshing, _startDate, _visibleSeries, _selectedRange, _failedRanges,
        repository.getAllRatePeriods(), _costResults, lastKnownSummaryFlow, livePollingFlow, _viewMode, _livePhaseHistory, todaySummaryFlow
    ) { args ->
        try {
            val summaries = args[0] as List<Any>
            val p1 = args[1] as HomeWizardData?; val battery = args[2] as HomeWizardData?
            val liveSolar = args[3] as Int; val conn = args[4] as Map<String, String>; val settings = args[5] as Array<Any?>
            val refreshing = args[6] as Boolean; val startMs = args[7] as Long; val visible = args[8] as Set<String>
            val range = args[9] as TimeRange; val failed = args[10] as Set<TimeRange>
            val rates = args[11] as List<RatePeriod>; val costRes = args[12] as List<CostBreakdown>
            val lastStand = args[13] as DailySummary?; val mode = args[15] as ViewMode
            val phaseHistory = args[16] as List<LivePhasePoint>

            if (p1 != null) {
                val l1W = p1.activePowerL1W ?: ((p1.activeCurrentL1A ?: 0.0) * 230.0)
                val l2W = p1.activePowerL2W ?: ((p1.activeCurrentL2A ?: 0.0) * 230.0)
                val l3W = p1.activePowerL3W ?: ((p1.activeCurrentL3A ?: 0.0) * 230.0)

                val l1A = p1.activeCurrentL1A ?: (if (l1W > 0) l1W / 230.0 else 0.0)
                val l2A = p1.activeCurrentL2A ?: (if (l2W > 0) l2W / 230.0 else 0.0)
                val l3A = p1.activeCurrentL3A ?: (if (l3W > 0) l3W / 230.0 else 0.0)

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastPhaseUpdateMs >= 2000L) {
                    lastPhaseUpdateMs = nowMs
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(nowMs))
                    val newPoint = LivePhasePoint(timeStr, l1W, l2W, l3W, l1A, l2A, l3A)
                    viewModelScope.launch {
                        _livePhaseHistory.value = (_livePhaseHistory.value + newPoint).takeLast(60)
                    }
                }
            }

            // HomeWizard also reports a total active_power_w value. On some P1
            // meters that total can lag or differ from the per-phase readings;
            // use the three phase values together when all are present so the
            // dashboard total agrees with the phase screen.
            val phasePowerReadings = listOf(p1?.activePowerL1W, p1?.activePowerL2W, p1?.activePowerL3W)
            val gridPowerW = if (phasePowerReadings.all { it != null }) {
                phasePowerReadings.sumOf { it ?: 0.0 }
            } else {
                p1?.activePowerW ?: p1?.powerW ?: 0.0
            }
            val gridPower = gridPowerW.toInt()
            val batPower = (battery?.powerW ?: battery?.activePowerW ?: 0.0).toInt()
            val housePower = (liveSolar + gridPower - batPower).coerceAtLeast(0)
            
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val todaySummary = summaries.filterIsInstance<DailySummary>().find { it.date == todayStr }
            val previousDayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
            val previousDaySummary = summaries.filterIsInstance<DailySummary>().find { it.date == previousDayStr }
            val todayRawImp = todaySummary?.let { if (it.rawGridImportKwh > 0.0) it.rawGridImportKwh else (it.absGridImportKwh - it.gridImportOffsetKwh).coerceAtLeast(0.0) } ?: 0.0
            val todayRawExp = todaySummary?.let { if (it.rawGridExportKwh > 0.0) it.rawGridExportKwh else (it.absGridExportKwh - it.gridExportOffsetKwh).coerceAtLeast(0.0) } ?: 0.0

            val hasDirectStartBaseline = todaySummary != null && todaySummary.p1DayStartImportAbsKwh > 0.0 && todayRawImp >= todaySummary.p1DayStartImportAbsKwh
            val hasPreviousStand = todaySummary != null && lastStand != null && todaySummary.absGridImportKwh >= lastStand.absGridImportKwh
            val midnightBaselineAvailable = lastStand?.date == previousDayStr && (todaySummary?.p1DayBaselineTimestamp ?: 0L) > 0L
            val previousDayCorrectionAvailable = lastStand?.date == previousDayStr &&
                (todaySummary?.p1SampleTimestamp ?: 0L) > 0L && previousDaySummary != null
            val gridTodayReliable = hasDirectStartBaseline || hasPreviousStand || midnightBaselineAvailable || previousDayCorrectionAvailable || (todaySummary != null && (todaySummary.absGridImportKwh > 0 || todaySummary.absGridExportKwh > 0))
            val importSincePreviousStand = if (todaySummary != null && lastStand != null)
                (todaySummary.absGridImportKwh - lastStand.absGridImportKwh).coerceAtLeast(0.0) else 0.0
            val exportSincePreviousStand = if (todaySummary != null && lastStand != null)
                (todaySummary.absGridExportKwh - lastStand.absGridExportKwh).coerceAtLeast(0.0) else 0.0
            val gridImpToday = when {
                hasDirectStartBaseline -> (todayRawImp - todaySummary!!.p1DayStartImportAbsKwh).coerceAtLeast(0.0)
                hasPreviousStand -> (todaySummary!!.absGridImportKwh - lastStand!!.absGridImportKwh).coerceAtLeast(0.0)
                midnightBaselineAvailable -> (todayRawImp - todaySummary!!.p1DayStartImportAbsKwh).coerceAtLeast(0.0)
                previousDayCorrectionAvailable -> (importSincePreviousStand - (previousDaySummary?.gridImportKwh ?: 0.0)).coerceAtLeast(0.0)
                todaySummary != null -> todaySummary.gridImportKwh
                else -> 0.0
            }
            val gridExpToday = when {
                hasDirectStartBaseline -> (todayRawExp - todaySummary!!.p1DayStartExportAbsKwh).coerceAtLeast(0.0)
                hasPreviousStand -> (todaySummary!!.absGridExportKwh - lastStand!!.absGridExportKwh).coerceAtLeast(0.0)
                midnightBaselineAvailable -> (todayRawExp - todaySummary!!.p1DayStartExportAbsKwh).coerceAtLeast(0.0)
                previousDayCorrectionAvailable -> (exportSincePreviousStand - (previousDaySummary?.gridExportKwh ?: 0.0)).coerceAtLeast(0.0)
                todaySummary != null -> todaySummary.gridExportKwh
                else -> 0.0
            }
            val batInToday = if (battery != null && lastStand != null && lastStand.absBatteryInKwh > 0.001) ((battery.energyInKwh ?: battery.totalImportKwh ?: 0.0) - lastStand.absBatteryInKwh).coerceAtLeast(0.0) else 0.0
            val batOutToday = if (battery != null && lastStand != null && lastStand.absBatteryOutKwh > 0.001) ((battery.energyOutKwh ?: battery.totalExportKwh ?: 0.0) - lastStand.absBatteryOutKwh).coerceAtLeast(0.0) else 0.0
            val gasToday = if (p1 != null && (p1.totalGasM3 ?: 0.0) > 0.001 && lastStand != null && lastStand.absGasM3 > 0.001) {
                ((p1.totalGasM3 ?: 0.0) + lastStand.gasOffsetM3 - lastStand.absGasM3).coerceAtLeast(0.0)
            } else {
                todaySummary?.gasM3 ?: 0.0
            }

            val solarToday = summaries.filterIsInstance<DailySummary>().find { it.date == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }?.solarKwh ?: 0.0
            val previousDayHouseTotal = previousDaySummary?.let {
                (it.solarKwh + it.gridImportKwh - it.gridExportKwh - it.batteryInKwh + it.batteryOutKwh).coerceAtLeast(0.0)
            } ?: 0.0
            val houseEnergyToday = when {
                midnightBaselineAvailable -> (solarToday + gridImpToday - gridExpToday - batInToday + batOutToday).coerceAtLeast(0.0)
                previousDayCorrectionAvailable -> (solarToday + importSincePreviousStand - exportSincePreviousStand - batInToday + batOutToday - previousDayHouseTotal).coerceAtLeast(0.0)
                else -> 0.0
            }

            val periodBatIn = summaries.sumOf { (it as? DailySummary)?.batteryInKwh ?: (it as? MonthlySummary)?.batteryInKwh ?: 0.0 }
            val periodBatOut = summaries.sumOf { (it as? DailySummary)?.batteryOutKwh ?: (it as? MonthlySummary)?.batteryOutKwh ?: 0.0 }
            val efficiency = if (periodBatIn > 0.1) (periodBatOut / periodBatIn) * 100.0 else null

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startMs }
            val (computedMinX, computedMaxX) = when (range) {
                TimeRange.MONTH -> 1f to cal.getActualMaximum(Calendar.DAY_OF_MONTH).toFloat()
                TimeRange.YEAR -> 1f to 12f
                TimeRange.TOTAL -> {
                    val years = summaries.filterIsInstance<YearlySummary>().mapNotNull { it.year.toFloatOrNull() }
                    val currentYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
                    val minY = years.minOrNull() ?: (currentYear - 5).toFloat()
                    val maxY = years.maxOrNull() ?: currentYear.toFloat()
                    minY to maxY
                }
            }

            val periodImp = summaries.sumOf { (it as? DailySummary)?.gridImportKwh ?: (it as? MonthlySummary)?.gridImportKwh ?: (it as? YearlySummary)?.gridImportKwh ?: 0.0 }
            val periodExp = summaries.sumOf { (it as? DailySummary)?.gridExportKwh ?: (it as? MonthlySummary)?.gridExportKwh ?: (it as? YearlySummary)?.gridExportKwh ?: 0.0 }
            val computedPeriodGas = summaries.sumOf { (it as? DailySummary)?.gasM3 ?: (it as? MonthlySummary)?.gasM3 ?: (it as? YearlySummary)?.gasM3 ?: 0.0 }

            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val isCurrentMonth = monthFormat.format(Date(startMs)) == monthFormat.format(Date())

            DashboardUiState(
                solarEnergyTodayKwh = solarToday,
                periodSolarKwh = summaries.sumOf { (it as? DailySummary)?.solarKwh ?: (it as? MonthlySummary)?.solarKwh ?: (it as? YearlySummary)?.solarKwh ?: 0.0 },
                gridImportTodayKwh = if (isCurrentMonth) gridImpToday else periodImp,
                gridExportTodayKwh = if (isCurrentMonth) gridExpToday else periodExp,
                gridTodayTotalsReliable = !isCurrentMonth || gridTodayReliable,
                houseTodayTotalReliable = gridTodayReliable,
                batteryInTodayKwh = batInToday, batteryOutTodayKwh = batOutToday,
                gasTodayM3 = if (isCurrentMonth) gasToday else computedPeriodGas, periodGasM3 = computedPeriodGas,
                liveSolarPower = liveSolar, gridPowerWatts = gridPower, batteryPowerWatts = batPower, housePowerWatts = housePower,
                connectionStatus = conn, chartData = processSummaries(summaries), visibleSeries = visible,
                selectedRange = range, failedRanges = failed, isRefreshing = refreshing, startDate = startMs, minX = computedMinX, maxX = computedMaxX,
                inverterIp = settings[0] as? String ?: "", p1Ip = settings[1] as? String ?: "", batteryIp = settings[2] as? String ?: "",
                batteryLocalToken = settings[3] as? String ?: "", appKey = settings[5] as? String ?: "", appSecret = settings[6] as? String ?: "",
                plantId = settings[7] as? String ?: "", apiKey = settings[8] as? String ?: "", gatewayToken = settings[4] as? String ?: "",
                inverterBrand = settings[10] as? String ?: "Solplanet", seApiKey = settings[11] as? String ?: "", seSiteId = settings[12] as? String ?: "",
                ratePeriods = rates, costResults = costRes, viewMode = mode, p1Data = p1, batteryData = battery,
                houseEnergyInTodayKwh = houseEnergyToday, batteryEfficiency = efficiency,
                livePhaseHistory = phaseHistory
            )
        } catch (e: Exception) { Log.e("MainViewModel", "UI State Error", e); DashboardUiState(error = "Fout: ${e.message}") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun processSummaries(data: List<Any>): List<ChartDataPoint> {
        val res = mutableListOf<ChartDataPoint>()
        val prefix = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(_startDate.value))
        val currentMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isCurrentMonth = prefix == currentMonthPrefix

        data.forEach { item ->
            when (item) {
                is DailySummary -> if (item.date.startsWith(prefix) && (!isCurrentMonth || item.date <= today)) {
                    val x = item.date.substringAfterLast("-").toFloatOrNull() ?: 0f
                    res.add(ChartDataPoint(x, item.solarKwh.toFloat(), item.date, "Solar"))
                    res.add(ChartDataPoint(x, item.gridImportKwh.toFloat(), item.date, "Import"))
                    res.add(ChartDataPoint(x, item.gridExportKwh.toFloat(), item.date, "Export"))
                    res.add(ChartDataPoint(x, item.batteryInKwh.toFloat(), item.date, "Bat-In"))
                    res.add(ChartDataPoint(x, item.batteryOutKwh.toFloat(), item.date, "Bat-Out"))
                    res.add(ChartDataPoint(x, item.gasM3.toFloat(), item.date, "Gas"))
                }
                is MonthlySummary -> {
                    val x = item.month.substringAfterLast("-").toFloatOrNull() ?: 0f
                    res.add(ChartDataPoint(x, item.solarKwh.toFloat(), item.month, "Solar"))
                    res.add(ChartDataPoint(x, item.gridImportKwh.toFloat(), item.month, "Import"))
                    res.add(ChartDataPoint(x, item.gridExportKwh.toFloat(), item.month, "Export"))
                    res.add(ChartDataPoint(x, item.batteryInKwh.toFloat(), item.month, "Bat-In"))
                    res.add(ChartDataPoint(x, item.batteryOutKwh.toFloat(), item.month, "Bat-Out"))
                    res.add(ChartDataPoint(x, item.gasM3.toFloat(), item.month, "Gas"))
                }
                is YearlySummary -> {
                    val x = item.year.toFloatOrNull() ?: 0f
                    res.add(ChartDataPoint(x, item.solarKwh.toFloat(), item.year, "Solar"))
                    res.add(ChartDataPoint(x, item.gridImportKwh.toFloat(), item.year, "Import"))
                    res.add(ChartDataPoint(x, item.gridExportKwh.toFloat(), item.year, "Export"))
                    res.add(ChartDataPoint(x, item.batteryInKwh.toFloat(), item.year, "Bat-In"))
                    res.add(ChartDataPoint(x, item.batteryOutKwh.toFloat(), item.year, "Bat-Out"))
                    res.add(ChartDataPoint(x, item.gasM3.toFloat(), item.year, "Gas"))
                }
            }
        }
        return res.sortedWith(compareBy({ it.x }, { it.seriesName }))
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        _visibleSeries.value = if (mode == ViewMode.ELECTRICITY) {
            setOf("Solar", "Import", "Export", "Bat-In", "Bat-Out")
        } else {
            setOf("Gas")
        }
    }
    fun setRange(range: TimeRange) {
        _selectedRange.value = range
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = _startDate.value; if (range != TimeRange.TOTAL) set(Calendar.DAY_OF_MONTH, 1) }
        _startDate.value = cal.timeInMillis
        syncHistory()
    }
    fun navigateDate(forward: Boolean) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = _startDate.value }
        if (_selectedRange.value == TimeRange.MONTH) cal.add(Calendar.MONTH, if (forward) 1 else -1)
        else cal.add(Calendar.YEAR, if (forward) 1 else -1)
        _startDate.value = cal.timeInMillis
        syncHistory()
    }
    fun navigateYear(forward: Boolean) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = _startDate.value; add(Calendar.YEAR, if (forward) 1 else -1) }
        _startDate.value = cal.timeInMillis
        syncHistory()
    }
    fun toggleSeriesVisibility(name: String) { _visibleSeries.value = if (_visibleSeries.value.contains(name)) _visibleSeries.value - name else _visibleSeries.value + name }
    fun clearEnergyData() { viewModelScope.launch { repository.clearEnergyDataOnly() } }
    fun recalculateTotals() { viewModelScope.launch { _isRefreshing.value = true; repository.triggerFullRecalculation(); _isRefreshing.value = false } }
    fun updateSettings(inv: String, p1: String, bat: String, batToken: String, ak: String, sec: String, pid: String, apiKey: String, gatewayToken: String, brand: String, seApiKey: String, seSiteId: String) {
        viewModelScope.launch {
            settingsRepository.saveInverterIp(inv.trim()); settingsRepository.saveInverterBrand(brand)
            settingsRepository.saveHomeWizardSettings(p1.trim(), bat.trim()); settingsRepository.saveBatteryLocalToken(batToken.trim())
            settingsRepository.saveCloudSettings(ak.trim(), sec.trim(), pid.trim(), apiKey.trim())
            settingsRepository.saveSolplanetToken(gatewayToken.trim()); settingsRepository.saveSolarEdgeSettings(seApiKey.trim(), seSiteId.trim())
            syncHistory()
        }
    }
    fun importMeterCsv(uri: Uri, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = historyImporter.importMeterCsv(uri)
            _isRefreshing.value = false
            onComplete(result.fold(
                onSuccess = { count -> if (count > 0) "$count meterstanden/dagen geïmporteerd" else "Geen geldige data gevonden in het bestand" },
                onFailure = { error -> "Import mislukt: ${error.message ?: "onbekende fout"}" }
            ))
        }
    }
    fun importBatteryCsv(uri: Uri, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = historyImporter.importBatteryCsv(uri)
            _isRefreshing.value = false
            onComplete(result.fold(
                onSuccess = { count -> if (count > 0) "$count batterijstanden/dagen geïmporteerd" else "Geen geldige data gevonden in het bestand" },
                onFailure = { error -> "Import mislukt: ${error.message ?: "onbekende fout"}" }
            ))
        }
    }
    fun importGasCsv(uri: Uri, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = historyImporter.importGasCsv(uri)
            _isRefreshing.value = false
            onComplete(result.fold(
                onSuccess = { count -> if (count > 0) "$count gasstanden/dagen geïmporteerd" else "Geen geldige data gevonden in het bestand" },
                onFailure = { error -> "Import mislukt: ${error.message ?: "onbekende fout"}" }
            ))
        }
    }
    fun importRatesCsv(uri: Uri, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = historyImporter.importRatesCsv(uri)
            _isRefreshing.value = false
            onComplete(result.fold(
                onSuccess = { count -> if (count > 0) "$count tariefregels geïmporteerd" else "Geen tariefregels gevonden in het bestand" },
                onFailure = { error -> "Import mislukt: ${error.message ?: "onbekende fout"}" }
            ))
        }
    }
    fun addRatePeriod(period: RatePeriod) { viewModelScope.launch { repository.insertRatePeriod(period) } }
    fun removeRatePeriod(id: Long) { viewModelScope.launch { repository.deleteRatePeriod(id) } }
    fun calculateCosts(start: String, end: String) {
        viewModelScope.launch {
            val summaries = repository.getDailySummariesRangeOnce("2000-01-01", "2100-12-31")
            _costResults.value = calculator.calculate(start, end, uiState.value.ratePeriods, summaries)
        }
    }

    suspend fun getExportCsv(type: String): String {
        val exportLocale = Locale.forLanguageTag("nl-NL")
        if (type != "Tarieven") {
            repository.triggerFullRecalculation()
        }
        val summaries = repository.getDailySummariesRangeOnce("2000-01-01", "2100-12-31").sortedBy { it.date }
        val sb = StringBuilder()
        when (type) {
            "Tarieven" -> {
                sb.append("Einddatum;Tarief;EB;GasTarief;GasEB;GasAE;GasVLK;VBV;TLK;AE;VEB;VLK;Korting\n")
                repository.getAllRatePeriods().first().forEach { r -> 
                    sb.append("${r.endDate};${String.format(exportLocale, "%.5f", r.tariff)};${String.format(exportLocale, "%.5f", r.energyTax)};${String.format(exportLocale, "%.5f", r.gasTariff)};${String.format(exportLocale, "%.5f", r.gasTax)};${String.format(exportLocale, "%.5f", r.gasConnection)};${String.format(exportLocale, "%.5f", r.variableGasDeliveryCost)};${String.format(exportLocale, "%.5f", r.compensation)};${String.format(exportLocale, "%.5f", r.tlkRate)};${String.format(exportLocale, "%.5f", r.gridConnection)};${String.format(exportLocale, "%.5f", r.taxReduction)};${String.format(exportLocale, "%.5f", r.fixedDeliveryCost)};${String.format(exportLocale, "%.2f", r.discountPercentage)}\n") 
                }
            }
            else -> {
                sb.append("Datum;Solar;Abs_Import;Abs_Export;Abs_BatIn;Abs_BatOut;Abs_Gas\n")
                summaries.forEach { sb.append("${it.date};${String.format(exportLocale, "%.3f", it.solarKwh)};${String.format(exportLocale, "%.3f", it.absGridImportKwh)};${String.format(exportLocale, "%.3f", it.absGridExportKwh)};${String.format(exportLocale, "%.3f", it.absBatteryInKwh)};${String.format(exportLocale, "%.3f", it.absBatteryOutKwh)};${String.format(exportLocale, "%.3f", it.absGasM3)}\n") }
            }
        }
        return sb.toString()
    }

    fun syncHistory() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val s = settingsFlow.first()
                repository.syncCloudHistory(
                    range = _selectedRange.value, date = _startDate.value, 
                    brand = s[10] as? String ?: "Solplanet",
                    ak = s[5] as? String ?: "", sec = s[6] as? String ?: "", pid = s[7] as? String ?: "", 
                    apiKey = s[8] as? String ?: "", token = s[4] as? String ?: "",
                    seApiKey = s[11] as? String ?: "", seSiteId = s[12] as? String ?: ""
                )
            } catch (e: Exception) { Log.e("MainViewModel", "Sync error", e) }
            _isRefreshing.value = false
        }
    }

    class Factory(private val repository: InverterRepository, private val settingsRepository: SettingsRepository, private val historyImporter: HistoryImporter) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository, settingsRepository, historyImporter) as T
    }
}
