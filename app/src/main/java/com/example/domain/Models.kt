package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class ReportingPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    CURRENT_QUARTER("Current Quarter"),
    PREVIOUS_QUARTER("Previous Quarter"),
    CURRENT_HALF_YEAR("Current Half-Year"),
    PREVIOUS_HALF_YEAR("Previous Half-Year"),
    THIS_YEAR("This Year"),
    LAST_YEAR("Last Year"),
    ALL_TIME("All Time"),
    CUSTOM("Custom Date Range")
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    CASH("Cash"),
    NET_BANKING("Net Banking"),
    BANK_TRANSFER("Bank Transfer"),
    WALLET("Digital Wallet")
}

enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    REFUND("Refund"),
    REIMBURSEMENT("Reimbursement")
}

enum class TransactionStatus {
    ACTIVE,
    VOIDED,
    DELETED
}

enum class RecurrenceFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class RecurrenceStatus(val label: String) {
    ACTIVE("Active"),
    PAUSED("Paused"),
    CANCELLED("Cancelled")
}

enum class CurrencyOption(val code: String, val symbol: String, val label: String) {
    INR("INR", "₹", "Indian Rupee (₹)"),
    USD("USD", "$", "US Dollar ($)"),
    EUR("EUR", "€", "Euro (€)"),
    GBP("GBP", "£", "British Pound (£)"),
    JPY("JPY", "¥", "Japanese Yen (¥)"),
    AED("AED", "AED ", "UAE Dirham (AED)");

    companion object {
        fun fromCode(code: String): CurrencyOption =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: INR
    }
}

enum class ThemePreference(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default")
}

enum class SortField(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    AMOUNT_DESC("Amount (High to Low)"),
    AMOUNT_ASC("Amount (Low to High)")
}

enum class GroupByMode(val label: String) {
    NONE("Ungrouped"),
    DAY("By Day"),
    WEEK("By Week"),
    MONTH("By Month")
}

/**
 * Decimal-safe monetary conversion utilities.
 * All monetary amounts are stored as minor units (cents/paise: Long, where 100 = 1.00 major unit)
 * and manipulated using BigDecimal with RoundingMode.HALF_UP to eliminate floating-point errors.
 */
object MoneyUtils {
    private val HUNDRED = BigDecimal(100)

    fun toMinorUnits(amountStr: String): Long? {
        val cleaned = amountStr.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            if (bd <= BigDecimal.ZERO) null else bd.multiply(HUNDRED).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun toMinorUnitsAllowZero(amountStr: String): Long? {
        val cleaned = amountStr.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            if (bd < BigDecimal.ZERO) null else bd.multiply(HUNDRED).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun fromMajorDouble(value: Double): Long {
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .toLong()
    }

    fun toBigDecimal(minorUnits: Long): BigDecimal {
        return BigDecimal.valueOf(minorUnits).divide(HUNDRED, 2, RoundingMode.HALF_UP)
    }

    fun formatMoney(minorUnits: Long, currencyCode: String = "INR", showDecimals: Boolean = true): String {
        val currency = CurrencyOption.fromCode(currencyCode)
        val isNegative = minorUnits < 0
        val absMinor = kotlin.math.abs(minorUnits)
        val bd = toBigDecimal(absMinor)
        val symbols = DecimalFormatSymbols(Locale.US)
        val hasFraction = (absMinor % 100L) != 0L
        val pattern = if (showDecimals && hasFraction) "#,##0.00" else if (showDecimals) "#,##0.00" else "#,##0"
        val formatter = DecimalFormat(pattern, symbols)
        val formattedNumber = formatter.format(bd)
        return if (isNegative) "-${currency.symbol}$formattedNumber" else "${currency.symbol}$formattedNumber"
    }

    fun formatCompact(minorUnits: Long, currencyCode: String = "INR"): String {
        val hasFraction = (kotlin.math.abs(minorUnits) % 100L) != 0L
        return formatMoney(minorUnits, currencyCode, showDecimals = hasFraction)
    }

    fun percentage(partMinor: Long, totalMinor: Long): BigDecimal {
        if (totalMinor <= 0L || partMinor <= 0L) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        return BigDecimal.valueOf(partMinor)
            .multiply(HUNDRED)
            .divide(BigDecimal.valueOf(totalMinor), 2, RoundingMode.HALF_UP)
    }

    fun percentageChange(currentMinor: Long, previousMinor: Long): BigDecimal? {
        if (previousMinor <= 0L) return null
        val diff = BigDecimal.valueOf(currentMinor - previousMinor)
        return diff.multiply(HUNDRED)
            .divide(BigDecimal.valueOf(previousMinor), 2, RoundingMode.HALF_UP)
    }
}

data class ExpenseFilterState(
    val searchQuery: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedSubcategoryIds: Set<String> = emptySet(),
    val selectedPaymentMethods: Set<String> = emptySet(),
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val startDateIso: String? = null, // yyyy-MM-dd inclusive
    val endDateIso: String? = null,   // yyyy-MM-dd inclusive
    val sortField: SortField = SortField.DATE_DESC,
    val groupByMode: GroupByMode = GroupByMode.DAY,
    val page: Int = 1,
    val pageSize: Int = 25
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: Map<String, String> = emptyMap()
)

object ExpenseValidator {
    fun validateExpenseInput(
        title: String,
        amountText: String,
        categoryId: String,
        expenseDateIso: String,
        paymentMethod: String
    ): ValidationResult {
        val errors = mutableMapOf<String, String>()
        if (title.trim().isEmpty()) {
            errors["title"] = "Expense title is required."
        } else if (title.trim().length > 120) {
            errors["title"] = "Title must be 120 characters or fewer."
        }

        val amountMinor = MoneyUtils.toMinorUnits(amountText)
        if (amountMinor == null || amountMinor <= 0L) {
            errors["amount"] = "Enter a valid positive amount (e.g., 1500 or 49.95)."
        } else if (amountMinor > 999_999_999_00L) {
            errors["amount"] = "Amount exceeds maximum allowed limit."
        }

        if (categoryId.trim().isEmpty()) {
            errors["category"] = "Please select a category."
        }

        if (!DateRangeEngine.isValidIsoDate(expenseDateIso)) {
            errors["date"] = "Select a valid date in YYYY-MM-DD format."
        }

        if (paymentMethod.trim().isEmpty()) {
            errors["paymentMethod"] = "Select a payment method."
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }
}
