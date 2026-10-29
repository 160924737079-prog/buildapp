package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CategoryEntity
import com.example.data.ExpenseEntity
import com.example.domain.CategoryBreakdownItem
import com.example.domain.DailyTotalItem
import com.example.domain.DateRangeEngine
import com.example.domain.MoneyUtils
import com.example.domain.MonthlyTotalItem
import com.example.domain.PaymentMethodBreakdownItem
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.parseHexColor

/**
 * 1. Interactive Donut Chart for Category-Wise Spending
 */
@Composable
fun CategoryDonutChart(
    items: List<CategoryBreakdownItem>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyChartPlaceholder("No category spending recorded for this period.")
        return
    }
    var selectedIdx by remember(items) { mutableIntStateOf(0) }
    val safeIndex = selectedIdx.coerceIn(0, items.lastIndex)
    val selected = items[safeIndex]

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    val strokeWidth = 26.dp.toPx()
                    var startAngle = -90f
                    val total = items.sumOf { it.totalMinor }.coerceAtLeast(1L)
                    items.forEachIndexed { idx, item ->
                        val sweep = (item.totalMinor.toFloat() / total.toFloat()) * 360f
                        val isSel = idx == safeIndex
                        drawArc(
                            color = parseHexColor(item.colorHex),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                            useCenter = false,
                            style = Stroke(
                                width = if (isSel) strokeWidth * 1.18f else strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selected.categoryName.take(14),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "${selected.percentageOfTotal.toPlainString()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = MoneyUtils.formatCompact(selected.totalMinor, currencyCode),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = Emerald600
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.take(6).forEachIndexed { index, item ->
                    val isSelected = index == safeIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else Color.Transparent
                            )
                            .clickable { selectedIdx = index }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(item.colorHex))
                            )
                            Text(
                                text = item.categoryName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${item.percentageOfTotal.toPlainString()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. Vertical Bar Chart for Total Spending by Category
 */
@Composable
fun CategoryVerticalBarChart(
    items: List<CategoryBreakdownItem>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyChartPlaceholder("No category totals to display.")
        return
    }
    val topItems = items.take(7)
    val maxVal = topItems.maxOf { it.totalMinor }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            topItems.forEach { item ->
                val ratio = (item.totalMinor.toFloat() / maxVal.toFloat()).coerceIn(0.06f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text(
                        text = MoneyUtils.formatCompact(item.totalMinor, currencyCode),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight(ratio * 0.75f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(parseHexColor(item.colorHex))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.categoryName.take(8),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 3. Line Chart for Daily Expense Trends
 */
@Composable
fun DailyTrendLineChart(
    dailyItems: List<DailyTotalItem>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (dailyItems.isEmpty()) {
        EmptyChartPlaceholder("No daily expense points in the selected range.")
        return
    }
    val maxVal = dailyItems.maxOf { it.totalMinor }.coerceAtLeast(1L)
    val peakDay = dailyItems.maxByOrNull { it.totalMinor }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (peakDay != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Peak Day: ${peakDay.dayLabel} (${MoneyUtils.formatMoney(peakDay.totalMinor, currencyCode)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${dailyItems.size} active day(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Emerald600
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val w = size.width
            val h = size.height
            val points = dailyItems.mapIndexed { idx, d ->
                val x = if (dailyItems.size == 1) w / 2f else (idx.toFloat() / (dailyItems.size - 1)) * w
                val y = h - ((d.totalMinor.toFloat() / maxVal.toFloat()) * (h * 0.85f))
                Offset(x, y)
            }

            if (points.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Emerald500.copy(alpha = 0.32f), Color.Transparent)
                    )
                )

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = strokePath,
                    color = Emerald500,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            points.forEach { pt ->
                drawCircle(color = Emerald600, radius = 4.5.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dailyItems.first().dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dailyItems.size > 2) {
                Text(
                    text = dailyItems[dailyItems.size / 2].dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = dailyItems.last().dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 4. Column Chart for Monthly Expenses
 */
@Composable
fun MonthlyColumnChart(
    monthlyItems: List<MonthlyTotalItem>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (monthlyItems.isEmpty()) {
        EmptyChartPlaceholder("No monthly data available.")
        return
    }
    val recent = monthlyItems.takeLast(6)
    val maxVal = recent.maxOf { it.totalMinor }.coerceAtLeast(1L)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(165.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        recent.forEach { m ->
            val ratio = (m.totalMinor.toFloat() / maxVal.toFloat()).coerceIn(0.08f, 1f)
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = MoneyUtils.formatCompact(m.totalMinor, currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .fillMaxHeight(ratio * 0.74f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Emerald500)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = m.monthLabel.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 5. Grouped Bar Chart for Comparing Categories Across Months
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupedCategoryMonthChart(
    monthlyItems: List<MonthlyTotalItem>,
    categories: List<CategoryEntity>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val months = monthlyItems.takeLast(4)
    val topCats = categories.filter { it.parentCategoryId == null }.take(3)
    if (months.isEmpty() || topCats.isEmpty()) {
        EmptyChartPlaceholder("Not enough multi-month category data to compare.")
        return
    }
    val maxVal = months.maxOf { m ->
        topCats.maxOfOrNull { c -> m.byCategoryMinor[c.id] ?: 0L } ?: 1L
    }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            topCats.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(cat.colorHex)))
                    Text(cat.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(145.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            months.forEach { m ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        topCats.forEach { cat ->
                            val v = m.byCategoryMinor[cat.id] ?: 0L
                            val hRatio = (v.toFloat() / maxVal.toFloat()).coerceIn(0.05f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(hRatio * 0.85f)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(parseHexColor(cat.colorHex))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(m.monthLabel.take(8), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 6. Horizontal Bar Chart for Top Spending Categories
 */
@Composable
fun TopCategoriesHorizontalBarChart(
    items: List<CategoryBreakdownItem>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyChartPlaceholder("No category ranking data available.")
        return
    }
    val maxVal = items.first().totalMinor.coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.take(6).forEach { item ->
            val ratio = (item.totalMinor.toFloat() / maxVal.toFloat()).coerceIn(0.04f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.categoryName} (${item.transactionCount} tx)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${MoneyUtils.formatMoney(item.totalMinor, currencyCode)} • ${item.percentageOfTotal.toPlainString()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(parseHexColor(item.colorHex))
                    )
                }
            }
        }
    }
}

/**
 * 7. Calendar Heatmap for Daily Spending Intensity
 */
@Composable
fun CalendarSpendingHeatmap(
    year: Int,
    month1Based: Int,
    expenses: List<ExpenseEntity>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val maxDays = DateRangeEngine.maxDaysInMonth(year, month1Based)
    val ymPrefix = String.format("%04d-%02d", year, month1Based)
    val byDay = expenses
        .filter { it.status == "ACTIVE" && it.transactionType == "EXPENSE" && it.expenseDateIso.startsWith(ymPrefix) }
        .groupBy { it.expenseDateIso.takeLast(2).toIntOrNull() ?: 1 }
        .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

    val maxDaySpend = (byDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    var selectedDay by remember(year, month1Based) { mutableIntStateOf(1) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = (maxDays + 6) / 7
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c + 1
                    if (dayNum <= maxDays) {
                        val spend = byDay[dayNum] ?: 0L
                        val intensity = if (spend == 0L) 0f else (spend.toFloat() / maxDaySpend.toFloat()).coerceIn(0.22f, 1f)
                        val bg = if (spend == 0L) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Emerald500.copy(alpha = intensity)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .border(
                                    width = if (selectedDay == dayNum) 1.5.dp else 0.dp,
                                    color = if (selectedDay == dayNum) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedDay = dayNum },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNum.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (spend > 0L) FontWeight.Bold else FontWeight.Normal,
                                color = if (intensity > 0.55f) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(36.dp))
                    }
                }
            }
        }
        val selectedSpend = byDay[selectedDay] ?: 0L
        Text(
            text = "Selected Day: $ymPrefix-${String.format("%02d", selectedDay)} • Spent: ${MoneyUtils.formatMoney(selectedSpend, currencyCode)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 8. Stacked Bar Chart for Category-Wise Monthly Spending
 */
@Composable
fun MonthlyStackedCategoryChart(
    monthlyItems: List<MonthlyTotalItem>,
    categories: List<CategoryEntity>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (monthlyItems.isEmpty()) {
        EmptyChartPlaceholder("No monthly category data to stack.")
        return
    }
    val catMap = categories.associateBy { it.id }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        monthlyItems.takeLast(5).forEach { m ->
            val total = m.totalMinor.coerceAtLeast(1L)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(m.monthLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(MoneyUtils.formatMoney(m.totalMinor, currencyCode), style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    m.byCategoryMinor.entries.sortedByDescending { it.value }.forEach { (catId, amt) ->
                        val weight = (amt.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                        val colorHex = catMap[catId]?.colorHex ?: "#10B981"
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(parseHexColor(colorHex))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 9. Payment-Method Distribution Chart
 */
@Composable
fun PaymentMethodDistributionChart(
    items: List<PaymentMethodBreakdownItem>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyChartPlaceholder("No payment method transactions in this period.")
        return
    }
    val palette = listOf(
        Color(0xFF10B981),
        Color(0xFF3B82F6),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFF06B6D4)
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            items.forEachIndexed { idx, item ->
                val w = item.percentageOfTotal.toFloat().coerceAtLeast(2f)
                Box(
                    modifier = Modifier
                        .weight(w)
                        .fillMaxHeight()
                        .background(palette[idx % palette.size])
                )
            }
        }
        items.forEachIndexed { idx, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette[idx % palette.size])
                    )
                    Text(
                        text = "${item.paymentMethod} (${item.transactionCount} tx)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "${MoneyUtils.formatMoney(item.totalMinor, currencyCode)} (${item.percentageOfTotal.toPlainString()}%)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * 10. Yearly Spending Trend Chart
 */
@Composable
fun YearlySpendingTrendChart(
    expenses: List<ExpenseEntity>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val byYear = expenses
        .filter { it.status == "ACTIVE" && it.transactionType == "EXPENSE" }
        .groupBy { it.expenseDateIso.take(4) }
        .entries
        .sortedBy { it.key }
        .map { (yr, list) -> yr to list.sumOf { it.amountMinor } }

    if (byYear.isEmpty()) {
        EmptyChartPlaceholder("No annual data recorded yet.")
        return
    }
    val maxVal = byYear.maxOf { it.second }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        byYear.forEach { (yr, totalMinor) ->
            val ratio = (totalMinor.toFloat() / maxVal.toFloat()).coerceIn(0.06f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = yr,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFamily,
                    modifier = Modifier.width(46.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(listOf(Emerald600, Emerald500))
                            )
                    )
                }
                Text(
                    text = MoneyUtils.formatMoney(totalMinor, currencyCode),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(105.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyChartPlaceholder(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier.padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
