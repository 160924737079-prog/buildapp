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

object DefaultCategoriesSeed {

    val TEMPLATES: List<CategoryTemplate> = listOf(
        CategoryTemplate(
            slug = "groceries",
            name = "Groceries",
            colorHex = "#10B981",
            iconName = "shopping_cart",
            defaultMonthlyBudgetMinor = 12_000_00L,
            subcategories = listOf(
                "Vegetables",
                "Fruits",
                "Rice and Grains",
                "Grocery Store",
                "Snacks",
                "Other Groceries"
            )
        ),
        CategoryTemplate(
            slug = "dairy",
            name = "Milk and Dairy",
            colorHex = "#06B6D4",
            iconName = "water_drop",
            defaultMonthlyBudgetMinor = 3_500_00L,
            subcategories = listOf(
                "Milk",
                "Curd",
                "Butter",
                "Cheese",
                "Other Dairy Products"
            )
        ),
        CategoryTemplate(
            slug = "transport",
            name = "Fuel and Transportation",
            colorHex = "#F59E0B",
            iconName = "local_gas_station",
            defaultMonthlyBudgetMinor = 8_000_00L,
            subcategories = listOf(
                "Petrol",
                "Diesel",
                "Public Transport",
                "Taxi and Cab",
                "Vehicle Maintenance",
                "Parking",
                "Other Transportation"
            )
        ),
        CategoryTemplate(
            slug = "bills",
            name = "Household Bills",
            colorHex = "#3B82F6",
            iconName = "receipt_long",
            defaultMonthlyBudgetMinor = 7_500_00L,
            subcategories = listOf(
                "Electricity Bill",
                "Water Bill",
                "Gas Bill",
                "Mobile Recharge",
                "Telephone Bill",
                "Internet Bill",
                "Cable and DTH",
                "Other Utility Bills"
            )
        ),
        CategoryTemplate(
            slug = "housing",
            name = "Housing",
            colorHex = "#6366F1",
            iconName = "home",
            defaultMonthlyBudgetMinor = 22_000_00L,
            subcategories = listOf(
                "House Rent",
                "Maintenance",
                "Repairs",
                "Home Improvement",
                "Other Housing Expenses"
            )
        ),
        CategoryTemplate(
            slug = "dining",
            name = "Food and Dining",
            colorHex = "#EC4899",
            iconName = "restaurant",
            defaultMonthlyBudgetMinor = 6_000_00L,
            subcategories = listOf(
                "Restaurants",
                "Food Delivery",
                "Tea and Coffee",
                "Fast Food",
                "Other Food Expenses"
            )
        ),
        CategoryTemplate(
            slug = "healthcare",
            name = "Healthcare",
            colorHex = "#EF4444",
            iconName = "medical_services",
            defaultMonthlyBudgetMinor = 5_000_00L,
            subcategories = listOf(
                "Doctor Visits",
                "Medicines",
                "Medical Tests",
                "Health Insurance",
                "Other Healthcare Expenses"
            )
        ),
        CategoryTemplate(
            slug = "education",
            name = "Education",
            colorHex = "#8B5CF6",
            iconName = "school",
            defaultMonthlyBudgetMinor = 8_000_00L,
            subcategories = listOf(
                "Tuition Fees",
                "Books",
                "Courses",
                "Stationery",
                "Examination Fees",
                "Other Education Expenses"
            )
        ),
        CategoryTemplate(
            slug = "personal",
            name = "Personal Expenses",
            colorHex = "#14B8A6",
            iconName = "person",
            defaultMonthlyBudgetMinor = 6_000_00L,
            subcategories = listOf(
                "Clothing",
                "Footwear",
                "Personal Care",
                "Entertainment",
                "Shopping",
                "Other Personal Expenses"
            )
        ),
        CategoryTemplate(
            slug = "financial",
            name = "Financial Obligations",
            colorHex = "#0EA5E9",
            iconName = "account_balance",
            defaultMonthlyBudgetMinor = 15_000_00L,
            subcategories = listOf(
                "Loan Payments",
                "Credit Card Payments",
                "Insurance Premiums",
                "Bank Charges",
                "Other Financial Expenses"
            )
        ),
        CategoryTemplate(
            slug = "travel",
            name = "Travel",
            colorHex = "#F97316",
            iconName = "flight",
            defaultMonthlyBudgetMinor = 10_000_00L,
            subcategories = listOf(
                "Train Tickets",
                "Flight Tickets",
                "Hotel Accommodation",
                "Travel Food",
                "Other Travel Expenses"
            )
        ),
        CategoryTemplate(
            slug = "family_misc",
            name = "Family and Miscellaneous",
            colorHex = "#64748B",
            iconName = "family_restroom",
            defaultMonthlyBudgetMinor = 5_000_00L,
            subcategories = listOf(
                "Family Expenses",
                "Gifts",
                "Donations",
                "Emergency Expenses",
                "Other Expenses"
            )
        )
    )

