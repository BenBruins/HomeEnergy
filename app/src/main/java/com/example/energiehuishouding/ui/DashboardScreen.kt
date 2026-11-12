package com.example.energiehuishouding.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.energiehuishouding.data.local.RatePeriod
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showCostCalculator by remember { mutableStateOf(false) }
    var importFeedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val meterLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> viewModel.importMeterCsv(uri) { message -> importFeedback = message } }
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> viewModel.importBatteryCsv(uri) { message -> importFeedback = message } }
    }
    val gasLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> viewModel.importGasCsv(uri) { message -> importFeedback = message } }
    }
    val ratesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> viewModel.importRatesCsv(uri) { message -> importFeedback = message } }
    }

    var exportType by remember { mutableStateOf("Alles") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            scope.launch {
                val csv = viewModel.getExportCsv(exportType)
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(csv.toByteArray())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (uiState.viewMode) { ViewMode.ELECTRICITY -> "Home Energy"; ViewMode.GAS -> "Home Gas"; ViewMode.PHASES -> "3-Fasen Belasting" }) },
                actions = {
                    IconButton(onClick = { viewModel.setViewMode(ViewMode.ELECTRICITY) }) {
                        Icon(Icons.Default.Bolt, "Elektra", tint = if (uiState.viewMode == ViewMode.ELECTRICITY) Color.Yellow else Color.Gray)
                    }
                    IconButton(onClick = { viewModel.setViewMode(ViewMode.GAS) }) {
                        Icon(Icons.Default.GasMeter, "Gas", tint = if (uiState.viewMode == ViewMode.GAS) Color.Yellow else Color.Gray)
                    }
                    IconButton(onClick = { viewModel.setViewMode(ViewMode.PHASES) }) {
                        Icon(Icons.Default.Speed, "3-Fasen", tint = if (uiState.viewMode == ViewMode.PHASES) Color.Yellow else Color.Gray)
                    }
                    IconButton(onClick = { showCostCalculator = true }) { Icon(Icons.Default.Calculate, null) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (uiState.isRefreshing) { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            when (uiState.viewMode) {
                ViewMode.ELECTRICITY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricCard(
                            "Solar",
                            "${uiState.liveSolarPower}W",
                            "",
                            Color.Cyan,
                            Modifier.weight(1f),
                            status = uiState.connectionStatus["Solar"],
                            subContent = {
                                Text(
                                    "${String.format(Locale.getDefault(), "%.1f", uiState.solarEnergyTodayKwh)} kWh",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SolarColor
                                )
                            }
                        )
                        MetricCard(
                            "Batterij",
                            "${uiState.batteryPowerWatts}W",
                            "",
                            Color.Green,
                            Modifier.weight(1f),
                            status = uiState.connectionStatus["Battery"],
                            subContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${String.format(Locale.getDefault(), "%.1f", uiState.batteryInTodayKwh)} kWh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BatInColor
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "${String.format(Locale.getDefault(), "%.1f", uiState.batteryOutTodayKwh)} kWh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BatOutColor
                                    )
                                }
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricCard(
                            "Net vandaag",
                            "${uiState.gridPowerWatts}W",
                            "",
                            Color(0xFFFFA500),
                            Modifier.weight(1f),
                            status = uiState.connectionStatus["P1 Meter"],
                            subContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${if (uiState.gridTodayTotalsReliable) String.format(Locale.getDefault(), "%.1f", uiState.gridImportTodayKwh) else "—"} kWh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetInColor
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "${if (uiState.gridTodayTotalsReliable) String.format(Locale.getDefault(), "%.1f", uiState.gridExportTodayKwh) else "—"} kWh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NetOutColor
                                    )
                                }
                            }
                        )
                        MetricCard(
                            "Huis",
                            "${uiState.housePowerWatts}W",
                            "",
                            Color.LightGray,
                            Modifier.weight(1f),
                            subContent = {
                                Text(
                                    "${if (uiState.houseTodayTotalReliable) String.format(Locale.getDefault(), "%.1f", uiState.houseEnergyInTodayKwh) else "—"} kWh",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        )
                    }
                }
                ViewMode.GAS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetricCard("Gas Vandaag", "${String.format("%.3f", uiState.gasTodayM3)}m³", "", Color.Yellow, Modifier.weight(1f), status = uiState.connectionStatus["P1 Meter"])
                        MetricCard("Gas Periode", "${String.format("%.3f", uiState.periodGasM3)}m³", "", Color.Yellow, Modifier.weight(1f))
                    }
                }
                ViewMode.PHASES -> {
                    val latestPt = uiState.livePhaseHistory.lastOrNull()
                    val l1W = latestPt?.l1W ?: 0.0; val l2W = latestPt?.l2W ?: 0.0; val l3W = latestPt?.l3W ?: 0.0
                    val l1A = latestPt?.l1A ?: 0.0; val l2A = latestPt?.l2A ?: 0.0; val l3A = latestPt?.l3A ?: 0.0
                    val maxA = maxOf(l1A, l2A, l3A)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("Fase L1", "${l1W.toInt()}W", "${String.format(Locale.getDefault(), "%.1f", l1A)}A", Color(0xFF00BCD4), Modifier.weight(1f))
                        MetricCard("Fase L2", "${l2W.toInt()}W", "${String.format(Locale.getDefault(), "%.1f", l2A)}A", Color(0xFFFF9800), Modifier.weight(1f))
                        MetricCard("Fase L3", "${l3W.toInt()}W", "${String.format(Locale.getDefault(), "%.1f", l3A)}A", Color(0xFF9C27B0), Modifier.weight(1f))
                    }

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Max Fase Belasting: ${String.format(Locale.getDefault(), "%.1f", maxA)}A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val fuseStatus = if (maxA >= 22.0) "WAARSCHUWING (>22A)" else if (maxA >= 16.0) "HOGE BELASTING" else "Normaal"
                            val fuseColor = if (maxA >= 22.0) Color.Red else if (maxA >= 16.0) Color(0xFFFF9800) else Color(0xFF4CAF50)
                            Text(fuseStatus, style = MaterialTheme.typography.bodyMedium, color = fuseColor, fontWeight = FontWeight.Bold)
                        }
                    }

                    LivePhaseChart(
                        history = uiState.livePhaseHistory,
                        modifier = Modifier.height(300.dp).fillMaxWidth()
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TimeRange.entries.forEach { r ->
                    FilterChip(selected = uiState.selectedRange == r, onClick = { viewModel.setRange(r) }, label = { Text(r.name) }, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            // NAVIGATION BAR
            if (uiState.selectedRange != TimeRange.TOTAL) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { viewModel.navigateYear(forward = false) }) {
                        Icon(Icons.Default.KeyboardDoubleArrowLeft, contentDescription = "Year Back")
                    }
                    IconButton(onClick = { viewModel.navigateDate(forward = false) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                    }
                    
                    val dateLabel = remember(uiState.startDate, uiState.selectedRange) {
                        val date = Date(uiState.startDate)
                        when (uiState.selectedRange) {
                            TimeRange.MONTH -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(date)
                            TimeRange.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(date)
                            else -> "Historie"
                        }
                    }
                    
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    IconButton(onClick = { viewModel.navigateDate(forward = true) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                    }
                    IconButton(onClick = { viewModel.navigateYear(forward = true) }) {
                        Icon(Icons.Default.KeyboardDoubleArrowRight, contentDescription = "Year Forward")
                    }
                }
            }

            SolarChart(
                chartData = uiState.chartData, 
                visibleSeries = uiState.visibleSeries, 
                onToggleSeries = { viewModel.toggleSeriesVisibility(it) }, 
                modifier = Modifier.height(300.dp).fillMaxWidth(),
                minX = uiState.minX, 
                maxX = uiState.maxX, 
                range = uiState.selectedRange,
                currentSolarPowerWatts = uiState.liveSolarPower
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            uiState = uiState,
            onMeterImport = { meterLauncher.launch("text/*") },
            onBatteryImport = { batteryLauncher.launch("text/*") },
            onGasImport = { gasLauncher.launch("text/*") },
            onRatesImport = { ratesLauncher.launch("text/*") },
            onDismiss = { showSettings = false },
            onSave = { inv, p1, bat, batToken, ak, sec, pid, apiKey, gatewayToken, brand, seApiKey, seSiteId ->
                viewModel.updateSettings(inv, p1, bat, batToken, ak, sec, pid, apiKey, gatewayToken, brand, seApiKey, seSiteId)
                showSettings = false
            },
            onAddRate = { viewModel.addRatePeriod(it) },
            onDeleteRate = { viewModel.removeRatePeriod(it) },
            onExport = { type ->
                exportType = type
                exportLauncher.launch("energy_${type.lowercase()}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv")
            },
            onClearEnergyData = { viewModel.clearEnergyData() },
            onRecalculate = { viewModel.recalculateTotals() }
        )
    }

    if (showCostCalculator) {
        CostCalculatorDialog(uiState, { showCostCalculator = false }, { s, e -> viewModel.calculateCosts(s, e) })
    }

    importFeedback?.let { message ->
        AlertDialog(
            onDismissRequest = { importFeedback = null },
            title = { Text("Data import") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importFeedback = null }) { Text("OK") } }
        )
    }
}

