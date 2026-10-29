package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.ExpenseProDatabase
import com.example.data.ExpenseProRepository
import com.example.ui.AppPage
import com.example.ui.ExpenseProUiState
import com.example.ui.ExpenseProViewModel
import com.example.ui.screens.AiInsightsScreen
import com.example.ui.screens.BudgetManagementScreen
import com.example.ui.screens.CategoriesManagementScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExportAndAccountScreen
import com.example.ui.screens.LandingScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.QuickAddExpenseScreen
import com.example.ui.screens.RecurringExpensesScreen
import com.example.ui.screens.RegistrationScreen
import com.example.ui.screens.ReportsAndAnalyticsScreen
import com.example.ui.screens.TransactionHistoryScreen
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.ExpenseProTheme
import com.example.ui.theme.Navy900
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            ExpenseProDatabase::class.java,
            "expensepro_ai_db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    private val repository by lazy {
        ExpenseProRepository(database.dao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ExpenseProViewModel = viewModel(
                factory = ExpenseProViewModel.Factory(repository)
            )
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            ExpenseProTheme(themePreference = uiState.themePreference) {
                ExpenseProAppRoot(uiState = uiState, viewModel = vm)
            }
        }
    }
}

private data class NavMenuItem(
    val page: AppPage,
    val label: String,
    val icon: ImageVector
)

