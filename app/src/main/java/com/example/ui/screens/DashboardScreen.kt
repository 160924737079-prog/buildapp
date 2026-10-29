package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ExpenseEntity
import com.example.domain.BudgetAlertLevel
import com.example.domain.CurrencyOption
import com.example.domain.DateRangeEngine
import com.example.domain.MoneyUtils
import com.example.domain.PaymentMethod
import com.example.domain.ReportingPeriod
import com.example.ui.AppPage
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DailyTrendLineChart
import com.example.ui.components.MonthlyColumnChart
import com.example.ui.components.PaymentMethodDistributionChart
import com.example.ui.theme.AlertRed600
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.WarningAmber600
import com.example.ui.theme.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val metrics = uiState.dashboardMetrics ?: return
    val currency = uiState.currencyCode
    val currencySymbol = CurrencyOption.fromCode(currency).symbol
    var customStartInput by remember(uiState.customStartDateIso) { mutableStateOf(uiState.customStartDateIso) }
    var customEndInput by remember(uiState.customEndDateIso) { mutableStateOf(uiState.customEndDateIso) }

    // Instant Add Expense state right on the Dashboard
    val topCategories = uiState.categories.filter { it.parentCategoryId == null && !it.isArchived }
    var quickTitle by remember { mutableStateOf("") }
    var quickAmount by remember { mutableStateOf("") }
    var quickCategoryId by remember(topCategories.size) {
        mutableStateOf(topCategories.firstOrNull()?.id.orEmpty())
    }
    var quickPaymentMethod by remember { mutableStateOf(PaymentMethod.UPI.label) }
    var pendingDeleteExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    val triggeredAlerts = uiState.budgetStatuses.filter { it.alertLevel != BudgetAlertLevel.NORMAL }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header & Period Selector Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Navy900)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${uiState.profile?.fullName ?: "Alex Morgan"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Period: ${metrics.periodBounds.label} • TZ: ${uiState.profile?.timezone ?: "Asia/Kolkata"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Button(
                        onClick = { viewModel.navigateTo(AppPage.QUICK_ADD) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        modifier = Modifier.testTag("dashboard_quick_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Expense", tint = Navy900)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Full Form", color = Navy900, fontWeight = FontWeight.Bold)
                    }
                }

                // Global Reporting Period Selector (All 14 Options)
                Text(
                    text = "Global Reporting Period",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportingPeriod.entries.forEach { period ->
                        val selected = uiState.globalPeriod == period
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setGlobalReportingPeriod(period) },
                            label = {
                                Text(
                                    text = period.label,
                                    color = if (selected) Navy900 else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald500,
                                containerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("period_chip_${period.name.lowercase()}")
                        )
                    }
                }

                if (uiState.globalPeriod == ReportingPeriod.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customStartInput,
                            onValueChange = { customStartInput = it },
                            label = { Text("Start (YYYY-MM-DD)", color = Color.White) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customEndInput,
                            onValueChange = { customEndInput = it },
                            label = { Text("End (YYYY-MM-DD)", color = Color.White) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                viewModel.setGlobalReportingPeriod(
                                    ReportingPeriod.CUSTOM,
                                    customStartInput.trim(),
                                    customEndInput.trim()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                        ) {
                            Text("Apply", color = Navy900, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Instant Quick-Add Expense Card directly on Dashboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Add Expense",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            quickTitle = "Fresh Groceries & Vegetables"
                            quickAmount = "500"
                            val groc = topCategories.firstOrNull { it.name.contains("Grocer", true) }
                            if (groc != null) quickCategoryId = groc.id
                            quickPaymentMethod = "UPI"
                        },
                        modifier = Modifier.testTag("dashboard_fill_sample_button")
                    ) {
                        Text("Fill Sample (₹500)")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = quickTitle,
                        onValueChange = { quickTitle = it },
                        label = { Text("Expense Title *") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f).testTag("dashboard_instant_title_input")
                    )
                    OutlinedTextField(
                        value = quickAmount,
                        onValueChange = { quickAmount = it },
                        label = { Text("Amount ($currencySymbol) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dashboard_instant_amount_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category:", style = MaterialTheme.typography.labelSmall)
                    topCategories.forEach { cat ->
                        val activeCatId = quickCategoryId.ifEmpty { topCategories.firstOrNull()?.id.orEmpty() }
                        FilterChip(
                            selected = activeCatId == cat.id,
                            onClick = { quickCategoryId = cat.id },
                            label = { Text(cat.name) },
                            modifier = Modifier.testTag("dashboard_cat_chip_${cat.name.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Method:", style = MaterialTheme.typography.labelSmall)
                        listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD).forEach { pm ->
                            FilterChip(
                                selected = quickPaymentMethod.equals(pm.label, true),
                                onClick = { quickPaymentMethod = pm.label },
                                label = { Text(pm.label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targetCatId = quickCategoryId.ifEmpty { topCategories.firstOrNull()?.id.orEmpty() }
                            val firstSubId = uiState.categories.firstOrNull { it.parentCategoryId == targetCatId }?.id
                            viewModel.saveExpense(
                                title = quickTitle,
                                amountText = quickAmount,
                                categoryId = targetCatId,
                                subcategoryId = firstSubId,
                                expenseDateIso = uiState.referenceDateIso,
                                paymentMethod = quickPaymentMethod,
                                merchant = "",
                                description = "",
                                notes = "",
                                tags = "",
                                receiptUri = null,
                                onSuccess = {
                                    quickTitle = ""
                                    quickAmount = ""
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.testTag("dashboard_instant_save_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Expense", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Recent & Active Transactions Card (with instant Edit and Delete buttons!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Recent Transactions (${metrics.periodBounds.label})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: ${MoneyUtils.formatMoney(metrics.totalPeriodExpensesMinor, currency)} across ${metrics.totalTransactionCount} transaction(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald600
                        )
                    }
                    TextButton(onClick = { viewModel.navigateTo(AppPage.TRANSACTIONS) }) {
                        Text("All History →")
                    }
                }

                val catMap = uiState.categories.associateBy { it.id }
                val periodTxs = uiState.expenses.filter {
                    it.expenseDateIso in metrics.periodBounds.startDateIso..metrics.periodBounds.endDateIso
                }.take(10)

                if (periodTxs.isEmpty()) {
                    Text(
                        text = "No transactions recorded in ${metrics.periodBounds.label}. Use Quick Add above to add an expense.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    periodTxs.forEach { tx ->
                        val cat = catMap[tx.categoryId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                                .testTag("dashboard_tx_row_${tx.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(cat?.colorHex ?: "#10B981").copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (cat?.name ?: "E").take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = parseHexColor(cat?.colorHex ?: "#10B981"),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text(
                                        text = tx.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${cat?.name ?: "Expense"} • ${tx.paymentMethod} • ${DateRangeEngine.formatDisplayDate(tx.expenseDateIso, uiState.dateFormatPattern)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = MoneyUtils.formatMoney(tx.amountMinor, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { viewModel.startEditingExpense(tx) },
                                    modifier = Modifier.size(36.dp).testTag("dashboard_edit_tx_${tx.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit ${tx.title}",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeleteExpense = tx },
                                    modifier = Modifier.size(36.dp).testTag("dashboard_delete_tx_${tx.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete ${tx.title}",
                                        tint = AlertRed600,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Budget Threshold Warning Banner (80%, 90%, 100%)
        if (triggeredAlerts.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
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
                            Icons.Default.Warning,
                            contentDescription = "Budget Alert",
                            tint = AlertRed600
                        )
                        Text(
                            text = "Budget Utilization Warnings (${triggeredAlerts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = AlertRed600,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    triggeredAlerts.take(3).forEach { alert ->
                        val badge = when (alert.alertLevel) {
                            BudgetAlertLevel.EXCEEDED_100 -> "100%+ EXCEEDED"
                            BudgetAlertLevel.CRITICAL_90 -> "90%+ CRITICAL"
                            BudgetAlertLevel.WARNING_80 -> "80%+ WARNING"
                            BudgetAlertLevel.NORMAL -> "OK"
                        }
                        Text(
                            text = "• ${alert.name}: ${alert.utilizationPercent.toPlainString()}% used (${MoneyUtils.formatMoney(alert.spentMinor, currency)} of ${MoneyUtils.formatMoney(alert.budgetLimitMinor, currency)}) [$badge]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 10 Summary Cards Grid (Section 5)
        Text(
            text = "Key Financial Metrics (10 Live Summary Indicators)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val summaryCards = listOf(
            SummaryCardData(
                title = "1. Total Expenses (${uiState.globalPeriod.label})",
                value = MoneyUtils.formatMoney(metrics.totalPeriodExpensesMinor, currency),
                subtitle = "${metrics.totalTransactionCount} qualifying transactions",
                accentColor = Emerald600,
                tag = "card_total_expenses"
            ),
            SummaryCardData(
                title = "2. Today's Expenses",
                value = MoneyUtils.formatMoney(metrics.todayExpensesMinor, currency),
                subtitle = DateRangeEngine.formatDisplayDate(uiState.referenceDateIso, uiState.dateFormatPattern),
                accentColor = Color(0xFF0EA5E9),
                tag = "card_today_expenses"
            ),
            SummaryCardData(
                title = "3. This Week's Expenses",
                value = MoneyUtils.formatMoney(metrics.thisWeekExpensesMinor, currency),
                subtitle = "Current Mon–Sun week",
                accentColor = Color(0xFF6366F1),
                tag = "card_week_expenses"
            ),
            SummaryCardData(
                title = "4. This Month's Expenses",
                value = MoneyUtils.formatMoney(metrics.thisMonthExpensesMinor, currency),
                subtitle = "Current calendar month",
                accentColor = Emerald600,
                tag = "card_month_expenses"
            ),
            SummaryCardData(
                title = "5. Previous Month's Expenses",
                value = MoneyUtils.formatMoney(metrics.previousMonthExpensesMinor, currency),
                subtitle = "Prior calendar month benchmark",
                accentColor = Color(0xFF64748B),
                tag = "card_prev_month_expenses"
            ),
            SummaryCardData(
                title = "6. Average Daily Expenses",
                value = MoneyUtils.formatMoney(metrics.averageDailyExpenseMinor, currency),
                subtitle = "Avg Tx: ${MoneyUtils.formatMoney(metrics.averageTransactionMinor, currency)}",
                accentColor = Color(0xFF8B5CF6),
                tag = "card_avg_daily"
            ),
            SummaryCardData(
                title = "7. Highest Spending Category",
                value = metrics.highestSpendingCategory?.categoryName ?: "None",
                subtitle = metrics.highestSpendingCategory?.let {
                    "${MoneyUtils.formatMoney(it.totalMinor, currency)} (${it.percentageOfTotal.toPlainString()}%)"
                } ?: "₹0.00",
                accentColor = WarningAmber600,
                tag = "card_highest_category"
            ),
            SummaryCardData(
                title = "8. Total Transactions",
                value = "${metrics.totalTransactionCount}",
                subtitle = "Active records in selected period",
                accentColor = Color(0xFF14B8A6),
                tag = "card_tx_count"
            ),
            SummaryCardData(
                title = "9. Remaining Monthly Budget",
                value = MoneyUtils.formatMoney(metrics.remainingMonthlyBudgetMinor, currency),
                subtitle = "${metrics.budgetUtilizationPercent.toPlainString()}% of ${MoneyUtils.formatMoney(metrics.overallMonthlyBudgetMinor, currency)} used",
                accentColor = if (metrics.remainingMonthlyBudgetMinor < 0L) AlertRed600 else Emerald600,
                tag = "card_remaining_budget"
            ),
            SummaryCardData(
                title = "10. Month-over-Month Change",
                value = metrics.monthOverMonthChangePercent?.let {
                    if (it > java.math.BigDecimal.ZERO) "+${it.toPlainString()}%" else "${it.toPlainString()}%"
                } ?: "N/A",
                subtitle = metrics.monthOverMonthMessage,
                accentColor = if ((metrics.monthOverMonthChangePercent?.toDouble() ?: 0.0) > 0.0) AlertRed600 else Emerald600,
                tag = "card_mom_change"
            )
        )

        // Responsive 2-column grid of summary cards
        for (pair in summaryCards.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { card ->
                    DashboardMetricCard(
                        data = card,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Visual Analytics Section on Dashboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category-Wise Spending Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.navigateTo(AppPage.REPORTS) }) {
                        Text("All 10 Charts →")
                    }
                }
                CategoryDonutChart(
                    items = metrics.categoryBreakdown,
                    currencyCode = currency
                )
            }
        }

        // Daily Expense Trend & Monthly Overview Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Daily Spending Trend (${metrics.periodBounds.label})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                DailyTrendLineChart(
                    dailyItems = metrics.dailyTrend,
                    currencyCode = currency
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Monthly Spending Comparison & Payment Methods",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                MonthlyColumnChart(
                    monthlyItems = metrics.monthlyTrend,
                    currencyCode = currency
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaymentMethodDistributionChart(
                    items = metrics.paymentMethodBreakdown,
                    currencyCode = currency
                )
            }
        }

        // Quick Navigation Shortcuts Footer
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { viewModel.navigateTo(AppPage.AI_INSIGHTS) }) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Insights")
            }
            OutlinedButton(onClick = { viewModel.navigateTo(AppPage.BUDGETS) }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Budgets")
            }
            OutlinedButton(onClick = { viewModel.navigateTo(AppPage.EXPORTS) }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export CSV/XLSX/PDF")
            }
        }
    }

    // Delete Confirmation Dialog on Dashboard
    pendingDeleteExpense?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDeleteExpense = null },
            title = { Text("Confirm Delete Expense") },
            text = {
                Text(
                    "Are you sure you want to delete '${tx.title}' (${MoneyUtils.formatMoney(tx.amountMinor, currency)} on ${tx.expenseDateIso})? All dashboard totals, budgets, and charts will recalculate immediately."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(tx.id)
                        pendingDeleteExpense = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed600),
                    modifier = Modifier.testTag("confirm_delete_tx_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteExpense = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class SummaryCardData(
    val title: String,
    val value: String,
    val subtitle: String,
    val accentColor: Color,
    val tag: String
)

@Composable
private fun DashboardMetricCard(
    data: SummaryCardData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag(data.tag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(data.accentColor)
                )
            }
            Text(
                text = data.value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
