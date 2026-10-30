package com.example.domain

import com.example.data.BudgetEntity
import com.example.data.CategoryEntity
import com.example.data.ExpenseEntity
import com.example.data.RecurringExpenseEntity

data class CategoryTemplate(
    val slug: String,
    val name: String,
    val colorHex: String,
    val iconName: String,
    val defaultMonthlyBudgetMinor: Long,
    val subcategories: List<String>
)

/**
 * Preset category templates have been removed as requested so visualizations are generated
 * directly from the expense amounts entered in Add Expense.
 */
object DefaultCategoriesSeed {

    val TEMPLATES: List<CategoryTemplate> = emptyList()

    fun buildCategoriesForUser(userId: String): List<CategoryEntity> = emptyList()

    fun buildSampleExpensesForDemoUser(
        userId: String,
        categories: List<CategoryEntity>,
        todayIso: String = DateRangeEngine.todayIso()
    ): List<ExpenseEntity> = emptyList()

    fun buildSampleRecurringForDemoUser(
        userId: String,
        categories: List<CategoryEntity>
    ): List<RecurringExpenseEntity> = emptyList()

    fun buildSampleBudgetsForDemoUser(
        userId: String,
        categories: List<CategoryEntity>
    ): List<BudgetEntity> = emptyList()
}