private val AUTHENTICATED_NAV_ITEMS = listOf(
    NavMenuItem(AppPage.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    NavMenuItem(AppPage.QUICK_ADD, "Quick Add", Icons.Default.AddCircle),
    NavMenuItem(AppPage.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Filled.ListAlt),
    NavMenuItem(AppPage.CATEGORIES, "Categories", Icons.Default.Category),
    NavMenuItem(AppPage.BUDGETS, "Budgets", Icons.Default.Savings),
    NavMenuItem(AppPage.RECURRING, "Recurring", Icons.Default.Repeat),
    NavMenuItem(AppPage.REPORTS, "Reports & Charts", Icons.Default.Analytics),
    NavMenuItem(AppPage.AI_INSIGHTS, "AI Insights", Icons.Default.AutoAwesome),
    NavMenuItem(AppPage.EXPORTS, "Export Reports", Icons.Default.Download),
    NavMenuItem(AppPage.PROFILE, "Profile & Settings", Icons.Default.Person),
    NavMenuItem(AppPage.LANDING, "Product Overview", Icons.Default.Home)
)

private val PUBLIC_NAV_ITEMS = listOf(
    NavMenuItem(AppPage.LANDING, "Landing Page", Icons.Default.Home),
    NavMenuItem(AppPage.LOGIN, "Sign In", Icons.AutoMirrored.Filled.Login),
    NavMenuItem(AppPage.REGISTER, "Register", Icons.Default.PersonAdd)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseProAppRoot(
    uiState: ExpenseProUiState,
    viewModel: ExpenseProViewModel
) {
    BackHandler(enabled = uiState.pageBackStack.isNotEmpty()) {
        viewModel.navigateBack()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isDesktopSidebarCollapsed by remember { mutableStateOf(false) }

    val navItems = if (uiState.session != null) AUTHENTICATED_NAV_ITEMS else PUBLIC_NAV_ITEMS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 680.dp

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isWideScreen,
            drawerContent = {
                ModalDrawerSheet {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Emerald600
                            )
                            Column {
                                Text(
                                    text = "ExpensePro AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.profile?.email ?: "Personal Finance SaaS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        navItems.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                selected = uiState.currentPage == item.page,
                                onClick = {
                                    viewModel.navigateTo(item.page)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.testTag("drawer_nav_${item.page.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.currentPage.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (uiState.session != null) {
                                    Text(
                                        text = "${uiState.currencyCode} • ${uiState.globalPeriod.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isWideScreen) {
                                            isDesktopSidebarCollapsed = !isDesktopSidebarCollapsed
                                        } else {
                                            scope.launch { drawerState.open() }
                                        }
                                    },
                                    modifier = Modifier.testTag("open_navigation_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Toggle Navigation Menu",
                                        tint = Color.White
                                    )
                                }
                                if (uiState.pageBackStack.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.navigateBack() },
                                        modifier = Modifier.testTag("top_bar_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Navigate Back",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            if (uiState.session != null) {
                                IconButton(
                                    onClick = { viewModel.navigateTo(AppPage.QUICK_ADD) },
                                    modifier = Modifier.testTag("top_bar_quick_add_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircle,
                                        contentDescription = "Quick Add Expense",
                                        tint = Emerald500
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.navigateTo(AppPage.PROFILE) },
                                    modifier = Modifier.testTag("top_bar_profile_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Profile & Settings",
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Navy900,
                            titleContentColor = Color.White
                        )
                    )
                },
                bottomBar = {
                    if (!isWideScreen && uiState.session != null) {
                        val bottomTabs = listOf(
                            NavMenuItem(AppPage.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
                            NavMenuItem(AppPage.QUICK_ADD, "Add", Icons.Default.AddCircle),
                            NavMenuItem(AppPage.TRANSACTIONS, "History", Icons.AutoMirrored.Filled.ListAlt),
                            NavMenuItem(AppPage.REPORTS, "Reports", Icons.Default.Analytics),
                            NavMenuItem(AppPage.AI_INSIGHTS, "Insights", Icons.Default.AutoAwesome)
                        )
                        NavigationBar {
                            bottomTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = uiState.currentPage == tab.page,
                                    onClick = { viewModel.navigateTo(tab.page) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("bottom_nav_${tab.page.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Collapsible Desktop / Tablet Sidebar
                    if (isWideScreen) {
                        Surface(
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(if (isDesktopSidebarCollapsed) 72.dp else 230.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                navItems.forEach { item ->
                                    val selected = uiState.currentPage == item.page
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                                else Color.Transparent
                                            )
                                            .clickable { viewModel.navigateTo(item.page) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = if (selected) Emerald600 else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isDesktopSidebarCollapsed) {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Content Area
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                            uiState.statusBannerMessage?.let { msg ->
                                Surface(
                                    color = Emerald600,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.clearBanner() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss notification",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Protected route enforcement
                            val requiresAuth = uiState.currentPage !in listOf(
                                AppPage.LANDING,
                                AppPage.LOGIN,
                                AppPage.REGISTER
                            )
                            if (requiresAuth && uiState.session == null) {
                                LoginScreen(uiState = uiState, viewModel = viewModel)
                            } else {
                                when (uiState.currentPage) {
                                    AppPage.LANDING -> LandingScreen(uiState, viewModel)
                                    AppPage.LOGIN -> LoginScreen(uiState, viewModel)
                                    AppPage.REGISTER -> RegistrationScreen(uiState, viewModel)
                                    AppPage.DASHBOARD -> DashboardScreen(uiState, viewModel)
                                    AppPage.QUICK_ADD -> QuickAddExpenseScreen(uiState, viewModel)
                                    AppPage.TRANSACTIONS -> TransactionHistoryScreen(uiState, viewModel)
                                    AppPage.CATEGORIES -> CategoriesManagementScreen(uiState, viewModel)
                                    AppPage.BUDGETS -> BudgetManagementScreen(uiState, viewModel)
                                    AppPage.RECURRING -> RecurringExpensesScreen(uiState, viewModel)
                                    AppPage.REPORTS -> ReportsAndAnalyticsScreen(uiState, viewModel)
                                    AppPage.AI_INSIGHTS -> AiInsightsScreen(uiState, viewModel)
                                    AppPage.PROFILE -> ProfileAndSettingsScreen(uiState, viewModel)
                                    AppPage.EXPORTS -> ExportAndAccountScreen(uiState, viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
