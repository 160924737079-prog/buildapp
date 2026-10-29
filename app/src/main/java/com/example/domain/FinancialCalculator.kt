package com.example.domain

import com.example.data.BudgetEntity
import com.example.data.CategoryEntity
import com.example.data.ExpenseEntity
import com.example.data.RecurringExpenseEntity
import java.math.BigDecimal
import java.math.RoundingMode

data class CategoryBreakdownItem(
    val categoryId: String,
    val categoryName: String,
    val colorHex: String,
    val iconName: String,
    val totalMinor: Long,
    val transactionCount: Int,
    val percentageOfTotal: BigDecimal,
    val budgetMinor: Long? = null
)

data class DailyTotalItem(
    val dateIso: String,
    val dayLabel: String,
    val totalMinor: Long,
    val transactionCount: Int
)

data class MonthlyTotalItem(
    val yearMonth: String, // yyyy-MM
    val monthLabel: String,
    val totalMinor: Long,
    val transactionCount: Int,
    val byCategoryMinor: Map<String, Long> = emptyMap()
)

data class PaymentMethodBreakdownItem(
    val paymentMethod: String,
    val totalMinor: Long,
    val transactionCount: Int,
    val percentageOfTotal: BigDecimal
)

data class DashboardSummaryMetrics(
    val periodBounds: DateRangeBounds,
    val totalPeriodExpensesMinor: Long,
    val todayExpensesMinor: Long,
    val thisWeekExpensesMinor: Long,
    val thisMonthExpensesMinor: Long,
    val previousMonthExpensesMinor: Long,
    val averageDailyExpenseMinor: Long,
    val averageTransactionMinor: Long,
    val highestSpendingCategory: CategoryBreakdownItem?,
    val totalTransactionCount: Int,
    val overallMonthlyBudgetMinor: Long,
    val remainingMonthlyBudgetMinor: Long,
    val budgetUtilizationPercent: BigDecimal,
    val monthOverMonthChangePercent: BigDecimal?, // null if previousMonth == 0
    val monthOverMonthMessage: String,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val paymentMethodBreakdown: List<PaymentMethodBreakdownItem>,
    val dailyTrend: List<DailyTotalItem>,
    val monthlyTrend: List<MonthlyTotalItem>
)

enum class BudgetAlertLevel {
    NORMAL,
    WARNING_80,
    CRITICAL_90,
    EXCEEDED_100
}

data class BudgetStatusItem(
    val categoryId: String?, // null = Overall Monthly Budget
    val name: String,
    val colorHex: String,
    val budgetLimitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val utilizationPercent: BigDecimal,
    val alertLevel: BudgetAlertLevel
)

data class TimeHorizonReport(
    val title: String,
    val bounds: DateRangeBounds,
    val totalExpensesMinor: Long,
    val transactionCount: Int,
    val averageDailyMinor: Long,
    val averageTransactionMinor: Long,
    val highestCategory: CategoryBreakdownItem?,
    val highestDay: DailyTotalItem?,
    val highestMonth: MonthlyTotalItem?,
    val previousPeriodTotalMinor: Long,
    val comparisonChangePercent: BigDecimal?,
    val comparisonLabel: String,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val dailyTotals: List<DailyTotalItem>,
    val monthlyTotals: List<MonthlyTotalItem>,
    val paymentBreakdown: List<PaymentMethodBreakdownItem>,
    val qualifyingTransactions: List<ExpenseEntity>
)

/**
 * Pure, deterministic financial calculation engine.
 * - Uses integer minor units (paise/cents) and BigDecimal to prevent floating-point errors.
 * - Excludes deleted/voided transactions and non-expense transaction types from expense totals.
 * - Deduplicates by transaction ID so a transaction is never counted twice.
 */
object FinancialCalculator {

