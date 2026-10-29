package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CategoryEntity
import com.example.data.ExpenseEntity
import com.example.data.ExpenseProDatabase
import com.example.data.ExpenseProRepository
import com.example.data.RecurringExpenseEntity
import com.example.domain.AiInsightsEngine
import com.example.domain.DateRangeEngine
import com.example.domain.ExportEngine
import com.example.domain.FinancialCalculator
import com.example.domain.MoneyUtils
import com.example.domain.RecurrenceFrequency
import com.example.domain.ReportingPeriod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExpenseProAcceptanceTest {

    private lateinit var context: Context
    private lateinit var db: ExpenseProDatabase
    private lateinit var repository: ExpenseProRepository

    private val testUserId = "user_test_alpha"
    private val otherUserId = "user_test_beta"

    private val catGroceries = CategoryEntity(
        id = "cat_groceries",
        userId = testUserId,
        name = "Groceries",
        colorHex = "#10B981"
    )
    private val catDairy = CategoryEntity(
        id = "cat_dairy",
        userId = testUserId,
        name = "Milk and Dairy",
        colorHex = "#06B6D4"
    )
    private val catPetrol = CategoryEntity(
        id = "cat_petrol",
        userId = testUserId,
        name = "Fuel and Transportation",
        colorHex = "#F59E0B"
    )
    private val categories = listOf(catGroceries, catDairy, catPetrol)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExpenseProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExpenseProRepository(db.dao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Test 1:
     * Add Groceries: ₹500, Milk: ₹60, Petrol: ₹300 -> Expected total: ₹860.
     */
    @Test
    fun test1_addGroceriesMilkPetrol_equals860() {
        val expenses = listOf(
            ExpenseEntity(
                id = "t1",
                userId = testUserId,
                categoryId = catGroceries.id,
                title = "Groceries",
                amountMinor = MoneyUtils.toMinorUnits("500")!!,
                expenseDateIso = "2026-10-29",
                paymentMethod = "UPI"
            ),
            ExpenseEntity(
                id = "t2",
                userId = testUserId,
                categoryId = catDairy.id,
                title = "Milk",
                amountMinor = MoneyUtils.toMinorUnits("60")!!,
                expenseDateIso = "2026-10-29",
                paymentMethod = "Cash"
            ),
            ExpenseEntity(
                id = "t3",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Petrol",
                amountMinor = MoneyUtils.toMinorUnits("300")!!,
                expenseDateIso = "2026-10-29",
                paymentMethod = "Credit Card"
            )
        )

        val metrics = FinancialCalculator.computeDashboardMetrics(
            allExpenses = expenses,
            categories = categories,
            period = ReportingPeriod.TODAY,
            referenceDateIso = "2026-10-29",
            overallMonthlyBudgetMinor = 50_000_00L
        )

        assertEquals(860_00L, metrics.totalPeriodExpensesMinor)
        assertEquals("₹860.00", MoneyUtils.formatMoney(metrics.totalPeriodExpensesMinor, "INR"))
    }

    /**
     * Test 2:
     * Verify category-wise totals and percentages.
     */
    @Test
    fun test2_verifyCategoryWiseTotals() {
        val expenses = listOf(
            ExpenseEntity(
                id = "t1",
                userId = testUserId,
                categoryId = catGroceries.id,
                title = "Vegetables",
                amountMinor = 300_00L,
                expenseDateIso = "2026-10-29"
            ),
            ExpenseEntity(
                id = "t2",
                userId = testUserId,
                categoryId = catGroceries.id,
                title = "Fruits",
                amountMinor = 200_00L,
                expenseDateIso = "2026-10-29"
            ),
            ExpenseEntity(
                id = "t3",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Petrol",
                amountMinor = 500_00L,
                expenseDateIso = "2026-10-29"
            )
        )

        val breakdown = FinancialCalculator.calculateCategoryBreakdown(expenses, categories)
        assertEquals(2, breakdown.size)
        val grocItem = breakdown.first { it.categoryId == catGroceries.id }
        val petItem = breakdown.first { it.categoryId == catPetrol.id }

        assertEquals(500_00L, grocItem.totalMinor)
        assertEquals(2, grocItem.transactionCount)
        assertEquals("50.00", grocItem.percentageOfTotal.toPlainString())
        assertEquals(500_00L, petItem.totalMinor)
        assertEquals("50.00", petItem.percentageOfTotal.toPlainString())
    }

    /**
     * Test 3:
     * Verify daily, weekly, monthly, quarterly, half-yearly, and yearly calculations.
     */
    @Test
    fun test3_verifyMultiHorizonCalculations() {
        val expenses = listOf(
            // Today (2026-10-29, Q4, H2)
            ExpenseEntity(id = "e1", userId = testUserId, categoryId = catGroceries.id, title = "Today Tx", amountMinor = 500_00L, expenseDateIso = "2026-10-29"),
            // Earlier this week (2026-10-27, Tuesday)
            ExpenseEntity(id = "e2", userId = testUserId, categoryId = catDairy.id, title = "Week Tx", amountMinor = 200_00L, expenseDateIso = "2026-10-27"),
            // Earlier in October 2026
            ExpenseEntity(id = "e3", userId = testUserId, categoryId = catPetrol.id, title = "Oct Tx", amountMinor = 1000_00L, expenseDateIso = "2026-10-05"),
            // November 2026 (still Q4 and H2)
            ExpenseEntity(id = "e4", userId = testUserId, categoryId = catGroceries.id, title = "Nov Q4 Tx", amountMinor = 300_00L, expenseDateIso = "2026-11-10"),
            // August 2026 (Q3, still H2)
            ExpenseEntity(id = "e5", userId = testUserId, categoryId = catGroceries.id, title = "Aug H2 Tx", amountMinor = 400_00L, expenseDateIso = "2026-08-15"),
            // March 2026 (Q1, H1, still 2026)
            ExpenseEntity(id = "e6", userId = testUserId, categoryId = catPetrol.id, title = "Mar H1 Tx", amountMinor = 600_00L, expenseDateIso = "2026-03-10")
        )

        val refDate = "2026-10-29"
        val daily = FinancialCalculator.computeDashboardMetrics(expenses, categories, ReportingPeriod.TODAY, refDate, 50_000_00L)
        val weekly = FinancialCalculator.computeDashboardMetrics(expenses, categories, ReportingPeriod.THIS_WEEK, refDate, 50_000_00L)
        val monthly = FinancialCalculator.computeDashboardMetrics(expenses, categories, ReportingPeriod.THIS_MONTH, refDate, 50_000_00L)
        val quarterly = FinancialCalculator.computeDashboardMetrics(expenses, categories, ReportingPeriod.CURRENT_QUARTER, refDate, 50_000_00L)
        val halfYearly = FinancialCalculator.computeDashboardMetrics(expenses, categories, ReportingPeriod.CURRENT_HALF_YEAR, refDate, 50_000_00L)
        val yearly = FinancialCalculator.computeDashboardMetrics(expenses, categories, ReportingPeriod.THIS_YEAR, refDate, 50_000_00L)

        assertEquals(500_00L, daily.totalPeriodExpensesMinor)
        assertEquals(700_00L, weekly.totalPeriodExpensesMinor)       // 500 + 200
        assertEquals(1700_00L, monthly.totalPeriodExpensesMinor)     // 500 + 200 + 1000
        assertEquals(2000_00L, quarterly.totalPeriodExpensesMinor)   // Oct (1700) + Nov (300)
        assertEquals(2400_00L, halfYearly.totalPeriodExpensesMinor)  // Q4 (2000) + Aug (400)
        assertEquals(3000_00L, yearly.totalPeriodExpensesMinor)      // H2 (2400) + Mar H1 (600)
    }

    /**
     * Test 4:
     * Verify custom date ranges, including transactions on both boundary dates.
     */
    @Test
    fun test4_verifyCustomDateRangeBoundaryInclusive() {
        val expenses = listOf(
            ExpenseEntity(id = "b0", userId = testUserId, categoryId = catPetrol.id, title = "Before Start", amountMinor = 100_00L, expenseDateIso = "2025-12-31"),
            ExpenseEntity(id = "b1", userId = testUserId, categoryId = catPetrol.id, title = "On Start Date", amountMinor = 250_00L, expenseDateIso = "2026-01-01"),
            ExpenseEntity(id = "b2", userId = testUserId, categoryId = catPetrol.id, title = "Mid Range", amountMinor = 350_00L, expenseDateIso = "2026-02-15"),
            ExpenseEntity(id = "b3", userId = testUserId, categoryId = catPetrol.id, title = "On End Date", amountMinor = 400_00L, expenseDateIso = "2026-03-31"),
            ExpenseEntity(id = "b4", userId = testUserId, categoryId = catPetrol.id, title = "After End", amountMinor = 900_00L, expenseDateIso = "2026-04-01")
        )

        val metrics = FinancialCalculator.computeDashboardMetrics(
            allExpenses = expenses,
            categories = categories,
            period = ReportingPeriod.CUSTOM,
            referenceDateIso = "2026-10-29",
            overallMonthlyBudgetMinor = 50_000_00L,
            customStartIso = "2026-01-01",
            customEndIso = "2026-03-31"
        )

        assertEquals(3, metrics.totalTransactionCount)
        assertEquals(1000_00L, metrics.totalPeriodExpensesMinor) // 250 + 350 + 400
    }

    /**
     * Test 5 & Test 6:
     * Edit an expense and confirm that all affected totals update.
     * Delete an expense and confirm that all affected totals update.
     */
    @Test
    fun test5_and_test6_editAndDeleteExpenseUpdatesTotals() = runBlocking {
        repository.saveCategory(testUserId, catGroceries)
        val initial = ExpenseEntity(
            id = "tx_edit_1",
            userId = testUserId,
            categoryId = catGroceries.id,
            title = "Weekly Groceries",
            amountMinor = 500_00L,
            expenseDateIso = "2026-10-29"
        )
        repository.saveExpense(testUserId, initial)

        var list = repository.getExpensesForUser(testUserId)
        var metrics = FinancialCalculator.computeDashboardMetrics(list, categories, ReportingPeriod.THIS_MONTH, "2026-10-29", 50_000_00L)
        assertEquals(500_00L, metrics.totalPeriodExpensesMinor)

        // Test 5: Edit amount from ₹500 to ₹750
        val edited = initial.copy(amountMinor = 750_00L, createdAt = initial.createdAt - 10_000L)
        repository.saveExpense(testUserId, edited)
        list = repository.getExpensesForUser(testUserId)
        metrics = FinancialCalculator.computeDashboardMetrics(list, categories, ReportingPeriod.THIS_MONTH, "2026-10-29", 50_000_00L)
        assertEquals(750_00L, metrics.totalPeriodExpensesMinor)

        // Test 6: Delete expense and verify total updates to 0
        repository.deleteExpenseForUser(initial.id, testUserId)
        list = repository.getExpensesForUser(testUserId)
        metrics = FinancialCalculator.computeDashboardMetrics(list, categories, ReportingPeriod.THIS_MONTH, "2026-10-29", 50_000_00L)
        assertEquals(0L, metrics.totalPeriodExpensesMinor)
        assertEquals(0, metrics.totalTransactionCount)
    }

    /**
     * Test 7:
     * Verify combined category, payment-method, amount, and date filters.
     */
    @Test
    fun test7_verifyCombinedFilters() {
        val expenses = listOf(
            ExpenseEntity(
                id = "f1",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Highway Petrol",
                amountMinor = 1200_00L,
                expenseDateIso = "2026-02-10",
                paymentMethod = "UPI"
            ),
            ExpenseEntity(
                id = "f2",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Small Petrol Topup",
                amountMinor = 150_00L, // below minAmount 500
                expenseDateIso = "2026-02-12",
                paymentMethod = "UPI"
            ),
            ExpenseEntity(
                id = "f3",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Card Petrol",
                amountMinor = 1400_00L,
                expenseDateIso = "2026-02-15",
                paymentMethod = "Credit Card" // different payment method
            ),
            ExpenseEntity(
                id = "f4",
                userId = testUserId,
                categoryId = catGroceries.id, // different category
                title = "Supermarket",
                amountMinor = 1200_00L,
                expenseDateIso = "2026-02-10",
                paymentMethod = "UPI"
            )
        )

        val filtered = FinancialCalculator.filterQualifyingExpenses(
            allTransactions = expenses,
            startDateIso = "2026-01-01",
            endDateIso = "2026-03-31",
            categoryIds = setOf(catPetrol.id),
            paymentMethods = setOf("UPI"),
            minAmountMinor = 500_00L,
            maxAmountMinor = 2000_00L
        )

        assertEquals(1, filtered.size)
        assertEquals("f1", filtered.first().id)
        assertEquals(1200_00L, filtered.sumOf { it.amountMinor })
    }

    /**
     * Test 8:
     * Verify that empty periods do not cause calculation errors.
     */
    @Test
    fun test8_verifyEmptyPeriodsSafeCalculation() {
        val metrics = FinancialCalculator.computeDashboardMetrics(
            allExpenses = emptyList(),
            categories = categories,
            period = ReportingPeriod.THIS_MONTH,
            referenceDateIso = "2026-10-29",
            overallMonthlyBudgetMinor = 50_000_00L
        )

        assertEquals(0L, metrics.totalPeriodExpensesMinor)
        assertEquals(0L, metrics.averageDailyExpenseMinor)
        assertEquals(0L, metrics.averageTransactionMinor)
        assertNull(metrics.highestSpendingCategory)
        assertNull(metrics.monthOverMonthChangePercent)
        assertTrue(metrics.monthOverMonthMessage.isNotBlank())
    }

    /**
     * Test 9:
     * Verify that users cannot access another user's records (RLS isolation).
     */
    @Test
    fun test9_verifyUserIsolationRLS() = runBlocking {
        val userATx = ExpenseEntity(
            id = "tx_user_a",
            userId = testUserId,
            categoryId = catGroceries.id,
            title = "User A Private Expense",
            amountMinor = 900_00L,
            expenseDateIso = "2026-10-29"
        )
        val userBTx = ExpenseEntity(
            id = "tx_user_b",
            userId = otherUserId,
            categoryId = catGroceries.id,
            title = "User B Confidential Expense",
            amountMinor = 4500_00L,
            expenseDateIso = "2026-10-29"
        )

        repository.saveExpense(testUserId, userATx)
        repository.saveExpense(otherUserId, userBTx)

        // User A queries their records
        val userARecords = repository.getExpensesForUser(testUserId)
        assertEquals(1, userARecords.size)
        assertEquals("tx_user_a", userARecords.first().id)

        // User A attempts to delete User B's record -> should be ignored due to userId check
        repository.deleteExpenseForUser("tx_user_b", testUserId)
        val userBRecords = repository.getExpensesForUser(otherUserId)
        assertEquals(1, userBRecords.size)
        assertEquals("tx_user_b", userBRecords.first().id)

        // Cross-user write attempt fails with SecurityException
        val crossWriteResult = repository.saveExpense(testUserId, userBTx)
        assertTrue(crossWriteResult.isFailure)
    }

    /**
     * Test 10:
     * Verify that CSV, Excel XLSX, and PDF reports match the selected filters.
     */
    @Test
    fun test10_verifyCsvXlsxPdfExportsMatchFilters() {
        val filteredExpenses = listOf(
            ExpenseEntity(
                id = "exp_1",
                userId = testUserId,
                categoryId = catGroceries.id,
                title = "Monthly Groceries",
                amountMinor = 1500_00L,
                expenseDateIso = "2026-10-29",
                paymentMethod = "UPI"
            ),
            ExpenseEntity(
                id = "exp_2",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Petrol Refill",
                amountMinor = 300_00L,
                expenseDateIso = "2026-10-29",
                paymentMethod = "Credit Card"
            )
        )
        val outDir = File(context.cacheDir, "test_exports")

        val csv = ExportEngine.exportCsvToFile(
            outputDir = outDir,
            fileNameSymbol = "ExpensePro_Category_Report_2026-10.csv",
            reportTitle = "October Filtered Report",
            dateRangeLabel = "Oct 2026",
            currencyCode = "INR",
            generationDateIso = "2026-10-29",
            appliedFiltersSummary = "Groceries + Petrol",
            expenses = filteredExpenses,
            categories = categories
        )
        val xlsx = ExportEngine.exportXlsxToFile(
            outputDir = outDir,
            fileNameSymbol = "ExpensePro_Transactions_2026-10.xlsx",
            reportTitle = "October Filtered Report",
            dateRangeLabel = "Oct 2026",
            currencyCode = "INR",
            generationDateIso = "2026-10-29",
            appliedFiltersSummary = "Groceries + Petrol",
            expenses = filteredExpenses,
            categories = categories
        )
        val pdf = ExportEngine.exportPdfToFile(
            outputDir = outDir,
            fileNameSymbol = "ExpensePro_Monthly_Report_2026-10.pdf",
            reportTitle = "October Filtered Report",
            dateRangeLabel = "Oct 2026",
            currencyCode = "INR",
            generationDateIso = "2026-10-29",
            appliedFiltersSummary = "Groceries + Petrol",
            expenses = filteredExpenses,
            categories = categories
        )

        assertTrue(csv.file.exists() && csv.file.length() > 0)
        assertTrue(xlsx.file.exists() && xlsx.file.length() > 0)
        assertTrue(pdf.file.exists() && pdf.file.length() > 0)

        assertEquals(2, csv.totalRecords)
        assertEquals(1800_00L, csv.totalAmountMinor)
        assertEquals(1800_00L, xlsx.totalAmountMinor)
        assertEquals(1800_00L, pdf.totalAmountMinor)
        assertTrue(csv.previewContent.contains("1800.00"))
        assertTrue(csv.previewContent.contains("Monthly Groceries"))
    }

    /**
     * Test 11:
     * Verify recurring expense behavior, leap-year/month-end handling, and duplicate prevention.
     */
    @Test
    fun test11_verifyRecurringExpensesAndDuplicatePrevention() {
        // Month-end & leap-year check: 2024-01-31 -> 2024-02-29 (leap year), 2026-01-31 -> 2026-02-28
        assertEquals("2024-02-29", DateRangeEngine.nextRecurringDate("2024-01-31", RecurrenceFrequency.MONTHLY))
        assertEquals("2026-02-28", DateRangeEngine.nextRecurringDate("2026-01-31", RecurrenceFrequency.MONTHLY))
        assertEquals("2025-02-28", DateRangeEngine.nextRecurringDate("2024-02-29", RecurrenceFrequency.YEARLY))

        val schedule = RecurringExpenseEntity(
            id = "rec_rent",
            userId = testUserId,
            categoryId = catGroceries.id,
            title = "House Rent",
            amountMinor = 18_000_00L,
            frequency = "MONTHLY",
            nextDueDateIso = "2026-10-05",
            active = true,
            status = "ACTIVE"
        )

        // First materialization up to 2026-10-29 generates 1 transaction for 2026-10-05 and advances nextDueDateIso to 2026-11-05
        val (firstBatch, updatedSchedules) = FinancialCalculator.materializeDueRecurringExpenses(
            schedules = listOf(schedule),
            existingExpenses = emptyList(),
            referenceDateIso = "2026-10-29",
            currencyCode = "INR"
        )
        assertEquals(1, firstBatch.size)
        assertEquals("2026-10-05", firstBatch.first().expenseDateIso)
        assertEquals("2026-11-05", updatedSchedules.first().nextDueDateIso)

        // Running again with the already generated expense produces 0 duplicates
        val (secondBatch, _) = FinancialCalculator.materializeDueRecurringExpenses(
            schedules = listOf(schedule),
            existingExpenses = firstBatch,
            referenceDateIso = "2026-10-29",
            currencyCode = "INR"
        )
        assertEquals(0, secondBatch.size)
    }

    /**
     * Test 12 & Test 13:
     * Verify deterministic AI Insights work without an external AI API key.
     */
    @Test
    fun test12_and_test13_verifyAiInsightsOfflineWithoutApiKey() {
        val expenses = listOf(
            ExpenseEntity(
                id = "ai_1",
                userId = testUserId,
                categoryId = catGroceries.id,
                title = "Monthly Groceries",
                amountMinor = 1500_00L,
                expenseDateIso = "2026-10-29"
            ),
            ExpenseEntity(
                id = "ai_2",
                userId = testUserId,
                categoryId = catPetrol.id,
                title = "Petrol Refill",
                amountMinor = 600_00L,
                expenseDateIso = "2026-10-28"
            )
        )

        val monthInsight = AiInsightsEngine.answerQuestion(
            question = "How much did I spend this month?",
            allExpenses = expenses,
            categories = categories,
            recurringExpenses = emptyList(),
            referenceDateIso = "2026-10-29",
            currencyCode = "INR",
            overallMonthlyBudgetMinor = 50_000_00L
        )
        assertNotNull(monthInsight)
        assertTrue(monthInsight.headlineAnswer.contains("₹2,100.00"))

        val topCatInsight = AiInsightsEngine.answerQuestion(
            question = "Which category costs me the most?",
            allExpenses = expenses,
            categories = categories,
            recurringExpenses = emptyList(),
            referenceDateIso = "2026-10-29",
            currencyCode = "INR",
            overallMonthlyBudgetMinor = 50_000_00L
        )
        assertTrue(topCatInsight.headlineAnswer.contains("Groceries"))
    }
}