    fun buildCategoriesForUser(userId: String): List<CategoryEntity> {
        val result = mutableListOf<CategoryEntity>()
        for (tpl in TEMPLATES) {
            val parentId = "cat_${userId}_${tpl.slug}"
            result.add(
                CategoryEntity(
                    id = parentId,
                    userId = userId,
                    name = tpl.name,
                    parentCategoryId = null,
                    colorHex = tpl.colorHex,
                    iconName = tpl.iconName,
                    monthlyBudgetMinor = tpl.defaultMonthlyBudgetMinor,
                    isArchived = false
                )
            )
            tpl.subcategories.forEachIndexed { idx, subName ->
                val subSlug = subName.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                result.add(
                    CategoryEntity(
                        id = "sub_${userId}_${tpl.slug}_${idx}_$subSlug",
                        userId = userId,
                        name = subName,
                        parentCategoryId = parentId,
                        colorHex = tpl.colorHex,
                        iconName = tpl.iconName,
                        monthlyBudgetMinor = null,
                        isArchived = false
                    )
                )
            }
        }
        return result
    }

    fun buildSampleExpensesForDemoUser(
        userId: String,
        categories: List<CategoryEntity>
    ): List<ExpenseEntity> {
        fun catId(name: String): String =
            categories.firstOrNull { it.parentCategoryId == null && it.name.equals(name, true) }?.id
                ?: "cat_${userId}_groceries"

        fun subId(parentName: String, subName: String): String? {
            val pid = catId(parentName)
            return categories.firstOrNull { it.parentCategoryId == pid && it.name.equals(subName, true) }?.id
        }

        return listOf(
            ExpenseEntity(
                id = "demo_tx_1",
                userId = userId,
                categoryId = catId("Groceries"),
                subcategoryId = subId("Groceries", "Grocery Store"),
                title = "Monthly Groceries",
                amountMinor = 1_500_00L,
                currency = "INR",
                expenseDateIso = "2026-10-29",
                paymentMethod = "UPI",
                merchant = "FreshMart Supermarket",
                description = "Monthly household staples, pulses, and cooking oil",
                notes = "Used UPI cashback offer",
                tags = "groceries,monthly,essential"
            ),
            ExpenseEntity(
                id = "demo_tx_2",
                userId = userId,
                categoryId = catId("Milk and Dairy"),
                subcategoryId = subId("Milk and Dairy", "Milk"),
                title = "Morning Farm Fresh Milk & Curd",
                amountMinor = 180_00L,
                currency = "INR",
                expenseDateIso = "2026-10-29",
                paymentMethod = "UPI",
                merchant = "Nandini Dairy Parlour",
                description = "2L full cream milk and 500g curd",
                notes = "Daily dairy purchase",
                tags = "dairy,milk"
            ),
            ExpenseEntity(
                id = "demo_tx_3",
                userId = userId,
                categoryId = catId("Fuel and Transportation"),
                subcategoryId = subId("Fuel and Transportation", "Petrol"),
                title = "Car Petrol Refill",
                amountMinor = 2_450_00L,
                currency = "INR",
                expenseDateIso = "2026-10-28",
                paymentMethod = "Credit Card",
                merchant = "IndianOil Highway Station",
                description = "Full tank petrol refill",
                notes = "Surcharge waiver applied",
                tags = "petrol,commute"
            ),
            ExpenseEntity(
                id = "demo_tx_4",
                userId = userId,
                categoryId = catId("Household Bills"),
                subcategoryId = subId("Household Bills", "Electricity Bill"),
                title = "October Electricity Bill",
                amountMinor = 2_840_00L,
                currency = "INR",
                expenseDateIso = "2026-10-25",
                paymentMethod = "Net Banking",
                merchant = "BESCOM Power Utility",
                description = "Monthly residential electricity bill (342 kWh)",
                notes = "Paid before due date",
                tags = "electricity,utility,bills"
            ),
            ExpenseEntity(
                id = "demo_tx_5",
                userId = userId,
                categoryId = catId("Household Bills"),
                subcategoryId = subId("Household Bills", "Internet Bill"),
                title = "Fiber Broadband 300 Mbps",
                amountMinor = 1_178_00L,
                currency = "INR",
                expenseDateIso = "2026-10-22",
                paymentMethod = "Credit Card",
                merchant = "Airtel Xstream Fiber",
                description = "High-speed home broadband plan",
                notes = "Includes GST",
                tags = "internet,wifi,work"
            ),
            ExpenseEntity(
                id = "demo_tx_6",
                userId = userId,
                categoryId = catId("Housing"),
                subcategoryId = subId("Housing", "House Rent"),
                title = "October Apartment Rent",
                amountMinor = 18_500_00L,
                currency = "INR",
                expenseDateIso = "2026-10-05",
                paymentMethod = "Bank Transfer",
                merchant = "GreenView Residency",
                description = "Monthly 2BHK apartment rent",
                notes = "NEFT reference #98412",
                tags = "rent,housing"
            ),
            ExpenseEntity(
                id = "demo_tx_7",
                userId = userId,
                categoryId = catId("Food and Dining"),
                subcategoryId = subId("Food and Dining", "Restaurants"),
                title = "Family Weekend Dinner",
                amountMinor = 1_860_00L,
                currency = "INR",
                expenseDateIso = "2026-10-18",
                paymentMethod = "Credit Card",
                merchant = "Spice Terrace Bistro",
                description = "Dinner with family",
                notes = "10% dining reward",
                tags = "dining,family"
            ),
            ExpenseEntity(
                id = "demo_tx_8",
                userId = userId,
                categoryId = catId("Healthcare"),
                subcategoryId = subId("Healthcare", "Medicines"),
                title = "Monthly Vitamins & Pharmacy",
                amountMinor = 940_00L,
                currency = "INR",
                expenseDateIso = "2026-10-14",
                paymentMethod = "UPI",
                merchant = "Apollo Pharmacy",
                description = "Multivitamins and first-aid supplies",
                notes = "",
                tags = "health,pharmacy"
            ),
            ExpenseEntity(
                id = "demo_tx_9",
                userId = userId,
                categoryId = catId("Education"),
                subcategoryId = subId("Education", "Courses"),
                title = "Cloud Architecture Certification Course",
                amountMinor = 3_200_00L,
                currency = "INR",
                expenseDateIso = "2026-10-10",
                paymentMethod = "Credit Card",
                merchant = "Coursera / TechAcademy",
                description = "Annual professional learning module",
                notes = "",
                tags = "education,upskilling"
            ),
            // Previous month (September 2026) for MoM & Q3 comparisons
            ExpenseEntity(
                id = "demo_tx_10",
                userId = userId,
                categoryId = catId("Housing"),
                subcategoryId = subId("Housing", "House Rent"),
                title = "September Apartment Rent",
                amountMinor = 18_500_00L,
                currency = "INR",
                expenseDateIso = "2026-09-05",
                paymentMethod = "Bank Transfer",
                merchant = "GreenView Residency",
                description = "Monthly apartment rent",
                notes = "",
                tags = "rent,housing"
            ),
            ExpenseEntity(
                id = "demo_tx_11",
                userId = userId,
                categoryId = catId("Groceries"),
                subcategoryId = subId("Groceries", "Vegetables"),
                title = "September Supermarket & Produce",
                amountMinor = 6_400_00L,
                currency = "INR",
                expenseDateIso = "2026-09-16",
                paymentMethod = "UPI",
                merchant = "FreshMart Supermarket",
                description = "Groceries, fruits, and grains",
                notes = "",
                tags = "groceries"
            ),
            ExpenseEntity(
                id = "demo_tx_12",
                userId = userId,
                categoryId = catId("Fuel and Transportation"),
                subcategoryId = subId("Fuel and Transportation", "Petrol"),
                title = "September Petrol Refills",
                amountMinor = 2_100_00L,
                currency = "INR",
                expenseDateIso = "2026-09-21",
                paymentMethod = "Credit Card",
                merchant = "HP Auto Care",
                description = "Petrol for city commute",
                notes = "",
                tags = "petrol"
            ),
            ExpenseEntity(
                id = "demo_tx_13",
                userId = userId,
                categoryId = catId("Household Bills"),
                subcategoryId = subId("Household Bills", "Electricity Bill"),
                title = "September Electricity Bill",
                amountMinor = 2_610_00L,
                currency = "INR",
                expenseDateIso = "2026-09-24",
                paymentMethod = "Net Banking",
                merchant = "BESCOM Power Utility",
                description = "Electricity bill",
                notes = "",
                tags = "electricity,bills"
            ),
            // Earlier in 2026 (H1: March & May 2026) and 2025 for Half-Yearly & YoY charts
            ExpenseEntity(
                id = "demo_tx_14",
                userId = userId,
                categoryId = catId("Travel"),
                subcategoryId = subId("Travel", "Flight Tickets"),
                title = "Spring Family Vacation Flights",
                amountMinor = 14_200_00L,
                currency = "INR",
                expenseDateIso = "2026-03-14",
                paymentMethod = "Credit Card",
                merchant = "IndiGo Airlines",
                description = "Round-trip domestic flights",
                notes = "Q1 travel",
                tags = "travel,flights"
            ),
            ExpenseEntity(
                id = "demo_tx_15",
                userId = userId,
                categoryId = catId("Financial Obligations"),
                subcategoryId = subId("Financial Obligations", "Insurance Premiums"),
                title = "Annual Term & Health Insurance Renewal",
                amountMinor = 11_500_00L,
                currency = "INR",
                expenseDateIso = "2026-05-19",
                paymentMethod = "Net Banking",
                merchant = "HDFC ERGO Insurance",
                description = "Annual policy renewal",
                notes = "Q2 H1 protection",
                tags = "insurance,finance"
            ),
            ExpenseEntity(
                id = "demo_tx_16",
                userId = userId,
                categoryId = catId("Housing"),
                subcategoryId = subId("Housing", "House Rent"),
                title = "2025 Annual Housing Summary Entry",
                amountMinor = 28_000_00L,
                currency = "INR",
                expenseDateIso = "2025-10-15",
                paymentMethod = "Bank Transfer",
                merchant = "GreenView Residency",
                description = "Prior year comparative benchmark",
                notes = "",
                tags = "housing,2025"
            )
        )
    }