@Composable
fun SettingsDialog(
    uiState: DashboardUiState,
    onMeterImport: () -> Unit,
    onBatteryImport: () -> Unit,
    onGasImport: () -> Unit,
    onRatesImport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String, String, String, String, String) -> Unit,
    onAddRate: (RatePeriod) -> Unit,
    onDeleteRate: (Long) -> Unit,
    onExport: (String) -> Unit,
    onClearEnergyData: () -> Unit,
    onRecalculate: () -> Unit
) {
    var activeSubMenu by remember { mutableStateOf<String?>(null) }
    if (activeSubMenu == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Instellingen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsMenuButton("Lokale Apparaten", Icons.Default.Settings) { activeSubMenu = "local" }
                    SettingsMenuButton("Tarief Beheer", Icons.Default.Calculate) { activeSubMenu = "rates" }
                    SettingsMenuButton("Data Beheer", Icons.Default.ImportExport) { activeSubMenu = "data" }
                    SettingsMenuButton("Cloud API", Icons.Default.Cloud) { activeSubMenu = "cloud" }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Sluiten") } }
        )
    }
    when (activeSubMenu) {
        "local" -> LocalDevicesDialog(uiState, { activeSubMenu = null }, onSave)
        "rates" -> RatesManagementDialog(uiState, { activeSubMenu = null }, onAddRate, onDeleteRate)
        "data" -> DataManagementDialog(uiState, onMeterImport, onBatteryImport, onGasImport, onRatesImport, { activeSubMenu = null }, onExport, onClearEnergyData, onRecalculate)
        "cloud" -> CloudApiDialog(uiState, { activeSubMenu = null }, onSave)
    }
}

