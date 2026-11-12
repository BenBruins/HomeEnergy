package com.example.energiehuishouding.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

import java.util.Locale

// Unified Color Palette
val SolarColor = Color(0xFF00BCD4)  // Cyan
val ImportColor = Color(0xFFFF9800) // Orange
val ExportColor = Color(0xFF448AFF) // Bright Light Royal Blue (High contrast on Dark Theme)
val NetInColor = ImportColor
val NetOutColor = ExportColor
val BatInColor = Color(0xFF9C27B0)  // Purple
val BatOutColor = Color(0xFF4CAF50) // Green
val GasColor = Color(0xFFFFEB3B)    // Bright Yellow (Vivid on black)

@Composable
fun SolarChart(
    chartData: List<ChartDataPoint>,
    visibleSeries: Set<String>,
    onToggleSeries: (String) -> Unit,
    modifier: Modifier = Modifier,
    minX: Float? = null,
    maxX: Float? = null,
    range: TimeRange = TimeRange.MONTH,
    currentSolarPowerWatts: Int = 0
) {
    val zoomState = rememberVicoZoomState(initialZoom = Zoom.Content, zoomEnabled = true)
    val scrollState = rememberVicoScrollState(scrollEnabled = true)
    val modelProducer = remember { CartesianChartModelProducer() }

    // Reset scroll and zoom when switching range category
    LaunchedEffect(range) {
        scrollState.scroll(Scroll.Absolute.End)
        delay(100)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Current solar power: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${currentSolarPowerWatts} W", style = MaterialTheme.typography.titleSmall, color = SolarColor, fontWeight = FontWeight.SemiBold)
        }

        val xFormatter = remember(range) {
            CartesianValueFormatter { _, x, _ ->
                val xInt = x.roundToInt()
                when (range) {
                    TimeRange.MONTH -> xInt.toString()
                    TimeRange.YEAR -> xInt.toString()
                    TimeRange.TOTAL -> xInt.toString()
                }
            }
        }

        val rangeProvider = remember(minX, maxX) {
            val start = (minX ?: 1f).toDouble() - 0.5
            val end = (maxX ?: 31f).toDouble() + 0.5
            CartesianLayerRangeProvider.fixed(minX = start, maxX = end)
        }

        val visibleSeriesList = remember(visibleSeries) {
            listOf("Solar", "Import", "Export", "Bat-In", "Bat-Out", "Gas").filter { visibleSeries.contains(it) }
        }

        val colThickness = if (visibleSeriesList.size == 1) 12.dp else 4.dp
        val solarComp = rememberLineComponent(fill = fill(SolarColor.copy(alpha = 0.85f)), thickness = colThickness)
        val importComp = rememberLineComponent(fill = fill(ImportColor.copy(alpha = 0.85f)), thickness = colThickness)
        val exportComp = rememberLineComponent(fill = fill(ExportColor.copy(alpha = 0.85f)), thickness = colThickness)
        val batInComp = rememberLineComponent(fill = fill(BatInColor.copy(alpha = 0.85f)), thickness = colThickness)
        val batOutComp = rememberLineComponent(fill = fill(BatOutColor.copy(alpha = 0.85f)), thickness = colThickness)
        val gasComp = rememberLineComponent(fill = fill(GasColor.copy(alpha = 0.85f)), thickness = colThickness)

        val columnLayer = if (visibleSeriesList.isNotEmpty()) {
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    visibleSeriesList.map { name ->
                        when (name) {
                            "Solar" -> solarComp
                            "Import" -> importComp
                            "Export" -> exportComp
                            "Bat-In" -> batInComp
                            "Bat-Out" -> batOutComp
                            else -> gasComp
                        }
                    }
                ),
                mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() }
            )
        } else null

        LaunchedEffect(chartData, visibleSeriesList) {
            if (chartData.isEmpty() || visibleSeriesList.isEmpty()) return@LaunchedEffect
            val xPoints = chartData.map { it.x.roundToInt().toFloat() }.distinct().sorted()

            modelProducer.runTransaction {
                columnSeries {
                    visibleSeriesList.forEach { name ->
                        series(xPoints, xPoints.map { x ->
                            // Use average if multiple points exist for the same X (should not happen, but safety first)
                            chartData.filter { it.x.roundToInt().toFloat() == x && it.seriesName == name }.map { it.y }.firstOrNull() ?: 0f
                        })
                    }
                }
            }
        }

        if (chartData.isNotEmpty() && columnLayer != null) {
            val marker = rememberDefaultCartesianMarker(
                label = rememberTextComponent()
            )
            CartesianChartHost(
                chart = rememberCartesianChart(
                    columnLayer,
                    startAxis = VerticalAxis.rememberStart(
                        valueFormatter = { _, value, _ -> String.format(Locale.getDefault(), "%.3f", value) }
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xFormatter,
                        labelRotationDegrees = 90f,
                        itemPlacer = remember {
                            HorizontalAxis.ItemPlacer.segmented()
                        }
                    ),
                    marker = marker,
                    getXStep = { _, _, _ -> 1.0 }
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                zoomState = zoomState,
                scrollState = scrollState
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (visibleSeriesList.isEmpty()) "Selecteer minimaal één bron" else "Geen data beschikbaar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendToggleItem("Solar", SolarColor, visibleSeries.contains("Solar")) { onToggleSeries("Solar") }
            Spacer(modifier = Modifier.width(12.dp))
            LegendToggleItem("Import", ImportColor, visibleSeries.contains("Import")) { onToggleSeries("Import") }
            Spacer(modifier = Modifier.width(12.dp))
            LegendToggleItem("Export", ExportColor, visibleSeries.contains("Export")) { onToggleSeries("Export") }
            Spacer(modifier = Modifier.width(12.dp))
            LegendToggleItem("Batt-In", BatInColor, visibleSeries.contains("Bat-In")) { onToggleSeries("Bat-In") }
            Spacer(modifier = Modifier.width(12.dp))
            LegendToggleItem("Batt-Out", BatOutColor, visibleSeries.contains("Bat-Out")) { onToggleSeries("Bat-Out") }
            Spacer(modifier = Modifier.width(12.dp))
            LegendToggleItem("Gas", GasColor, visibleSeries.contains("Gas")) { onToggleSeries("Gas") }
        }
    }
}

