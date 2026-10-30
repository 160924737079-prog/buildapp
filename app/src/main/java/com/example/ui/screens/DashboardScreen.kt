package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.ExpenseEntity
import com.example.domain.CurrencyOption
import com.example.domain.DateRangeEngine
import com.example.domain.FinancialCalculator
import com.example.domain.MoneyUtils
import com.example.domain.PaymentMethod
import com.example.domain.ThemePreference
import com.example.ui.AppPage
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.CategoryVerticalBarChart
import com.example.ui.components.DailyTrendLineChart
import com.example.ui.theme.AlertRed600
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.parseHexColor
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Automatically sync expense date when the system day changes (both on resume and while open)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncWithCurrentSystemDay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.syncWithCurrentSystemDay()
            delay(30_000L)
        }
    }

    val currency = uiState.currencyCode
    val currencySymbol = CurrencyOption.fromCode(currency).symbol
    val activeDateIso = uiState.referenceDateIso
    val systemTodayIso = DateRangeEngine.todayIso(uiState.profile?.timezone ?: "Asia/Kolkata")
    val isToday = activeDateIso == systemTodayIso

    var expenseAmount by remember { mutableStateOf("") }
    var expenseTitle by remember { mutableStateOf("") }
    // Automatically updates the form's date whenever the active day changes
    var expenseDateInput by remember(activeDateIso) { mutableStateOf(activeDateIso) }
    var showOnlyActiveDayExpenses by remember { mutableStateOf(false) }

    // Live preview expense when user enters an amount in Add Expense
    val typedAmountMinor = MoneyUtils.toMinorUnits(expenseAmount)
    val effectiveDateForPreview = if (DateRangeEngine.isValidIsoDate(expenseDateInput.trim())) {
        expenseDateInput.trim()
    } else {
        activeDateIso
    }
    val previewExpense = if (typedAmountMinor != null && typedAmountMinor > 0L) {
        val previewLabel = expenseTitle.trim().ifEmpty {
            "Draft (${MoneyUtils.formatMoney(typedAmountMinor, currency)})"
        }
        ExpenseEntity(
            id = "live_draft_preview",
            userId = uiState.session.userId,
            categoryId = "exp_live_draft_preview",
            title = previewLabel,
            amountMinor = typedAmountMinor,
            currency = currency,
            expenseDateIso = effectiveDateForPreview,
            paymentMethod = PaymentMethod.UPI.label
        )
    } else null

    val expensesWithLiveAmount = if (previewExpense != null) {
        listOf(previewExpense) + uiState.expenses
    } else {
        uiState.expenses
    }

    val liveMetrics = FinancialCalculator.computeDashboardMetrics(
        allExpenses = expensesWithLiveAmount,
        categories = emptyList(),
        period = uiState.globalPeriod,
        referenceDateIso = activeDateIso,
        overallMonthlyBudgetMinor = uiState.profile?.overallMonthlyBudgetMinor ?: 50_000_00L,
        customStartIso = uiState.customStartDateIso,
        customEndIso = uiState.customEndDateIso
    )

    val selectedDayExpenses = uiState.expenses.filter { it.expenseDateIso == activeDateIso }
    val selectedDayTotalMinor = selectedDayExpenses.sumOf { it.amountMinor } +
        (if (previewExpense?.expenseDateIso == activeDateIso) previewExpense.amountMinor else 0L)

    val displayedExpenses = if (showOnlyActiveDayExpenses) {
        selectedDayExpenses
    } else {
        uiState.expenses
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Quick Light Mode / Dark Mode Switcher Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isDark = uiState.themePreference == ThemePreference.DARK
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFFBBF24) else Emerald600,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Appearance Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.themePreference == ThemePreference.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemePreference.LIGHT) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Light Mode") },
                        modifier = Modifier.testTag("dashboard_light_mode_chip")
                    )
                    FilterChip(
                        selected = uiState.themePreference == ThemePreference.DARK,
                        onClick = { viewModel.setThemeMode(ThemePreference.DARK) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Dark Mode") },
                        modifier = Modifier.testTag("dashboard_dark_mode_chip")
                    )
                }
            }
        }

        // 1. Live Date Selector & Clean Totals Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Navy900)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Active Expense Date",
                            tint = Emerald500,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isToday) {
                                    "Today • ${DateRangeEngine.formatDisplayDate(activeDateIso, uiState.dateFormatPattern)} (${DateRangeEngine.dayOfWeekName(activeDateIso)})"
                                } else {
                                    "${DateRangeEngine.formatDisplayDate(activeDateIso, uiState.dateFormatPattern)} (${DateRangeEngine.dayOfWeekName(activeDateIso)})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Expense date updates automatically as the day changes",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    if (!isToday) {
                        TextButton(
                            onClick = { viewModel.jumpToToday() },
                            modifier = Modifier.testTag("jump_to_today_button")
                        ) {
                            Text("Today", color = Emerald500, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Day switcher bar (Prev Day / Today / Next Day)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.stepReferenceDate(-1) },
                        modifier = Modifier.weight(1f).testTag("prev_day_button")
                    ) {
                        Text("← Prev Day", color = Color.White)
                    }
                    Button(
                        onClick = { viewModel.jumpToToday() },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        modifier = Modifier.weight(1f).testTag("today_date_button")
                    ) {
                        Text(activeDateIso, color = Navy900, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.stepReferenceDate(1) },
                        modifier = Modifier.weight(1f).testTag("next_day_button")
                    ) {
                        Text("Next Day →", color = Color.White)
                    }
                }

                // Clean 2-Card Summary Row: Selected Day Total & This Month Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f).testTag("card_today_expenses")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isToday) "Today's Spending" else "Day's Spending",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = MoneyUtils.formatMoney(selectedDayTotalMinor, currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = Emerald500
                            )
                            Text(
                                text = "${selectedDayExpenses.size} item(s) on $activeDateIso",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f).testTag("card_total_expenses")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "This Month's Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = MoneyUtils.formatMoney(liveMetrics.thisMonthExpensesMinor, currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${uiState.expenses.size} total transaction(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // 2. Simple Add Expense Card (Amount-First, No Preset Categories)
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
                Text(
                    text = "Add Expense",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter an expense amount below — visualizations are generated automatically from your amounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = expenseAmount,
                        onValueChange = { expenseAmount = it },
                        label = { Text("Amount ($currencySymbol)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dashboard_instant_amount_input")
                    )
                    OutlinedTextField(
                        value = expenseTitle,
                        onValueChange = { expenseTitle = it },
                        label = { Text("Note / Label (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f).testTag("dashboard_instant_title_input")
                    )
                }

                // Date + Add Expense Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = expenseDateInput,
                        onValueChange = {
                            expenseDateInput = it
                            if (DateRangeEngine.isValidIsoDate(it.trim())) {
                                viewModel.setReferenceDate(it.trim())
                            }
                        },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("dashboard_expense_date_input")
                    )

                    Button(
                        onClick = {
                            val validDate = if (DateRangeEngine.isValidIsoDate(expenseDateInput.trim())) {
                                expenseDateInput.trim()
                            } else {
                                activeDateIso
                            }
                            viewModel.saveExpense(
                                title = expenseTitle,
                                amountText = expenseAmount,
                                categoryId = "",
                                subcategoryId = null,
                                expenseDateIso = validDate,
                                paymentMethod = PaymentMethod.UPI.label,
                                merchant = "",
                                description = "",
                                notes = "",
                                tags = "",
                                receiptUri = null,
                                onSuccess = {
                                    expenseTitle = ""
                                    expenseAmount = ""
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.height(54.dp).testTag("dashboard_instant_save_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Expense", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Visualizations Generated Directly from Expense Amounts
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
                        text = "Expense Amount Visualization (Donut)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { viewModel.navigateTo(AppPage.REPORTS) },
                        modifier = Modifier.testTag("dashboard_view_charts_button")
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("More Charts →")
                    }
                }
                CategoryDonutChart(
                    items = liveMetrics.categoryBreakdown,
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
                    text = "Expense Amount Comparison (Bar Chart)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CategoryVerticalBarChart(
                    items = liveMetrics.categoryBreakdown,
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
                    text = "Day-by-Day Spending Visualization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                DailyTrendLineChart(
                    dailyItems = liveMetrics.dailyTrend,
                    currencyCode = currency
                )
            }
        }

        // 4. Direct Expense List with 1-Tap Delete
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
                    Text(
                        text = "Your Expenses (${displayedExpenses.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !showOnlyActiveDayExpenses,
                            onClick = { showOnlyActiveDayExpenses = false },
                            label = { Text("All (${uiState.expenses.size})") }
                        )
                        FilterChip(
                            selected = showOnlyActiveDayExpenses,
                            onClick = { showOnlyActiveDayExpenses = true },
                            label = { Text(activeDateIso) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald500.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                if (displayedExpenses.isEmpty()) {
                    Text(
                        text = "No expenses recorded yet. Enter an amount in Add Expense above and tap 'Add Expense'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    displayedExpenses.forEachIndexed { idx, tx ->
                        val itemColorHex = FinancialCalculator.colorForKey(tx.categoryId.ifBlank { tx.id }, idx)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(vertical = 8.dp, horizontal = 12.dp)
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
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(itemColorHex).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currencySymbol,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = parseHexColor(itemColorHex),
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
                                        text = DateRangeEngine.formatDisplayDate(tx.expenseDateIso, uiState.dateFormatPattern),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = MoneyUtils.formatMoney(tx.amountMinor, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { viewModel.deleteExpense(tx.id) },
                                    modifier = Modifier.size(36.dp).testTag("dashboard_delete_tx_${tx.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete ${tx.title}",
                                        tint = AlertRed600,
                                        modifier = Modifier.size(20.dp)
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
