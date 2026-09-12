package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.QRCodeGenerator
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDashboardScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onNavigateToStaff: () -> Unit,
    onNavigateToBranches: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToAbuse: () -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val ownedBusinesses = uiState.merchantBusinesses.ifEmpty {
        uiState.businesses.filter { it.ownerId == uiState.currentUser.id }
    }
    val selectedBiz = uiState.selectedBusiness?.takeIf { it.ownerId == uiState.currentUser.id }
        ?: ownedBusinesses.firstOrNull()
        ?: (if (uiState.currentUser.role == UserRole.ADMIN || uiState.currentUser.role == UserRole.SUPER_ADMIN) uiState.businesses.firstOrNull() else null)

    if (selectedBiz == null) {
        MerchantOnboardingScreen(
            uiState = uiState,
            viewModel = viewModel,
            onCancelOnboarding = { viewModel.logout() },
            onOnboardingFinished = {
                // When finished, the newly created business will be part of uiState.businesses
                // and selectedBiz will become non-null automatically.
            }
        )
        return
    }

    val branches = uiState.locations.filter { it.businessId == selectedBiz?.id }
    var selectedBranch by remember { mutableStateOf(branches.firstOrNull() ?: uiState.selectedLocation) }

    // Dialog state for Registering New Shop / Clinic
    var showRegisterShopDialog by remember { mutableStateOf(false) }
    var showDeleteShopDialog by remember { mutableStateOf(false) }
    var newShopName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(BusinessCategory.HEALTHCARE) }
    var newCounterName by remember { mutableStateOf("Counter 1") }
    var newShopAddress by remember { mutableStateOf("") }
    var newShopCity by remember { mutableStateOf("Lucknow") }
    var newShopPhone by remember { mutableStateOf("") }
    var newAvgDuration by remember { mutableStateOf("15") }

    // Dialog state for Printable Standee Poster Preview
    var showPrintablePosterDialog by remember { mutableStateOf(false) }

    // Admin subscription tool dialog state (Admin only)
    var showAdminSubDialog by remember { mutableStateOf(false) }
    var customDaysInput by remember { mutableStateOf("30") }

    var showProfileEditorDialog by remember { mutableStateOf(false) }
    var profNameInput by remember { mutableStateOf("") }
    var profExpInput by remember { mutableStateOf("5") }
    var profQualInput by remember { mutableStateOf("") }
    var profSpecsInput by remember { mutableStateOf("") }
    var profLangsInput by remember { mutableStateOf("") }
    var profWebInput by remember { mutableStateOf("") }
    var profDescInput by remember { mutableStateOf("") }

    var isActionLoading by remember { mutableStateOf(false) }

    // Synchronize branch selection when business changes
    LaunchedEffect(selectedBiz?.id) {
        val firstBranch = branches.firstOrNull()
        selectedBranch = firstBranch
        if (firstBranch != null) {
            viewModel.selectLocation(firstBranch)
        }
    }

    val branchTokens = if (selectedBranch != null) {
        uiState.tokens.filter { it.locationId == selectedBranch?.id }
    } else emptyList()

    val currentServing = branchTokens.find { it.status == TokenStatus.SERVING }
        ?: branchTokens.find { it.status == TokenStatus.CALLED }

    val waitingTokens = branchTokens.filter { it.status == TokenStatus.WAITING }
        .sortedWith(
            compareBy<Token> { it.priority == TokenPriority.STANDARD }
                .thenBy { it.sequenceNumber }
        )

    val servedToday = branchTokens.count { it.status == TokenStatus.SERVED }
    val noShows = branchTokens.count { it.status == TokenStatus.NO_SHOW }
    val isPaused = selectedBranch?.isPaused == true
    val staffName = uiState.currentUser.name
    val isAdmin = uiState.currentUser.role == UserRole.ADMIN

    // Unique Barcode / QR Data for current branch/shop
    val currentQrData = uiState.qrCodes.find { it.businessId == selectedBiz?.id && it.locationId == selectedBranch?.id && !it.isRevoked }
        ?: uiState.qrCodes.find { it.businessId == selectedBiz?.id && !it.isRevoked }

    val stableSeed = Math.abs(((selectedBiz?.id ?: "") + (selectedBranch?.id ?: "")).hashCode() % 9000 + 1000)
    val fallbackPublicCode = "SQ-${selectedBiz?.category?.name?.take(4)?.uppercase() ?: "SHOP"}-${selectedBranch?.branchName?.filter { it.isLetter() }?.take(4)?.uppercase()?.ifEmpty { "MAIN" } ?: "MAIN"}-$stableSeed"
    val uniqueBarcodeSlug = currentQrData?.publicCode ?: fallbackPublicCode

    LaunchedEffect(selectedBiz?.id, selectedBranch?.id) {
        val branch = selectedBranch
        if (selectedBiz != null && branch != null) {
            viewModel.ensurePermanentQrCode(selectedBiz.id, branch.id, selectedBiz.name, branch.branchName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedBiz?.name ?: "Merchant Hub",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedBiz?.isVerified == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = "Branch: ${selectedBranch?.branchName ?: "Main"} • Status: ${if (isPaused) "⏸️ Paused" else "🟢 Live & Active"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Queue Pause / Resume button
                    IconButton(
                        onClick = {
                            selectedBranch?.let {
                                viewModel.toggleLocationQueue(it.id, !isPaused)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume Queue" else "Pause Queue",
                            tint = if (isPaused) AccentEmerald else AccentAmber
                        )
                    }

                    // Standee Poster Quick Action
                    IconButton(onClick = { showPrintablePosterDialog = true }) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Print Poster", tint = PrimaryCyan)
                    }

                    // Admin only icon
                    if (isAdmin) {
                        IconButton(
                            onClick = { showAdminSubDialog = true },
                            modifier = Modifier.testTag("admin_sub_tool_appbar_btn")
                        ) {
                            Icon(Icons.Default.ManageAccounts, contentDescription = "Admin Plan Tools", tint = AccentAmber)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
        ) {
            // 1. Shop Switcher & "Register New Shop / Clinic" Header Row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏪 YOUR SHOPS / CLINICS",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (selectedBiz != null) {
                                    OutlinedButton(
                                        onClick = { showDeleteShopDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                        border = BorderStroke(1.dp, AccentRose),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRose, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", color = AccentRose, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }

                                // Register New Shop Button
                                Button(
                                    onClick = { showRegisterShopDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.AddBusiness, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Shop", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Horizontal Shop Selector Chips
                        val selectorList = ownedBusinesses.ifEmpty { uiState.businesses }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectorList) { biz ->
                                FilterChip(
                                    selected = selectedBiz?.id == biz.id,
                                    onClick = {
                                        viewModel.selectBusiness(biz)
                                        val newBranches = uiState.locations.filter { it.businessId == biz.id }
                                        selectedBranch = newBranches.firstOrNull()
                                    },
                                    label = { Text(biz.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (biz.category) {
                                                BusinessCategory.HEALTHCARE -> Icons.Default.LocalHospital
                                                BusinessCategory.SALON_BEAUTY -> Icons.Default.ContentCut
                                                BusinessCategory.REPAIR -> Icons.Default.Build
                                                BusinessCategory.RESTAURANT -> Icons.Default.Restaurant
                                                else -> Icons.Default.Store
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. CRITICAL FEATURE: Unique Shop Barcode & Printable QR Poster Standee Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, PrimaryCyan)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = PrimaryCyan)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SHOP QR & BARCODE STANDEE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                        color = PrimaryCyan
                                    )
                                    Text(
                                        text = selectedBiz?.name ?: "Shop QR Poster",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = uniqueBarcodeSlug,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = PrimaryCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // High-contrast QR Poster Graphic
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Scan to Book Token",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "दुकान/क्लीनिक के बाहर लगाएं। ग्राहक QR स्कैन करके घर या वेटिंग एरिया से टोकन ले सकेंगे।",
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Barcode: $uniqueBarcodeSlug",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Blue
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // QR Graphic Box
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.QrCode2,
                                        contentDescription = "Shop QR Code",
                                        tint = Color.Black,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Poster Action Buttons
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showPrintablePosterDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Print Standee Poster", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.showToast("📋 Barcode & Link copied: $uniqueBarcodeSlug")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share Code", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedBiz?.let { b ->
                                        profNameInput = b.professionalName
                                        profExpInput = b.experienceYears.toString()
                                        profQualInput = b.qualifications
                                        profSpecsInput = b.specialties
                                        profLangsInput = b.languages
                                        profWebInput = b.websiteUrl
                                        profDescInput = b.description
                                        showProfileEditorDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Professional Profile & Verification Status", color = PrimaryCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. Quick Stats (Waiting in line, Served Today, Est. Time)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SQStatCard(
                        title = "Waiting in Line",
                        value = "${waitingTokens.size}",
                        subtitle = "customers waiting",
                        icon = Icons.Default.Groups,
                        color = AccentAmber,
                        modifier = Modifier.weight(1f)
                    )
                    SQStatCard(
                        title = "Served Today",
                        value = "$servedToday",
                        subtitle = "completed tokens",
                        icon = Icons.Default.CheckCircle,
                        color = AccentEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. MAIN 1-TAP QUEUE COUNTER CONTROLLER (Call Next, Mark Served, Skip)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .testTag("operator_live_controller_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, if (currentServing != null) AccentEmerald else PrimaryCyan)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SQLiveDot(color = if (currentServing != null) AccentEmerald else AccentAmber)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPaused) "QUEUE PAUSED (रोक लगा है)" else "LIVE COUNTER CONTROLLER",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isPaused) AccentAmber else AccentEmerald
                                )
                            }
                            if (currentServing != null) {
                                SQStatusBadge(status = currentServing.status)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (currentServing != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "CURRENTLY SERVING (चालू टोकन)",
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentServing.tokenNumber,
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = AccentEmerald
                                    )
                                    Text(
                                        text = "${currentServing.userName} • ${currentServing.serviceName}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("COUNTER", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            currentServing.counterNumber ?: "Counter 1",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Large 1-Tap Action Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentServing.status == TokenStatus.CALLED) {
                                    Button(
                                        onClick = {
                                            isActionLoading = true
                                            viewModel.startServing(currentServing.id, staffName,
                                                onSuccess = { isActionLoading = false },
                                                onFailure = { isActionLoading = false }
                                            )
                                        },
                                        enabled = !isActionLoading,
                                        modifier = Modifier.weight(1.2f).height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                                    ) {
                                        if (isActionLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Service", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            isActionLoading = true
                                            viewModel.markServed(currentServing.id, staffName,
                                                onSuccess = { isActionLoading = false },
                                                onFailure = { isActionLoading = false }
                                            )
                                        },
                                        enabled = !isActionLoading,
                                        modifier = Modifier.weight(1.2f).height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                                    ) {
                                        if (isActionLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Mark Served (Done)", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        isActionLoading = true
                                        viewModel.skipToken(currentServing.id, staffName,
                                            onSuccess = { isActionLoading = false },
                                            onFailure = { isActionLoading = false }
                                        )
                                    },
                                    enabled = !isActionLoading,
                                    modifier = Modifier.weight(0.8f).height(48.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isActionLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Default.Redo, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Skip")
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Counter is Free (कोई ग्राहक अभी नहीं है)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${waitingTokens.size} customers waiting in queue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        selectedBranch?.let {
                                            isActionLoading = true
                                            viewModel.callNextToken(it.id, "Counter 1", staffName,
                                                onSuccess = { isActionLoading = false },
                                                onFailure = { isActionLoading = false }
                                            )
                                        }
                                    },
                                    enabled = waitingTokens.isNotEmpty() && !isPaused && !isActionLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                                ) {
                                    if (isActionLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Call Next Customer (${waitingTokens.firstOrNull()?.tokenNumber ?: "None"})",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (currentServing != null && waitingTokens.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    selectedBranch?.let {
                                        isActionLoading = true
                                        viewModel.callNextToken(it.id, "Counter 1", staffName,
                                            onSuccess = { isActionLoading = false },
                                            onFailure = { isActionLoading = false }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isPaused && !isActionLoading
                            ) {
                                if (isActionLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 1.5.dp)
                                } else {
                                    Icon(Icons.Default.SkipNext, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Call Next Waiting Token (${waitingTokens.first().tokenNumber})")
                                }
                            }
                        }
                    }
                }
            }

            // 5. Live Waiting List Table
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Waiting Customers (${waitingTokens.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Queue Order",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (waitingTokens.isEmpty()) {
                item {
                    SQEmptyState(
                        title = "No Customers Waiting",
                        description = "Queue is currently clear! Share the shop QR poster outside to let customers book digital tokens.",
                        icon = Icons.Default.Checklist
                    )
                }
            } else {
                items(waitingTokens) { token ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(PrimaryCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = token.tokenNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = PrimaryCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = token.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = "${token.serviceName} • ${token.userPhone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Button(
                                onClick = {
                                    selectedBranch?.let {
                                        isActionLoading = true
                                        viewModel.callNextToken(it.id, "Counter 1", staffName,
                                            onSuccess = { isActionLoading = false },
                                            onFailure = { isActionLoading = false }
                                        )
                                    }
                                },
                                enabled = !isActionLoading,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                if (isActionLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.Black, strokeWidth = 1.dp)
                                } else {
                                    Text("Call", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 6. Admin Only Diagnostics (Hidden from normal merchants)
            if (isAdmin) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, AccentAmber)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("👑 SuperAdmin Override Tools", fontWeight = FontWeight.Bold, color = AccentAmber)
                            Text("Active Subscription: ${selectedBiz?.plan?.title} (Expires in ${((selectedBiz?.subscriptionExpiresAt ?: 0L) - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)} days)", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showAdminSubDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open Admin Subscription Editor", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL: REGISTER NEW SHOP / CLINIC DIALOG
    // =========================================================================
    if (showRegisterShopDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterShopDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, tint = PrimaryCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register Shop / Clinic", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Fill shop details. A unique QR code & barcode poster will be instantly generated for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newShopName,
                        onValueChange = { newShopName = it },
                        label = { Text("Shop / Clinic Name (दुकान का नाम)") },
                        placeholder = { Text("e.g. Sharma Dental Clinic") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Category Selector
                    Text("Category (श्रेणी):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BusinessCategory.values()) { cat ->
                            FilterChip(
                                selected = newCategory == cat,
                                onClick = { newCategory = cat },
                                label = { Text(cat.title, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newCounterName,
                        onValueChange = { newCounterName = it },
                        label = { Text("Doctor / Counter Name (काउंण्टर)") },
                        placeholder = { Text("e.g. Dr. Sharma Cabin 1 / Counter A") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newAvgDuration,
                        onValueChange = { newAvgDuration = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Average Minutes per Token (समय/मिनट)") },
                        placeholder = { Text("15") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newShopCity,
                        onValueChange = { newShopCity = it },
                        label = { Text("City (शहर)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newShopAddress,
                        onValueChange = { newShopAddress = it },
                        label = { Text("Shop Address (पता)") },
                        placeholder = { Text("Main Road, Near Market") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newShopPhone,
                        onValueChange = { newShopPhone = it },
                        label = { Text("Contact Phone (फ़ोन नंबर)") },
                        placeholder = { Text("+91 98765 43210") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newShopName.isNotBlank()) {
                            viewModel.registerMerchantShop(
                                shopName = newShopName,
                                category = newCategory,
                                counterName = newCounterName,
                                address = newShopAddress,
                                city = newShopCity,
                                phone = newShopPhone,
                                avgDurationMinutes = newAvgDuration.toIntOrNull() ?: 15
                            ) {
                                showRegisterShopDialog = false
                                showPrintablePosterDialog = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Register & Generate QR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterShopDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // =========================================================================
    // MODAL: DELETE SHOP CONFIRMATION DIALOG
    // =========================================================================
    if (showDeleteShopDialog && selectedBiz != null) {
        AlertDialog(
            onDismissRequest = { showDeleteShopDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AccentRose, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = "Delete Shop / Clinic?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete '${selectedBiz.name}'?",
                        fontWeight = FontWeight.Bold,
                        color = AccentRose
                    )
                    Text(
                        text = "This will permanently remove the shop, its counters, all service configurations, barcodes, and active queue tokens. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBusiness(selectedBiz.id)
                        showDeleteShopDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteShopDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // =========================================================================
    // MODAL: PRINTABLE STANDEE POSTER PREVIEW DIALOG
    // =========================================================================
    if (showPrintablePosterDialog) {
        AlertDialog(
            onDismissRequest = { showPrintablePosterDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = PrimaryCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shop Standee Poster", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Printable Poster Frame
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(3.dp, Color.Black, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SMARTQUEUE DIGITAL PASS",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 2.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = selectedBiz?.name ?: "Shop Name",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                                color = Color.Black
                            )

                            Text(
                                text = "Category: ${selectedBiz?.category?.title ?: "Services"}",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            val qrBitmap = remember(uniqueBarcodeSlug) {
                                QRCodeGenerator.generateQRCodeBitmap(uniqueBarcodeSlug, 512)
                            }

                            // Large Printable QR Code
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "Printable QR",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = "Printable QR",
                                        tint = Color.Black,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "BARCODE: $uniqueBarcodeSlug",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.Black
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "👉 Scan QR Code to book token & skip the physical line!",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "👉 QR कोड स्कैन करके अपना लाइव टोकन बुक करें और आराम से प्रतीक्षा करें।",
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.showToast("🖨️ Sending standee poster to printer / PDF download!")
                        showPrintablePosterDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print / Download Poster", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintablePosterDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // =========================================================================
    // ADMIN SUBSCRIPTION CONTROLLER DIALOG (Admin only)
    // =========================================================================
    if (showAdminSubDialog && isAdmin) {
        val biz = selectedBiz ?: uiState.businesses.first()
        AlertDialog(
            onDismissRequest = { showAdminSubDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin SaaS Plan Controller", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Target Business: ${biz.name}", fontWeight = FontWeight.Bold)
                    SubscriptionPlan.values().forEach { plan ->
                        OutlinedButton(
                            onClick = { viewModel.adminSetBusinessPlan(biz.id, plan) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Switch to ${plan.title}")
                        }
                    }
                    Button(
                        onClick = { viewModel.adminExtendBusinessSubscription(biz.id, 30) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                    ) {
                        Text("+30 Days Extension", color = Color.Black)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAdminSubDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Merchant Profile & Verification Dialog
    if (showProfileEditorDialog && selectedBiz != null) {
        val biz = selectedBiz!!
        AlertDialog(
            onDismissRequest = { showProfileEditorDialog = false },
            title = { Text("Merchant Public Profile & Verification") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Current Status: ${biz.verificationStatus.name} (${if(biz.isVerified) "Verified" else "Unverified"})",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (biz.isVerified) PrimaryCyan else AccentAmber
                    )

                    if (!biz.isVerified && biz.verificationStatus != MerchantVerificationStatus.PENDING) {
                        Button(
                            onClick = {
                                viewModel.submitForVerification(biz.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                        ) {
                            Text("Submit for Admin Verification", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isAdmin && biz.verificationStatus == MerchantVerificationStatus.PENDING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.adminApproveVerification(biz.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                            ) {
                                Text("Approve", color = Color.Black)
                            }
                            Button(
                                onClick = { viewModel.adminRejectVerification(biz.id, "Documents incomplete") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                            ) {
                                Text("Reject", color = Color.White)
                            }
                        }
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = profNameInput,
                        onValueChange = { profNameInput = it },
                        label = { Text("Professional / Doctor Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profExpInput,
                        onValueChange = { profExpInput = it },
                        label = { Text("Years of Experience") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profQualInput,
                        onValueChange = { profQualInput = it },
                        label = { Text("Qualifications (e.g. MBBS, MD / MDS)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profSpecsInput,
                        onValueChange = { profSpecsInput = it },
                        label = { Text("Specialties (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profLangsInput,
                        onValueChange = { profLangsInput = it },
                        label = { Text("Languages Spoken") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profWebInput,
                        onValueChange = { profWebInput = it },
                        label = { Text("Website / Portfolio URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profDescInput,
                        onValueChange = { profDescInput = it },
                        label = { Text("Shop / Clinic Bio / Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                SQButton(
                    text = "Save Profile",
                    onClick = {
                        showProfileEditorDialog = false
                        viewModel.updateMerchantProfile(
                            businessId = biz.id,
                            professionalName = profNameInput,
                            experienceYears = profExpInput.toIntOrNull() ?: 0,
                            qualifications = profQualInput,
                            specialties = profSpecsInput,
                            languages = profLangsInput,
                            websiteUrl = profWebInput,
                            description = profDescInput,
                            about = "",
                            logoUrl = ""
                        )
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun OperatorNavIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