@Composable
fun SettingsMenuButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Icon(icon, null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable
fun DataManagementDialog(uiState: DashboardUiState, onMeterImport: () -> Unit, onBatteryImport: () -> Unit, onGasImport: () -> Unit, onRatesImport: () -> Unit, onDismiss: () -> Unit, onExport: (String) -> Unit, onClear: () -> Unit, onRecalc: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data Beheer") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Importeer", style = MaterialTheme.typography.labelLarge)
                Button(onMeterImport, Modifier.fillMaxWidth()) { Text("P1/Alles") }
                Button(onBatteryImport, Modifier.fillMaxWidth()) { Text("Batterij") }
                Button(onGasImport, Modifier.fillMaxWidth()) { Text("Gas") }
                Button(onRatesImport, Modifier.fillMaxWidth()) { Text("Tarieven") }
                HorizontalDivider()
                Text("Exporteer", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onExport("Tarieven") }, Modifier.weight(1f)) { Text("Tarieven", fontSize = 10.sp) }
                    OutlinedButton({ onExport("Alles") }, Modifier.weight(1f)) { Text("Alles", fontSize = 10.sp) }
                }
                HorizontalDivider()
                Text("Acties", style = MaterialTheme.typography.labelLarge, color = Color.Red)
                Button(onRecalc, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Herbereken") }
                OutlinedButton(onClear, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Wis alle data") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terug") } }
    )
}

