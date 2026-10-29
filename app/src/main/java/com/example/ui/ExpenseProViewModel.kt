package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AuthResult
import com.example.data.AuthSession
import com.example.data.BudgetEntity
import com.example.data.CategoryEntity
import com.example.data.ExpenseEntity
import com.example.data.ExpenseProRepository
import com.example.data.ProfileEntity
import com.example.data.RecurringExpenseEntity
import com.example.domain.AiInsightResponse
import com.example.domain.AiInsightsEngine
import com.example.domain.ApkBuildArtifact
import com.example.domain.BudgetStatusItem
import com.example.domain.DateRangeEngine
import com.example.domain.DashboardSummaryMetrics
import com.example.domain.ExpenseFilterState
import com.example.domain.ExpenseValidator
import com.example.domain.ExportEngine
import com.example.domain.FinancialCalculator
import com.example.domain.GeneratedExportFile
import com.example.domain.MoneyUtils
import com.example.domain.RecurrenceFrequency
import com.example.domain.ReportingPeriod
import com.example.domain.ThemePreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppPage(val title: String) {
    LANDING("Welcome to ExpensePro AI"),
    LOGIN("Sign In"),
    REGISTER("Create Account"),
    DASHBOARD("Financial Dashboard"),
    QUICK_ADD("Quick Add Expense"),
    TRANSACTIONS("Transaction History"),
    CATEGORIES("Expense Categories"),
    BUDGETS("Budget Management"),
    RECURRING("Recurring Expenses"),
    REPORTS("Reports & Analytics"),
    AI_INSIGHTS("AI Financial Insights"),
    PROFILE("Profile & Settings"),
    EXPORTS("Export & Account")
}

data class ExpenseProUiState(
    val currentPage: AppPage = AppPage.DASHBOARD,
    val pageBackStack: List<AppPage> = emptyList(),
    val session: AuthSession? = null,
    val profile: ProfileEntity? = null,
    val authError: String? = null,
    val statusBannerMessage: String? = null,
    val isSupabaseConfigured: Boolean = false,
    val referenceDateIso: String = "2026-10-29",
    val globalPeriod: ReportingPeriod = ReportingPeriod.THIS_MONTH,
    val customStartDateIso: String = "2026-10-01",
    val customEndDateIso: String = "2026-10-29",
    val categories: List<CategoryEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
    val dashboardMetrics: DashboardSummaryMetrics? = null,
    val budgetStatuses: List<BudgetStatusItem> = emptyList(),
    val historyFilter: ExpenseFilterState = ExpenseFilterState(),
    val editingExpense: ExpenseEntity? = null,
    val aiInsightsHistory: List<AiInsightResponse> = emptyList(),
    val lastGeneratedExport: GeneratedExportFile? = null,
    val isBuildingApk: Boolean = false,
    val apkBuildStepMessage: String? = null,
    val apkBuildArtifact: ApkBuildArtifact? = null
) {
    val currencyCode: String get() = profile?.preferredCurrency ?: "INR"
    val dateFormatPattern: String get() = profile?.dateFormat ?: "dd MMM yyyy"
    val themePreference: ThemePreference
        get() = runCatching { ThemePreference.valueOf(profile?.themeMode ?: "LIGHT") }
            .getOrDefault(ThemePreference.LIGHT)
}