    fun filterQualifyingExpenses(
        allTransactions: List<ExpenseEntity>,
        startDateIso: String? = null,
        endDateIso: String? = null,
        categoryIds: Set<String> = emptySet(),
        subcategoryIds: Set<String> = emptySet(),
        paymentMethods: Set<String> = emptySet(),
        minAmountMinor: Long? = null,
        maxAmountMinor: Long? = null,
        searchQuery: String = ""
    ): List<ExpenseEntity> {
        val seenIds = HashSet<String>()
        val queryTrimmed = searchQuery.trim().lowercase()

        return allTransactions.filter { tx ->
            if (!seenIds.add(tx.id)) return@filter false
            if (tx.status != "ACTIVE") return@filter false
            if (tx.transactionType != "EXPENSE") return@filter false
            if (startDateIso != null && tx.expenseDateIso < startDateIso) return@filter false
            if (endDateIso != null && tx.expenseDateIso > endDateIso) return@filter false
            if (categoryIds.isNotEmpty() && tx.categoryId !in categoryIds) return@filter false
            if (subcategoryIds.isNotEmpty() && (tx.subcategoryId == null || tx.subcategoryId !in subcategoryIds)) return@filter false
            if (paymentMethods.isNotEmpty() && tx.paymentMethod !in paymentMethods) return@filter false
            if (minAmountMinor != null && tx.amountMinor < minAmountMinor) return@filter false
            if (maxAmountMinor != null && tx.amountMinor > maxAmountMinor) return@filter false
            if (queryTrimmed.isNotEmpty()) {
                val matchesTitle = tx.title.lowercase().contains(queryTrimmed)
                val matchesMerchant = tx.merchant.lowercase().contains(queryTrimmed)
                val matchesNotes = tx.notes.lowercase().contains(queryTrimmed)
                val matchesTags = tx.tags.lowercase().contains(queryTrimmed)
                if (!matchesTitle && !matchesMerchant && !matchesNotes && !matchesTags) return@filter false
            }
            true
        }
    }