@Composable
fun LocalDevicesDialog(uiState: DashboardUiState, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String, String, String, String, String, String, String) -> Unit) {
    var invIp by remember { mutableStateOf(uiState.inverterIp) }
    var p1Ip by remember { mutableStateOf(uiState.p1Ip) }
    var batIp by remember { mutableStateOf(uiState.batteryIp) }
    var batToken by remember { mutableStateOf(uiState.batteryLocalToken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apparaten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(invIp, { invIp = it }, label = { Text("Inverter IP") })
                OutlinedTextField(p1Ip, { p1Ip = it }, label = { Text("HomeWizard P1 IP") })
                OutlinedTextField(batIp, { batIp = it }, label = { Text("Batterij IP") })
                OutlinedTextField(batToken, { batToken = it }, label = { Text("Batterij Token") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(invIp, p1Ip, batIp, batToken, uiState.appKey, uiState.appSecret, uiState.plantId, uiState.apiKey, uiState.gatewayToken, uiState.inverterBrand, uiState.seApiKey, uiState.seSiteId)
                onDismiss()
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Terug") } }
    )
}

@Composable
fun CloudApiDialog(uiState: DashboardUiState, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String, String, String, String, String, String, String) -> Unit) {
    var brand by remember { mutableStateOf(uiState.inverterBrand) }
    var seApiKey by remember { mutableStateOf(uiState.seApiKey) }
    var seSiteId by remember { mutableStateOf(uiState.seSiteId) }
    
    var ak by remember { mutableStateOf(uiState.appKey) }
    var `as` by remember { mutableStateOf(uiState.appSecret) }
    var pid by remember { mutableStateOf(uiState.plantId) }
    var apiKey by remember { mutableStateOf(uiState.apiKey) }
    var gToken by remember { mutableStateOf(uiState.gatewayToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud API Instellingen") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(brand == "Solplanet", { brand = "Solplanet" }, label = { Text("Solplanet") })
                    FilterChip(brand == "SolarEdge", { brand = "SolarEdge" }, label = { Text("SolarEdge") })
                }
                if (brand == "SolarEdge") {
                    OutlinedTextField(seApiKey, { seApiKey = it }, label = { Text("SolarEdge API Key") })
                    OutlinedTextField(seSiteId, { seSiteId = it }, label = { Text("SolarEdge Site ID") })
                } else {
                    OutlinedTextField(pid, { pid = it }, label = { Text("Plant ID") })
                    OutlinedTextField(ak, { ak = it }, label = { Text("App Key") })
                    OutlinedTextField(`as`, { `as` = it }, label = { Text("App Secret") })
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") })
                    OutlinedTextField(gToken, { gToken = it }, label = { Text("Pro Token") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(uiState.inverterIp, uiState.p1Ip, uiState.batteryIp, uiState.batteryLocalToken, ak, `as`, pid, apiKey, gToken, brand, seApiKey, seSiteId)
                onDismiss()
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Terug") } }
    )
}

@Composable
fun RatesManagementDialog(uiState: DashboardUiState, onDismiss: () -> Unit, onAddRate: (RatePeriod) -> Unit, onDeleteRate: (Long) -> Unit) {
    var rateToEdit by remember { mutableStateOf<RatePeriod?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tarieven")
                IconButton(onClick = { rateToEdit = null; showEditDialog = true }) { Icon(Icons.Default.Add, null) }
            }
        },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    uiState.ratePeriods.forEach { rate ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                            rateToEdit = rate
                            showEditDialog = true
                        }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Tot ${rate.endDate}", fontWeight = FontWeight.Bold)
                                    Text("€${String.format("%.4f", rate.tariff)} / kWh", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton({ onDeleteRate(rate.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terug") } }
    )
    if (showEditDialog) {
        RateDialog(rateToEdit, uiState.viewMode, { showEditDialog = false }, onAddRate)
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier,
    status: String? = null,
    subContent: (@Composable () -> Unit)? = null
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(0.1f))) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall)
                if (!status.isNullOrBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.equals("OK", ignoreCase = true)) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(value, style = MaterialTheme.typography.headlineMedium)
            if (subContent != null) subContent() else Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun CostCalculatorDialog(uiState: DashboardUiState, onDismiss: () -> Unit, onCalculate: (String, String) -> Unit) {
    var s by remember { mutableStateOf("2024-10-18") }
    var e by remember { mutableStateOf("2025-10-17") }
    var calculationRequested by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kosten Berekening") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(s, { s = it }, label = { Text("Start Datum (jjjj-mm-dd)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(e, { e = it }, label = { Text("Eind Datum (jjjj-mm-dd)") }, modifier = Modifier.fillMaxWidth())
                Button({
                    calculationRequested = true
                    if (uiState.ratePeriods.isNotEmpty()) onCalculate(s, e)
                }, Modifier.fillMaxWidth()) { Text("Bereken") }

                if (calculationRequested && uiState.costResults.isEmpty()) {
                    Text(
                        text = if (uiState.ratePeriods.isEmpty()) {
                            "Geen tarieven ingesteld. Voeg tarieven toe via Instellingen > Tarief Beheer."
                        } else {
                            "Geen berekening beschikbaar voor deze periode. Controleer de datums en of er meetgegevens zijn."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (uiState.costResults.isNotEmpty()) {
                    val total = uiState.costResults.sumOf { it.totalCost }
                    val totalElec = uiState.costResults.sumOf { it.energyCost + it.fixedEnergyCost }
                    val totalGas = uiState.costResults.sumOf { it.gasCost + it.fixedGasCost }
                    val totalNetKwh = uiState.costResults.sumOf { it.netKwh }
                    val totalGasM3 = uiState.costResults.sumOf { it.totalGasM3 }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Totaal Overzicht Periode:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Netto stroom: ${String.format(Locale.getDefault(), "%.1f", totalNetKwh)} kWh", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Gas: ${String.format(Locale.getDefault(), "%.3f", totalGasM3)} m³", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Stroomkosten: €${String.format(Locale.getDefault(), "%.2f", totalElec)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Gaskosten: €${String.format(Locale.getDefault(), "%.2f", totalGas)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Text("Totale Kosten: € ${String.format(Locale.getDefault(), "%.2f", total)}", style = MaterialTheme.typography.headlineSmall, color = if (total >= 0) Color.Red else Color.Green)
                    
                    uiState.costResults.forEach { res ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${res.label.ifEmpty { "${res.startDate} - ${res.endDate}" }} (${res.days} dagen)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                if (res.days > 0) {
                                    Text("Import: ${String.format(Locale.getDefault(), "%.1f", res.totalImportKwh)} kWh", style = MaterialTheme.typography.bodyMedium)
                                    Text("Export: ${String.format(Locale.getDefault(), "%.1f", res.totalExportKwh)} kWh", style = MaterialTheme.typography.bodyMedium)
                                    Text("Netto stroom: ${String.format(Locale.getDefault(), "%.1f", res.netKwh)} kWh", style = MaterialTheme.typography.bodyMedium)
                                    Text("Gas: ${String.format(Locale.getDefault(), "%.3f", res.totalGasM3)} m³", style = MaterialTheme.typography.bodyMedium)
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    Text("Stroomkosten: €${String.format(Locale.getDefault(), "%.2f", res.energyCost + res.fixedEnergyCost)}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Gaskosten: €${String.format(Locale.getDefault(), "%.2f", res.gasCost + res.fixedGasCost)}", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text("Bedrag: €${String.format(Locale.getDefault(), "%.2f", res.energyCost)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text("Totaal segment: €${String.format(Locale.getDefault(), "%.2f", res.totalCost)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Sluiten") } }
    )
}

@Composable
fun RateDialog(existing: RatePeriod?, viewMode: ViewMode, onDismiss: () -> Unit, onAdd: (RatePeriod) -> Unit) {
    var rEnd by remember { mutableStateOf(existing?.endDate ?: "") }
    var rTariff by remember { mutableStateOf(existing?.tariff?.toString() ?: "") }
    var rEB by remember { mutableStateOf(existing?.energyTax?.toString() ?: "") }
    var rVBV by remember { mutableStateOf(existing?.compensation?.toString() ?: "") }
    var rTLK by remember { mutableStateOf(existing?.tlkRate?.toString() ?: "") }
    var rAE by remember { mutableStateOf(existing?.gridConnection?.toString() ?: "") }
    var rVEB by remember { mutableStateOf(existing?.taxReduction?.toString() ?: "") }
    var rVLK by remember { mutableStateOf(existing?.fixedDeliveryCost?.toString() ?: "") }
    
    // Gas fields
    var rGT by remember { mutableStateOf(existing?.gasTariff?.toString() ?: "") }
    var rGX by remember { mutableStateOf(existing?.gasTax?.toString() ?: "") }
    var rGC by remember { mutableStateOf(existing?.gasConnection?.toString() ?: "") }
    var rGV by remember { mutableStateOf(existing?.variableGasDeliveryCost?.toString() ?: "") }
    
    var rDisc by remember { mutableStateOf(existing?.discountPercentage?.toString() ?: "0.0") }
    var rAbs by remember { mutableStateOf(existing?.useAbsoluteCalculation ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onAdd(RatePeriod(
                    id = existing?.id ?: 0,
                    endDate = rEnd,
                    tariff = rTariff.toDoubleOrNull() ?: 0.0,
                    energyTax = rEB.toDoubleOrNull() ?: 0.0,
                    compensation = rVBV.toDoubleOrNull() ?: 0.0,
                    tlkRate = rTLK.toDoubleOrNull() ?: 0.0,
                    gridConnection = rAE.toDoubleOrNull() ?: 0.0,
                    taxReduction = rVEB.toDoubleOrNull() ?: 0.0,
                    fixedDeliveryCost = rVLK.toDoubleOrNull() ?: 0.0,
                    gasTariff = rGT.toDoubleOrNull() ?: 0.0,
                    gasTax = rGX.toDoubleOrNull() ?: 0.0,
                    gasConnection = rGC.toDoubleOrNull() ?: 0.0,
                    variableGasDeliveryCost = rGV.toDoubleOrNull() ?: 0.0,
                    discountPercentage = rDisc.toDoubleOrNull() ?: 0.0,
                    useAbsoluteCalculation = rAbs
                ))
                onDismiss()
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
        title = { Text("Tarief Details") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rEnd, { rEnd = it }, label = { Text("Einddatum (jjjj-mm-dd)") })
                
                Text("Elektra", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(rTariff, { rTariff = it }, label = { Text("Netto Tarief (€/kWh)") })
                OutlinedTextField(rEB, { rEB = it }, label = { Text("Energiebelasting (€/kWh)") })
                OutlinedTextField(rVBV, { rVBV = it }, label = { Text("VBV/TLV (€/kWh)") })
                OutlinedTextField(rTLK, { rTLK = it }, label = { Text("TLK (€/dag of €/kWh)") })
                OutlinedTextField(rAE, { rAE = it }, label = { Text("AE (Enexis/dag)") })
                OutlinedTextField(rVEB, { rVEB = it }, label = { Text("VEB (€/dag)") })
                OutlinedTextField(rVLK, { rVLK = it }, label = { Text("VLK (€/dag)") })
                OutlinedTextField(rDisc, { rDisc = it }, label = { Text("Thuisvoordeel (%)") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rAbs, { rAbs = it })
                    Text("Geen saldering (Absoluut)")
                }

                HorizontalDivider()
                Text("Gas", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(rGT, { rGT = it }, label = { Text("Gas Tarief (€/m³)") })
                OutlinedTextField(rGX, { rGX = it }, label = { Text("Gas Belasting (€/m³)") })
                OutlinedTextField(rGC, { rGC = it }, label = { Text("Gas AE (€/dag)") })
                OutlinedTextField(rGV, { rGV = it }, label = { Text("Gas VLK (€/dag)") })
            }
        }
    )
}