    fun buildSampleRecurringForDemoUser(
        userId: String,
        categories: List<CategoryEntity>
    ): List<RecurringExpenseEntity> {
        fun catId(name: String): String =
            categories.firstOrNull { it.parentCategoryId == null && it.name.equals(name, true) }?.id
                ?: "cat_${userId}_housing"

        return listOf(
            RecurringExpenseEntity(
                id = "rec_demo_1",
                userId = userId,
                categoryId = catId("Housing"),
                title = "Monthly Apartment Rent",
                amountMinor = 18_500_00L,
                paymentMethod = "Bank Transfer",
                merchant = "GreenView Residency",
                frequency = "MONTHLY",
                nextDueDateIso = "2026-11-05",
                lastGeneratedDateIso = "2026-10-05",
                active = true,
                status = "ACTIVE"
            ),
            RecurringExpenseEntity(
                id = "rec_demo_2",
                userId = userId,
                categoryId = catId("Household Bills"),
                title = "BESCOM Electricity Bill",
                amountMinor = 2_800_00L,
                paymentMethod = "Net Banking",
                merchant = "BESCOM Power Utility",
                frequency = "MONTHLY",
                nextDueDateIso = "2026-11-25",
                lastGeneratedDateIso = "2026-10-25",
                active = true,
                status = "ACTIVE"
            ),
            RecurringExpenseEntity(
                id = "rec_demo_3",
                userId = userId,
                categoryId = catId("Household Bills"),
                title = "Airtel Fiber Internet Bill",
                amountMinor = 1_178_00L,
                paymentMethod = "Credit Card",
                merchant = "Airtel Xstream Fiber",
                frequency = "MONTHLY",
                nextDueDateIso = "2026-11-22",
                lastGeneratedDateIso = "2026-10-22",
                active = true,
                status = "ACTIVE"
            ),
            RecurringExpenseEntity(
                id = "rec_demo_4",
                userId = userId,
                categoryId = catId("Education"),
                title = "Tuition & Skill Subscription",
                amountMinor = 1_499_00L,
                paymentMethod = "UPI",
                merchant = "TechAcademy Pro",
                frequency = "MONTHLY",
                nextDueDateIso = "2026-11-10",
                lastGeneratedDateIso = "2026-10-10",
                active = true,
                status = "ACTIVE"
            )
        )
    }

    fun buildSampleBudgetsForDemoUser(
        userId: String,
        categories: List<CategoryEntity>
    ): List<BudgetEntity> {
        return categories
            .filter { it.parentCategoryId == null && (it.monthlyBudgetMinor ?: 0L) > 0L }
            .map { cat ->
                BudgetEntity(
                    id = "bgt_${userId}_${cat.id}",
                    userId = userId,
                    categoryId = cat.id,
                    amountMinor = cat.monthlyBudgetMinor ?: 5_000_00L,
                    startDateIso = "2026-10-01",
                    endDateIso = "2026-10-31"
                )
            }
    }
}
