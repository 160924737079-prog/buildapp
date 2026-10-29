package com.example.data

import com.example.BuildConfig
import com.example.domain.DateRangeEngine
import com.example.domain.DefaultCategoriesSeed
import com.example.domain.FinancialCalculator
import com.example.domain.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AuthSession(
    val userId: String,
    val email: String,
    val fullName: String,
    val accessToken: String,
    val isSupabaseCloudConnected: Boolean
)

sealed class AuthResult {
    data class Success(val session: AuthSession, val message: String = "Authenticated successfully") : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Repository combining Supabase Auth + PostgreSQL PostgREST API with local Room SQLite persistence.
 * Enforces strict per-user Row Level Security (userId scoping) on every read and write operation.
 */
class ExpenseProRepository(
    private val dao: ExpenseProDao
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val isSupabaseConfigured: Boolean
        get() {
            val url = runCatching { BuildConfig.SUPABASE_URL }.getOrDefault("")
            val key = runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrDefault("")
            return url.isNotBlank() &&
                !url.contains("your-project-id") &&
                key.isNotBlank() &&
                !key.contains("your-supabase-anon-key")
        }

    fun hashPassword(rawPassword: String, emailSalt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("${emailSalt.lowercase().trim()}::$rawPassword".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun ensureDemoAccountSeeded(): ProfileEntity = withContext(Dispatchers.IO) {
        val demoEmail = "alex.morgan@expensepro.ai"
        val existing = dao.getProfileByEmail(demoEmail)
        if (existing != null) {
            return@withContext existing
        }
        val demoUserId = "user_demo_expensepro_01"
        val profile = ProfileEntity(
            id = demoUserId,
            email = demoEmail,
            passwordHash = hashPassword("ExpensePro#2026", demoEmail),
            emailVerified = true,
            fullName = "Alex Morgan",
            preferredCurrency = "INR",
            dateFormat = "dd MMM yyyy",
            timezone = "Asia/Kolkata",
            themeMode = "LIGHT",
            overallMonthlyBudgetMinor = 50_000_00L,
            aiConsentGranted = true,
            aiEnabled = true
        )
        dao.upsertProfile(profile)
        val categories = DefaultCategoriesSeed.buildCategoriesForUser(demoUserId)
        dao.upsertCategories(categories)
        val expenses = DefaultCategoriesSeed.buildSampleExpensesForDemoUser(demoUserId, categories)
        dao.upsertExpenses(expenses)
        val budgets = DefaultCategoriesSeed.buildSampleBudgetsForDemoUser(demoUserId, categories)
        dao.upsertBudgets(budgets)
        val recurring = DefaultCategoriesSeed.buildSampleRecurringForDemoUser(demoUserId, categories)
        dao.upsertRecurringExpenses(recurring)
        profile
    }

    suspend fun registerUser(
        fullName: String,
        email: String,
        password: String,
        preferredCurrency: String = "INR",
        timezone: String = "Asia/Kolkata"
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanName = fullName.trim()
        if (cleanName.length < 2) {
            return@withContext AuthResult.Error("Full name must be at least 2 characters.")
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters.")
        }

        val existingLocal = dao.getProfileByEmail(cleanEmail)
        if (existingLocal != null) {
            return@withContext AuthResult.Error("An account with this email already exists. Please sign in.")
        }

        var supabaseUserId: String? = null
        var accessToken = "local_jwt_${UUID.randomUUID()}"
        var cloudConnected = false

        if (isSupabaseConfigured) {
            runCatching {
                val payload = JSONObject().apply {
                    put("email", cleanEmail)
                    put("password", password)
                    put("data", JSONObject().put("full_name", cleanName))
                }
                val req = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/signup")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val json = JSONObject(bodyStr)
                        supabaseUserId = json.optJSONObject("user")?.optString("id")
                            ?: json.optString("id").takeIf { it.isNotEmpty() }
                        accessToken = json.optString("access_token", accessToken)
                        cloudConnected = true
                    }
                }
            }
        }

        val userId = supabaseUserId ?: "user_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val profile = ProfileEntity(
            id = userId,
            email = cleanEmail,
            passwordHash = hashPassword(password, cleanEmail),
            emailVerified = true,
            fullName = cleanName,
            preferredCurrency = preferredCurrency,
            dateFormat = "dd MMM yyyy",
            timezone = timezone,
            themeMode = "LIGHT",
            overallMonthlyBudgetMinor = 50_000_00L,
            aiConsentGranted = false,
            aiEnabled = true
        )
        dao.upsertProfile(profile)

        // Seed the 12 default category hierarchies for this new user
        val defaultCategories = DefaultCategoriesSeed.buildCategoriesForUser(userId)
        dao.upsertCategories(defaultCategories)

        AuthResult.Success(
            session = AuthSession(
                userId = userId,
                email = cleanEmail,
                fullName = cleanName,
                accessToken = accessToken,
                isSupabaseCloudConnected = cloudConnected
            ),
            message = "Account created and verified for $cleanName."
        )
    }

