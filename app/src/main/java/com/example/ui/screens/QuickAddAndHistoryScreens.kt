package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ExpenseEntity
import com.example.domain.CurrencyOption
import com.example.domain.DateRangeEngine
import com.example.domain.FinancialCalculator
import com.example.domain.GroupByMode
import com.example.domain.MoneyUtils
import com.example.domain.PaymentMethod
import com.example.domain.SortField
import com.example.ui.AppPage
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.theme.AlertRed600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddExpenseScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val editing = uiState.editingExpense
    val topCategories = uiState.categories.filter { it.parentCategoryId == null && !it.isArchived }
    val defaultCatId = editing?.categoryId ?: topCategories.firstOrNull()?.id.orEmpty()

    var title by remember(editing) { mutableStateOf(editing?.title ?: "") }
    var amountText by remember(editing) {
        mutableStateOf(
            editing?.let { MoneyUtils.toBigDecimal(it.amountMinor).toPlainString() } ?: ""
        )
    }
    var selectedCategoryId by remember(editing, topCategories.size) {
        mutableStateOf(defaultCatId)
    }
    val subcategories = uiState.categories.filter { it.parentCategoryId == selectedCategoryId && !it.isArchived }
    var selectedSubcategoryId by remember(editing, selectedCategoryId) {
        mutableStateOf(editing?.subcategoryId ?: subcategories.firstOrNull()?.id)
    }
    var expenseDateIso by remember(editing) {
        mutableStateOf(editing?.expenseDateIso ?: uiState.referenceDateIso)
    }
    var paymentMethod by remember(editing) {
        mutableStateOf(editing?.paymentMethod ?: PaymentMethod.UPI.label)
    }
    var merchant by remember(editing) { mutableStateOf(editing?.merchant ?: "") }
    var description by remember(editing) { mutableStateOf(editing?.description ?: "") }
    var notes by remember(editing) { mutableStateOf(editing?.notes ?: "") }
    var tags by remember(editing) { mutableStateOf(editing?.tags ?: "") }
    var receiptUri by remember(editing) { mutableStateOf(editing?.receiptUri) }

    var showInlineCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Zero-permission Android Photo Picker for receipt attachment
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            receiptUri = uri.toString()
        }
    }

    val currencySymbol = CurrencyOption.fromCode(uiState.currencyCode).symbol

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    Text(
                        text = if (editing != null) "Edit Expense Record" else "Quick Add Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            title = "Monthly Groceries"
                            amountText = "1500"
                            val grocCat = topCategories.firstOrNull { it.name.contains("Grocer", true) }
                            if (grocCat != null) selectedCategoryId = grocCat.id
                            expenseDateIso = "2026-10-29"
                            paymentMethod = "UPI"
                            merchant = "FreshMart Store"
                        },
                        modifier = Modifier.testTag("quick_add_fill_example_button")
                    ) {
                        Text("Fill Sample (₹1,500)")
                    }
                }

                // Required Fields
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title * (e.g., Monthly Groceries)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount ($currencySymbol) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("expense_amount_input")
                    )
                    OutlinedTextField(
                        value = expenseDateIso,
                        onValueChange = { expenseDateIso = it },
                        label = { Text("Expense Date (YYYY-MM-DD) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("expense_date_input")
                    )
                }

                // Quick Date Presets
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = expenseDateIso == uiState.referenceDateIso,
                        onClick = { expenseDateIso = uiState.referenceDateIso },
                        label = { Text("Today (${uiState.referenceDateIso})") }
                    )
                    val yesterdayIso = DateRangeEngine.addDays(uiState.referenceDateIso, -1)
                    FilterChip(
                        selected = expenseDateIso == yesterdayIso,
                        onClick = { expenseDateIso = yesterdayIso },
                        label = { Text("Yesterday") }
                    )
                }

                // Category Selector + Inline Category Creation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category *", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showInlineCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Category")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = {
                                selectedCategoryId = cat.id
                                val firstSub = uiState.categories.firstOrNull { it.parentCategoryId == cat.id }
                                selectedSubcategoryId = firstSub?.id
                            },
                            label = { Text(cat.name) },
                            modifier = Modifier.testTag("cat_chip_${cat.name.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                // Subcategory Selector
                if (subcategories.isNotEmpty()) {
                    Text("Subcategory *", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subcategories.forEach { sub ->
                            FilterChip(
                                selected = selectedSubcategoryId == sub.id,
                                onClick = { selectedSubcategoryId = sub.id },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }

                // Payment Method
                Text("Payment Method *", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { pm ->
                        FilterChip(
                            selected = paymentMethod.equals(pm.label, ignoreCase = true),
                            onClick = { paymentMethod = pm.label },
                            label = { Text(pm.label) }
                        )
                    }
                }

                // Optional Fields
                Text(
                    text = "Optional Details & Receipt Attachment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant / Shop Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("expense_merchant_input")
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (receiptUri == null) "Attach Receipt Photo" else "Change Receipt Photo")
                    }
                    if (receiptUri != null) {
                        Text(
                            text = "Receipt attached ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald600,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveExpense(
                                title = title,
                                amountText = amountText,
                                categoryId = selectedCategoryId,
                                subcategoryId = selectedSubcategoryId,
                                expenseDateIso = expenseDateIso,
                                paymentMethod = paymentMethod,
                                merchant = merchant,
                                description = description,
                                notes = notes,
                                tags = tags,
                                receiptUri = receiptUri,
                                onSuccess = {
                                    viewModel.navigateTo(AppPage.DASHBOARD)
                                }
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("save_expense_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                    ) {
                        Text(
                            text = if (editing != null) "Update Expense" else "Save Expense",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (editing != null) {
                        Button(
                            onClick = {
                                viewModel.deleteExpense(editing.id)
                                viewModel.cancelEditingExpense()
                                viewModel.navigateTo(AppPage.DASHBOARD)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed600),
                            modifier = Modifier.height(48.dp).testTag("delete_editing_expense_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Expense")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelEditingExpense()
                            viewModel.navigateTo(AppPage.DASHBOARD)
                        },
                        modifier = Modifier.height(48.dp).testTag("cancel_expense_button")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    if (showInlineCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showInlineCategoryDialog = false },
            title = { Text("Create Category Inline") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        viewModel.createOrUpdateCategory(
                            name = newCategoryName,
                            onCreatedId = { newId ->
                                selectedCategoryId = newId
                                newCategoryName = ""
                                showInlineCategoryDialog = false
                            }
                        )
                    }
                }) {
                    Text("Create & Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInlineCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionHistoryScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    val filter = uiState.historyFilter
    val currency = uiState.currencyCode
    val catMap = uiState.categories.associateBy { it.id }
    val topCategories = uiState.categories.filter { it.parentCategoryId == null }

    var showFiltersPanel by remember { mutableStateOf(false) }
    var minAmtInput by remember(filter.minAmountMinor) {
        mutableStateOf(filter.minAmountMinor?.let { MoneyUtils.toBigDecimal(it).toPlainString() } ?: "")
    }
    var maxAmtInput by remember(filter.maxAmountMinor) {
        mutableStateOf(filter.maxAmountMinor?.let { MoneyUtils.toBigDecimal(it).toPlainString() } ?: "")
    }
    var startDateInput by remember(filter.startDateIso) { mutableStateOf(filter.startDateIso ?: "") }
    var endDateInput by remember(filter.endDateIso) { mutableStateOf(filter.endDateIso ?: "") }

    var viewingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var confirmingDeleteExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    val filteredList = FinancialCalculator.filterQualifyingExpenses(
        allTransactions = uiState.expenses,
        startDateIso = filter.startDateIso?.takeIf { it.isNotBlank() },
        endDateIso = filter.endDateIso?.takeIf { it.isNotBlank() },
        categoryIds = filter.selectedCategoryIds,
        subcategoryIds = filter.selectedSubcategoryIds,
        paymentMethods = filter.selectedPaymentMethods,
        minAmountMinor = filter.minAmountMinor,
        maxAmountMinor = filter.maxAmountMinor,
        searchQuery = filter.searchQuery
    )

    val sortedList = when (filter.sortField) {
        SortField.DATE_DESC -> filteredList.sortedWith(compareByDescending<ExpenseEntity> { it.expenseDateIso }.thenByDescending { it.createdAt })
        SortField.DATE_ASC -> filteredList.sortedWith(compareBy<ExpenseEntity> { it.expenseDateIso }.thenBy { it.createdAt })
        SortField.AMOUNT_DESC -> filteredList.sortedByDescending { it.amountMinor }
        SortField.AMOUNT_ASC -> filteredList.sortedBy { it.amountMinor }
    }

    val filteredTotalMinor = sortedList.sumOf { it.amountMinor }
    val totalPages = ((sortedList.size + filter.pageSize - 1) / filter.pageSize).coerceAtLeast(1)
    val currentPage = filter.page.coerceIn(1, totalPages)
    val pagedItems = sortedList.drop((currentPage - 1) * filter.pageSize).take(filter.pageSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Filter Controls Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filter.searchQuery,
                        onValueChange = { q ->
                            viewModel.updateHistoryFilter { it.copy(searchQuery = q, page = 1) }
                        },
                        label = { Text("Search title, merchant, notes, tags...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("history_search_input")
                    )
                    OutlinedButton(
                        onClick = { showFiltersPanel = !showFiltersPanel },
                        modifier = Modifier.testTag("toggle_filters_button")
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showFiltersPanel) "Hide" else "Filters")
                    }
                }

                // Sort & GroupBy Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sort:", style = MaterialTheme.typography.labelSmall)
                    SortField.entries.forEach { sf ->
                        FilterChip(
                            selected = filter.sortField == sf,
                            onClick = { viewModel.updateHistoryFilter { it.copy(sortField = sf) } },
                            label = { Text(sf.label) }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Group:", style = MaterialTheme.typography.labelSmall)
                    GroupByMode.entries.forEach { gm ->
                        FilterChip(
                            selected = filter.groupByMode == gm,
                            onClick = { viewModel.updateHistoryFilter { it.copy(groupByMode = gm) } },
                            label = { Text(gm.label) }
                        )
                    }
                }

                if (showFiltersPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Filter by Category", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            topCategories.forEach { cat ->
                                val isSel = cat.id in filter.selectedCategoryIds
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        viewModel.updateHistoryFilter { st ->
                                            val next = if (isSel) st.selectedCategoryIds - cat.id else st.selectedCategoryIds + cat.id
                                            st.copy(selectedCategoryIds = next, page = 1)
                                        }
                                    },
                                    label = { Text(cat.name) }
                                )
                            }
                        }

                        Text("Filter by Payment Method", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaymentMethod.entries.forEach { pm ->
                                val isSel = pm.label in filter.selectedPaymentMethods
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        viewModel.updateHistoryFilter { st ->
                                            val next = if (isSel) st.selectedPaymentMethods - pm.label else st.selectedPaymentMethods + pm.label
                                            st.copy(selectedPaymentMethods = next, page = 1)
                                        }
                                    },
                                    label = { Text(pm.label) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minAmtInput,
                                onValueChange = { minAmtInput = it },
                                label = { Text("Min Amount") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxAmtInput,
                                onValueChange = { maxAmtInput = it },
                                label = { Text("Max Amount") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startDateInput,
                                onValueChange = { startDateInput = it },
                                label = { Text("Start Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endDateInput,
                                onValueChange = { endDateInput = it },
                                label = { Text("End Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.updateHistoryFilter {
                                        it.copy(
                                            minAmountMinor = MoneyUtils.toMinorUnitsAllowZero(minAmtInput),
                                            maxAmountMinor = MoneyUtils.toMinorUnitsAllowZero(maxAmtInput),
                                            startDateIso = startDateInput.trim().takeIf { s -> DateRangeEngine.isValidIsoDate(s) },
                                            endDateIso = endDateInput.trim().takeIf { s -> DateRangeEngine.isValidIsoDate(s) },
                                            page = 1
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                            ) {
                                Text("Apply Range & Amount Filters")
                            }
                            OutlinedButton(
                                onClick = {
                                    minAmtInput = ""
                                    maxAmtInput = ""
                                    startDateInput = ""
                                    endDateInput = ""
                                    viewModel.resetHistoryFilters()
                                },
                                modifier = Modifier.testTag("reset_filters_button")
                            ) {
                                Text("Reset All Filters")
                            }
                        }
                    }
                }

                // Filtered Summary Banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filtered Transactions: ${sortedList.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Filtered Total: ${MoneyUtils.formatMoney(filteredTotalMinor, currency)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600,
                            modifier = Modifier.testTag("history_filtered_total")
                        )
                    }
                }
            }
        }

        // Grouped or Ungrouped Transaction List
        if (pagedItems.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No transactions match the active filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val groupedMap: Map<String, List<ExpenseEntity>> = when (filter.groupByMode) {
                GroupByMode.NONE -> mapOf("All Matching Transactions" to pagedItems)
                GroupByMode.DAY -> pagedItems.groupBy {
                    "${DateRangeEngine.formatDisplayDate(it.expenseDateIso, uiState.dateFormatPattern)} (${DateRangeEngine.dayOfWeekName(it.expenseDateIso)})"
                }
                GroupByMode.WEEK -> pagedItems.groupBy {
                    DateRangeEngine.weekBoundsContaining(it.expenseDateIso).label
                }
                GroupByMode.MONTH -> pagedItems.groupBy {
                    DateRangeEngine.formatDisplayDate("${it.expenseDateIso.take(7)}-01", "MMMM yyyy")
                }
            }

            groupedMap.forEach { (groupHeader, groupItems) ->
                val groupSubtotal = groupItems.sumOf { it.amountMinor }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$groupHeader (${groupItems.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Subtotal: ${MoneyUtils.formatMoney(groupSubtotal, currency)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = Emerald600
                            )
                        }

                        groupItems.forEach { tx ->
                            val cat = catMap[tx.categoryId]
                            val sub = tx.subcategoryId?.let { catMap[it] }
                            val dayName = DateRangeEngine.dayOfWeekName(tx.expenseDateIso)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(parseHexColor(cat?.colorHex ?: "#10B981").copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (cat?.name ?: "E").take(1),
                                                color = parseHexColor(cat?.colorHex ?: "#10B981"),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = tx.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${tx.expenseDateIso} ($dayName) • ${cat?.name ?: "Uncategorized"}${sub?.let { " › ${it.name}" } ?: ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Method: ${tx.paymentMethod}${if (tx.merchant.isNotBlank()) " • Merchant: ${tx.merchant}" else ""}${if (tx.notes.isNotBlank()) " • Note: ${tx.notes}" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = MoneyUtils.formatMoney(tx.amountMinor, currency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Action Buttons: View, Edit, Duplicate, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { viewingExpense = tx }) {
                                        Icon(Icons.Default.Visibility, contentDescription = "View Details", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { viewModel.startEditingExpense(tx) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Expense", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { viewModel.duplicateExpense(tx) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Expense", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { confirmingDeleteExpense = tx }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = AlertRed600, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pagination Bar
            if (totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        enabled = currentPage > 1,
                        onClick = { viewModel.updateHistoryFilter { it.copy(page = currentPage - 1) } }
                    ) {
                        Text("← Previous")
                    }
                    Text(
                        text = "Page $currentPage of $totalPages",
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedButton(
                        enabled = currentPage < totalPages,
                        onClick = { viewModel.updateHistoryFilter { it.copy(page = currentPage + 1) } }
                    ) {
                        Text("Next →")
                    }
                }
            }
        }
    }

    // View Transaction Modal
    viewingExpense?.let { tx ->
        val cat = catMap[tx.categoryId]
        val sub = tx.subcategoryId?.let { catMap[it] }
        AlertDialog(
            onDismissRequest = { viewingExpense = null },
            title = { Text(tx.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Amount: ${MoneyUtils.formatMoney(tx.amountMinor, currency)}", fontWeight = FontWeight.Bold)
                    Text("Date: ${tx.expenseDateIso} (${DateRangeEngine.dayOfWeekName(tx.expenseDateIso)})")
                    Text("Category: ${cat?.name ?: "Uncategorized"}")
                    Text("Subcategory: ${sub?.name ?: "None"}")
                    Text("Payment Method: ${tx.paymentMethod}")
                    Text("Merchant: ${tx.merchant.ifBlank { "-" }}")
                    Text("Description: ${tx.description.ifBlank { "-" }}")
                    Text("Notes: ${tx.notes.ifBlank { "-" }}")
                    Text("Tags: ${tx.tags.ifBlank { "-" }}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val toEdit = tx
                    viewingExpense = null
                    viewModel.startEditingExpense(toEdit)
                }) {
                    Text("Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingExpense = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete Confirmation Modal
    confirmingDeleteExpense?.let { tx ->
        AlertDialog(
            onDismissRequest = { confirmingDeleteExpense = null },
            title = { Text("Confirm Delete Transaction") },
            text = {
                Text(
                    "Are you sure you want to delete '${tx.title}' (${MoneyUtils.formatMoney(tx.amountMinor, currency)} on ${tx.expenseDateIso})? All affected totals and charts will be updated immediately."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(tx.id)
                        confirmingDeleteExpense = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed600)
                ) {
                    Text("Delete Transaction")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteExpense = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
