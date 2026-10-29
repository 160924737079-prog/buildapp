# ExpensePro AI — Smart Personal & Family Expense Tracker

**ExpensePro AI** is a complete, production-grade personal and family expense management application built with **Kotlin & Jetpack Compose** on the client and **Supabase (PostgreSQL + Supabase Auth + Row Level Security)** for cloud persistence, alongside an offline-first **Room SQLite** engine.

> **Technology Compliance:** This application uses **zero Firebase services** (`firebase-*` libraries and plugins are completely excluded). All authentication, row-level data isolation, and persistence are powered by Supabase PostgreSQL + local Room SQLite synchronization.

---

## 1. Key Capabilities

1. **Authentication & Multi-User Isolation (Supabase Auth + Local Vault):**
   - Registration, Email/Password Login, Email Verification workflow, Password Reset, Session Management, and Account Deletion.
   - Strict per-user isolation enforced via `user_id` scoping and PostgreSQL Row Level Security (`auth.uid() = user_id`).
2. **Main Dashboard & 14 Global Reporting Periods:**
   - 10 live summary cards: Total Expenses, Today's Expenses, This Week's Expenses, This Month's Expenses, Previous Month's Expenses, Average Daily Expenses, Highest Spending Category, Total Transactions, Remaining Monthly Budget, and Month-over-Month Change.
   - Global Period Selector: Today, Yesterday, This Week, Last Week, This Month, Last Month, Current Quarter, Previous Quarter, Current Half-Year, Previous Half-Year, This Year, Last Year, All Time, and Custom Date Range.
3. **Quick Add & Edit Expense:**
   - Decimal-safe monetary input (`BigDecimal` / integer minor units), Category & Subcategory selector, inline Custom Category creator, Payment Method (`UPI`, `Credit Card`, `Debit Card`, `Cash`, `Net Banking`, `Wallet`), Merchant, Description, Notes, Tags, Receipt attachment URI, and duplicate submission prevention.
4. **12 Pre-Configured Category Sections & 64 Subcategories:**
   - Groceries, Milk & Dairy, Fuel & Transportation, Household Bills, Housing, Food & Dining, Healthcare, Education, Personal Expenses, Financial Obligations, Travel, and Family & Miscellaneous.
   - Full CRUD: create custom categories/subcategories, rename, customize color & icon, set category monthly budget, and archive without losing historical transactions.
5. **Searchable, Filterable, Sortable Transaction History:**
   - Search by title/merchant, filter by category, subcategory, payment method, min/max amount, and date range.
   - Sort by Date or Amount; Group by Day, Week, or Month with subtotals; View, Edit, Duplicate, and Confirm-Delete actions.
6. **Time-Horizon Reports (Daily, Weekly, Monthly, Quarterly, Half-Yearly, Yearly & Custom):**
   - Dedicated tabs for Daily, Weekly, Monthly, Calendar Quarters (Q1–Q4), Half-Years (Jan–Jun, Jul–Dec), Annual YoY, and multi-filter Custom Date Range analytics.
7. **10 Interactive Financial Charts:**
   - Category Donut Chart, Category Vertical Bar Chart, Daily Trend Line Chart, Monthly Column Chart, Multi-Month Grouped Category Bar Chart, Top Categories Horizontal Bar Chart, Daily Spending Calendar Heatmap, Monthly Category Stacked Bar Chart, Payment Method Distribution Chart, and Yearly Trend Chart.
8. **Budget Management & Threshold Alerts:**
   - Overall monthly budget and category-level monthly budgets with automatic 80% (Warning), 90% (Critical), and 100% (Exceeded) in-app alerts.
9. **Recurring Expenses Engine:**
   - Daily, Weekly, Monthly, and Yearly schedules with leap-year and month-end safe calculation, pause/resume/cancel controls, and duplicate-prevention when materializing due transactions.
10. **Deterministic AI Financial Insights:**
    - Code-verified financial calculations answer natural-language questions accurately without hallucinating numbers, with optional external AI narrative enhancement gated by explicit user consent.
11. **Data Export (CSV, Excel XLSX, and PDF):**
    - Generates standards-compliant `.csv`, OpenXML `.xlsx`, and vector `.pdf` financial reports matching active filters.

---

## 2. Environment & Secrets Configuration

Copy `.env.example` or configure the following keys in the **AI Studio Secrets Panel**:

```properties
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=your-supabase-anon-key
AI_INSIGHTS_ENDPOINT=https://your-project-id.supabase.co/functions/v1/ai-insights
```

- Never place service-role keys in client configuration.
- When `SUPABASE_URL` and `SUPABASE_ANON_KEY` are left at their placeholder values, ExpensePro AI automatically operates in **Offline-First Local Mode** using Room SQLite with full multi-user isolation so you can test and use every feature immediately.

---

## 3. Supabase Database Setup Instructions

1. Create a new Supabase project at [https://supabase.com](https://supabase.com).
2. Open the **SQL Editor** in your Supabase Dashboard.
3. Paste and execute the contents of `supabase/schema.sql` (or apply via Supabase CLI: `supabase db push`).
4. Verify that Row Level Security (RLS) is enabled on `profiles`, `categories`, `expenses`, `budgets`, and `recurring_expenses`.
5. Add your project's `SUPABASE_URL` and `SUPABASE_ANON_KEY` to the AI Studio Secrets panel.

---

## 4. Running Automated Tests

All 13 acceptance test scenarios (Section 21) are implemented in `app/src/test/java/com/example/ExpenseProAcceptanceTest.kt`.

Run the test suite with:

```bash
gradle :app:testDebugUnitTest
```

---

## 5. Troubleshooting

- **Missing Supabase Credentials:** The app gracefully falls back to on-device Room persistence and displays the active storage status in Profile & Settings.
- **AI Service Unavailable / Disabled:** All financial insights are computed deterministically by `AiInsightsEngine` from local/authorized transaction summaries, so insights work 100% offline without an AI API key.
- **Export File Sharing:** Generated `.csv`, `.xlsx`, and `.pdf` files are written to `cacheDir/exports/` and shared via Android's `FileProvider`.