    suspend fun loginUser(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            return@withContext AuthResult.Error("Please enter both email and password.")
        }

        var accessToken = "local_jwt_${UUID.randomUUID()}"
        var cloudConnected = false

        if (isSupabaseConfigured) {
            runCatching {
                val payload = JSONObject().apply {
                    put("email", cleanEmail)
                    put("password", password)
                }
                val req = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=password")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        accessToken = json.optString("access_token", accessToken)
                        cloudConnected = true
                    }
                }
            }
        }

        val profile = dao.getProfileByEmail(cleanEmail)
            ?: return@withContext AuthResult.Error("No account found for $cleanEmail. Please register first.")

        val expectedHash = hashPassword(password, cleanEmail)
        if (profile.passwordHash != expectedHash) {
            return@withContext AuthResult.Error("Invalid email or password.")
        }

        // Ensure categories exist for this user
        if (dao.getCategoriesForUser(profile.id).isEmpty()) {
            dao.upsertCategories(DefaultCategoriesSeed.buildCategoriesForUser(profile.id))
        }

        AuthResult.Success(
            session = AuthSession(
                userId = profile.id,
                email = profile.email,
                fullName = profile.fullName,
                accessToken = accessToken,
                isSupabaseCloudConnected = cloudConnected
            ),
            message = "Welcome back, ${profile.fullName}!"
        )
    }

    suspend fun resetPassword(email: String, newPassword: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (newPassword.length < 6) {
            return@withContext AuthResult.Error("New password must be at least 6 characters.")
        }
        val profile = dao.getProfileByEmail(cleanEmail)
            ?: return@withContext AuthResult.Error("No account registered with $cleanEmail.")

        if (isSupabaseConfigured) {
            runCatching {
                val payload = JSONObject().put("email", cleanEmail)
                val req = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/recover")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute().close()
            }
        }

        val updated = profile.copy(
            passwordHash = hashPassword(newPassword, cleanEmail),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertProfile(updated)
        AuthResult.Success(
            session = AuthSession(
                userId = updated.id,
                email = updated.email,
                fullName = updated.fullName,
                accessToken = "reset_jwt_${UUID.randomUUID()}",
                isSupabaseCloudConnected = isSupabaseConfigured
            ),
            message = "Password reset successfully."
        )
    }

    // Reactive Flows strictly scoped by userId (Row Level Security)
    fun observeProfile(userId: String): Flow<ProfileEntity?> = dao.observeProfile(userId)
    fun observeCategories(userId: String): Flow<List<CategoryEntity>> = dao.observeCategories(userId)
    fun observeActiveExpenses(userId: String): Flow<List<ExpenseEntity>> = dao.observeActiveExpenses(userId)
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>> = dao.observeBudgets(userId)
    fun observeRecurringExpenses(userId: String): Flow<List<RecurringExpenseEntity>> = dao.observeRecurringExpenses(userId)

    suspend fun getExpensesForUser(userId: String): List<ExpenseEntity> = withContext(Dispatchers.IO) {
        dao.getActiveExpensesForUser(userId)
    }

    suspend fun getCategoriesForUser(userId: String): List<CategoryEntity> = withContext(Dispatchers.IO) {
        dao.getCategoriesForUser(userId)
    }

    suspend fun updateProfile(profile: ProfileEntity) = withContext(Dispatchers.IO) {
        dao.upsertProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveExpense(
        userId: String,
        expense: ExpenseEntity,
        accessToken: String? = null
    ): Result<ExpenseEntity> = withContext(Dispatchers.IO) {
        if (expense.userId != userId) {
            return@withContext Result.failure(SecurityException("RLS policy violation: userId mismatch"))
        }
        // Prevent accidental duplicate submission within 3 seconds for identical title/amount/date/category
        val existingList = dao.getActiveExpensesForUser(userId)
        val now = System.currentTimeMillis()
        val isDuplicateSubmission = existingList.any {
            it.id != expense.id &&
                it.title.equals(expense.title.trim(), ignoreCase = true) &&
                it.amountMinor == expense.amountMinor &&
                it.expenseDateIso == expense.expenseDateIso &&
                it.categoryId == expense.categoryId &&
                kotlin.math.abs(now - it.createdAt) < 3_000L
        }
        if (isDuplicateSubmission) {
            return@withContext Result.failure(IllegalStateException("Duplicate submission prevented: identical expense was just saved."))
        }

        val cleanExpense = expense.copy(
            title = expense.title.trim(),
            merchant = expense.merchant.trim(),
            description = expense.description.trim(),
            notes = expense.notes.trim(),
            tags = expense.tags.trim(),
            updatedAt = now
        )
        dao.upsertExpense(cleanExpense)

        if (isSupabaseConfigured && !accessToken.isNullOrBlank()) {
            syncExpenseToSupabase(cleanExpense, accessToken)
        }
        Result.success(cleanExpense)
    }

    suspend fun deleteExpenseForUser(expenseId: String, userId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getExpenseByIdForUser(expenseId, userId) ?: return@withContext
        dao.upsertExpense(existing.copy(status = "DELETED", updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateExpenseForUser(
        expense: ExpenseEntity,
        userId: String,
        newDateIso: String
    ): ExpenseEntity? = withContext(Dispatchers.IO) {
        if (expense.userId != userId) return@withContext null
        val copy = expense.copy(
            id = "tx_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            title = "${expense.title} (Copy)",
            expenseDateIso = newDateIso,
            recurringSourceId = null,
            createdAt = System.currentTimeMillis() + 5000L, // offset so duplicate guard allows explicit copy
            updatedAt = System.currentTimeMillis() + 5000L
        )
        dao.upsertExpense(copy)
        copy
    }

    suspend fun saveCategory(userId: String, category: CategoryEntity) = withContext(Dispatchers.IO) {
        if (category.userId != userId) throw SecurityException("RLS policy violation: userId mismatch")
        dao.upsertCategory(category.copy(name = category.name.trim()))
    }

    suspend fun saveBudget(userId: String, budget: BudgetEntity) = withContext(Dispatchers.IO) {
        if (budget.userId != userId) throw SecurityException("RLS policy violation: userId mismatch")
        dao.upsertBudget(budget.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveRecurringExpense(userId: String, recurring: RecurringExpenseEntity) = withContext(Dispatchers.IO) {
        if (recurring.userId != userId) throw SecurityException("RLS policy violation: userId mismatch")
        dao.upsertRecurringExpense(recurring.copy(title = recurring.title.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRecurringExpense(id: String, userId: String) = withContext(Dispatchers.IO) {
        dao.deleteRecurringExpense(id, userId)
    }

    suspend fun processDueRecurringSchedules(
        userId: String,
        referenceDateIso: String,
        currencyCode: String
    ): Int = withContext(Dispatchers.IO) {
        val schedules = dao.getRecurringExpensesForUser(userId)
        val existingExpenses = dao.getActiveExpensesForUser(userId)
        val (newExpenses, updatedSchedules) = FinancialCalculator.materializeDueRecurringExpenses(
            schedules = schedules,
            existingExpenses = existingExpenses,
            referenceDateIso = referenceDateIso,
            currencyCode = currencyCode
        )
        if (newExpenses.isNotEmpty()) {
            dao.upsertExpenses(newExpenses)
        }
        if (updatedSchedules.isNotEmpty()) {
            dao.upsertRecurringExpenses(updatedSchedules)
        }
        newExpenses.size
    }

    suspend fun deleteAccountAndAllData(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteAllUserExpenses(userId)
        dao.deleteAllUserBudgets(userId)
        dao.deleteAllUserRecurring(userId)
        dao.deleteAllUserCategories(userId)
        dao.deleteProfile(userId)
    }

    private fun syncExpenseToSupabase(expense: ExpenseEntity, accessToken: String) {
        runCatching {
            val payload = JSONArray().put(
                JSONObject().apply {
                    put("id", expense.id)
                    put("user_id", expense.userId)
                    put("category_id", expense.categoryId)
                    put("subcategory_id", expense.subcategoryId)
                    put("title", expense.title)
                    put("amount", MoneyUtils.toBigDecimal(expense.amountMinor).toPlainString())
                    put("currency", expense.currency)
                    put("expense_date", expense.expenseDateIso)
                    put("payment_method", expense.paymentMethod)
                    put("merchant", expense.merchant)
                    put("description", expense.description)
                    put("status", expense.status)
                }
            )
            val req = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/expenses")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().close()
        }
    }
}
