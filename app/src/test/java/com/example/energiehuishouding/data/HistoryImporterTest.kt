package com.example.energiehuishouding.data

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class HistoryImporterTest {

    @Test
    fun testParseUserCsvFormat() {
        val csvData = """
            Datum;Abs_Import(kWh);Abs_Export(kWh)
            16-10-2022;31269;14026
            17-10-2022;31269;14026
            18-10-2022;31309;14067
        """.trimIndent()

        val reader = BufferedReader(StringReader(csvData))
        val rawReadings = HistoryImporter.parseRawReadings(reader)

        assertEquals(3, rawReadings.size)
        
        // Check first reading
        assertEquals(31269.0, rawReadings[0].totalImport, 0.001)
        assertEquals(14026.0, rawReadings[0].totalExport, 0.001)
        
        // Check last reading
        assertEquals(31309.0, rawReadings[2].totalImport, 0.001)
        assertEquals(14067.0, rawReadings[2].totalExport, 0.001)
    }

    @Test
    fun testDetectColumnsDutch() {
        val header = "Datum;Afname T1;Afname T2;Teruglevering T1;Teruglevering T2"
        val mapping = HistoryImporter.detectColumns(header, ";")
        
        assertEquals(0, mapping.dateIdx)
        assertEquals(listOf(1, 2), mapping.importIndices)
        assertEquals(listOf(3, 4), mapping.exportIndices)
    }

    @Test
    fun testDetectColumnsEnglish() {
        val header = "Date,Import,Export"
        val mapping = HistoryImporter.detectColumns(header, ",")
        
        assertEquals(0, mapping.dateIdx)
        assertEquals(listOf(1), mapping.importIndices)
        assertEquals(listOf(2), mapping.exportIndices)
    }

    @Test
    fun testDetectColumnsNetInNetOut() {
        val header = "Datum;Net-In;Net-Out"
        val mapping = HistoryImporter.detectColumns(header, ";")
        
        assertEquals(0, mapping.dateIdx)
        assertEquals(listOf(1), mapping.importIndices)
        assertEquals(listOf(2), mapping.exportIndices)
    }

    @Test
    fun testParseDoubleWithQuotesAndMultipleCommas() {
        assertEquals(41321.24, HistoryImporter.parseDouble("\"41321,24\""), 0.001)
        assertEquals(41417.982, HistoryImporter.parseDouble("\"41,417,982\""), 0.001)
        assertEquals(22350.143, HistoryImporter.parseDouble("\"22,350,143\""), 0.001)
    }

    @Test
    fun testParseRawReadingsWithQuotedValues() {
        val csv = """
            Datum,Import,Export
            1-6-2026,"41321,24","22,350,143"
            2-6-2026,"41,417,982","22,545,552"
        """.trimIndent()
        
        val reader = BufferedReader(StringReader(csv))
        val results = HistoryImporter.parseRawReadings(reader)
        
        assertEquals(2, results.size)
        assertEquals(41321.24, results[0].totalImport, 0.001)
        assertEquals(22350.143, results[0].totalExport, 0.001)
        assertEquals(41417.982, results[1].totalImport, 0.001)
        assertEquals(22545.552, results[1].totalExport, 0.001)
    }

    @Test
    fun testDetectGasColumnsWithM3Superscript() {
        val header = "Datum;Gas (m³);Abs_Gas"
        val mapping = HistoryImporter.detectColumns(header, ";")
        
        assertEquals(0, mapping.dateIdx)
        assertEquals(listOf(1, 2), mapping.gasIndices)
    }

    @Test
    fun testParseGasReadings() {
        val csv = """
            Datum;Gas (m³)
            01-01-2024;1500,250
            02-01-2024;1503,750
        """.trimIndent()

        val reader = BufferedReader(StringReader(csv))
        val results = HistoryImporter.parseRawReadings(reader)

        assertEquals(2, results.size)
        assertEquals(1500.250, results[0].gasM3, 0.001)
        assertEquals(1503.750, results[1].gasM3, 0.001)
    }

    @Test
    fun testParseEuropeanThousandsSeparators() {
        assertEquals(17666.0, HistoryImporter.parseDouble("17,666"), 0.001)
        assertEquals(3290.0, HistoryImporter.parseDouble("3,290"), 0.001)
        assertEquals(17666.0, HistoryImporter.parseDouble("17.666"), 0.001)
        assertEquals(3290.0, HistoryImporter.parseDouble("3.290"), 0.001)
    }

    @Test
    fun testParseYearDates() {
        val ts2018 = HistoryImporter.parseDate("2018")
        val ts2018Month = HistoryImporter.parseDate("2018-05")
        
        Assert.assertTrue(ts2018 > 0L)
        Assert.assertTrue(ts2018Month > 0L)
    }

    @Test
    fun testParseOctober2026Readings() {
        val csv = """
            Datum;Solar;Abs_Import;Abs_Export;Abs_BatIn;Abs_BatOut;Abs_Gas
            30-9-2026;12,50;41.820,70;23.322,44;137,60;101,53;21.148,48
            1-10-2026;15,80;41.829,05;23.328,33;137,60;101,53;21.148,75
        """.trimIndent()

        val reader = BufferedReader(StringReader(csv))
        val results = HistoryImporter.parseRawReadings(reader)

        assertEquals(2, results.size)
        assertEquals(12.50, results[0].solarKwh, 0.001)
        assertEquals(41820.70, results[0].totalImport, 0.001)
        assertEquals(23322.44, results[0].totalExport, 0.001)
        assertEquals(15.80, results[1].solarKwh, 0.001)
        assertEquals(41829.05, results[1].totalImport, 0.001)
        assertEquals(23328.33, results[1].totalExport, 0.001)
    }

    @Test
    fun testDetectSolarColumns() {
        val header = "Datum;Zonnepanelen;Zon;PV;Opwek"
        val mapping = HistoryImporter.detectColumns(header, ";")

        assertEquals(0, mapping.dateIdx)
        assertEquals(listOf(1, 2, 3, 4), mapping.solarIndices)
    }
}
