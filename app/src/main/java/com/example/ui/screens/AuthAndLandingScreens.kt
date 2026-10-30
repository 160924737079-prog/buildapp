package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.AppPage
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandingScreen(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Card
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Navy900)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Navy900, Navy800)
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Emerald500),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "ExpensePro AI Logo",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "EXPENSEPRO AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct Personal Expense Tracker & Interactive Visualizations",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Text(
                    text = "Track Expenses Instantly & Explore 10 Interactive Charts — No Login Required.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Record daily expenses, organize categories and budgets, and analyze your spending date-wise, category-wise, month-wise, quarter-wise, half-yearly, and annually with live interactive charts and CSV/XLSX/PDF exports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.navigateTo(AppPage.DASHBOARD) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        modifier = Modifier.testTag("landing_open_dashboard_button")
                    ) {
                        Text("Open Expense Dashboard", color = Navy900, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(AppPage.REPORTS) },
                        modifier = Modifier.testTag("landing_open_charts_button")
                    ) {
                        Text("View All 10 Charts & Reports", color = Color.White)
                    }
                    TextButton(
                        onClick = { viewModel.navigateTo(AppPage.QUICK_ADD) },
                        modifier = Modifier.testTag("landing_add_expense_button")
                    ) {
                        Text("+ Add New Expense →", color = Emerald500)
                    }
                }
            }
        }

        // Feature Pillars
        val features = listOf(
            Triple(
                Icons.Default.AddCircle,
                "Instant Expense Tracking (Add, Edit & Delete)",
                "Log expenses in seconds directly from the Dashboard or Full Form with 12 built-in categories, 64 subcategories, and payment method tracking."
            ),
            Triple(
                Icons.Default.PieChart,
                "10 Interactive Visualizations",
                "Category Donut, Vertical Bar, Daily Trend Line, Monthly Column, Grouped Category-Month Bar, Top Horizontal Bar, Calendar Heatmap, Stacked Bar, Payment Distribution & Yearly Trend."
            ),
            Triple(
                Icons.Default.Analytics,
                "Multi-Horizon Financial Analytics",
                "Switch effortlessly between Daily, Weekly, Monthly, Quarterly (Q1–Q4), Half-Yearly (H1/H2), Annual, and Custom Date Range reports."
            ),
            Triple(
                Icons.Default.AutoAwesome,
                "Deterministic Spending Insights & Exports",
                "Zero-hallucination spending analysis computed directly from your local database, plus one-tap CSV, Excel XLSX, and PDF report exports."
            )
        )

        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            features.forEach { (icon, title, desc) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = title, tint = Emerald600)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
