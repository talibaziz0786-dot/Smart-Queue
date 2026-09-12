package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AdminTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    USERS("Users Master", Icons.Default.People),
    BUSINESSES("Businesses & Plans", Icons.Default.BusinessCenter),
    QUEUES("Live Queues", Icons.Default.ConfirmationNumber),
    PRICING("Pricing & Rates", Icons.Default.WorkspacePremium),
    AUDIT("Audit Trail", Icons.Default.Security)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    val isAdmin = uiState.userRole == UserRole.ADMIN

    if (!isAdmin) {
        AdminSecurityGateScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = onBack
        )
    } else {
        AdminMasterDashboard(
            uiState = uiState,
            viewModel = viewModel,
            onBack = onBack
        )
    }
}

/**
 * High-Security Master Authenticator Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSecurityGateScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SuperAdmin Master Gate", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(PrimaryCyan.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Restricted SuperAdmin Access",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Only authorized platform administrators (talibaziz0786@gmail.com) can access this control panel. Please enter your secure credentials to proceed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (authError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRose.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(authError ?: "", color = AccentRose, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = adminEmail,
                onValueChange = { adminEmail = it; authError = null },
                label = { Text("Admin Email (एडमिन ईमेल)") },
                placeholder = { Text("talibaziz0786@gmail.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PrimaryCyan) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = adminPassword,
                onValueChange = { adminPassword = it; authError = null },
                label = { Text("Admin Password (पासवर्ड)") },
                placeholder = { Text("••••••••") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryCyan) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (adminEmail.isBlank() || adminPassword.isBlank()) {
                        authError = "Please enter both admin email and password"
                        return@Button
                    }
                    isVerifying = true
                    val success = viewModel.verifyAdminCredentials(adminEmail.trim(), adminPassword.trim())
                    isVerifying = false
                    if (!success) {
                        authError = "Access Denied: Invalid administrator credentials"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("admin_verify_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                enabled = !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authenticate SuperAdmin", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Full Master SuperAdmin Dashboard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMasterDashboard(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AdminTab.OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SuperAdmin Control Center", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        color = AccentEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SQLiveDot(color = AccentEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SUPER_ADMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentEmerald)
                        }
                    }
                    IconButton(onClick = { viewModel.switchRole(UserRole.CUSTOMER) }) {
                        Icon(Icons.Default.Logout, contentDescription = "Exit Admin", tint = AccentRose)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scrollable Bento Tab Navigation Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) }
            ) {
                AdminTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tab.title, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        selectedContentColor = PrimaryCyan,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    AdminTab.OVERVIEW -> AdminOverviewTab(uiState, viewModel, onSelectTab = { selectedTab = it })
                    AdminTab.USERS -> AdminUsersTab(uiState, viewModel)
                    AdminTab.BUSINESSES -> AdminBusinessesTab(uiState, viewModel)
                    AdminTab.QUEUES -> AdminLiveQueuesTab(uiState, viewModel)
                    AdminTab.PRICING -> AdminPricingTab(uiState, viewModel)
                    AdminTab.AUDIT -> AdminAuditTab(uiState, viewModel)
                }
            }
        }
    }
}

/**
 * 1. Overview & Platform Health Tab
 */