    fun calculateCategoryBreakdown(
        qualifyingExpenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>
    ): List<CategoryBreakdownItem> {
        val catMap = categories.associateBy { it.id }
        val overallTotal = qualifyingExpenses.sumOf { it.amountMinor }
        if (qualifyingExpenses.isEmpty()) return emptyList()

        return qualifyingExpenses
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val cat = catMap[catId]
                val catTotal = list.sumOf { it.amountMinor }
                CategoryBreakdownItem(
                    categoryId = catId,
                    categoryName = cat?.name ?: "Uncategorized",
                    colorHex = cat?.colorHex ?: "#10B981",
                    iconName = cat?.iconName ?: "category",
                    totalMinor = catTotal,
                    transactionCount = list.size,
                    percentageOfTotal = MoneyUtils.percentage(catTotal, overallTotal),
                    budgetMinor = cat?.monthlyBudgetMinor
                )
            }
            .sortedByDescending { it.totalMinor }
    }

    fun calculatePaymentBreakdown(qualifyingExpenses: List<ExpenseEntity>): List<PaymentMethodBreakdownItem> {
        val overallTotal = qualifyingExpenses.sumOf { it.amountMinor }
        if (qualifyingExpenses.isEmpty()) return emptyList()

        return qualifyingExpenses
            .groupBy { it.paymentMethod }
            .map { (method, list) ->
                val sum = list.sumOf { it.amountMinor }
                PaymentMethodBreakdownItem(
                    paymentMethod = method,
                    totalMinor = sum,
                    transactionCount = list.size,
                    percentageOfTotal = MoneyUtils.percentage(sum, overallTotal)
                )
            }
            .sortedByDescending { it.totalMinor }
    }

    fun calculateDailyTotals(
        qualifyingExpenses: List<ExpenseEntity>,
        startDateIso: String,
        endDateIso: String
    ): List<DailyTotalItem> {
        val byDate = qualifyingExpenses.groupBy { it.expenseDateIso }
        return byDate.entries
            .filter { it.key in startDateIso..endDateIso }
            .sortedBy { it.key }
            .map { (dateIso, list) ->
                DailyTotalItem(
                    dateIso = dateIso,
                    dayLabel = DateRangeEngine.formatDisplayDate(dateIso, "dd MMM"),
                    totalMinor = list.sumOf { it.amountMinor },
                    transactionCount = list.size
                )
            }
    }

    fun calculateMonthlyTotals(
        qualifyingExpenses: List<ExpenseEntity>
    ): List<MonthlyTotalItem> {
        return qualifyingExpenses
            .groupBy { it.expenseDateIso.take(7) } // yyyy-MM
            .entries
            .sortedBy { it.key }
            .map { (ym, list) ->
                val byCat = list.groupBy { it.categoryId }
                    .mapValues { (_, v) -> v.sumOf { it.amountMinor } }
                val firstDayIso = "$ym-01"
                MonthlyTotalItem(
                    yearMonth = ym,
                    monthLabel = DateRangeEngine.formatDisplayDate(firstDayIso, "MMM yyyy"),
                    totalMinor = list.sumOf { it.amountMinor },
                    transactionCount = list.size,
                    byCategoryMinor = byCat
                )
            }
    }

    fun computeDashboardMetrics(
        allExpenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        period: ReportingPeriod,
        referenceDateIso: String,
        overallMonthlyBudgetMinor: Long,
        customStartIso: String? = null,
        customEndIso: String? = null
    ): DashboardSummaryMetrics {
        val bounds = DateRangeEngine.resolvePeriodBounds(period, referenceDateIso, customStartIso, customEndIso)
        val periodExpenses = filterQualifyingExpenses(
            allTransactions = allExpenses,
            startDateIso = bounds.startDateIso,
            endDateIso = bounds.endDateIso
        )

        val todayBounds = DateRangeEngine.resolvePeriodBounds(ReportingPeriod.TODAY, referenceDateIso)
        val thisWeekBounds = DateRangeEngine.resolvePeriodBounds(ReportingPeriod.THIS_WEEK, referenceDateIso)
        val thisMonthBounds = DateRangeEngine.resolvePeriodBounds(ReportingPeriod.THIS_MONTH, referenceDateIso)
        val lastMonthBounds = DateRangeEngine.resolvePeriodBounds(ReportingPeriod.LAST_MONTH, referenceDateIso)

        val todayTotal = filterQualifyingExpenses(allExpenses, todayBounds.startDateIso, todayBounds.endDateIso)
            .sumOf { it.amountMinor }
        val thisWeekTotal = filterQualifyingExpenses(allExpenses, thisWeekBounds.startDateIso, thisWeekBounds.endDateIso)
            .sumOf { it.amountMinor }
        val thisMonthTotal = filterQualifyingExpenses(allExpenses, thisMonthBounds.startDateIso, thisMonthBounds.endDateIso)
            .sumOf { it.amountMinor }
        val lastMonthTotal = filterQualifyingExpenses(allExpenses, lastMonthBounds.startDateIso, lastMonthBounds.endDateIso)
            .sumOf { it.amountMinor }

        val periodTotal = periodExpenses.sumOf { it.amountMinor }
        val effectiveDays = if (period == ReportingPeriod.ALL_TIME) {
            val minDate = periodExpenses.minOfOrNull { it.expenseDateIso } ?: referenceDateIso
            val maxDate = periodExpenses.maxOfOrNull { it.expenseDateIso } ?: referenceDateIso
            DateRangeEngine.daysBetweenInclusive(minDate, maxDate)
        } else {
            bounds.calendarDaysCount.coerceAtLeast(1)
        }

        val avgDailyMinor = if (periodTotal == 0L) 0L else {
            BigDecimal.valueOf(periodTotal)
                .divide(BigDecimal.valueOf(effectiveDays.toLong()), 0, RoundingMode.HALF_UP)
                .toLong()
        }
        val avgTxMinor = if (periodExpenses.isEmpty()) 0L else {
            BigDecimal.valueOf(periodTotal)
                .divide(BigDecimal.valueOf(periodExpenses.size.toLong()), 0, RoundingMode.HALF_UP)
                .toLong()
        }

        val catBreakdown = calculateCategoryBreakdown(periodExpenses, categories)
        val paymentBreakdown = calculatePaymentBreakdown(periodExpenses)
        val dailyTrend = calculateDailyTotals(periodExpenses, bounds.startDateIso, bounds.endDateIso)
        val monthlyTrend = calculateMonthlyTotals(
            filterQualifyingExpenses(allExpenses)
        )

        val remainingBudget = overallMonthlyBudgetMinor - thisMonthTotal
        val budgetUtilization = MoneyUtils.percentage(thisMonthTotal, overallMonthlyBudgetMinor)

        val momPercent = MoneyUtils.percentageChange(thisMonthTotal, lastMonthTotal)
        val momMessage = when {
            lastMonthTotal == 0L && thisMonthTotal == 0L -> "No spending in current or previous month"
            lastMonthTotal == 0L -> "No previous month baseline (₹0 spent last month)"
            momPercent != null && momPercent > BigDecimal.ZERO -> "+${momPercent.toPlainString()}% vs last month"
            momPercent != null && momPercent < BigDecimal.ZERO -> "${momPercent.toPlainString()}% vs last month"
            else -> "0.00% change vs last month"
        }

        return DashboardSummaryMetrics(
            periodBounds = bounds,
            totalPeriodExpensesMinor = periodTotal,
            todayExpensesMinor = todayTotal,
            thisWeekExpensesMinor = thisWeekTotal,
            thisMonthExpensesMinor = thisMonthTotal,
            previousMonthExpensesMinor = lastMonthTotal,
            averageDailyExpenseMinor = avgDailyMinor,
            averageTransactionMinor = avgTxMinor,
            highestSpendingCategory = catBreakdown.firstOrNull(),
            totalTransactionCount = periodExpenses.size,
            overallMonthlyBudgetMinor = overallMonthlyBudgetMinor,
            remainingMonthlyBudgetMinor = remainingBudget,
            budgetUtilizationPercent = budgetUtilization,
            monthOverMonthChangePercent = momPercent,
            monthOverMonthMessage = momMessage,
            categoryBreakdown = catBreakdown,
            paymentMethodBreakdown = paymentBreakdown,
            dailyTrend = dailyTrend,
            monthlyTrend = monthlyTrend
        )
    }

    fun buildTimeHorizonReport(
        title: String,
        bounds: DateRangeBounds,
        previousBounds: DateRangeBounds?,
        comparisonName: String,
        allExpenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        categoryIds: Set<String> = emptySet(),
        subcategoryIds: Set<String> = emptySet(),
        paymentMethods: Set<String> = emptySet(),
        minAmountMinor: Long? = null,
        maxAmountMinor: Long? = null
    ): TimeHorizonReport {
        val qualifying = filterQualifyingExpenses(
            allTransactions = allExpenses,
            startDateIso = bounds.startDateIso,
            endDateIso = bounds.endDateIso,
            categoryIds = categoryIds,
            subcategoryIds = subcategoryIds,
            paymentMethods = paymentMethods,
            minAmountMinor = minAmountMinor,
            maxAmountMinor = maxAmountMinor
        )
        val total = qualifying.sumOf { it.amountMinor }
        val daysCount = bounds.calendarDaysCount.coerceAtLeast(1)
        val avgDaily = if (total == 0L) 0L else {
            BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(daysCount.toLong()), 0, RoundingMode.HALF_UP)
                .toLong()
        }
        val avgTx = if (qualifying.isEmpty()) 0L else {
            BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(qualifying.size.toLong()), 0, RoundingMode.HALF_UP)
                .toLong()
        }

        val catBreakdown = calculateCategoryBreakdown(qualifying, categories)
        val dailyTotals = calculateDailyTotals(qualifying, bounds.startDateIso, bounds.endDateIso)
        val monthlyTotals = calculateMonthlyTotals(qualifying)
        val payBreakdown = calculatePaymentBreakdown(qualifying)

        val prevTotal = if (previousBounds != null) {
            filterQualifyingExpenses(
                allTransactions = allExpenses,
                startDateIso = previousBounds.startDateIso,
                endDateIso = previousBounds.endDateIso,
                categoryIds = categoryIds,
                subcategoryIds = subcategoryIds,
                paymentMethods = paymentMethods,
                minAmountMinor = minAmountMinor,
                maxAmountMinor = maxAmountMinor
            ).sumOf { it.amountMinor }
        } else 0L

        val changePercent = if (previousBounds != null) {
            MoneyUtils.percentageChange(total, prevTotal)
        } else null

        val compLabel = when {
            previousBounds == null -> ""
            prevTotal == 0L && total == 0L -> "No change vs $comparisonName"
            prevTotal == 0L -> "No baseline in $comparisonName"
            changePercent != null && changePercent >= BigDecimal.ZERO -> "+${changePercent.toPlainString()}% vs $comparisonName"
            changePercent != null -> "${changePercent.toPlainString()}% vs $comparisonName"
            else -> ""
        }

        return TimeHorizonReport(
            title = title,
            bounds = bounds,
            totalExpensesMinor = total,
            transactionCount = qualifying.size,
            averageDailyMinor = avgDaily,
            averageTransactionMinor = avgTx,
            highestCategory = catBreakdown.firstOrNull(),
            highestDay = dailyTotals.maxByOrNull { it.totalMinor },
            highestMonth = monthlyTotals.maxByOrNull { it.totalMinor },
            previousPeriodTotalMinor = prevTotal,
            comparisonChangePercent = changePercent,
            comparisonLabel = compLabel,
            categoryBreakdown = catBreakdown,
            dailyTotals = dailyTotals,
            monthlyTotals = monthlyTotals,
            paymentBreakdown = payBreakdown,
            qualifyingTransactions = qualifying
        )
    }

    fun evaluateBudgetStatuses(
        monthBounds: DateRangeBounds,
        allExpenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        overallMonthlyBudgetMinor: Long
    ): List<BudgetStatusItem> {
        val monthExpenses = filterQualifyingExpenses(
            allExpenses,
            startDateIso = monthBounds.startDateIso,
            endDateIso = monthBounds.endDateIso
        )
        val totalMonthSpent = monthExpenses.sumOf { it.amountMinor }
        val result = mutableListOf<BudgetStatusItem>()

        // 1. Overall Monthly Budget
        if (overallMonthlyBudgetMinor > 0L) {
            val util = MoneyUtils.percentage(totalMonthSpent, overallMonthlyBudgetMinor)
            result.add(
                BudgetStatusItem(
                    categoryId = null,
                    name = "Overall Monthly Budget",
                    colorHex = "#0F172A",
                    budgetLimitMinor = overallMonthlyBudgetMinor,
                    spentMinor = totalMonthSpent,
                    remainingMinor = overallMonthlyBudgetMinor - totalMonthSpent,
                    utilizationPercent = util,
                    alertLevel = resolveAlertLevel(util)
                )
            )
        }

        // 2. Category Budgets (from CategoryEntity.monthlyBudgetMinor or BudgetEntity)
        val explicitBudgetsByCat = budgets
            .filter { it.categoryId != null && it.startDateIso <= monthBounds.endDateIso && it.endDateIso >= monthBounds.startDateIso }
            .associateBy { it.categoryId!! }

        val topCategories = categories.filter { it.parentCategoryId == null && !it.isArchived }
        for (cat in topCategories) {
            val limitMinor = explicitBudgetsByCat[cat.id]?.amountMinor ?: cat.monthlyBudgetMinor ?: 0L
            if (limitMinor > 0L) {
                val catSpent = monthExpenses.filter { it.categoryId == cat.id }.sumOf { it.amountMinor }
                val util = MoneyUtils.percentage(catSpent, limitMinor)
                result.add(
                    BudgetStatusItem(
                        categoryId = cat.id,
                        name = cat.name,
                        colorHex = cat.colorHex,
                        budgetLimitMinor = limitMinor,
                        spentMinor = catSpent,
                        remainingMinor = limitMinor - catSpent,
                        utilizationPercent = util,
                        alertLevel = resolveAlertLevel(util)
                    )
                )
            }
        }

        return result
    }

    fun resolveAlertLevel(utilizationPercent: BigDecimal): BudgetAlertLevel {
        val pct = utilizationPercent.toDouble()
        return when {
            pct >= 100.0 -> BudgetAlertLevel.EXCEEDED_100
            pct >= 90.0 -> BudgetAlertLevel.CRITICAL_90
            pct >= 80.0 -> BudgetAlertLevel.WARNING_80
            else -> BudgetAlertLevel.NORMAL
        }
    }

    /**
     * Processes due recurring expenses up to [referenceDateIso] without ever creating duplicate transactions.
     * Returns newly created ExpenseEntity items and updated RecurringExpenseEntity schedules.
     */
    fun materializeDueRecurringExpenses(
        schedules: List<RecurringExpenseEntity>,
        existingExpenses: List<ExpenseEntity>,
        referenceDateIso: String,
        currencyCode: String
    ): Pair<List<ExpenseEntity>, List<RecurringExpenseEntity>> {
        val newExpenses = mutableListOf<ExpenseEntity>()
        val updatedSchedules = mutableListOf<RecurringExpenseEntity>()

        // Track existing (recurringSourceId, expenseDateIso) pairs to prevent duplicates
        val existingKeys = existingExpenses
            .filter { it.recurringSourceId != null && it.status == "ACTIVE" }
            .map { "${it.recurringSourceId}_${it.expenseDateIso}" }
            .toMutableSet()

        for (sched in schedules) {
            if (!sched.active || sched.status != "ACTIVE") continue
            var dueIso = sched.nextDueDateIso
            var lastGen = sched.lastGeneratedDateIso
            var changed = false
            val freq = runCatching { RecurrenceFrequency.valueOf(sched.frequency) }.getOrDefault(RecurrenceFrequency.MONTHLY)

            var safetyCounter = 0
            while (dueIso <= referenceDateIso && safetyCounter < 36) {
                val dedupKey = "${sched.id}_$dueIso"
                if (dedupKey !in existingKeys && lastGen != dueIso) {
                    val txId = "rec_tx_${sched.id}_$dueIso"
                    newExpenses.add(
                        ExpenseEntity(
                            id = txId,
                            userId = sched.userId,
                            categoryId = sched.categoryId,
                            subcategoryId = sched.subcategoryId,
                            title = sched.title,
                            amountMinor = sched.amountMinor,
                            currency = currencyCode,
                            expenseDateIso = dueIso,
                            paymentMethod = sched.paymentMethod,
                            merchant = sched.merchant,
                            description = "Auto-generated from ${freq.label} recurring schedule",
                            notes = "Recurring schedule: ${sched.title}",
                            tags = "recurring,${freq.name.lowercase()}",
                            transactionType = "EXPENSE",
                            status = "ACTIVE",
                            recurringSourceId = sched.id
                        )
                    )
                    existingKeys.add(dedupKey)
                    lastGen = dueIso
                }
                dueIso = DateRangeEngine.nextRecurringDate(dueIso, freq)
                changed = true
                safetyCounter++
            }

            if (changed) {
                updatedSchedules.add(
                    sched.copy(
                        nextDueDateIso = dueIso,
                        lastGeneratedDateIso = lastGen,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return Pair(newExpenses, updatedSchedules)
    }
}
