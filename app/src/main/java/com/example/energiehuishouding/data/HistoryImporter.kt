package com.example.energiehuishouding.data

import android.content.Context
import android.net.Uri
import java.util.Calendar
import com.example.energiehuishouding.data.local.*
import com.example.energiehuishouding.SolarApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryImporter(private val context: Context) {
    companion object {
        private val NON_DIGIT_REGEX = Regex("[^0-9\\-:/\\.]")
        private val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("d-M-yyyy", Locale.US)
        ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }

        fun parseDate(str: String): Long {
            if (str.isBlank()) return 0L
            val clean = str.replace(NON_DIGIT_REGEX, " ").trim()
            val digitsOnly = clean.split(" ", "-", ".", "/", ":").filter { it.isNotEmpty() }
            val utcZone = TimeZone.getTimeZone("UTC")
            if (digitsOnly.size == 1 && digitsOnly[0].length == 4) {
                try {
                    val y = digitsOnly[0].toInt()
                    if (y in 1900..2100) {
                        val cal = Calendar.getInstance(utcZone)
                        cal.set(y, 0, 1, 12, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        return cal.timeInMillis
                    }
                } catch (_: Exception) {}
            }
            if (digitsOnly.size == 2) {
                try {
                    val p0 = digitsOnly[0].toInt(); val p1 = digitsOnly[1].toInt()
                    val cal = Calendar.getInstance(utcZone)
                    if (p0 > 1000) { cal.set(p0, (p1 - 1).coerceIn(0, 11), 1, 12, 0, 0) }
                    else if (p1 > 1000) { cal.set(p1, (p0 - 1).coerceIn(0, 11), 1, 12, 0, 0) }
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                } catch (_: Exception) {}
            }
            if (digitsOnly.size >= 3) {
                try {
                    val p0 = digitsOnly[0].toInt(); val p1 = digitsOnly[1].toInt(); val p2 = digitsOnly[2].toInt()
                    val cal = Calendar.getInstance(utcZone)
                    if (p0 > 1000) { cal.set(p0, (p1 - 1).coerceIn(0, 11), p2.coerceIn(1, 31), 12, 0, 0) } 
                    else if (p2 > 1000) { cal.set(p2, (p1 - 1).coerceIn(0, 11), p0.coerceIn(1, 31), 12, 0, 0) } 
                    else { return 0L }
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                } catch (_: Exception) {}
            }
            for (df in dateFormats) {
                try { val d = df.parse(clean); if (d != null) return d.time } catch (_: Exception) {}
            }
            return 0L
        }

        fun parseDouble(str: String): Double {
            return try {
                var clean = str.trim().replace("\"", "").replace(" ", "")
                if (clean.isEmpty()) return 0.0

                val dotCount = clean.count { it == '.' }
                val commaCount = clean.count { it == ',' }

                if (dotCount > 0 && commaCount > 0) {
                    val lastDot = clean.lastIndexOf('.')
                    val lastComma = clean.lastIndexOf(',')
                    if (lastComma > lastDot) {
                        // European format: 1.234,56 -> 1234.56
                        clean = clean.replace(".", "").replace(",", ".")
                    } else {
                        // US format: 1,234.56 -> 1234.56
                        clean = clean.replace(",", "")
                    }
                } else if (commaCount > 1) {
                    val lastComma = clean.lastIndexOf(',')
                    val intPart = clean.substring(0, lastComma).replace(",", "")
                    val decPart = clean.substring(lastComma + 1)
                    clean = "$intPart.$decPart"
                } else if (dotCount > 1) {
                    val lastDot = clean.lastIndexOf('.')
                    val intPart = clean.substring(0, lastDot).replace(".", "")
                    val decPart = clean.substring(lastDot + 1)
                    clean = "$intPart.$decPart"
                } else if (commaCount == 1 || dotCount == 1) {
                    val sep = if (commaCount == 1) ',' else '.'
                    val parts = clean.split(sep)
                    if (parts.size == 2 && parts[1].length == 3 && parts[0].length in 1..2) {
                        clean = parts[0] + parts[1]
                    } else {
                        clean = clean.replace(",", ".")
                    }
                }
                clean.toDoubleOrNull() ?: 0.0
            } catch (e: Exception) { 0.0 }
        }

        data class ColumnMapping(
            val dateIdx: Int = -1, 
            val importIndices: List<Int> = emptyList(), 
            val exportIndices: List<Int> = emptyList(), 
            val gasIndices: List<Int> = emptyList(), 
            val batInIndices: List<Int> = emptyList(), 
            val batOutIndices: List<Int> = emptyList(),
            val solarIndices: List<Int> = emptyList()
        )

        fun detectColumns(header: String, separator: String): ColumnMapping {
            val parts = header.split(separator).map { it.trim().lowercase() }
            var dateIdx = -1
            val imp = mutableListOf<Int>(); val exp = mutableListOf<Int>(); val gas = mutableListOf<Int>()
            val bIn = mutableListOf<Int>(); val bOut = mutableListOf<Int>(); val sol = mutableListOf<Int>()
            
            val hasSplitImport = parts.any { it.contains("t1") || it.contains("t2") || it.contains("dal") || it.contains("normaal") || it.contains("offpeak") || it.contains("peak") }

            parts.forEachIndexed { index, s ->
                if (dateIdx == -1 && (s.contains("datum") || s.contains("date") || s.contains("time") || s.contains("tijd") || s.contains("timestamp"))) dateIdx = index
                else if (s.contains("solar") || s.contains("zon") || s.contains("pv") || s.contains("opwek") || s.contains("opbrengst")) sol.add(index)
                else if (s.contains("gas") || s.contains("m3") || s.contains("m³")) gas.add(index)
                else if (s.contains("bat") || s.contains("accu")) {
                    if (s.contains("in") || s.contains("import") || s.contains("laden")) bIn.add(index)
                    else if (s.contains("out") || s.contains("export") || s.contains("ontladen")) bOut.add(index)
                } 
                else if (s.contains("export") || s.contains("uit") || s.contains("out") || s.contains("injectie") || (s.contains("terug") && !s.contains("kosten") && !s.contains("tarief"))) {
                    if (!hasSplitImport || !s.contains("totaal")) exp.add(index)
                }
                else if (s.contains("import") || s.contains("afname") || s.contains("levering") || s.contains("verbruik") || s.contains("in")) {
                    if (!hasSplitImport || !s.contains("totaal")) imp.add(index)
                }
            }
            return ColumnMapping(dateIdx, imp, exp, gas, bIn, bOut, sol)
        }

        data class RawReading(
            val timestamp: Long, 
            val totalImport: Double, 
            val totalExport: Double, 
            val gasM3: Double = 0.0, 
            val batIn: Double = 0.0, 
            val batOut: Double = 0.0,
            val solarKwh: Double = 0.0
        )

        fun parseRawReadings(reader: BufferedReader): List<RawReading> {
            val list = ArrayList<RawReading>()
            val lines = reader.readLines().filter { it.trim().isNotEmpty() }
            if (lines.size < 2) return emptyList()

            val header = lines[0]
            val sep = if (header.contains(";")) ";" else if (header.contains("\t")) "\t" else ","
            val m = detectColumns(header, sep)
            if (m.dateIdx == -1) return emptyList()

            val hasQuotes = lines.any { it.contains('"') }
            val regexSplit = if (hasQuotes) Regex("$sep(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)") else null

            for (i in 1 until lines.size) {
                val line = lines[i]
                val p = if (regexSplit != null) line.split(regexSplit).map { it.trim() } else line.split(sep).map { it.trim() }
                if (p.size <= m.dateIdx) continue
                val ts = parseDate(p[m.dateIdx])
                if (ts == 0L) continue

                var imp = 0.0; var exp = 0.0; var gas = 0.0; var bIn = 0.0; var bOut = 0.0; var sol = 0.0
                for (idx in m.importIndices) { if (idx < p.size) imp += parseDouble(p[idx]) }
                for (idx in m.exportIndices) { if (idx < p.size) exp += parseDouble(p[idx]) }
                for (idx in m.gasIndices) { if (idx < p.size) gas += parseDouble(p[idx]) }
                for (idx in m.batInIndices) { if (idx < p.size) bIn += parseDouble(p[idx]) }
                for (idx in m.batOutIndices) { if (idx < p.size) bOut += parseDouble(p[idx]) }
                for (idx in m.solarIndices) { if (idx < p.size) sol += parseDouble(p[idx]) }

                list.add(RawReading(ts, imp, exp, gas, bIn, bOut, sol))
            }
            return list
        }

        fun anchorToNoon(ts: Long): Long {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = ts; cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }

    suspend fun importMeterCsv(uri: Uri) = importGenericCsv(uri, "auto")
    suspend fun importBatteryCsv(uri: Uri) = importGenericCsv(uri, "battery")
    suspend fun importGasCsv(uri: Uri) = importGenericCsv(uri, "gas")

    private suspend fun importGenericCsv(uri: Uri, type: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { 
                parseRawReadings(BufferedReader(InputStreamReader(it))) 
            } ?: emptyList()
            
            if (raw.isEmpty()) return@withContext Result.failure(Exception("Geen geldige data gevonden"))

            val repo = (context.applicationContext as SolarApplication).repository
            val toInsert = mutableListOf<DailySummary>()
            val utcIsoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            
            var firstGas = -1.0; var firstImp = -1.0; var firstExp = -1.0; var firstBIn = -1.0; var firstBOut = -1.0; var firstSol = -1.0
            var maxGas = 0.0; var maxImp = 0.0; var maxExp = 0.0; var maxBIn = 0.0; var maxBOut = 0.0; var maxSol = 0.0

            for (r in raw) {
                if (r.gasM3 > 0.0001) { if (firstGas < 0) firstGas = r.gasM3; if (r.gasM3 > maxGas) maxGas = r.gasM3 }
                if (r.totalImport > 0.001) { if (firstImp < 0) firstImp = r.totalImport; if (r.totalImport > maxImp) maxImp = r.totalImport }
                if (r.totalExport > 0.001) { if (firstExp < 0) firstExp = r.totalExport; if (r.totalExport > maxExp) maxExp = r.totalExport }
                if (r.batIn > 0.001) { if (firstBIn < 0) firstBIn = r.batIn; if (r.batIn > maxBIn) maxBIn = r.batIn }
                if (r.batOut > 0.001) { if (firstBOut < 0) firstBOut = r.batOut; if (r.batOut > maxBOut) maxBOut = r.batOut }
                if (r.solarKwh > 0.001) { if (firstSol < 0) firstSol = r.solarKwh; if (r.solarKwh > maxSol) maxSol = r.solarKwh }
            }

            val isGasAbsolute = firstGas > 50.0 || maxGas > 50.0
            val isImpAbsolute = (firstImp > 0.0 && firstImp > 100.0) || maxImp > 100.0
            val isExpAbsolute = (firstExp > 0.0 && firstExp > 100.0) || maxExp > 100.0
            val isBInAbsolute = (firstBIn > 0.0 && firstBIn > 100.0) || maxBIn > 100.0
            val isBOutAbsolute = (firstBOut > 0.0 && firstBOut > 100.0) || maxBOut > 100.0
            val isSolAbsolute = (firstSol > 0.0 && firstSol > 100.0) || maxSol > 100.0

            val existingMap = repo.getDailySummariesRangeOnce("2000-01-01", "2100-12-31").associateBy { it.date }

            for (reading in raw) {
                val dateStr = utcIsoFormat.format(Date(reading.timestamp))
                val existing = existingMap[dateStr]
                
                val importImp = reading.totalImport
                val importExp = reading.totalExport
                val importGas = reading.gasM3
                val importBIn = reading.batIn
                val importBOut = reading.batOut
                val importSol = reading.solarKwh

                val newAbsImp = if (importImp > 0.001) (if (isImpAbsolute) importImp else (existing?.absGridImportKwh ?: 0.0)) else (existing?.absGridImportKwh ?: 0.0)
                val newDeltaImp = if (importImp > 0.001) (if (!isImpAbsolute) importImp else (existing?.gridImportKwh ?: 0.0)) else (existing?.gridImportKwh ?: 0.0)

                val newAbsExp = if (importExp > 0.001) (if (isExpAbsolute) importExp else (existing?.absGridExportKwh ?: 0.0)) else (existing?.absGridExportKwh ?: 0.0)
                val newDeltaExp = if (importExp > 0.001) (if (!isExpAbsolute) importExp else (existing?.gridExportKwh ?: 0.0)) else (existing?.gridExportKwh ?: 0.0)

                val newAbsGas = if (importGas > 0.0001) (if (isGasAbsolute) importGas else (existing?.absGasM3 ?: 0.0)) else (existing?.absGasM3 ?: 0.0)
                val newDeltaGas = if (importGas > 0.0001) (if (!isGasAbsolute) importGas else (existing?.gasM3 ?: 0.0)) else (existing?.gasM3 ?: 0.0)

                val newAbsBIn = if (importBIn > 0.001) (if (isBInAbsolute) importBIn else (existing?.absBatteryInKwh ?: 0.0)) else (existing?.absBatteryInKwh ?: 0.0)
                val newDeltaBIn = if (importBIn > 0.001) (if (!isBInAbsolute) importBIn else (existing?.batteryInKwh ?: 0.0)) else (existing?.batteryInKwh ?: 0.0)

                val newAbsBOut = if (importBOut > 0.001) (if (isBOutAbsolute) importBOut else (existing?.absBatteryOutKwh ?: 0.0)) else (existing?.absBatteryOutKwh ?: 0.0)
                val newDeltaBOut = if (importBOut > 0.001) (if (!isBOutAbsolute) importBOut else (existing?.batteryOutKwh ?: 0.0)) else (existing?.batteryOutKwh ?: 0.0)

                val newAbsSol = if (importSol > 0.001) (if (isSolAbsolute) importSol else (existing?.absSolarKwh ?: 0.0)) else (existing?.absSolarKwh ?: 0.0)
                val newDeltaSol = if (importSol > 0.001) (if (!isSolAbsolute) importSol else (existing?.solarKwh ?: 0.0)) else (existing?.solarKwh ?: 0.0)

                if (existing != null) {
                    toInsert.add(existing.copy(
                        solarKwh = if (importSol > 0.001) newDeltaSol else existing.solarKwh,
                        gridImportKwh = if (importImp > 0.001) newDeltaImp else existing.gridImportKwh,
                        gridExportKwh = if (importExp > 0.001) newDeltaExp else existing.gridExportKwh,
                        batteryInKwh = if (importBIn > 0.001) newDeltaBIn else existing.batteryInKwh,
                        batteryOutKwh = if (importBOut > 0.001) newDeltaBOut else existing.batteryOutKwh,
                        gasM3 = if (importGas > 0.0001) newDeltaGas else existing.gasM3,
                        absSolarKwh = if (importSol > 0.001) newAbsSol else existing.absSolarKwh,
                        absGridImportKwh = if (importImp > 0.001) newAbsImp else existing.absGridImportKwh,
                        absGridExportKwh = if (importExp > 0.001) newAbsExp else existing.absGridExportKwh,
                        absBatteryInKwh = if (importBIn > 0.001) newAbsBIn else existing.absBatteryInKwh,
                        absBatteryOutKwh = if (importBOut > 0.001) newAbsBOut else existing.absBatteryOutKwh,
                        absGasM3 = if (importGas > 0.0001) newAbsGas else existing.absGasM3,
                        rawGridImportKwh = if (importImp > 0.001 && isImpAbsolute) importImp else existing.rawGridImportKwh,
                        rawGridExportKwh = if (importExp > 0.001 && isExpAbsolute) importExp else existing.rawGridExportKwh,
                        rawGasM3 = if (importGas > 0.0001 && isGasAbsolute) importGas else existing.rawGasM3,
                        gridImportOffsetKwh = if (importImp > 0.001 && isImpAbsolute) 0.0 else existing.gridImportOffsetKwh,
                        gridExportOffsetKwh = if (importExp > 0.001 && isExpAbsolute) 0.0 else existing.gridExportOffsetKwh,
                        gasOffsetM3 = if (importGas > 0.0001 && isGasAbsolute) 0.0 else existing.gasOffsetM3
                    ))
                } else {
                    toInsert.add(DailySummary(
                        date = dateStr,
                        timestamp = anchorToNoon(reading.timestamp),
                        solarKwh = if (importSol > 0.001) newDeltaSol else 0.0,
                        absSolarKwh = if (importSol > 0.001) newAbsSol else 0.0,
                        gridImportKwh = if (importImp > 0.001) newDeltaImp else 0.0,
                        gridExportKwh = if (importExp > 0.001) newDeltaExp else 0.0,
                        batteryInKwh = if (importBIn > 0.001) newDeltaBIn else 0.0,
                        batteryOutKwh = if (importBOut > 0.001) newDeltaBOut else 0.0,
                        gasM3 = if (importGas > 0.0001) newDeltaGas else 0.0,
                        absGridImportKwh = if (importImp > 0.001) newAbsImp else 0.0,
                        absGridExportKwh = if (importExp > 0.001) newAbsExp else 0.0,
                        absBatteryInKwh = if (importBIn > 0.001) newAbsBIn else 0.0,
                        absBatteryOutKwh = if (importBOut > 0.001) newAbsBOut else 0.0,
                        absGasM3 = if (importGas > 0.0001) newAbsGas else 0.0,
                        rawGridImportKwh = if (importImp > 0.001 && isImpAbsolute) importImp else 0.0,
                        rawGridExportKwh = if (importExp > 0.001 && isExpAbsolute) importExp else 0.0,
                        rawGasM3 = if (importGas > 0.0001 && isGasAbsolute) importGas else 0.0,
                        gridImportOffsetKwh = 0.0,
                        gridExportOffsetKwh = 0.0,
                        gasOffsetM3 = 0.0
                    ))
                }
            }
            
            repo.insertBulkDailySummaries(toInsert)
            Result.success(toInsert.size)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun importRatesCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as SolarApplication
            val repo = app.repository
            var count = 0
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val allLines = reader.readLines().filter { it.trim().isNotEmpty() }
                    if (allLines.isEmpty()) return@withContext Result.failure(Exception("Leeg"))
                    val header = allLines[0]
                    val sep = if (header.contains(";")) ";" else if (header.contains("\t")) "\t" else ","
                    val parts = header.split(sep).map { it.trim().lowercase() }
                    
                    val ie = parts.indexOfFirst { it.contains("eind") || it.contains("date") || it.contains("tot") }
                    val it = parts.indexOfFirst { (it.contains("tarief") || it.contains("levering")) && !it.contains("gas") }
                    // Our own rate export uses the short header "EB". Accept that
                    // alias as well as longer Dutch headers so a round-trip import
                    // does not silently zero the electricity tax.
                    val ieb = parts.indexOfFirst {
                        (it == "eb" || it.contains("belasting")) && !it.contains("gas")
                    }
                    val ic = parts.indexOfFirst { it.contains("vbv") || it.contains("vergoeding") || it.contains("overschot") || it.contains("tlv") }
                    val itlk = parts.indexOfFirst { it.contains("tlk") || it.contains("terugleverkosten") }
                    val iae = parts.indexOfFirst { (it.contains("ae") || it.contains("aansluiting") || it.contains("enexis")) && !it.contains("gas") }
                    val iveb = parts.indexOfFirst { it.contains("veb") || it.contains("reduction") }
                    val ivlk = parts.indexOfFirst { it.contains("vlk") && !it.contains("gas") }
                    val id = parts.indexOfFirst { it.contains("korting") || it.contains("thuisvoordeel") }
                    val igt = parts.indexOfFirst { it.contains("gas") && it.contains("tarief") }
                    // The exported column is named "GasEB"; older/imported files
                    // may use a longer "gasbelasting" header.
                    val igeb = parts.indexOfFirst {
                        it == "gaseb" || (it.contains("gas") && it.contains("belasting"))
                    }
                    val igae = parts.indexOfFirst { it.contains("gas") && (it.contains("ae") || it.contains("aansluiting")) }
                    val igvlk = parts.indexOfFirst { it.contains("gas") && (it.contains("vlk") || it.contains("vaste")) }

                    if (ie < 0 || ieb < 0 || igeb < 0) {
                        return@withContext Result.failure(Exception("Kolommen Einddatum, EB of GasEB ontbreken in het CSV-bestand"))
                    }
                    
                    val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    for (i in 1 until allLines.size) {
                        val p = allLines[i].split(Regex("$sep(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")).map { it.trim() }
                        if (p.size >= 2) {
                            val normDate = try { val ts = parseDate(p[ie]); if (ts > 0) outFmt.format(Date(ts)) else p[ie] } catch (e: Exception) { p[ie] }
                            repo.insertRatePeriod(RatePeriod(
                                endDate = normDate,
                                tariff = if (it >= 0 && it < p.size) parseDouble(p[it]) else 0.0,
                                energyTax = if (ieb >= 0 && ieb < p.size) parseDouble(p[ieb]) else 0.0,
                                gasTariff = if (igt >= 0 && igt < p.size) parseDouble(p[igt]) else 0.0,
                                gasTax = if (igeb >= 0 && igeb < p.size) parseDouble(p[igeb]) else 0.0,
                                gasConnection = if (igae >= 0 && igae < p.size) parseDouble(p[igae]) else 0.0,
                                variableGasDeliveryCost = if (igvlk >= 0 && igvlk < p.size) parseDouble(p[igvlk]) else 0.0,
                                compensation = if (ic >= 0 && ic < p.size) parseDouble(p[ic]) else 0.0,
                                tlkRate = if (itlk >= 0 && itlk < p.size) parseDouble(p[itlk]) else 0.0,
                                gridConnection = if (iae >= 0 && iae < p.size) parseDouble(p[iae]) else 0.0,
                                taxReduction = if (iveb >= 0 && iveb < p.size) parseDouble(p[iveb]) else 0.0,
                                fixedDeliveryCost = if (ivlk >= 0 && ivlk < p.size) parseDouble(p[ivlk]) else 0.0,
                                discountPercentage = if (id >= 0 && id < p.size) parseDouble(p[id]) else 0.0
                            )); count++
                        }
                    }
                }
            }
            if (count == 0) Result.failure(Exception("Geen tariefregels gevonden in het CSV-bestand"))
            else Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun importAllCsv(uri: Uri) = importMeterCsv(uri)
}
