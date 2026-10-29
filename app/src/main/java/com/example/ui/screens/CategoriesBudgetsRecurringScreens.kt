package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.example.data.CategoryEntity
import com.example.data.RecurringExpenseEntity
import com.example.domain.BudgetAlertLevel
import com.example.domain.MoneyUtils
import com.example.domain.PaymentMethod
import com.example.domain.RecurrenceFrequency
import com.example.ui.AppPage
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.theme.AlertRed600
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.WarningAmber600
import com.example.ui.theme.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesManagementScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val currency = uiState.currencyCode
    val periodBounds = uiState.dashboardMetrics?.periodBounds
    val periodTotalsByCat = uiState.dashboardMetrics?.categoryBreakdown?.associateBy { it.categoryId }.orEmpty()

    var showArchived by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var parentForSubcategory by remember { mutableStateOf<CategoryEntity?>(null) }

    val topCategories = uiState.categories.filter {
        it.parentCategoryId == null && (showArchived || !it.isArchived)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "12 Standard & Custom Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Period Totals: ${periodBounds?.label ?: uiState.globalPeriod.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = !showArchived },
                        label = { Text("Archived") }
                    )
                    Button(
                        onClick = {
                            parentForSubcategory = null
                            editingCategory = null
                            showCreateDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.testTag("add_custom_category_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Category")
                    }
                }
            }
        }

        topCategories.forEach { cat ->
            val subcats = uiState.categories.filter { it.parentCategoryId == cat.id }
            val breakdown = periodTotalsByCat[cat.id]
            val catSpent = breakdown?.totalMinor ?: 0L
            val txCount = breakdown?.transactionCount ?: 0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(cat.colorHex).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.name.take(1).uppercase(),
                                    color = parseHexColor(cat.colorHex),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = if (cat.isArchived) "${cat.name} (Archived)" else cat.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$txCount tx in period • Budget: ${cat.monthlyBudgetMinor?.let { MoneyUtils.formatMoney(it, currency) } ?: "Unset"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyUtils.formatMoney(catSpent, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600
                            )
                            Row {
                                IconButton(onClick = {
                                    parentForSubcategory = cat
                                    editingCategory = null
                                    showCreateDialog = true
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Subcategory", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    parentForSubcategory = null
                                    editingCategory = cat
                                    showCreateDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename / Edit Category", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.toggleArchiveCategory(cat) }) {
                                    Icon(
                                        imageVector = if (cat.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = "Archive Category",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Subcategories chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subcats.forEach { sub ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.updateHistoryFilter {
                                it.copy(selectedCategoryIds = setOf(cat.id), page = 1)
                            }
                            viewModel.navigateTo(AppPage.TRANSACTIONS)
                        }
                    ) {
                        Text("View ${cat.name} Transactions →")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var nameInput by remember(editingCategory) { mutableStateOf(editingCategory?.name ?: "") }
        var colorInput by remember(editingCategory, parentForSubcategory) {
            mutableStateOf(editingCategory?.colorHex ?: parentForSubcategory?.colorHex ?: "#10B981")
        }
        var budgetInput by remember(editingCategory) {
            mutableStateOf(
                editingCategory?.monthlyBudgetMinor?.let { MoneyUtils.toBigDecimal(it).toPlainString() } ?: "5000"
            )
        }
        val colorChoices = listOf("#10B981", "#06B6D4", "#3B82F6", "#6366F1", "#8B5CF6", "#EC4899", "#F59E0B", "#EF4444")

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    when {
                        editingCategory != null -> "Edit Category '${editingCategory!!.name}'"
                        parentForSubcategory != null -> "Add Subcategory to ${parentForSubcategory!!.name}"
                        else -> "Create Custom Category"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (parentForSubcategory == null) {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            label = { Text("Monthly Budget Limit (${uiState.currencyCode})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Choose Category Color", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorChoices.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(hex))
                                        .clickable { colorInput = hex }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createOrUpdateCategory(
                        existingId = editingCategory?.id,
                        name = nameInput,
                        parentCategoryId = parentForSubcategory?.id ?: editingCategory?.parentCategoryId,
                        colorHex = colorInput,
                        monthlyBudgetText = if (parentForSubcategory == null) budgetInput else ""
                    )
                    showCreateDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BudgetManagementScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val currency = uiState.currencyCode
    val overallBudgetMinor = uiState.profile?.overallMonthlyBudgetMinor ?: 50_000_00L
    var overallBudgetInput by remember(overallBudgetMinor) {
        mutableStateOf(MoneyUtils.toBigDecimal(overallBudgetMinor).toPlainString())
    }
    var editingCatBudget by remember { mutableStateOf<CategoryEntity?>(null) }
    var catBudgetInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Overall Monthly Budget Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Overall Monthly Budget Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Receive automatic in-app threshold warnings at 80% (Warning), 90% (Critical), and 100% (Exceeded).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = overallBudgetInput,
                        onValueChange = { overallBudgetInput = it },
                        label = { Text("Monthly Limit ($currency)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("overall_budget_input")
                    )
                    Button(
                        onClick = { viewModel.updateOverallMonthlyBudget(overallBudgetInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.height(52.dp).testTag("save_overall_budget_button")
                    ) {
                        Text("Update Limit")
                    }
                }
            }
        }

        // Budget Utilization & Threshold Cards
        Text(
            text = "Current Month Budget Utilization vs Actual Spending",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        uiState.budgetStatuses.forEach { status ->
            val ratio = (status.utilizationPercent.toFloat() / 100f).coerceIn(0f, 1f)
            val barColor = when (status.alertLevel) {
                BudgetAlertLevel.EXCEEDED_100 -> AlertRed600
                BudgetAlertLevel.CRITICAL_90 -> AlertRed600
                BudgetAlertLevel.WARNING_80 -> WarningAmber600
                BudgetAlertLevel.NORMAL -> Emerald500
            }
            val badgeText = when (status.alertLevel) {
                BudgetAlertLevel.EXCEEDED_100 -> "100%+ EXCEEDED"
                BudgetAlertLevel.CRITICAL_90 -> "90%+ CRITICAL"
                BudgetAlertLevel.WARNING_80 -> "80%+ WARNING"
                BudgetAlertLevel.NORMAL -> "ON TRACK"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = status.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Spent: ${MoneyUtils.formatMoney(status.spentMinor, currency)} of ${MoneyUtils.formatMoney(status.budgetLimitMinor, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = barColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${status.utilizationPercent.toPlainString()}% • $badgeText",
                                    color = barColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (status.categoryId != null) {
                                val catObj = uiState.categories.firstOrNull { it.id == status.categoryId }
                                if (catObj != null) {
                                    IconButton(onClick = {
                                        editingCatBudget = catObj
                                        catBudgetInput = MoneyUtils.toBigDecimal(status.budgetLimitMinor).toPlainString()
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Category Budget", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
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
                                .background(barColor)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (status.remainingMinor >= 0L) {
                                "Remaining: ${MoneyUtils.formatMoney(status.remainingMinor, currency)}"
                            } else {
                                "Over Budget by: ${MoneyUtils.formatMoney(-status.remainingMinor, currency)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (status.remainingMinor < 0L) AlertRed600 else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Limit: ${MoneyUtils.formatMoney(status.budgetLimitMinor, currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    editingCatBudget?.let { cat ->
        AlertDialog(
            onDismissRequest = { editingCatBudget = null },
            title = { Text("Set Monthly Budget for ${cat.name}") },
            text = {
                OutlinedTextField(
                    value = catBudgetInput,
                    onValueChange = { catBudgetInput = it },
                    label = { Text("Monthly Limit ($currency)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateCategoryMonthlyBudget(cat, catBudgetInput)
                    editingCatBudget = null
                }) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCatBudget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringExpensesScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val currency = uiState.currencyCode
    val topCategories = uiState.categories.filter { it.parentCategoryId == null && !it.isArchived }
    val catMap = uiState.categories.associateBy { it.id }

    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringExpenseEntity?>(null) }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recurring Expenses & Subscriptions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Supports Daily, Weekly, Monthly & Yearly schedules with month-end, leap-year, and duplicate protection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingItem = null
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    modifier = Modifier.testTag("add_recurring_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Schedule")
                }
            }
        }

        uiState.recurringExpenses.forEach { item ->
            val cat = catMap[item.categoryId]
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${cat?.name ?: "Category"} • ${item.frequency} • ${item.paymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Next Due: ${item.nextDueDateIso} • Last Recorded: ${item.lastGeneratedDateIso ?: "Not yet"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyUtils.formatMoney(item.amountMinor, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (item.status) {
                                    "ACTIVE" -> Emerald500.copy(alpha = 0.16f)
                                    "PAUSED" -> WarningAmber600.copy(alpha = 0.16f)
                                    else -> AlertRed600.copy(alpha = 0.16f)
                                }
                            ) {
                                Text(
                                    text = "SCHEDULED: ${item.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.recordRecurringOccurrenceNow(item) },
                            enabled = item.status == "ACTIVE"
                        ) {
                            Text("Record Due Occurrence")
                        }
                        Row {
                            IconButton(onClick = {
                                editingItem = item
                                showDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Schedule", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.toggleRecurringPause(item) }) {
                                Icon(
                                    imageVector = if (item.active) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Pause or Resume",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.cancelRecurringSchedule(item) }) {
                                Icon(Icons.Default.Cancel, contentDescription = "Cancel Schedule", tint = AlertRed600, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var titleInput by remember(editingItem) { mutableStateOf(editingItem?.title ?: "") }
        var amountInput by remember(editingItem) {
            mutableStateOf(
                editingItem?.let { MoneyUtils.toBigDecimal(it.amountMinor).toPlainString() } ?: ""
            )
        }
        var selectedCatId by remember(editingItem) {
            mutableStateOf(editingItem?.categoryId ?: topCategories.firstOrNull()?.id.orEmpty())
        }
        var selectedFreq by remember(editingItem) {
            mutableStateOf(
                runCatching { RecurrenceFrequency.valueOf(editingItem?.frequency ?: "MONTHLY") }
                    .getOrDefault(RecurrenceFrequency.MONTHLY)
            )
        }
        var nextDueInput by remember(editingItem) {
            mutableStateOf(editingItem?.nextDueDateIso ?: "2026-11-05")
        }
        var paymentMethodInput by remember(editingItem) {
            mutableStateOf(editingItem?.paymentMethod ?: PaymentMethod.UPI.label)
        }
        var merchantInput by remember(editingItem) {
            mutableStateOf(editingItem?.merchant ?: "")
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingItem != null) "Edit Recurring Expense" else "Add Recurring Expense") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Title * (e.g., House Rent, Netflix)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount ($currency) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nextDueInput,
                        onValueChange = { nextDueInput = it },
                        label = { Text("Next Due Date (YYYY-MM-DD) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = merchantInput,
                        onValueChange = { merchantInput = it },
                        label = { Text("Merchant / Biller") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Frequency", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurrenceFrequency.entries.forEach { f ->
                            FilterChip(
                                selected = selectedFreq == f,
                                onClick = { selectedFreq = f },
                                label = { Text(f.label) }
                            )
                        }
                    }
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        topCategories.take(8).forEach { c ->
                            FilterChip(
                                selected = selectedCatId == c.id,
                                onClick = { selectedCatId = c.id },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveRecurringSchedule(
                        existingId = editingItem?.id,
                        title = titleInput,
                        amountText = amountInput,
                        categoryId = selectedCatId,
                        paymentMethod = paymentMethodInput,
                        merchant = merchantInput,
                        frequency = selectedFreq,
                        nextDueDateIso = nextDueInput
                    )
                    showDialog = false
                }) {
                    Text("Save Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
