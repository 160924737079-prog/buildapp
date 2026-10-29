package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val email: String,
    val passwordHash: String,
    val emailVerified: Boolean = true,
    val fullName: String,
    val preferredCurrency: String = "INR",
    val dateFormat: String = "dd MMM yyyy",
    val timezone: String = "Asia/Kolkata",
    val themeMode: String = "LIGHT",
    val overallMonthlyBudgetMinor: Long = 50_000_00L, // ₹50,000.00 default
    val aiConsentGranted: Boolean = false,
    val aiEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index("userId"), Index("parentCategoryId")]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val parentCategoryId: String? = null, // null for top-level Category, non-null for Subcategory
    val colorHex: String = "#10B981",
    val iconName: String = "shopping_bag",
    val monthlyBudgetMinor: Long? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    indices = [Index("userId", "expenseDateIso"), Index("userId", "categoryId")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val title: String,
    val amountMinor: Long, // stored in minor units (paise/cents) for exact decimal math
    val currency: String = "INR",
    val expenseDateIso: String, // yyyy-MM-dd
    val paymentMethod: String = "UPI",
    val merchant: String = "",
    val description: String = "",
    val notes: String = "",
    val tags: String = "",
    val receiptUri: String? = null,
    val transactionType: String = "EXPENSE", // EXPENSE, INCOME, TRANSFER, REFUND, REIMBURSEMENT
    val status: String = "ACTIVE", // ACTIVE, VOIDED, DELETED
    val recurringSourceId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    indices = [Index("userId", "startDateIso", "endDateIso")]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val categoryId: String? = null, // null = overall monthly budget
    val amountMinor: Long,
    val startDateIso: String,
    val endDateIso: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recurring_expenses",
    indices = [Index("userId", "nextDueDateIso")]
)
data class RecurringExpenseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val title: String,
    val amountMinor: Long,
    val paymentMethod: String = "UPI",
    val merchant: String = "",
    val frequency: String = "MONTHLY", // DAILY, WEEKLY, MONTHLY, YEARLY
    val nextDueDateIso: String,
    val lastGeneratedDateIso: String? = null,
    val active: Boolean = true,
    val status: String = "ACTIVE", // ACTIVE, PAUSED, CANCELLED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface ExpenseProDao {
    // Profiles
    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    suspend fun getProfileById(userId: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getProfileByEmail(email: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :userId")
    suspend fun deleteProfile(userId: String)

    // Categories (Strict RLS userId scoping)
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun observeCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    suspend fun getCategoriesForUser(userId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :categoryId AND userId = :userId")
    suspend fun deleteCategory(categoryId: String, userId: String)

    // Expenses (Strict RLS userId scoping)
    @Query("SELECT * FROM expenses WHERE userId = :userId AND status = 'ACTIVE' ORDER BY expenseDateIso DESC, createdAt DESC")
    fun observeActiveExpenses(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND status = 'ACTIVE' ORDER BY expenseDateIso DESC, createdAt DESC")
    suspend fun getActiveExpensesForUser(userId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId AND userId = :userId LIMIT 1")
    suspend fun getExpenseByIdForUser(expenseId: String, userId: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :expenseId AND userId = :userId")
    suspend fun hardDeleteExpense(expenseId: String, userId: String)

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAllUserExpenses(userId: String)

    // Budgets (Strict RLS userId scoping)
    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY startDateIso DESC")
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY startDateIso DESC")
    suspend fun getBudgetsForUser(userId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgets(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :budgetId AND userId = :userId")
    suspend fun deleteBudget(budgetId: String, userId: String)

    // Recurring Expenses (Strict RLS userId scoping)
    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId ORDER BY nextDueDateIso ASC")
    fun observeRecurringExpenses(userId: String): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE userId = :userId ORDER BY nextDueDateIso ASC")
    suspend fun getRecurringExpensesForUser(userId: String): List<RecurringExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringExpense(item: RecurringExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringExpenses(items: List<RecurringExpenseEntity>)

    @Query("DELETE FROM recurring_expenses WHERE id = :id AND userId = :userId")
    suspend fun deleteRecurringExpense(id: String, userId: String)

    // Account Wipe
    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun deleteAllUserCategories(userId: String)

    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllUserBudgets(userId: String)

    @Query("DELETE FROM recurring_expenses WHERE userId = :userId")
    suspend fun deleteAllUserRecurring(userId: String)
}

@Database(
    entities = [
        ProfileEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        BudgetEntity::class,
        RecurringExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ExpenseProDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseProDao
}
