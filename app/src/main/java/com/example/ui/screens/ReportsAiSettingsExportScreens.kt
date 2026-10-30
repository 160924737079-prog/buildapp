package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AiInsightsEngine
import com.example.domain.CurrencyOption
import com.example.domain.DateRangeBounds
import com.example.domain.DateRangeEngine
import com.example.domain.ExportEngine
import com.example.domain.FinancialCalculator
import com.example.domain.MoneyUtils
import com.example.domain.PaymentMethod
import com.example.domain.ThemePreference
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.components.CalendarSpendingHeatmap
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.CategoryVerticalBarChart
import com.example.ui.components.DailyTrendLineChart
import com.example.ui.components.GroupedCategoryMonthChart
import com.example.ui.components.MonthlyColumnChart
import com.example.ui.components.MonthlyStackedCategoryChart
import com.example.ui.components.PaymentMethodDistributionChart
import com.example.ui.components.TopCategoriesHorizontalBarChart
import com.example.ui.components.YearlySpendingTrendChart
import com.example.ui.theme.AlertRed600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFamily

private enum class ReportTab(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly (Q1–Q4)"),
    HALF_YEARLY("Half-Yearly (H1/H2)"),
    YEARLY("Yearly (YoY)"),
    CUSTOM_FILTER("Custom Range & Filter"),
    TEN_CHARTS("All 10 Charts")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsAndAnalyticsScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val context = LocalContext.current
    val currency = uiState.currencyCode
    var activeTab by remember { mutableStateOf(ReportTab.MONTHLY) }

    val refParts = uiState.referenceDateIso.split("-")
    val initialYear = refParts.getOrNull(0)?.toIntOrNull() ?: 2026
    val initialMonth = refParts.getOrNull(1)?.toIntOrNull() ?: 10

    var selectedDateIso by remember(uiState.referenceDateIso) { mutableStateOf(uiState.referenceDateIso) }
    var selectedYear by remember(initialYear) { mutableIntStateOf(initialYear) }
    var selectedMonth by remember(initialMonth) { mutableIntStateOf(initialMonth) }
    var selectedQuarter by remember(initialMonth) { mutableIntStateOf(((initialMonth - 1) / 3) + 1) }
    var selectedHalf by remember(initialMonth) { mutableIntStateOf(if (initialMonth <= 6) 1 else 2) }

    // Custom Filter Report states
    var customStart by remember(initialYear, initialMonth) {
        mutableStateOf(String.format("%04d-%02d-01", initialYear, initialMonth))
    }
    var customEnd by remember(uiState.referenceDateIso) { mutableStateOf(uiState.referenceDateIso) }
    var selectedCustomCatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCustomPayMethods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customMinAmt by remember { mutableStateOf("") }
    var customMaxAmt by remember { mutableStateOf("") }

    val topCategories = uiState.categories.filter { it.parentCategoryId == null }

    val currentBoundsAndPrev: Triple<DateRangeBounds, DateRangeBounds?, String> = when (activeTab) {
        ReportTab.DAILY -> Triple(
            DateRangeBounds(selectedDateIso, selectedDateIso, "Daily (${selectedDateIso})", 1),
            DateRangeBounds(DateRangeEngine.addDays(selectedDateIso, -1), DateRangeEngine.addDays(selectedDateIso, -1), "Previous Day", 1),
            "previous day"
        )
        ReportTab.WEEKLY -> Triple(
            DateRangeEngine.weekBoundsContaining(selectedDateIso),
            DateRangeEngine.weekBoundsContaining(DateRangeEngine.addDays(selectedDateIso, -7)),
            "previous week"
        )
        ReportTab.MONTHLY, ReportTab.TEN_CHARTS -> {
            val prevBounds = if (selectedMonth == 1) DateRangeEngine.monthBounds(selectedYear - 1, 12)
            else DateRangeEngine.monthBounds(selectedYear, selectedMonth - 1)
            Triple(DateRangeEngine.monthBounds(selectedYear, selectedMonth), prevBounds, "previous month")
        }
        ReportTab.QUARTERLY -> {
            val prevQ = if (selectedQuarter == 1) DateRangeEngine.quarterBounds(selectedYear - 1, 4)
            else DateRangeEngine.quarterBounds(selectedYear, selectedQuarter - 1)
            Triple(DateRangeEngine.quarterBounds(selectedYear, selectedQuarter), prevQ, "previous quarter")
        }
        ReportTab.HALF_YEARLY -> {
            val prevH = if (selectedHalf == 1) DateRangeEngine.halfYearBounds(selectedYear - 1, 2)
            else DateRangeEngine.halfYearBounds(selectedYear, 1)
            Triple(DateRangeEngine.halfYearBounds(selectedYear, selectedHalf), prevH, "previous half-year")
        }
        ReportTab.YEARLY -> Triple(
            DateRangeEngine.yearBounds(selectedYear),
            DateRangeEngine.yearBounds(selectedYear - 1),
            "prior year (${selectedYear - 1})"
        )
        ReportTab.CUSTOM_FILTER -> {
            val s = if (DateRangeEngine.isValidIsoDate(customStart)) customStart else "2026-01-01"
            val e = if (DateRangeEngine.isValidIsoDate(customEnd)) customEnd else "2026-10-31"
            Triple(
                DateRangeBounds(
                    minOf(s, e),
                    maxOf(s, e),
                    "Custom (${minOf(s, e)} to ${maxOf(s, e)})",
                    DateRangeEngine.daysBetweenInclusive(minOf(s, e), maxOf(s, e))
                ),
                null,
                ""
            )
        }
    }

    val report = FinancialCalculator.buildTimeHorizonReport(
        title = "${activeTab.label} Financial Report",
        bounds = currentBoundsAndPrev.first,
        previousBounds = currentBoundsAndPrev.second,
        comparisonName = currentBoundsAndPrev.third,
        allExpenses = uiState.expenses,
        categories = uiState.categories,
        categoryIds = if (activeTab == ReportTab.CUSTOM_FILTER) selectedCustomCatIds else emptySet(),
        paymentMethods = if (activeTab == ReportTab.CUSTOM_FILTER) selectedCustomPayMethods else emptySet(),
        minAmountMinor = if (activeTab == ReportTab.CUSTOM_FILTER) MoneyUtils.toMinorUnitsAllowZero(customMinAmt) else null,
        maxAmountMinor = if (activeTab == ReportTab.CUSTOM_FILTER) MoneyUtils.toMinorUnitsAllowZero(customMaxAmt) else null
    )

    val allMonthlyTotals = FinancialCalculator.calculateMonthlyTotals(uiState.expenses)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Horizon Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("report_tab_${tab.name.lowercase()}")
                )
            }
        }

        // Period Navigation Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (activeTab) {
                    ReportTab.DAILY, ReportTab.WEEKLY -> {
                        val step = if (activeTab == ReportTab.DAILY) 1 else 7
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { selectedDateIso = DateRangeEngine.addDays(selectedDateIso, -step) }) {
                                Text("← Prev")
                            }
                            Text(
                                text = report.bounds.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(onClick = { selectedDateIso = DateRangeEngine.addDays(selectedDateIso, step) }) {
                                Text("Next →")
                            }
                        }
                    }
                    ReportTab.MONTHLY, ReportTab.TEN_CHARTS -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = {
                                if (selectedMonth == 1) {
                                    selectedMonth = 12
                                    selectedYear -= 1
                                } else selectedMonth -= 1
                            }) { Text("← Prev Month") }
                            Text(report.bounds.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = {
                                if (selectedMonth == 12) {
                                    selectedMonth = 1
                                    selectedYear += 1
                                } else selectedMonth += 1
                            }) { Text("Next Month →") }
                        }
                    }
                    ReportTab.QUARTERLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { q ->
                                FilterChip(
                                    selected = selectedQuarter == q,
                                    onClick = { selectedQuarter = q },
                                    label = {
                                        val monthsStr = when (q) {
                                            1 -> "Jan–Mar"
                                            2 -> "Apr–Jun"
                                            3 -> "Jul–Sep"
                                            else -> "Oct–Dec"
                                        }
                                        Text("Q$q ($monthsStr)")
                                    }
                                )
                            }
                        }
                    }
                    ReportTab.HALF_YEARLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedHalf == 1,
                                onClick = { selectedHalf = 1 },
                                label = { Text("H1: January–June $selectedYear") }
                            )
                            FilterChip(
                                selected = selectedHalf == 2,
                                onClick = { selectedHalf = 2 },
                                label = { Text("H2: July–December $selectedYear") }
                            )
                        }
                    }
                    ReportTab.YEARLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2024, 2025, 2026, 2027).forEach { yr ->
                                FilterChip(
                                    selected = selectedYear == yr,
                                    onClick = { selectedYear = yr },
                                    label = { Text("Year $yr") }
                                )
                            }
                        }
                    }
                    ReportTab.CUSTOM_FILTER -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customStart,
                                onValueChange = { customStart = it },
                                label = { Text("Start Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customEnd,
                                onValueChange = { customEnd = it },
                                label = { Text("End Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text("Filter Categories", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            topCategories.forEach { cat ->
                                val isSel = cat.id in selectedCustomCatIds
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        selectedCustomCatIds = if (isSel) selectedCustomCatIds - cat.id else selectedCustomCatIds + cat.id
                                    },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                        Text("Filter Payment Method", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaymentMethod.entries.forEach { pm ->
                                val isSel = pm.label in selectedCustomPayMethods
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        selectedCustomPayMethods = if (isSel) selectedCustomPayMethods - pm.label else selectedCustomPayMethods + pm.label
                                    },
                                    label = { Text(pm.label) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customMinAmt,
                                onValueChange = { customMinAmt = it },
                                label = { Text("Min Amount") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customMaxAmt,
                                onValueChange = { customMaxAmt = it },
                                label = { Text("Max Amount") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                customStart = "2026-01-01"
                                customEnd = "2026-10-31"
                                selectedCustomCatIds = emptySet()
                                selectedCustomPayMethods = emptySet()
                                customMinAmt = ""
                                customMaxAmt = ""
                            }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }

        // Report Summary KPI Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(report.bounds.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = {
                        viewModel.generateExportReport(
                            context = context,
                            format = "PDF",
                            expensesToExport = report.qualifyingTransactions,
                            reportTitle = report.title,
                            dateRangeLabel = report.bounds.label,
                            appliedFiltersSummary = "Period: ${report.bounds.label}"
                        )
                    }) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download PDF")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Spending", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = MoneyUtils.formatMoney(report.totalExpensesMinor, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Transactions", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "${report.transactionCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Highest Category: ${report.highestCategory?.categoryName ?: "None"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (report.highestMonth != null && activeTab in listOf(ReportTab.QUARTERLY, ReportTab.HALF_YEARLY, ReportTab.YEARLY)) {
                            "Highest Month: ${report.highestMonth.monthLabel}"
                        } else {
                            "Highest Day: ${report.highestDay?.dayLabel ?: "None"}"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (report.comparisonLabel.isNotBlank()) {
                    Text(
                        text = "Comparison: ${report.comparisonLabel} (Prior: ${MoneyUtils.formatMoney(report.previousPeriodTotalMinor, currency)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Complete 10 Interactive Charts Showcase (Section 13)
        ChartCard("1. Category-Wise Spending Donut Chart") {
            CategoryDonutChart(items = report.categoryBreakdown, currencyCode = currency)
        }
        ChartCard("2. Total Spending by Category Vertical Bar Chart") {
            CategoryVerticalBarChart(items = report.categoryBreakdown, currencyCode = currency)
        }
        ChartCard("3. Daily Expense Trends Line Chart") {
            DailyTrendLineChart(dailyItems = report.dailyTotals, currencyCode = currency)
        }
        ChartCard("4. Monthly Expenses Column Chart") {
            MonthlyColumnChart(monthlyItems = allMonthlyTotals, currencyCode = currency)
        }
        ChartCard("5. Grouped Bar Chart Comparing Categories Across Months") {
            GroupedCategoryMonthChart(
                monthlyItems = allMonthlyTotals,
                categories = uiState.categories,
                currencyCode = currency
            )
        }
        ChartCard("6. Top Spending Categories Horizontal Bar Chart") {
            TopCategoriesHorizontalBarChart(items = report.categoryBreakdown, currencyCode = currency)
        }
        ChartCard("7. Daily Spending Intensity Calendar Heatmap ($selectedYear-${String.format("%02d", selectedMonth)})") {
            CalendarSpendingHeatmap(
                year = selectedYear,
                month1Based = selectedMonth,
                expenses = uiState.expenses,
                currencyCode = currency
            )
        }
        ChartCard("8. Category-Wise Monthly Stacked Bar Chart") {
            MonthlyStackedCategoryChart(
                monthlyItems = allMonthlyTotals,
                categories = uiState.categories,
                currencyCode = currency
            )
        }
        ChartCard("9. Payment-Method Distribution Chart") {
            PaymentMethodDistributionChart(items = report.paymentBreakdown, currencyCode = currency)
        }
        ChartCard("10. Yearly Spending Trend (YoY)") {
            YearlySpendingTrendChart(expenses = uiState.expenses, currencyCode = currency)
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiInsightsScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    var questionInput by remember { mutableStateOf("") }
    val aiEnabled = uiState.profile?.aiEnabled ?: true
    val aiConsent = uiState.profile?.aiConsentGranted ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Emerald600)
                        Text(
                            text = "AI-Powered Financial Insights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { enabled ->
                            val p = uiState.profile ?: return@Switch
                            viewModel.updateProfilePreferences(
                                fullName = p.fullName,
                                preferredCurrency = p.preferredCurrency,
                                dateFormat = p.dateFormat,
                                timezone = p.timezone,
                                themeMode = p.themeMode,
                                aiConsentGranted = p.aiConsentGranted,
                                aiEnabled = enabled
                            )
                        }
                    )
                }

                Text(
                    text = "All monetary figures are computed deterministically by ExpensePro's calculation engine from your authorized records — never invented or hallucinated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Consent to share anonymized summaries with external AI proxy",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = aiConsent,
                        onCheckedChange = { consent ->
                            val p = uiState.profile ?: return@Switch
                            viewModel.updateProfilePreferences(
                                fullName = p.fullName,
                                preferredCurrency = p.preferredCurrency,
                                dateFormat = p.dateFormat,
                                timezone = p.timezone,
                                themeMode = p.themeMode,
                                aiConsentGranted = consent,
                                aiEnabled = p.aiEnabled
                            )
                        }
                    )
                }

                if (aiEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = questionInput,
                            onValueChange = { questionInput = it },
                            label = { Text("Ask a question about your spending...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("ai_question_input")
                        )
                        Button(
                            onClick = {
                                viewModel.askAiQuestion(questionInput)
                                questionInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            modifier = Modifier.height(52.dp).testTag("ai_ask_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Ask")
                        }
                    }

                    Text("Suggested Financial Questions:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiInsightsEngine.SUGGESTED_QUESTIONS.forEach { q ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.askAiQuestion(q) },
                                label = { Text(q) }
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "AI Insights are currently disabled. All core expense tracking, reporting, and exporting features remain 100% operational.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Insights History Cards
        uiState.aiInsightsHistory.forEach { resp ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Q: ${resp.question}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Emerald600
                    )
                    Text(
                        text = resp.headlineAnswer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = resp.detailedAnalysis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (resp.keyMetrics.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            resp.keyMetrics.forEach { (k, v) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(k, style = MaterialTheme.typography.labelSmall)
                                        Text(v, style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = "Tip: ${resp.recommendation}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAndSettingsScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val context = LocalContext.current
    val profile = uiState.profile
    var fullName by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "Alex Morgan") }
    var currency by remember(profile?.preferredCurrency) { mutableStateOf(profile?.preferredCurrency ?: "INR") }
    var dateFormat by remember(profile?.dateFormat) { mutableStateOf(profile?.dateFormat ?: "dd MMM yyyy") }
    var timezone by remember(profile?.timezone) { mutableStateOf(profile?.timezone ?: "Asia/Kolkata") }
    var themeMode by remember(profile?.themeMode) { mutableStateOf(profile?.themeMode ?: "LIGHT") }

    val dateFormats = listOf("dd MMM yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy")
    val timezones = listOf("Asia/Kolkata", "UTC", "America/New_York", "Europe/London", "Asia/Singapore", "Australia/Sydney")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Build APK & Device Installation Section in Settings Menu
        BuildApkSettingsCard(
            uiState = uiState,
            onBuildApk = { viewModel.triggerBuildApk(context, shareImmediately = false) },
            onDownloadApk = { viewModel.downloadBuiltApk(context) }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tracker Preferences & Regional Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local SQLite Database • Direct Expense Tracking & Visualizations (No Login Required)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Preferred Currency", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrencyOption.entries.forEach { opt ->
                        FilterChip(
                            selected = currency == opt.code,
                            onClick = { currency = opt.code },
                            label = { Text("${opt.code} (${opt.symbol})") }
                        )
                    }
                }

                Text("Date Format Preference", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dateFormats.forEach { fmt ->
                        FilterChip(
                            selected = dateFormat == fmt,
                            onClick = { dateFormat = fmt },
                            label = { Text(fmt) }
                        )
                    }
                }

                Text("Time Zone", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timezones.forEach { tz ->
                        FilterChip(
                            selected = timezone == tz,
                            onClick = { timezone = tz },
                            label = { Text(tz) }
                        )
                    }
                }

                Text("Appearance Theme", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.entries.forEach { tp ->
                        FilterChip(
                            selected = themeMode == tp.name,
                            onClick = { themeMode = tp.name },
                            label = { Text(tp.label) }
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.updateProfilePreferences(
                            fullName = fullName,
                            preferredCurrency = currency,
                            dateFormat = dateFormat,
                            timezone = timezone,
                            themeMode = themeMode,
                            aiConsentGranted = profile?.aiConsentGranted ?: false,
                            aiEnabled = profile?.aiEnabled ?: true
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_profile_button")
                ) {
                    Text("Save Preferences", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.restoreSampleExpenses() },
                        modifier = Modifier.weight(1f).testTag("settings_restore_sample_button")
                    ) {
                        Text("Restore Sample Data")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearAllExpenses() },
                        modifier = Modifier.weight(1f).testTag("settings_clear_expenses_button")
                    ) {
                        Text("Clear All Expenses", color = AlertRed600)
                    }
                }
            }
        }
    }
}

@Composable
fun BuildApkSettingsCard(
    uiState: ExpenseProUiState,
    onBuildApk: () -> Unit,
    onDownloadApk: () -> Unit
) {
    val artifact = uiState.apkBuildArtifact

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = "Android APK Builder",
                        tint = Emerald600,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Build & Install Android APK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Package signed APK (ExpensePro_AI_v1.0.apk) for device installation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.isBuildingApk) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Emerald600
                )
                Text(
                    text = uiState.apkBuildStepMessage ?: "Building APK...",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald600
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBuildApk,
                    enabled = !uiState.isBuildingApk,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("build_apk_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (artifact == null) "Build APK" else "Rebuild APK",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onDownloadApk,
                    enabled = !uiState.isBuildingApk,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("download_apk_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download & Install", fontWeight = FontWeight.SemiBold)
                }
            }

            if (artifact != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "APK Build Succeeded — Ready for Installation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600
                            )
                        }
                        Text(
                            text = "File: ${artifact.fileName} (${ExportEngine.formatApkSize(artifact.fileSizeBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Package ID: ${artifact.packageName} • Version: ${artifact.versionName} (${artifact.versionCode})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "SHA-256: ${artifact.sha256Checksum.take(36)}...",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFamily
                        )

                        // Terminal Build Logs output
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                artifact.buildLogs.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = if (line.startsWith("BUILD SUCCESSFUL")) Color(0xFF10B981) else Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportAndAccountScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val context = LocalContext.current
    val metrics = uiState.dashboardMetrics
    val periodBounds = metrics?.periodBounds
    val periodExpenses = uiState.expenses.filter {
        periodBounds == null || it.expenseDateIso in periodBounds.startDateIso..periodBounds.endDateIso
    }
    var showDeleteAccountModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BuildApkSettingsCard(
            uiState = uiState,
            onBuildApk = { viewModel.triggerBuildApk(context, shareImmediately = false) },
            onDownloadApk = { viewModel.downloadBuiltApk(context) }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Data Export & Financial Report Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Export records matching your active period (${periodBounds?.label ?: "Current Period"}: ${periodExpenses.size} transactions, Total: ${MoneyUtils.formatMoney(periodExpenses.sumOf { it.amountMinor }, uiState.currencyCode)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val ym = uiState.referenceDateIso.take(7)

                Button(
                    onClick = {
                        viewModel.generateExportReport(
                            context = context,
                            format = "CSV",
                            expensesToExport = periodExpenses,
                            reportTitle = "Category & Transaction Report",
                            dateRangeLabel = periodBounds?.label ?: "All",
                            appliedFiltersSummary = "Period: ${uiState.globalPeriod.label}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_csv_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download CSV (ExpensePro_Category_Report_$ym.csv)")
                }

                Button(
                    onClick = {
                        viewModel.generateExportReport(
                            context = context,
                            format = "XLSX",
                            expensesToExport = periodExpenses,
                            reportTitle = "Excel Transactions Workbook",
                            dateRangeLabel = periodBounds?.label ?: "All",
                            appliedFiltersSummary = "Period: ${uiState.globalPeriod.label}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_xlsx_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Excel XLSX (ExpensePro_Transactions_$ym.xlsx)")
                }

                Button(
                    onClick = {
                        viewModel.generateExportReport(
                            context = context,
                            format = "PDF",
                            expensesToExport = periodExpenses,
                            reportTitle = "Monthly Financial Summary",
                            dateRangeLabel = periodBounds?.label ?: "All",
                            appliedFiltersSummary = "Period: ${uiState.globalPeriod.label}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_pdf_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download PDF Summary (ExpensePro_Monthly_Report_$ym.pdf)")
                }

                uiState.lastGeneratedExport?.let { exp ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Last Generated Report: ${exp.fileName} (${exp.formatLabel})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Records: ${exp.totalRecords} • Total: ${MoneyUtils.formatMoney(exp.totalAmountMinor, uiState.currencyCode)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = exp.previewContent.take(600),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    }
                }
            }
        }

        // Local Data Management Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Local Expense Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage your local expense records. You can restore sample transactions to preview all 10 interactive charts or clear all expenses to start fresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { viewModel.restoreSampleExpenses() },
                    modifier = Modifier.fillMaxWidth().testTag("restore_sample_expenses_button")
                ) {
                    Text("Restore Sample Expenses for Visualizations")
                }
                OutlinedButton(
                    onClick = { showDeleteAccountModal = true },
                    modifier = Modifier.fillMaxWidth().testTag("clear_all_expenses_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AlertRed600)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All Expenses (Start Fresh)", color = AlertRed600)
                }
            }
        }
    }

    if (showDeleteAccountModal) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountModal = false },
            title = { Text("Clear All Expenses?") },
            text = {
                Text("This will clear all recorded expenses from your local database while keeping your categories and preferences intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountModal = false
                        viewModel.clearAllExpenses()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed600)
                ) {
                    Text("Clear All Expenses")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
