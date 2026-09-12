package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.model.UserRole
import com.example.ui.components.SQLiveDot
import com.example.ui.screens.*
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SmartQueueTheme
import com.example.viewmodel.SmartQueueViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SmartQueueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "FirebaseApp init notice: ${e.message}")
        }
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val context = LocalContext.current

            // Toast handler
            LaunchedEffect(uiState.toastMessage) {
                uiState.toastMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }

            SmartQueueTheme(darkTheme = uiState.isDarkMode) {
                if (!uiState.isLoggedIn) {
                    LoginScreen(uiState = uiState, viewModel = viewModel, onLoginSuccess = {})
                } else {
                    SmartQueueApp(viewModel = viewModel)
                }
            }
        }
    }
}

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Explore : Screen("explore", "Explore", Icons.Filled.Search, Icons.Outlined.Search)
    object LiveToken : Screen("live_token", "My Pass", Icons.Filled.ConfirmationNumber, Icons.Outlined.ConfirmationNumber)
    object BusinessHub : Screen("business_hub", "Operator", Icons.Filled.Storefront, Icons.Outlined.Storefront)
    object History : Screen("history", "Activity", Icons.Filled.History, Icons.Outlined.History)
    object Profile : Screen("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Sub-screens
    object ScanQr : Screen("scan_qr", "Scan QR", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner)
    object BusinessDetail : Screen("business_detail", "Business Details", Icons.Filled.Store, Icons.Outlined.Store)
    object StaffManagement : Screen("staff_management", "Staff", Icons.Filled.People, Icons.Outlined.People)
    object MultiBranch : Screen("multi_branch", "Branches", Icons.Filled.LocationOn, Icons.Outlined.LocationOn)
    object QrManagement : Screen("qr_management", "QR Standee", Icons.Filled.QrCode, Icons.Outlined.QrCode)
    object SubscriptionPay : Screen("subscription_pay", "SaaS Plans", Icons.Filled.WorkspacePremium, Icons.Outlined.WorkspacePremium)
    object AbusePrevention : Screen("abuse_prevention", "Abuse Shield", Icons.Filled.Shield, Icons.Outlined.Shield)
    object AdminPanel : Screen("admin_panel", "SuperAdmin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    object LandingMarketing : Screen("landing_marketing", "Overview", Icons.Filled.RocketLaunch, Icons.Outlined.RocketLaunch)
}

@Composable
fun SmartQueueApp(viewModel: SmartQueueViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Role-Adaptive Bottom Navigation Items
    val bottomNavItems = remember(uiState.currentUser.role) {
        when (uiState.currentUser.role) {
            UserRole.ADMIN -> listOf(
                Screen.AdminPanel,
                Screen.BusinessHub,
                Screen.Home,
                Screen.History,
                Screen.Profile
            )
            UserRole.MERCHANT, UserRole.BUSINESS_OWNER -> listOf(
                Screen.BusinessHub,
                Screen.QrManagement,
                Screen.StaffManagement,
                Screen.LiveToken,
                Screen.Profile
            )
            UserRole.STAFF -> listOf(
                Screen.BusinessHub,
                Screen.StaffManagement,
                Screen.LiveToken,
                Screen.History,
                Screen.Profile
            )
            else -> listOf(
                Screen.Home,
                Screen.Explore,
                Screen.LiveToken,
                Screen.History,
                Screen.Profile
            )
        }
    }

    val startDestination = remember(uiState.currentUser.role) {
        when (uiState.currentUser.role) {
            UserRole.ADMIN -> Screen.AdminPanel.route
            UserRole.MERCHANT, UserRole.BUSINESS_OWNER, UserRole.STAFF -> Screen.BusinessHub.route
            else -> Screen.Home.route
        }
    }

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar"),
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        val hasActiveToken = screen == Screen.LiveToken && uiState.activeUserToken != null

                        NavigationBarItem(
                            icon = {
                                Box {
                                    Icon(
                                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                    if (hasActiveToken) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-2).dp)
                                        ) {
                                            SQLiveDot(color = PrimaryCyan)
                                        }
                                    }
                                }
                            },
                            label = { Text(screen.title, fontSize = 11.sp) },
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToToken = { navController.navigate(Screen.LiveToken.route) },
                    onNavigateToScan = { navController.navigate(Screen.ScanQr.route) },
                    onNavigateToExplore = { navController.navigate(Screen.Explore.route) },
                    onNavigateToBusiness = { biz ->
                        viewModel.selectBusiness(biz)
                        navController.navigate(Screen.BusinessDetail.route)
                    }
                )
            }

            composable(Screen.Explore.route) {
                ExploreScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToBusiness = { biz ->
                        viewModel.selectBusiness(biz)
                        navController.navigate(Screen.BusinessDetail.route)
                    }
                )
            }

            composable(Screen.LiveToken.route) {
                TokenLiveScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.BusinessHub.route) {
                BusinessDashboardScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToStaff = { navController.navigate(Screen.StaffManagement.route) },
                    onNavigateToBranches = { navController.navigate(Screen.MultiBranch.route) },
                    onNavigateToQr = { navController.navigate(Screen.QrManagement.route) },
                    onNavigateToSubscription = { navController.navigate(Screen.SubscriptionPay.route) },
                    onNavigateToAbuse = { navController.navigate(Screen.AbusePrevention.route) },
                    onNavigateToAdmin = { navController.navigate(Screen.AdminPanel.route) }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToLanding = { navController.navigate(Screen.LandingMarketing.route) },
                    onNavigateToAdmin = { navController.navigate(Screen.AdminPanel.route) }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToToken = { navController.navigate(Screen.LiveToken.route) }
                )
            }

            composable(Screen.ScanQr.route) {
                ScanQrScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onQrResolved = { biz, loc ->
                        viewModel.selectBusiness(biz)
                        viewModel.selectLocation(loc)
                        navController.navigate(Screen.BusinessDetail.route)
                    }
                )
            }

            composable(Screen.BusinessDetail.route) {
                val selectedBiz = uiState.selectedBusiness ?: uiState.businesses.first()
                BusinessDetailScreen(
                    business = selectedBiz,
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToToken = { navController.navigate(Screen.LiveToken.route) }
                )
            }

            composable(Screen.StaffManagement.route) {
                StaffManagementScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.MultiBranch.route) {
                MultiBranchScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.QrManagement.route) {
                QrManagementScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.SubscriptionPay.route) {
                SubscriptionPayScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AbusePrevention.route) {
                AbusePreventionScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AdminPanel.route) {
                AdminPanelScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.LandingMarketing.route) {
                LandingMarketingScreen(
                    onBack = { navController.popBackStack() },
                    onGetStarted = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