@Composable
fun LegendToggleItem(label: String, color: Color, isVisible: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onToggle() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(if (isVisible) color else Color.Gray, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LivePhaseChart(
    history: List<LivePhasePoint>,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Wachten op P1 fase-metingen...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val l1Color = Color(0xFF00BCD4) // Cyan
    val l2Color = Color(0xFFFF9800) // Orange
    val l3Color = Color(0xFF9C27B0) // Purple

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = MaterialTheme.shapes.medium).padding(12.dp)) {
        Text("Actuele 3-Fasen Belasting (Live Rolling)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val width = size.width
            val height = size.height

            val allValues = history.flatMap { listOf(it.l1W, it.l2W, it.l3W) }
            var minVal = allValues.minOrNull() ?: 0.0
            var maxVal = allValues.maxOrNull() ?: 100.0

            if (minVal > 0.0) minVal = 0.0
            val rangeVal = maxOf(30.0, (maxVal - minVal) * 1.15)

            // Grid lines
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = height - (i.toFloat() / gridCount * height)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // Zero line if minVal < 0
            if (minVal < 0.0) {
                val zeroY = height - (((0.0 - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            if (history.size >= 2) {
                val stepX = width / (history.size - 1).coerceAtLeast(1)

                val pathL1 = Path()
                val pathL2 = Path()
                val pathL3 = Path()

                history.forEachIndexed { index, pt ->
                    val x = index * stepX
                    val y1 = height - (((pt.l1W - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)
                    val y2 = height - (((pt.l2W - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)
                    val y3 = height - (((pt.l3W - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)

                    if (index == 0) {
                        pathL1.moveTo(x, y1)
                        pathL2.moveTo(x, y2)
                        pathL3.moveTo(x, y3)
                    } else {
                        pathL1.lineTo(x, y1)
                        pathL2.lineTo(x, y2)
                        pathL3.lineTo(x, y3)
                    }
                }

                drawPath(pathL1, color = l1Color, style = Stroke(width = 6f))
                drawPath(pathL2, color = l2Color, style = Stroke(width = 6f))
                drawPath(pathL3, color = l3Color, style = Stroke(width = 6f))

                val lastX = width
                val lastPt = history.last()
                val lastY1 = height - (((lastPt.l1W - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)
                val lastY2 = height - (((lastPt.l2W - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)
                val lastY3 = height - (((lastPt.l3W - minVal) / rangeVal) * height).toFloat().coerceIn(0f, height)

                drawCircle(color = l1Color, radius = 10f, center = Offset(lastX, lastY1))
                drawCircle(color = l2Color, radius = 10f, center = Offset(lastX, lastY2))
                drawCircle(color = l3Color, radius = 10f, center = Offset(lastX, lastY3))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(l1Color, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("L1", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(l2Color, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("L2", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(l3Color, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("L3", style = MaterialTheme.typography.labelSmall)
            }
            val firstTime = history.firstOrNull()?.timeLabel ?: ""
            val lastTime = history.lastOrNull()?.timeLabel ?: ""
            Text("$firstTime - $lastTime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