@Composable
fun AdminOverviewTab(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onSelectTab: (AdminTab) -> Unit
) {
    val totalUsers = uiState.allUsers.size
    val blockedUsers = uiState.allUsers.count { it.isBlocked || it.isRestricted }
    val totalBusinesses = uiState.businesses.size
    val totalBranches = uiState.locations.size
    val activeTokens = uiState.tokens.count { it.status in listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING) }
    val isAnyPaused = uiState.locations.any { it.isPaused }

    // Estimate MRR based on subscriptions
    val estimatedMrr = uiState.businesses.sumOf {
        when (it.plan) {
            SubscriptionPlan.FREE -> 0
            SubscriptionPlan.PRO -> 49
            SubscriptionPlan.BUSINESS -> 149
            SubscriptionPlan.ENTERPRISE -> 399
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        // Welcome Banner Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(PrimaryCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryCyan)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("SuperAdmin Master Controller", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Logged in as Talib Aziz with unrestricted root access to all users, businesses, subscriptions & queues.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Metrics Bento Grid
        item {
            Text("Platform Metrics & Revenue", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PrimaryCyan)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SQStatCard(
                        title = "Platform Users",
                        value = "$totalUsers",
                        subtitle = "$blockedUsers blocked / restricted",
                        icon = Icons.Default.People,
                        color = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    SQStatCard(
                        title = "SaaS Businesses",
                        value = "$totalBusinesses",
                        subtitle = "$totalBranches active branches",
                        icon = Icons.Default.Storefront,
                        color = AccentEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SQStatCard(
                        title = "Active Tokens",
                        value = "$activeTokens",
                        subtitle = "live in line across all counters",
                        icon = Icons.Default.ConfirmationNumber,
                        color = AccentAmber,
                        modifier = Modifier.weight(1f)
                    )
                    SQStatCard(
                        title = "Monthly Revenue",
                        value = "$$estimatedMrr",
                        subtitle = "MRR subscription run rate",
                        icon = Icons.Default.Paid,
                        color = AccentEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Global Emergency Control Bento Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, if (isAnyPaused) AccentRose else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isAnyPaused) Icons.Default.Warning else Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (isAnyPaused) AccentRose else AccentEmerald
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Global Emergency Queue Override",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            color = if (isAnyPaused) AccentRose.copy(alpha = 0.2f) else AccentEmerald.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                if (isAnyPaused) "QUEUES PAUSED" else "QUEUES OPERATIONAL",
                                color = if (isAnyPaused) AccentRose else AccentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Freeze or resume queue joins across ALL platform locations instantly in case of system maintenance or site emergencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.adminEmergencyPauseAll(!isAnyPaused) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAnyPaused) AccentEmerald else AccentRose
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(if (isAnyPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isAnyPaused) "Resume All Queues" else "Emergency Halt All")
                        }

                        OutlinedButton(
                            onClick = { viewModel.adminPurgeCompletedTokens() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Purge Served Tokens", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quick Navigation Bento Links
        item {
            Text("Admin Quick Management Hub", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectTab(AdminTab.USERS) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = PrimaryCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Manage Users", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Add, block & role control", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectTab(AdminTab.BUSINESSES) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.CardMembership, contentDescription = null, tint = AccentAmber)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Extend Subscriptions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Grant time & change plans", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * 2. User Master Control Tab (Add, Block/Unblock, Role Change, Delete)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersTab(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<UserRole?>(null) }
    var showOnlyBlocked by remember { mutableStateOf(false) }

    // Dialog states
    var showAddUserDialog by remember { mutableStateOf(false) }
    var userToBlock by remember { mutableStateOf<User?>(null) }
    var blockReason by remember { mutableStateOf("Violation of queue policies & frequent no-shows") }
    var userToChangeRole by remember { mutableStateOf<User?>(null) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    val filteredUsers = uiState.allUsers.filter { user ->
        val matchesSearch = user.name.contains(searchQuery, ignoreCase = true) ||
                user.email.contains(searchQuery, ignoreCase = true) ||
                user.phone.contains(searchQuery, ignoreCase = true)
        val matchesRole = selectedRoleFilter == null || user.role == selectedRoleFilter
        val matchesBlocked = !showOnlyBlocked || (user.isBlocked || user.isRestricted)
        matchesSearch && matchesRole && matchesBlocked
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddUserDialog = true },
                containerColor = PrimaryCyan,
                contentColor = Color.Black,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add User", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("admin_add_user_fab")
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {
            // Search Input
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search users by name, email, phone...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Role Filters
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == null && !showOnlyBlocked,
                            onClick = {
                                selectedRoleFilter = null
                                showOnlyBlocked = false
                            },
                            label = { Text("All (${uiState.allUsers.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = showOnlyBlocked,
                            onClick = {
                                showOnlyBlocked = !showOnlyBlocked
                                if (showOnlyBlocked) selectedRoleFilter = null
                            },
                            label = { Text("Blocked (${uiState.allUsers.count { it.isBlocked || it.isRestricted }})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRose.copy(alpha = 0.2f),
                                selectedLabelColor = AccentRose
                            )
                        )
                    }
                    UserRole.values().forEach { role ->
                        item {
                            FilterChip(
                                selected = selectedRoleFilter == role && !showOnlyBlocked,
                                onClick = {
                                    selectedRoleFilter = if (selectedRoleFilter == role) null else role
                                    showOnlyBlocked = false
                                },
                                label = { Text(role.name) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Showing ${filteredUsers.size} Users",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(filteredUsers, key = { it.id }) { user ->
                val isUserBlocked = user.isBlocked || user.isRestricted
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isUserBlocked) AccentRose.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            when (user.role) {
                                                UserRole.ADMIN, UserRole.SUPER_ADMIN -> AccentAmber.copy(alpha = 0.2f)
                                                UserRole.MERCHANT -> PrimaryIndigo.copy(alpha = 0.2f)
                                                UserRole.STAFF -> PrimaryCyan.copy(alpha = 0.2f)
                                                UserRole.USER -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.name.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = when (user.role) {
                                            UserRole.ADMIN, UserRole.SUPER_ADMIN -> AccentAmber
                                            UserRole.MERCHANT -> PrimaryIndigo
                                            UserRole.STAFF -> PrimaryCyan
                                            UserRole.USER -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (user.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = user.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Role & Status Badges
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    color = when (user.role) {
                                        UserRole.ADMIN, UserRole.SUPER_ADMIN -> AccentAmber.copy(alpha = 0.2f)
                                        UserRole.MERCHANT -> PrimaryIndigo.copy(alpha = 0.2f)
                                        UserRole.STAFF -> PrimaryCyan.copy(alpha = 0.2f)
                                        UserRole.USER -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = user.role.name,
                                        color = when (user.role) {
                                            UserRole.ADMIN, UserRole.SUPER_ADMIN -> AccentAmber
                                            UserRole.MERCHANT -> PrimaryIndigo
                                            UserRole.STAFF -> PrimaryCyan
                                            UserRole.USER -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (isUserBlocked) {
                                    Surface(
                                        color = AccentRose.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "🚫 BLOCKED",
                                            color = AccentRose,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Restriction Reason Banner if blocked
                        if (isUserBlocked && !user.restrictionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = AccentRose.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Report, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reason: ${user.restrictionReason}",
                                        color = AccentRose,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isUserBlocked) {
                                Button(
                                    onClick = { viewModel.adminUnblockUser(user.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unblock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        userToBlock = user
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Block User", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { userToChangeRole = user },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Role", fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = { userToDelete = user },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AccentRose)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add User Modal Dialog
    if (showAddUserDialog) {
        var newName by remember { mutableStateOf("") }
        var newEmail by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("+91 ") }
        var newRole by remember { mutableStateOf(UserRole.CUSTOMER) }

        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add New Platform User", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Initial Role:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UserRole.values().forEach { role ->
                            FilterChip(
                                selected = newRole == role,
                                onClick = { newRole = role },
                                label = { Text(role.name.take(4), fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newEmail.isNotBlank()) {
                            viewModel.adminAddUser(newName.trim(), newEmail.trim(), newPhone.trim(), newRole)
                            showAddUserDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Create User", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Block User Modal Dialog
    if (userToBlock != null) {
        val user = userToBlock!!
        AlertDialog(
            onDismissRequest = { userToBlock = null },
            title = { Text("Block & Restrict User", fontWeight = FontWeight.Bold, color = AccentRose) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Are you sure you want to block ${user.name}? This user will be prohibited from joining live queues or managing business counters.")
                    OutlinedTextField(
                        value = blockReason,
                        onValueChange = { blockReason = it },
                        label = { Text("Lockout Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminBlockUser(user.id, blockReason)
                        userToBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Confirm Block", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToBlock = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Role Dialog
    if (userToChangeRole != null) {
        val user = userToChangeRole!!
        AlertDialog(
            onDismissRequest = { userToChangeRole = null },
            title = { Text("Change Role for ${user.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserRole.values().forEach { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.adminUpdateUserRole(user.id, role)
                                    userToChangeRole = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = user.role == role,
                                onClick = {
                                    viewModel.adminUpdateUserRole(user.id, role)
                                    userToChangeRole = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(role.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { userToChangeRole = null }) { Text("Close") }
            }
        )
    }

    // Delete User Confirmation
    if (userToDelete != null) {
        val user = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User Permanently?", fontWeight = FontWeight.Bold, color = AccentRose) },
            text = { Text("This will remove '${user.name}' (${user.email}) from the platform database. This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminDeleteUser(user.id)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Delete User", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * 3. Businesses & Subscription Master Engine (Extend Time, Change Tier, Freeze/Suspend)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBusinessesTab(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPlanFilter by remember { mutableStateOf<SubscriptionPlan?>(null) }

    // Dialog states
    var bizToExtend by remember { mutableStateOf<Business?>(null) }
    var bizToChangePlan by remember { mutableStateOf<Business?>(null) }
    var bizToSuspend by remember { mutableStateOf<Business?>(null) }
    var suspensionReason by remember { mutableStateOf("Pending platform compliance & subscription renewal verification") }
    var showAddBizDialog by remember { mutableStateOf(false) }

    val filteredBiz = uiState.businesses.filter { biz ->
        val matchesSearch = biz.name.contains(searchQuery, ignoreCase = true) ||
                biz.primaryCity.contains(searchQuery, ignoreCase = true) ||
                biz.description.contains(searchQuery, ignoreCase = true)
        val matchesPlan = selectedPlanFilter == null || biz.plan == selectedPlanFilter
        matchesSearch && matchesPlan
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddBizDialog = true },
                containerColor = AccentEmerald,
                contentColor = Color.Black,
                icon = { Icon(Icons.Default.AddBusiness, contentDescription = null) },
                text = { Text("Onboard Business", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("admin_add_biz_fab")
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {
            // Search Input
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search businesses by name, city...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Plan Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedPlanFilter == null,
                            onClick = { selectedPlanFilter = null },
                            label = { Text("All Plans (${uiState.businesses.size})") }
                        )
                    }
                    SubscriptionPlan.values().forEach { plan ->
                        item {
                            FilterChip(
                                selected = selectedPlanFilter == plan,
                                onClick = { selectedPlanFilter = if (selectedPlanFilter == plan) null else plan },
                                label = { Text(plan.title) }
                            )
                        }
                    }
                }
            }

            items(filteredBiz, key = { it.id }) { biz ->
                val remainingMillis = biz.subscriptionExpiresAt - System.currentTimeMillis()
                val daysRemaining = (remainingMillis / (24L * 60L * 60L * 1000L)).coerceAtLeast(0)
                val isExpired = remainingMillis <= 0
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val formattedExpiry = dateFormat.format(Date(biz.subscriptionExpiresAt))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.5.dp,
                        if (biz.isSuspended) AccentRose
                        else when (biz.plan) {
                            SubscriptionPlan.ENTERPRISE -> AccentAmber
                            SubscriptionPlan.BUSINESS -> PrimaryCyan
                            SubscriptionPlan.PRO -> PrimaryIndigo
                            SubscriptionPlan.FREE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(PrimaryCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = biz.name.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = biz.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (biz.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = "${biz.category.title} • ${biz.primaryCity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Plan Tier Chip
                            Surface(
                                color = when (biz.plan) {
                                    SubscriptionPlan.ENTERPRISE -> AccentAmber.copy(alpha = 0.2f)
                                    SubscriptionPlan.BUSINESS -> PrimaryCyan.copy(alpha = 0.2f)
                                    SubscriptionPlan.PRO -> PrimaryIndigo.copy(alpha = 0.2f)
                                    SubscriptionPlan.FREE -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = biz.plan.title,
                                    color = when (biz.plan) {
                                        SubscriptionPlan.ENTERPRISE -> AccentAmber
                                        SubscriptionPlan.BUSINESS -> PrimaryCyan
                                        SubscriptionPlan.PRO -> PrimaryIndigo
                                        SubscriptionPlan.FREE -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Subscription Time Bar
                        Surface(
                            color = if (isExpired) AccentRose.copy(alpha = 0.1f) else AccentEmerald.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isExpired) AccentRose.copy(alpha = 0.3f) else AccentEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isExpired) Icons.Default.Warning else Icons.Default.HourglassBottom,
                                        contentDescription = null,
                                        tint = if (isExpired) AccentRose else AccentEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (daysRemaining > 365) "♾️ Lifetime VIP Access" else "$daysRemaining Days Remaining",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isExpired) AccentRose else AccentEmerald
                                        )
                                        Text(
                                            text = "Expires: $formattedExpiry",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { bizToExtend = biz },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Extend Time", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Suspension Banner if suspended
                        if (biz.isSuspended) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = AccentRose.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "BUSINESS SUSPENDED: ${biz.suspensionReason ?: "Admin action"}",
                                        color = AccentRose,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { bizToChangePlan = biz },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change Plan", fontSize = 11.sp)
                            }

                            if (biz.isSuspended) {
                                Button(
                                    onClick = { viewModel.adminToggleBusinessSuspension(biz.id, false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unfreeze", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { bizToSuspend = biz },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Suspend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(
                                onClick = { viewModel.adminToggleBusinessVerification(biz.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Toggle Badge",
                                    tint = if (biz.isVerified) PrimaryCyan else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Extend Subscription Modal Sheet
    if (bizToExtend != null) {
        val biz = bizToExtend!!
        AlertDialog(
            onDismissRequest = { bizToExtend = null },
            title = { Text("⚡ Extend Subscription Time", fontWeight = FontWeight.Bold, color = AccentEmerald) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select additional duration to grant '${biz.name}':", style = MaterialTheme.typography.bodyMedium)

                    val options = listOf(
                        30 to "+30 Days (1 Month)",
                        60 to "+60 Days (2 Months)",
                        90 to "+90 Days (Quarterly)",
                        180 to "+180 Days (Half Year)",
                        365 to "+365 Days (1 Full Year)",
                        3650 to "♾️ Lifetime VIP Access (10 Years)"
                    )

                    options.forEach { (days, label) ->
                        Button(
                            onClick = {
                                viewModel.adminExtendBusinessSubscription(biz.id, days)
                                bizToExtend = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (days >= 3650) AccentAmber else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = label,
                                color = if (days >= 3650) Color.Black else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bizToExtend = null }) { Text("Close") }
            }
        )
    }

    // Change Plan Tier Dialog
    if (bizToChangePlan != null) {
        val biz = bizToChangePlan!!
        AlertDialog(
            onDismissRequest = { bizToChangePlan = null },
            title = { Text("💎 Upgrade/Override Subscription Plan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Change active SaaS tier for '${biz.name}':")
                    SubscriptionPlan.values().forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.adminSetBusinessPlan(biz.id, plan)
                                    bizToChangePlan = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = biz.plan == plan,
                                onClick = {
                                    viewModel.adminSetBusinessPlan(biz.id, plan)
                                    bizToChangePlan = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(plan.title, fontWeight = FontWeight.Bold)
                                Text("₹${plan.priceMonthlyInr}/mo • Max ${plan.maxLocations} branches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bizToChangePlan = null }) { Text("Close") }
            }
        )
    }

    // Suspend Business Dialog
    if (bizToSuspend != null) {
        val biz = bizToSuspend!!
        AlertDialog(
            onDismissRequest = { bizToSuspend = null },
            title = { Text("Freeze / Suspend Business", fontWeight = FontWeight.Bold, color = AccentRose) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Suspending '${biz.name}' will immediately lock all branch queues and prevent users from joining.")
                    OutlinedTextField(
                        value = suspensionReason,
                        onValueChange = { suspensionReason = it },
                        label = { Text("Suspension Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminToggleBusinessSuspension(biz.id, true, suspensionReason)
                        bizToSuspend = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Confirm Suspension", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bizToSuspend = null }) { Text("Cancel") }
            }
        )
    }

    // Onboard Business Dialog
    if (showAddBizDialog) {
        var bizName by remember { mutableStateOf("") }
        var bizDesc by remember { mutableStateOf("") }
        var bizCity by remember { mutableStateOf("Lucknow") }
        var bizCat by remember { mutableStateOf(BusinessCategory.HEALTHCARE) }
        var bizPlan by remember { mutableStateOf(SubscriptionPlan.PRO) }

        AlertDialog(
            onDismissRequest = { showAddBizDialog = false },
            title = { Text("🏢 Onboard New Business", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text("Business Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bizDesc,
                        onValueChange = { bizDesc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bizCity,
                        onValueChange = { bizCity = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Initial SaaS Tier:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubscriptionPlan.values().forEach { plan ->
                            FilterChip(
                                selected = bizPlan == plan,
                                onClick = { bizPlan = plan },
                                label = { Text(plan.title.take(4), fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bizName.isNotBlank()) {
                            viewModel.adminAddBusiness(bizName.trim(), bizCat, bizDesc.trim(), bizCity.trim(), bizPlan)
                            showAddBizDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Onboard", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBizDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * 4. Global Live Queues Master Monitor (Force Serve, Force Cancel, Purge)
 */
@Composable
fun AdminLiveQueuesTab(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel
) {
    var statusFilter by remember { mutableStateOf<TokenStatus?>(null) }
    var tokenToCancel by remember { mutableStateOf<Token?>(null) }
    var cancelReason by remember { mutableStateOf("Admin queue clearance") }

    val filteredTokens = uiState.tokens.filter { token ->
        statusFilter == null || token.status == statusFilter
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Real-Time Platform Queue Monitor", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Button(
                    onClick = { viewModel.adminPurgeCompletedTokens() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Purge Finished", fontSize = 11.sp)
                }
            }
        }

        // Status Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All Tokens (${uiState.tokens.size})") }
                    )
                }
                listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING, TokenStatus.SERVED, TokenStatus.CANCELLED).forEach { status ->
                    item {
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { statusFilter = if (statusFilter == status) null else status },
                            label = { Text(status.name) }
                        )
                    }
                }
            }
        }

        items(filteredTokens, key = { it.id }) { token ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = PrimaryCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = token.tokenNumber,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = PrimaryCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(token.businessName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${token.branchName} • ${token.serviceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Customer: ${token.userName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Token Status Badge
                        SQStatusBadge(status = token.status)
                    }

                    if (token.status in listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.adminForceServeToken(token.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Force Serve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { tokenToCancel = token },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Force Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (tokenToCancel != null) {
        val token = tokenToCancel!!
        AlertDialog(
            onDismissRequest = { tokenToCancel = null },
            title = { Text("Force Cancel Token ${token.tokenNumber}?", fontWeight = FontWeight.Bold, color = AccentRose) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This token will be cancelled immediately across the live display.")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Admin Cancellation Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminCancelToken(token.id, cancelReason)
                        tokenToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Cancel Token", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tokenToCancel = null }) { Text("Close") }
            }
        )
    }
}

/**
 * 5. Platform Audit Trail & Security Stream
 */
@Composable
fun AdminAuditTab(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLogs = uiState.auditLogs.filter {
        it.actorName.contains(searchQuery, ignoreCase = true) ||
                it.action.contains(searchQuery, ignoreCase = true) ||
                it.target.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search audit logs by actor, action, target...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        item {
            Text("Real-Time Immutable Audit Log (${filteredLogs.size})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }

        items(filteredLogs) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${log.actorName} (${log.actorRole})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryCyan
                        )
                        Surface(
                            color = AccentEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = log.action,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AccentEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.target,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AdminPricingTab(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = PrimaryCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dynamic Subscription Pricing & Rates Manager", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Increase or decrease subscription rates for Pro, Business, and Enterprise plans anytime. Changes apply instantly across the platform with 0% AI server overhead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(listOf(SubscriptionPlan.PRO, SubscriptionPlan.BUSINESS, SubscriptionPlan.ENTERPRISE)) { plan ->
            val currentPrice = uiState.planPrices[plan] ?: when (plan) {
                SubscriptionPlan.PRO -> 199
                SubscriptionPlan.BUSINESS -> 499
                SubscriptionPlan.ENTERPRISE -> 999
                else -> 0
            }
            var priceInput by remember(currentPrice) { mutableStateOf(currentPrice.toString()) }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(plan.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Max ${plan.maxLocations} Branches • ${plan.maxStaff} Staff • Analytics: ${if (plan.hasAnalytics) "Yes" else "No"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "₹$currentPrice / mo",
                                fontWeight = FontWeight.Black,
                                color = PrimaryCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = priceInput,
                            onValueChange = { priceInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Monthly Rate (INR)") },
                            leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = PrimaryCyan) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                val newP = priceInput.toIntOrNull() ?: currentPrice
                                viewModel.updatePlanPrice(plan, newP)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                        ) {
                            Text("Update Rate", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
