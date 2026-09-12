package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AbuseStatus
import com.example.model.SubscriptionPlan
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.QRCodeGenerator
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrManagementScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    val currentBiz紧 = uiState.selectedBusiness ?: uiState.businesses.firstOrNull()
    val branches = uiState.locations.filter { it.businessId == currentBiz紧?.id }
    var selectedBranch by remember { mutableStateOf(branches.firstOrNull() ?: uiState.selectedLocation) }

    val currentQr = uiState.qrCodes.find { it.locationId == selectedBranch?.id && !it.isRevoked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Code Pass Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Safe Public Identification",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "QR codes contain only safe public slugs with tamper-proof validation. Revoked codes stop functioning immediately across all devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Printable High-Res QR Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, PrimaryCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentBiz紧?.name?.uppercase() ?: "SMARTQUEUE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Branch: ${selectedBranch?.branchName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // QR Code Visual Pass
                        Surface(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            color = Color.White
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                                    .border(2.dp, Color.Black, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val qrCodeStr = currentQr?.publicCode ?: "SQ-ACTIVE-2026"
                                val qrBitmap = remember(qrCodeStr) {
                                    QRCodeGenerator.generateQRCodeBitmap(qrCodeStr, 512)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap,
                                            contentDescription = "Public QR Code",
                                            modifier = Modifier.size(130.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.QrCode2,
                                            contentDescription = "Public QR Code",
                                            tint = Color.Black,
                                            modifier = Modifier.size(130.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = qrCodeStr,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Scan with SmartQueue to get live token",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Scanned ${currentQr?.scanCount ?: 0} times",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // QR Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SQButton(
                        text = "Print Standee PDF",
                        onClick = { viewModel.showToast("🖨️ PDF Standee layout prepared for print") },
                        variant = SQButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Print
                    )
                    SQButton(
                        text = "Revoke & Regenerate",
                        onClick = {
                            selectedBranch?.let {
                                viewModel.generateOrRevokeQr(it.id, "REVOKE")
                            }
                        },
                        variant = SQButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Refresh
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPayScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    val currentBiz = uiState.selectedBusiness ?: uiState.businesses.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monetization & Plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            item {
                Text(
                    text = "Unlock Higher Branch & Staff Scale",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Current Plan for ${currentBiz?.name}: ${currentBiz?.plan?.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryCyan
                )
            }

            items(SubscriptionPlan.values()) { plan ->
                val isCurrent = currentBiz?.plan == plan

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        if (isCurrent) 2.dp else 1.dp,
                        if (isCurrent) PrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (plan.priceMonthlyInr == 0) "FREE" else "₹${plan.priceMonthlyInr}/mo",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (isCurrent) PrimaryCyan else MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("• Up to ${plan.maxLocations} Location Branches", style = MaterialTheme.typography.bodySmall)
                        Text("• Up to ${plan.maxStaff} Staff Operators", style = MaterialTheme.typography.bodySmall)
                        Text("• Up to ${plan.maxServices} Queue Services", style = MaterialTheme.typography.bodySmall)
                        if (plan.hasAnalytics) Text("• Advanced Analytics & Drop-off Heatmaps", style = MaterialTheme.typography.bodySmall, color = AccentEmerald)
                        if (plan.hasCustomBranding) Text("• Custom Brand Logo & Standees", style = MaterialTheme.typography.bodySmall, color = AccentEmerald)

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isCurrent) {
                            Surface(
                                color = AccentEmerald.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "CURRENT ACTIVE PLAN",
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            SQButton(
                                text = "Upgrade via Razorpay",
                                onClick = {
                                    if (currentBiz != null) {
                                        viewModel.upgradePlan(currentBiz.id, plan)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                variant = SQButtonVariant.PRIMARY
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbusePreventionScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    var appealText by remember { mutableStateOf("") }
    var showAppealDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abuse Prevention Engine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = AccentAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Automated 3-Strike Protection Policy",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Monitors repeated queue joins without physical appearance, rapid spam cancellations, and phantom accounts. Users with 3 confirmed strikes face temporary queue lockout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.currentUser.isRestricted) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, AccentRose)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ YOUR ACCOUNT IS CURRENTLY RESTRICTED",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AccentRose
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reason: ${uiState.currentUser.restrictionReason ?: "Policy violations"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SQButton(
                                text = "Submit Restriction Appeal",
                                onClick = { showAppealDialog = true },
                                variant = SQButtonVariant.DANGER
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Violation Audit Records (${uiState.abuseRecords.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (uiState.abuseRecords.isEmpty()) {
                item {
                    SQEmptyState(
                        title = "No Abuse Violations Recorded",
                        description = "System queue integrity is clean. All operations are complying with fair-use thresholds.",
                        icon = Icons.Default.VerifiedUser
                    )
                }
            } else {
                items(uiState.abuseRecords) { record ->
                    SQCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = record.userName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AccentRose.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = record.status.name,
                                            color = AccentRose,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = record.violationType.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = record.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            TextButton(onClick = { viewModel.dismissAbuseViolation(record.id) }) {
                                Text("Dismiss", color = PrimaryCyan)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppealDialog) {
        AlertDialog(
            onDismissRequest = { showAppealDialog = false },
            title = { Text("Submit Appeal") },
            text = {
                Column {
                    Text("Provide justification to reinstate queue access:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = appealText,
                        onValueChange = { appealText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Explain circumstances...") }
                    )
                }
            },
            confirmButton = {
                SQButton(
                    text = "Send Appeal",
                    onClick = {
                        viewModel.appealAbuseRestriction(appealText)
                        showAppealDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAppealDialog = false }) { Text("Cancel") }
            }
        )
    }
}