class ExpenseProViewModel(
    private val repository: ExpenseProRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExpenseProUiState(isSupabaseConfigured = repository.isSupabaseConfigured)
    )
    val uiState: StateFlow<ExpenseProUiState> = _uiState.asStateFlow()

    private var userObservationJob: Job? = null

    init {
        viewModelScope.launch {
            val demoProfile = repository.ensureDemoAccountSeeded()
            val demoSession = AuthSession(
                userId = demoProfile.id,
                email = demoProfile.email,
                fullName = demoProfile.fullName,
                accessToken = "demo_session_token",
                isSupabaseCloudConnected = repository.isSupabaseConfigured
            )
            activateSession(demoSession, initialPage = AppPage.DASHBOARD)
        }
    }

    private fun activateSession(session: AuthSession, initialPage: AppPage) {
        userObservationJob?.cancel()
        _uiState.update {
            it.copy(
                session = session,
                currentPage = initialPage,
                pageBackStack = emptyList(),
                authError = null
            )
        }

        userObservationJob = viewModelScope.launch {
            repository.processDueRecurringSchedules(
                userId = session.userId,
                referenceDateIso = _uiState.value.referenceDateIso,
                currencyCode = _uiState.value.currencyCode
            )

            combine(
                repository.observeProfile(session.userId),
                repository.observeCategories(session.userId),
                repository.observeActiveExpenses(session.userId),
                repository.observeBudgets(session.userId),
                repository.observeRecurringExpenses(session.userId)
            ) { profile, categories, expenses, budgets, recurring ->
                 Quintuple(profile, categories, expenses, budgets, recurring)
            }.collectLatest { (profile, categories, expenses, budgets, recurring) ->
                val state = _uiState.value
                val refDate = state.referenceDateIso
                val overallBudget = profile?.overallMonthlyBudgetMinor ?: 50_000_00L
                val metrics = FinancialCalculator.computeDashboardMetrics(
                    allExpenses = expenses,
                    categories = categories,
                    period = state.globalPeriod,
                    referenceDateIso = refDate,
                    overallMonthlyBudgetMinor = overallBudget,
                    customStartIso = state.customStartDateIso,
                    customEndIso = state.customEndDateIso
                )
                val refParts = refDate.split("-")
                val y = refParts.getOrNull(0)?.toIntOrNull() ?: 2026
                val m = refParts.getOrNull(1)?.toIntOrNull() ?: 10
                val monthBounds = DateRangeEngine.monthBounds(y, m)
                val budgetStatuses = FinancialCalculator.evaluateBudgetStatuses(
                    monthBounds = monthBounds,
                    allExpenses = expenses,
                    categories = categories,
                    budgets = budgets,
                    overallMonthlyBudgetMinor = overallBudget
                )
                val initialInsights = if (state.aiInsightsHistory.isEmpty()) {
                    listOf(
                        AiInsightsEngine.answerQuestion(
                            question = "How much did I spend this month?",
                            allExpenses = expenses,
                            categories = categories,
                            recurringExpenses = recurring,
                            referenceDateIso = refDate,
                            currencyCode = profile?.preferredCurrency ?: "INR",
                            overallMonthlyBudgetMinor = overallBudget
                        )
                    )
                } else state.aiInsightsHistory

                _uiState.update {
                    it.copy(
                        profile = profile,
                        categories = categories,
                        expenses = expenses,
                        budgets = budgets,
                        recurringExpenses = recurring,
                        dashboardMetrics = metrics,
                        budgetStatuses = budgetStatuses,
                        aiInsightsHistory = initialInsights
                    )
                }
            }
        }
    }

    fun navigateTo(page: AppPage, clearEditExpense: Boolean = true) {
        _uiState.update { state ->
            if (state.currentPage == page) return@update state
            val newStack = (state.pageBackStack + state.currentPage).takeLast(12)
            state.copy(
                currentPage = page,
                pageBackStack = newStack,
                editingExpense = if (clearEditExpense && page != AppPage.QUICK_ADD) null else state.editingExpense,
                statusBannerMessage = null,
                authError = null
            )
        }
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.pageBackStack
        if (stack.isEmpty()) return false
        val prev = stack.last()
        _uiState.update {
            it.copy(
                currentPage = prev,
                pageBackStack = stack.dropLast(1),
                statusBannerMessage = null
            )
        }
        return true
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBannerMessage = null, authError = null) }
    }

    fun setGlobalReportingPeriod(
        period: ReportingPeriod,
        customStartIso: String? = null,
        customEndIso: String? = null
    ) {
        _uiState.update { state ->
            val newStart = customStartIso ?: state.customStartDateIso
            val newEnd = customEndIso ?: state.customEndDateIso
            val overallBudget = state.profile?.overallMonthlyBudgetMinor ?: 50_000_00L
            val updatedMetrics = FinancialCalculator.computeDashboardMetrics(
                allExpenses = state.expenses,
                categories = state.categories,
                period = period,
                referenceDateIso = state.referenceDateIso,
                overallMonthlyBudgetMinor = overallBudget,
                customStartIso = newStart,
                customEndIso = newEnd
            )
            state.copy(
                globalPeriod = period,
                customStartDateIso = newStart,
                customEndDateIso = newEnd,
                dashboardMetrics = updatedMetrics
            )
        }
    }

    fun setReferenceDate(newDateIso: String) {
        if (!DateRangeEngine.isValidIsoDate(newDateIso)) return
        _uiState.update { state ->
            val overallBudget = state.profile?.overallMonthlyBudgetMinor ?: 50_000_00L
            val updatedMetrics = FinancialCalculator.computeDashboardMetrics(
                allExpenses = state.expenses,
                categories = state.categories,
                period = state.globalPeriod,
                referenceDateIso = newDateIso,
                overallMonthlyBudgetMinor = overallBudget,
                customStartIso = state.customStartDateIso,
                customEndIso = state.customEndDateIso
            )
            state.copy(
                referenceDateIso = newDateIso,
                dashboardMetrics = updatedMetrics
            )
        }
    }

    // Auth Actions
    fun login(email: String, password: String) {
        viewModelScope.launch {
            when (val res = repository.loginUser(email, password)) {
                is AuthResult.Success -> {
                    activateSession(res.session, AppPage.DASHBOARD)
                    _uiState.update { it.copy(statusBannerMessage = res.message) }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(authError = res.message) }
                }
            }
        }
    }

    fun register(
        fullName: String,
        email: String,
        password: String,
        currencyCode: String,
        timezone: String
    ) {
        viewModelScope.launch {
            when (val res = repository.registerUser(fullName, email, password, currencyCode, timezone)) {
                is AuthResult.Success -> {
                    activateSession(res.session, AppPage.DASHBOARD)
                    _uiState.update { it.copy(statusBannerMessage = res.message) }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(authError = res.message) }
                }
            }
        }
    }

    fun resetPassword(email: String, newPassword: String) {
        viewModelScope.launch {
            when (val res = repository.resetPassword(email, newPassword)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(statusBannerMessage = res.message, authError = null) }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(authError = res.message) }
                }
            }
        }
    }

    fun logout() {
        userObservationJob?.cancel()
        _uiState.update {
            it.copy(
                session = null,
                profile = null,
                expenses = emptyList(),
                categories = emptyList(),
                budgets = emptyList(),
                recurringExpenses = emptyList(),
                currentPage = AppPage.LANDING,
                pageBackStack = emptyList(),
                statusBannerMessage = "You have been signed out safely."
            )
        }
    }

    fun loginAsDemoUser() {
        viewModelScope.launch {
            val demo = repository.ensureDemoAccountSeeded()
            activateSession(
                AuthSession(
                    userId = demo.id,
                    email = demo.email,
                    fullName = demo.fullName,
                    accessToken = "demo_token",
                    isSupabaseCloudConnected = repository.isSupabaseConfigured
                ),
                AppPage.DASHBOARD
            )
        }
    }

    // Expense CRUD
    fun startEditingExpense(expense: ExpenseEntity) {
        _uiState.update {
            it.copy(
                editingExpense = expense,
                currentPage = AppPage.QUICK_ADD,
                pageBackStack = (it.pageBackStack + it.currentPage).takeLast(12)
            )
        }
    }

    fun cancelEditingExpense() {
        _uiState.update { it.copy(editingExpense = null) }
    }

    fun saveExpense(
        title: String,
        amountText: String,
        categoryId: String,
        subcategoryId: String?,
        expenseDateIso: String,
        paymentMethod: String,
        merchant: String,
        description: String,
        notes: String,
        tags: String,
        receiptUri: String?,
        onSuccess: () -> Unit = {}
    ) {
        val session = _uiState.value.session ?: return
        val validation = ExpenseValidator.validateExpenseInput(
            title = title,
            amountText = amountText,
            categoryId = categoryId,
            expenseDateIso = expenseDateIso,
            paymentMethod = paymentMethod
        )
        if (!validation.isValid) {
            val msg = validation.errors.values.firstOrNull() ?: "Please check form inputs."
            _uiState.update { it.copy(statusBannerMessage = msg) }
            return
        }

        val amountMinor = MoneyUtils.toMinorUnits(amountText) ?: return
        val editing = _uiState.value.editingExpense
        val entity = ExpenseEntity(
            id = editing?.id ?: "tx_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            userId = session.userId,
            categoryId = categoryId,
            subcategoryId = subcategoryId?.takeIf { it.isNotBlank() },
            title = title.trim(),
            amountMinor = amountMinor,
            currency = _uiState.value.currencyCode,
            expenseDateIso = expenseDateIso,
            paymentMethod = paymentMethod,
            merchant = merchant.trim(),
            description = description.trim(),
            notes = notes.trim(),
            tags = tags.trim(),
            receiptUri = receiptUri,
            transactionType = "EXPENSE",
            status = "ACTIVE",
            createdAt = editing?.createdAt ?: System.currentTimeMillis()
        )

        viewModelScope.launch {
            val result = repository.saveExpense(session.userId, entity, session.accessToken)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            editingExpense = null,
                            statusBannerMessage = if (editing != null) {
                                "Updated '${entity.title}' (${MoneyUtils.formatMoney(amountMinor, it.currencyCode)})"
                            } else {
                                "Saved '${entity.title}' (${MoneyUtils.formatMoney(amountMinor, it.currencyCode)})"
                            }
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusBannerMessage = err.message ?: "Could not save expense.") }
                }
            )
        }
    }

    fun deleteExpense(expenseId: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.deleteExpenseForUser(expenseId, session.userId)
            _uiState.update { it.copy(statusBannerMessage = "Transaction deleted and totals recalculated.") }
        }
    }

    fun duplicateExpense(expense: ExpenseEntity) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.duplicateExpenseForUser(expense, session.userId, _uiState.value.referenceDateIso)
            _uiState.update { it.copy(statusBannerMessage = "Duplicated '${expense.title}'.") }
        }
    }

    // Filter state updates
    fun updateHistoryFilter(transform: (ExpenseFilterState) -> ExpenseFilterState) {
        _uiState.update { it.copy(historyFilter = transform(it.historyFilter)) }
    }

    fun resetHistoryFilters() {
        _uiState.update { it.copy(historyFilter = ExpenseFilterState()) }
    }

    // Categories CRUD
    fun createOrUpdateCategory(
        existingId: String? = null,
        name: String,
        parentCategoryId: String? = null,
        colorHex: String = "#10B981",
        iconName: String = "shopping_bag",
        monthlyBudgetText: String = "",
        onCreatedId: (String) -> Unit = {}
    ) {
        val session = _uiState.value.session ?: return
        if (name.trim().isEmpty()) {
            _uiState.update { it.copy(statusBannerMessage = "Category name cannot be empty.") }
            return
        }
        val budgetMinor = MoneyUtils.toMinorUnitsAllowZero(monthlyBudgetText)
        val catId = existingId ?: "cat_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val category = CategoryEntity(
            id = catId,
            userId = session.userId,
            name = name.trim(),
            parentCategoryId = parentCategoryId,
            colorHex = colorHex,
            iconName = iconName,
            monthlyBudgetMinor = budgetMinor?.takeIf { it > 0L },
            isArchived = false
        )
        viewModelScope.launch {
            repository.saveCategory(session.userId, category)
            _uiState.update { it.copy(statusBannerMessage = "Saved category '${category.name}'.") }
            onCreatedId(catId)
        }
    }

    fun toggleArchiveCategory(category: CategoryEntity) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.saveCategory(
                session.userId,
                category.copy(isArchived = !category.isArchived)
            )
            val action = if (!category.isArchived) "Archived" else "Restored"
            _uiState.update { it.copy(statusBannerMessage = "$action '${category.name}'. Historical transactions preserved.") }
        }
    }

    // Budgets
    fun updateOverallMonthlyBudget(amountText: String) {
        val profile = _uiState.value.profile ?: return
        val minor = MoneyUtils.toMinorUnitsAllowZero(amountText) ?: return
        viewModelScope.launch {
            repository.updateProfile(profile.copy(overallMonthlyBudgetMinor = minor))
            _uiState.update {
                it.copy(statusBannerMessage = "Overall monthly budget updated to ${MoneyUtils.formatMoney(minor, it.currencyCode)}.")
            }
        }
    }

    fun updateCategoryMonthlyBudget(category: CategoryEntity, amountText: String) {
        val session = _uiState.value.session ?: return
        val minor = MoneyUtils.toMinorUnitsAllowZero(amountText) ?: 0L
        viewModelScope.launch {
            repository.saveCategory(
                session.userId,
                category.copy(monthlyBudgetMinor = minor.takeIf { it > 0L })
            )
            val refParts = _uiState.value.referenceDateIso.split("-")
            val y = refParts.getOrNull(0)?.toIntOrNull() ?: 2026
            val m = refParts.getOrNull(1)?.toIntOrNull() ?: 10
            val bounds = DateRangeEngine.monthBounds(y, m)
            repository.saveBudget(
                session.userId,
                BudgetEntity(
                    id = "bgt_${session.userId}_${category.id}",
                    userId = session.userId,
                    categoryId = category.id,
                    amountMinor = minor,
                    startDateIso = bounds.startDateIso,
                    endDateIso = bounds.endDateIso
                )
            )
            _uiState.update {
                it.copy(statusBannerMessage = "Updated '${category.name}' monthly budget to ${MoneyUtils.formatMoney(minor, it.currencyCode)}.")
            }
        }
    }

    // Recurring Expenses
    fun saveRecurringSchedule(
        existingId: String? = null,
        title: String,
        amountText: String,
        categoryId: String,
        paymentMethod: String,
        merchant: String,
        frequency: RecurrenceFrequency,
        nextDueDateIso: String
    ) {
        val session = _uiState.value.session ?: return
        val minor = MoneyUtils.toMinorUnits(amountText)
        if (title.trim().isEmpty() || minor == null || minor <= 0L || !DateRangeEngine.isValidIsoDate(nextDueDateIso)) {
            _uiState.update { it.copy(statusBannerMessage = "Provide a valid title, positive amount, and due date.") }
            return
        }
        val entity = RecurringExpenseEntity(
            id = existingId ?: "rec_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            userId = session.userId,
            categoryId = categoryId,
            title = title.trim(),
            amountMinor = minor,
            paymentMethod = paymentMethod,
            merchant = merchant.trim(),
            frequency = frequency.name,
            nextDueDateIso = nextDueDateIso,
            active = true,
            status = "ACTIVE"
        )
        viewModelScope.launch {
            repository.saveRecurringExpense(session.userId, entity)
            repository.processDueRecurringSchedules(
                userId = session.userId,
                referenceDateIso = _uiState.value.referenceDateIso,
                currencyCode = _uiState.value.currencyCode
            )
            _uiState.update { it.copy(statusBannerMessage = "Saved recurring schedule '${entity.title}'.") }
        }
    }

    fun toggleRecurringPause(item: RecurringExpenseEntity) {
        val session = _uiState.value.session ?: return
        val newActive = !item.active
        val newStatus = if (newActive) "ACTIVE" else "PAUSED"
        viewModelScope.launch {
            repository.saveRecurringExpense(
                session.userId,
                item.copy(active = newActive, status = newStatus)
            )
            _uiState.update { it.copy(statusBannerMessage = "${item.title} is now ${newStatus.lowercase()}.") }
        }
    }

    fun cancelRecurringSchedule(item: RecurringExpenseEntity) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.saveRecurringExpense(
                session.userId,
                item.copy(active = false, status = "CANCELLED")
            )
            _uiState.update { it.copy(statusBannerMessage = "Cancelled recurring schedule '${item.title}'.") }
        }
    }

    fun recordRecurringOccurrenceNow(item: RecurringExpenseEntity) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            val freq = runCatching { RecurrenceFrequency.valueOf(item.frequency) }.getOrDefault(RecurrenceFrequency.MONTHLY)
            val dueDate = item.nextDueDateIso
            val existing = _uiState.value.expenses.any {
                it.recurringSourceId == item.id && it.expenseDateIso == dueDate && it.status == "ACTIVE"
            }
            if (existing) {
                _uiState.update { it.copy(statusBannerMessage = "Occurrence for $dueDate was already recorded (duplicate prevented).") }
                return@launch
            }
            val tx = ExpenseEntity(
                id = "rec_tx_${item.id}_$dueDate",
                userId = session.userId,
                categoryId = item.categoryId,
                subcategoryId = item.subcategoryId,
                title = item.title,
                amountMinor = item.amountMinor,
                currency = _uiState.value.currencyCode,
                expenseDateIso = dueDate,
                paymentMethod = item.paymentMethod,
                merchant = item.merchant,
                description = "Recorded from ${freq.label} recurring schedule",
                notes = "Scheduled due date: $dueDate",
                tags = "recurring",
                recurringSourceId = item.id
            )
            repository.saveExpense(session.userId, tx, session.accessToken)
            val nextDue = DateRangeEngine.nextRecurringDate(dueDate, freq)
            repository.saveRecurringExpense(
                session.userId,
                item.copy(nextDueDateIso = nextDue, lastGeneratedDateIso = dueDate)
            )
            _uiState.update {
                it.copy(statusBannerMessage = "Recorded '${item.title}' for $dueDate. Next due: $nextDue.")
            }
        }
    }

    // AI Insights
    fun askAiQuestion(question: String) {
        val state = _uiState.value
        if (question.trim().isEmpty()) return
        if (state.profile?.aiEnabled == false) {
            _uiState.update { it.copy(statusBannerMessage = "AI Insights are currently disabled in Profile & Settings.") }
            return
        }
        val response = AiInsightsEngine.answerQuestion(
            question = question,
            allExpenses = state.expenses,
            categories = state.categories,
            recurringExpenses = state.recurringExpenses,
            referenceDateIso = state.referenceDateIso,
            currencyCode = state.currencyCode,
            overallMonthlyBudgetMinor = state.profile?.overallMonthlyBudgetMinor ?: 50_000_00L
        )
        _uiState.update {
            it.copy(aiInsightsHistory = listOf(response) + it.aiInsightsHistory.take(15))
        }
    }

    // Profile & Settings
    fun updateProfilePreferences(
        fullName: String,
        preferredCurrency: String,
        dateFormat: String,
        timezone: String,
        themeMode: String,
        aiConsentGranted: Boolean,
        aiEnabled: Boolean
    ) {
        val current = _uiState.value.profile ?: return
        viewModelScope.launch {
            val updated = current.copy(
                fullName = fullName.trim().ifEmpty { current.fullName },
                preferredCurrency = preferredCurrency,
                dateFormat = dateFormat,
                timezone = timezone,
                themeMode = themeMode,
                aiConsentGranted = aiConsentGranted,
                aiEnabled = aiEnabled
            )
            repository.updateProfile(updated)
            _uiState.update { it.copy(statusBannerMessage = "Profile and preferences saved.") }
        }
    }

    // Export Reports
    fun generateExportReport(
        context: Context,
        format: String, // "CSV", "XLSX", "PDF"
        expensesToExport: List<ExpenseEntity>,
        reportTitle: String,
        dateRangeLabel: String,
        appliedFiltersSummary: String,
        shareImmediately: Boolean = true
    ) {
        val state = _uiState.value
        val ym = state.referenceDateIso.take(7)
        val outDir = File(context.cacheDir, "exports")
        viewModelScope.launch {
            val generated = when (format.uppercase()) {
                "CSV" -> ExportEngine.exportCsvToFile(
                    outputDir = outDir,
                    fileNameSymbol = "ExpensePro_Category_Report_$ym.csv",
                    reportTitle = reportTitle,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = state.currencyCode,
                    generationDateIso = state.referenceDateIso,
                    appliedFiltersSummary = appliedFiltersSummary,
                    expenses = expensesToExport,
                    categories = state.categories
                )
                "XLSX" -> ExportEngine.exportXlsxToFile(
                    outputDir = outDir,
                    fileNameSymbol = "ExpensePro_Transactions_$ym.xlsx",
                    reportTitle = reportTitle,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = state.currencyCode,
                    generationDateIso = state.referenceDateIso,
                    appliedFiltersSummary = appliedFiltersSummary,
                    expenses = expensesToExport,
                    categories = state.categories
                )
                else -> ExportEngine.exportPdfToFile(
                    outputDir = outDir,
                    fileNameSymbol = "ExpensePro_Monthly_Report_$ym.pdf",
                    reportTitle = reportTitle,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = state.currencyCode,
                    generationDateIso = state.referenceDateIso,
                    appliedFiltersSummary = appliedFiltersSummary,
                    expenses = expensesToExport,
                    categories = state.categories
                )
            }
            _uiState.update {
                it.copy(
                    lastGeneratedExport = generated,
                    statusBannerMessage = "Generated ${generated.fileName} (${generated.totalRecords} records, ${MoneyUtils.formatMoney(generated.totalAmountMinor, state.currencyCode)})"
                )
            }
            if (shareImmediately) {
                ExportEngine.shareExportedFile(context, generated)
            }
        }
    }

    fun triggerBuildApk(context: Context, shareImmediately: Boolean = false) {
        if (_uiState.value.isBuildingApk) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBuildingApk = true,
                    apkBuildStepMessage = "Compiling & packaging signed Android APK..."
                )
            }
            val artifact = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ExportEngine.buildAndPackageApk(
                    context = context,
                    builtAtIso = _uiState.value.referenceDateIso
                )
            }
            _uiState.update {
                it.copy(
                    isBuildingApk = false,
                    apkBuildStepMessage = "APK Build Complete: ${artifact.fileName} (${ExportEngine.formatApkSize(artifact.fileSizeBytes)})",
                    apkBuildArtifact = artifact,
                    statusBannerMessage = "Built ${artifact.fileName} (${ExportEngine.formatApkSize(artifact.fileSizeBytes)}) — Ready to download & install!"
                )
            }
            if (shareImmediately) {
                ExportEngine.shareOrDownloadApk(context, artifact)
            }
        }
    }

    fun downloadBuiltApk(context: Context) {
        val artifact = _uiState.value.apkBuildArtifact
        if (artifact != null) {
            ExportEngine.shareOrDownloadApk(context, artifact)
        } else {
            triggerBuildApk(context, shareImmediately = true)
        }
    }

    fun deleteCurrentAccount() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.deleteAccountAndAllData(session.userId)
            logout()
            _uiState.update { it.copy(statusBannerMessage = "Your account and all financial records have been permanently deleted.") }
        }
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    class Factory(private val repository: ExpenseProRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpenseProViewModel(repository) as T
        }
    }
}
