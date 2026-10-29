package com.example.domain

import com.example.data.CategoryEntity
import com.example.data.ExpenseEntity
import com.example.data.RecurringExpenseEntity
import java.math.BigDecimal

data class AiInsightResponse(
    val question: String,
    val headlineAnswer: String,
    val detailedAnalysis: String,
    val keyMetrics: List<Pair<String, String>>,
    val recommendation: String,
    val computedLocally: Boolean = true
)

/**
 * Deterministic Financial Insights Engine.
 * Calculates all financial figures strictly using application code so statistics are never hallucinated.
 * Works 100% offline or when external AI services are disabled/unavailable.
 */
object AiInsightsEngine {

    val SUGGESTED_QUESTIONS: List<String> = listOf(
        "How much did I spend this month?",
        "Which category costs me the most?",
        "How much did I spend on groceries this quarter?",
        "Did my petrol expenses increase?",
        "What was my total electricity bill expense this year?",
        "Which categories increased compared with the previous month?",
        "What are my most significant recurring expenses?"
    )

    fun answerQuestion(
        question: String,
        allExpenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        recurringExpenses: List<RecurringExpenseEntity>,
        referenceDateIso: String,
        currencyCode: String,
        overallMonthlyBudgetMinor: Long
    ): AiInsightResponse {
        val q = question.trim().lowercase()
        val refParts = referenceDateIso.split("-")
        val year = refParts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = refParts.getOrNull(1)?.toIntOrNull() ?: 10
        val currentQuarter = ((month - 1) / 3) + 1

        val thisMonthBounds = DateRangeEngine.monthBounds(year, month)
        val lastMonthBounds = if (month == 1) DateRangeEngine.monthBounds(year - 1, 12) else DateRangeEngine.monthBounds(year, month - 1)
        val quarterBounds = DateRangeEngine.quarterBounds(year, currentQuarter)
        val yearBounds = DateRangeEngine.yearBounds(year)

        val thisMonthExpenses = FinancialCalculator.filterQualifyingExpenses(
            allExpenses, thisMonthBounds.startDateIso, thisMonthBounds.endDateIso
        )
        val lastMonthExpenses = FinancialCalculator.filterQualifyingExpenses(
            allExpenses, lastMonthBounds.startDateIso, lastMonthBounds.endDateIso
        )
        val quarterExpenses = FinancialCalculator.filterQualifyingExpenses(
            allExpenses, quarterBounds.startDateIso, quarterBounds.endDateIso
        )
        val yearExpenses = FinancialCalculator.filterQualifyingExpenses(
            allExpenses, yearBounds.startDateIso, yearBounds.endDateIso
        )

        val catMap = categories.associateBy { it.id }

        return when {
            q.contains("grocer") && q.contains("quarter") -> {
                val groceryCatIds = categories.filter {
                    it.name.contains("Grocer", ignoreCase = true)
                }.map { it.id }.toSet()
                val matches = quarterExpenses.filter {
                    it.categoryId in groceryCatIds ||
                        (it.subcategoryId != null && it.subcategoryId in groceryCatIds) ||
                        it.title.contains("grocer", ignoreCase = true)
                }
                val sumMinor = matches.sumOf { it.amountMinor }
                val qTotal = quarterExpenses.sumOf { it.amountMinor }
                val share = MoneyUtils.percentage(sumMinor, qTotal)
                AiInsightResponse(
                    question = question,
                    headlineAnswer = "You spent ${MoneyUtils.formatMoney(sumMinor, currencyCode)} on Groceries in ${quarterBounds.label}.",
                    detailedAnalysis = "Across ${matches.size} grocery transactions between ${quarterBounds.startDateIso} and ${quarterBounds.endDateIso}, Groceries accounted for ${share.toPlainString()}% of your total quarterly spending (${MoneyUtils.formatMoney(qTotal, currencyCode)}).",
                    keyMetrics = listOf(
                        "Quarter Grocery Total" to MoneyUtils.formatMoney(sumMinor, currencyCode),
                        "Transactions" to "${matches.size}",
                        "Share of ${quarterBounds.label}" to "${share.toPlainString()}%"
                    ),
                    recommendation = if (share.toDouble() > 25.0) {
                        "Groceries represent over a quarter of your spending this quarter. Buying staples in bulk can trim 8–12%."
                    } else {
                        "Your quarterly grocery spending is well within a balanced household allocation."
                    }
                )
            }

            q.contains("petrol") || q.contains("fuel") -> {
                val thisMonthPetrol = thisMonthExpenses.filter { tx ->
                    val subName = tx.subcategoryId?.let { catMap[it]?.name }.orEmpty()
                    val catName = catMap[tx.categoryId]?.name.orEmpty()
                    tx.title.contains("petrol", true) ||
                        subName.contains("petrol", true) ||
                        catName.contains("Fuel", true)
                }.sumOf { it.amountMinor }

                val lastMonthPetrol = lastMonthExpenses.filter { tx ->
                    val subName = tx.subcategoryId?.let { catMap[it]?.name }.orEmpty()
                    val catName = catMap[tx.categoryId]?.name.orEmpty()
                    tx.title.contains("petrol", true) ||
                        subName.contains("petrol", true) ||
                        catName.contains("Fuel", true)
                }.sumOf { it.amountMinor }

                val deltaMinor = thisMonthPetrol - lastMonthPetrol
                val pctChange = MoneyUtils.percentageChange(thisMonthPetrol, lastMonthPetrol)
                val statusWord = when {
                    deltaMinor > 0L -> "increased by ${MoneyUtils.formatMoney(deltaMinor, currencyCode)}" +
                        (pctChange?.let { " (+${it.toPlainString()}%)" } ?: "")
                    deltaMinor < 0L -> "decreased by ${MoneyUtils.formatMoney(-deltaMinor, currencyCode)}" +
                        (pctChange?.let { " (${it.toPlainString()}%)" } ?: "")
                    else -> "remained unchanged"
                }

                AiInsightResponse(
                    question = question,
                    headlineAnswer = "Your petrol & fuel expenses $statusWord compared to last month.",
                    detailedAnalysis = "In ${thisMonthBounds.label}, fuel/petrol spending is ${MoneyUtils.formatMoney(thisMonthPetrol, currencyCode)} versus ${MoneyUtils.formatMoney(lastMonthPetrol, currencyCode)} in ${lastMonthBounds.label}.",
                    keyMetrics = listOf(
                        thisMonthBounds.label to MoneyUtils.formatMoney(thisMonthPetrol, currencyCode),
                        lastMonthBounds.label to MoneyUtils.formatMoney(lastMonthPetrol, currencyCode),
                        "Net Change" to MoneyUtils.formatMoney(deltaMinor, currencyCode)
                    ),
                    recommendation = if (deltaMinor > 0L) {
                        "Fuel expenditure rose this month. Consolidating errands and tracking fuel card cashback can help offset pump costs."
                    } else {
                        "Great job keeping fuel expenses steady or lower than last month."
                    }
                )
            }

            q.contains("electric") -> {
                val elecMatches = yearExpenses.filter { tx ->
                    val subName = tx.subcategoryId?.let { catMap[it]?.name }.orEmpty()
                    tx.title.contains("electric", true) ||
                        subName.contains("electric", true) ||
                        tx.merchant.contains("bescom", true)
                }
                val sumMinor = elecMatches.sumOf { it.amountMinor }
                val avgBill = if (elecMatches.isEmpty()) 0L else sumMinor / elecMatches.size
                AiInsightResponse(
                    question = question,
                    headlineAnswer = "Your total electricity bill expense in $year is ${MoneyUtils.formatMoney(sumMinor, currencyCode)}.",
                    detailedAnalysis = "Calculated from ${elecMatches.size} recorded electricity bill payment(s) in calendar year $year, with an average bill of ${MoneyUtils.formatMoney(avgBill, currencyCode)}.",
                    keyMetrics = listOf(
                        "$year Electricity Total" to MoneyUtils.formatMoney(sumMinor, currencyCode),
                        "Bills Recorded" to "${elecMatches.size}",
                        "Average Bill" to MoneyUtils.formatMoney(avgBill, currencyCode)
                    ),
                    recommendation = "Monitor peak summer/autumn appliance usage to keep monthly utility bills predictable."
                )
            }

            q.contains("increased") && q.contains("category") || q.contains("categories increased") -> {
                val thisByCat = thisMonthExpenses.groupBy { it.categoryId }.mapValues { it.value.sumOf { tx -> tx.amountMinor } }
                val lastByCat = lastMonthExpenses.groupBy { it.categoryId }.mapValues { it.value.sumOf { tx -> tx.amountMinor } }
                val allCatIds = (thisByCat.keys + lastByCat.keys).distinct()
                val increases = allCatIds.mapNotNull { cid ->
                    val cur = thisByCat[cid] ?: 0L
                    val prev = lastByCat[cid] ?: 0L
                    if (cur > prev) {
                        val catName = catMap[cid]?.name ?: "Category"
                        Triple(catName, cur - prev, cur)
                    } else null
                }.sortedByDescending { it.second }

                if (increases.isEmpty()) {
                    AiInsightResponse(
                        question = question,
                        headlineAnswer = "No categories increased in ${thisMonthBounds.label} compared with ${lastMonthBounds.label}.",
                        detailedAnalysis = "Every active category is currently at or below last month's spending level.",
                        keyMetrics = listOf("Categories Increased" to "0"),
                        recommendation = "You are maintaining strong month-over-month spending discipline."
                    )
                } else {
                    val topNames = increases.joinToString(", ") { "${it.first} (+${MoneyUtils.formatMoney(it.second, currencyCode)})" }
                    AiInsightResponse(
                        question = question,
                        headlineAnswer = "${increases.size} category(ies) increased this month: $topNames.",
                        detailedAnalysis = "Comparing ${thisMonthBounds.label} against ${lastMonthBounds.label}, the largest increase was in ${increases.first().first} (+${MoneyUtils.formatMoney(increases.first().second, currencyCode)}).",
                        keyMetrics = increases.take(3).map { (name, diff, _) ->
                            name to "+${MoneyUtils.formatMoney(diff, currencyCode)}"
                        },
                        recommendation = "Review discretionary transactions in ${increases.first().first} to stay comfortably within your monthly budget."
                    )
                }
            }

            q.contains("recurring") -> {
                val activeRec = recurringExpenses.filter { it.active && it.status == "ACTIVE" }
                    .sortedByDescending { it.amountMinor }
                val monthlyEquiv = activeRec.sumOf {
                    when (it.frequency) {
                        "DAILY" -> it.amountMinor * 30L
                        "WEEKLY" -> it.amountMinor * 4L
                        "YEARLY" -> it.amountMinor / 12L
                        else -> it.amountMinor
                    }
                }
                val topItem = activeRec.firstOrNull()
                AiInsightResponse(
                    question = question,
                    headlineAnswer = if (topItem != null) {
                        "Your largest recurring expense is ${topItem.title} at ${MoneyUtils.formatMoney(topItem.amountMinor, currencyCode)} (${topItem.frequency.lowercase()})."
                    } else {
                        "You have no active recurring expenses scheduled."
                    },
                    detailedAnalysis = "You have ${activeRec.size} active recurring schedules totaling approximately ${MoneyUtils.formatMoney(monthlyEquiv, currencyCode)} per month.",
                    keyMetrics = activeRec.take(3).map {
                        it.title to "${MoneyUtils.formatMoney(it.amountMinor, currencyCode)} (${it.frequency})"
                    },
                    recommendation = "Audit recurring subscriptions quarterly and pause unused plans before their next renewal date."
                )
            }

            q.contains("which category") || q.contains("most") || q.contains("highest") -> {
                val breakdown = FinancialCalculator.calculateCategoryBreakdown(thisMonthExpenses, categories)
                val top = breakdown.firstOrNull()
                if (top == null) {
                    AiInsightResponse(
                        question = question,
                        headlineAnswer = "No expenses recorded yet for ${thisMonthBounds.label}.",
                        detailedAnalysis = "Add transactions to see your highest-cost category.",
                        keyMetrics = emptyList(),
                        recommendation = "Record daily expenses using Quick Add to track category shares."
                    )
                } else {
                    AiInsightResponse(
                        question = question,
                        headlineAnswer = "${top.categoryName} is your highest spending category at ${MoneyUtils.formatMoney(top.totalMinor, currencyCode)} (${top.percentageOfTotal.toPlainString()}%).",
                        detailedAnalysis = "In ${thisMonthBounds.label}, ${top.categoryName} had ${top.transactionCount} transaction(s), representing ${top.percentageOfTotal.toPlainString()}% of your ${MoneyUtils.formatMoney(thisMonthExpenses.sumOf { it.amountMinor }, currencyCode)} monthly total.",
                        keyMetrics = breakdown.take(3).map {
                            it.categoryName to "${MoneyUtils.formatMoney(it.totalMinor, currencyCode)} (${it.percentageOfTotal.toPlainString()}%)"
                        },
                        recommendation = "Ensure ${top.categoryName} stays aligned with its category budget limit."
                    )
                }
            }

            else -> {
                // Default / "How much did I spend this month?"
                val monthTotal = thisMonthExpenses.sumOf { it.amountMinor }
                val lastTotal = lastMonthExpenses.sumOf { it.amountMinor }
                val remaining = overallMonthlyBudgetMinor - monthTotal
                val util = MoneyUtils.percentage(monthTotal, overallMonthlyBudgetMinor)
                val breakdown = FinancialCalculator.calculateCategoryBreakdown(thisMonthExpenses, categories)
                val topCat = breakdown.firstOrNull()

                AiInsightResponse(
                    question = question,
                    headlineAnswer = "You have spent ${MoneyUtils.formatMoney(monthTotal, currencyCode)} in ${thisMonthBounds.label} across ${thisMonthExpenses.size} transactions.",
                    detailedAnalysis = "You have used ${util.toPlainString()}% of your ${MoneyUtils.formatMoney(overallMonthlyBudgetMinor, currencyCode)} monthly budget, leaving ${MoneyUtils.formatMoney(remaining, currencyCode)}. Last month (${lastMonthBounds.label}) total was ${MoneyUtils.formatMoney(lastTotal, currencyCode)}." +
                        (topCat?.let { " Your top category is ${it.categoryName} (${MoneyUtils.formatMoney(it.totalMinor, currencyCode)})." } ?: ""),
                    keyMetrics = listOf(
                        "This Month Total" to MoneyUtils.formatMoney(monthTotal, currencyCode),
                        "Remaining Budget" to MoneyUtils.formatMoney(remaining, currencyCode),
                        "Budget Used" to "${util.toPlainString()}%",
                        "Transactions" to "${thisMonthExpenses.size}"
                    ),
                    recommendation = if (util > BigDecimal(80)) {
                        "You have exceeded 80% of your monthly budget. Prioritize essential expenses for the remainder of the month."
                    } else {
                        "Your monthly spending pace is healthy and within your target budget."
                    }
                )
            }
        }
    }
}
