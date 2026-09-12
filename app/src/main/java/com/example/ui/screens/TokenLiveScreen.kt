package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Token
import com.example.model.TokenPriority
import com.example.model.TokenStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenLiveScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("Change of plans") }
    var isCancelling by remember { mutableStateOf(false) }

    val activeToken = uiState.activeUserToken
    val userTokens = uiState.tokens.filter { it.userId == uiState.currentUser.id }
        .sortedByDescending { it.createdAt }
    val displayedToken = activeToken ?: userTokens.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BentoPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "SmartQueue",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = displayedToken?.businessName ?: "Live Queue Pass",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (displayedToken != null && displayedToken.status in listOf(TokenStatus.WAITING, TokenStatus.CALLED)) {
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Cancel Token",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (displayedToken == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                SQEmptyState(
                    title = "No Active Token",
                    description = "You are not currently in any queue. Scan a business QR code or explore nearby services to get your digital token.",
                    icon = Icons.Default.ConfirmationNumber,
                    actionLabel = "Explore Queues",
                    onAction = onBack
                )
            }
        } else {
            val locationTokens = uiState.tokens.filter { it.locationId == displayedToken.locationId }
            val currentServing = locationTokens.find { it.status == TokenStatus.SERVING }
                ?: locationTokens.find { it.status == TokenStatus.CALLED }

            val waitingList = locationTokens.filter { it.status == TokenStatus.WAITING }
                .sortedBy { it.sequenceNumber }

            val peopleAhead = if (displayedToken.status == TokenStatus.WAITING) {
                waitingList.count { it.sequenceNumber < displayedToken.sequenceNumber }
            } else {
                0
            }

            val queueHealth = when {
                peopleAhead > 10 -> Pair("Busy", BentoRoseContainer to BentoOnRoseContainer)
                peopleAhead in 4..10 -> Pair("Moderate", BentoLavenderContainer to BentoOnLavenderContainer)
                else -> Pair("Fast Moving", BentoMintContainer to BentoOnMintContainer)
            }

            val isTerminal = displayedToken.status in listOf(
                TokenStatus.SERVED,
                TokenStatus.SKIPPED,
                TokenStatus.NO_SHOW,
                TokenStatus.CANCELLED,
                TokenStatus.EXPIRED,
                TokenStatus.BLOCKED
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
            ) {
                // 1. Bento primary / Adaptive Hero Card (Top Box)
                item {
                    val cardBg = when (displayedToken.status) {
                        TokenStatus.SERVED -> BentoMintContainer
                        TokenStatus.CANCELLED, TokenStatus.SKIPPED, TokenStatus.NO_SHOW -> BentoRoseContainer
                        else -> BentoPrimaryContainer
                    }
                    val cardBorder = when (displayedToken.status) {
                        TokenStatus.SERVED -> AccentEmerald.copy(alpha = 0.4f)
                        TokenStatus.CANCELLED, TokenStatus.SKIPPED, TokenStatus.NO_SHOW -> AccentRose.copy(alpha = 0.4f)
                        else -> BentoSkyBorder
                    }
                    val cardTextPrimary = when (displayedToken.status) {
                        TokenStatus.SERVED -> BentoOnMintContainer
                        TokenStatus.CANCELLED, TokenStatus.SKIPPED, TokenStatus.NO_SHOW -> BentoOnRoseContainer
                        else -> BentoOnPrimaryContainer
                    }
                    val badgeBg = when (displayedToken.status) {
                        TokenStatus.SERVED -> AccentEmerald
                        TokenStatus.CANCELLED, TokenStatus.SKIPPED, TokenStatus.NO_SHOW -> AccentRose
                        TokenStatus.CALLED, TokenStatus.SERVING -> AccentAmber
                        else -> BentoPrimary
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                            .padding(vertical = 24.dp, horizontal = 20.dp)
                            .testTag("bento_hero_token_card"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isTerminal) "RESOLVED QUEUE PASS" else "YOUR CURRENT TOKEN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                ),
                                color = cardTextPrimary.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayedToken.tokenNumber,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 58.sp,
                                    letterSpacing = (-1.5).sp,
                                    fontFamily = FontFamily.SansSerif
                                ),
                                color = cardTextPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Bento Status Pill
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = badgeBg,
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (displayedToken.status in listOf(TokenStatus.CALLED, TokenStatus.SERVING)) {
                                        SQLiveDot(color = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "Status: ${displayedToken.status.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${displayedToken.branchName} • ${displayedToken.serviceName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cardTextPrimary.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                // 2. Bento 2x2 + Full Width Metrics Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Row 1: Now Serving & Ahead of You
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bento Card: Now Serving
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "NOW SERVING",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentServing?.tokenNumber ?: "None",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.5).sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(BentoPrimary.copy(alpha = 0.4f))
                                    )
                                }
                            }

                            // Bento Card: Ahead of You
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "AHEAD OF YOU",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format("%02d", peopleAhead),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.5).sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isTerminal) "Queue finalized" else "People in line",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Row 2: Estimated Wait (Full Width Span)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isTerminal) "REMAINING WAIT" else "ESTIMATED WAIT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = if (isTerminal) MaterialTheme.colorScheme.onSurfaceVariant else BentoPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = if (isTerminal) "0" else "${displayedToken.estimatedWaitMinutes}",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 22.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "mins",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }

                                if (!isTerminal) {
                                    SQBentoEqualizerBars(color = BentoPrimary)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Row 3: Queue Health & Counter Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Queue Health (Lavender Box)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(105.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(BentoLavenderContainer)
                                    .border(1.dp, BentoLavenderBorder, RoundedCornerShape(24.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "QUEUE STATUS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = BentoOnLavenderContainer
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SQLiveDot(color = if (isTerminal) AccentRose else AccentEmerald)
                                        Text(
                                            text = if (isTerminal) "Completed" else queueHealth.first,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = BentoOnLavenderContainer
                                        )
                                    }
                                }
                            }

                            // Counter Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(105.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "ASSIGNED DESK",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = displayedToken.counterNumber ?: "Desk 01",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Bento Full-Width Refresh Action Button (Only show for active)
                if (!isTerminal) {
                    item {
                        Button(
                            onClick = { viewModel.showToast("Queue status refreshed!") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("scan_refresh_bento_button"),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B1B1F),
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "REFRESH LIVE QUEUE STATUS",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. Bento Interactive Journey Step-Card
                item {
                    SQCard(shapeRadius = 24.dp) {
                        Text(
                            text = "LIVE QUEUE JOURNEY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = BentoPrimary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            QueueTimelineStep(
                                title = "Token Issued",
                                subtitle = "Checked into ${displayedToken.branchName}",
                                isCompleted = true,
                                isActive = displayedToken.status == TokenStatus.CREATED
                            )
                            QueueTimelineStep(
                                title = "Waiting in Line",
                                subtitle = if (displayedToken.status == TokenStatus.CANCELLED) "Cancelled by customer" else "$peopleAhead customers ahead of you",
                                isCompleted = displayedToken.status in listOf(TokenStatus.CALLED, TokenStatus.SERVING, TokenStatus.SERVED) || (isTerminal && displayedToken.status != TokenStatus.CANCELLED),
                                isActive = displayedToken.status == TokenStatus.WAITING
                            )
                            QueueTimelineStep(
                                title = "Called to Desk",
                                subtitle = if (displayedToken.status == TokenStatus.SKIPPED) "Missed / Skipped" else if (displayedToken.status == TokenStatus.NO_SHOW) "No-Show Recorded" else displayedToken.counterNumber ?: "Assigned counter operator",
                                isCompleted = displayedToken.status in listOf(TokenStatus.SERVING, TokenStatus.SERVED),
                                isActive = displayedToken.status == TokenStatus.CALLED
                            )

                            val step4Title = when (displayedToken.status) {
                                TokenStatus.CANCELLED -> "Token Cancelled"
                                TokenStatus.SKIPPED -> "Token Skipped"
                                TokenStatus.NO_SHOW -> "No-Show"
                                else -> "Service Completed"
                            }
                            val step4Subtitle = when (displayedToken.status) {
                                TokenStatus.CANCELLED -> "Cancelled and released"
                                TokenStatus.SKIPPED -> "Missed when called"
                                TokenStatus.NO_SHOW -> "Marked absent"
                                else -> "Session closed and archived"
                            }
                            QueueTimelineStep(
                                title = step4Title,
                                subtitle = step4Subtitle,
                                isCompleted = isTerminal,
                                isActive = displayedToken.status == TokenStatus.SERVING
                            )
                        }
                    }
                }

                // 5. Verification QR Pass (Only show for active, hide for terminal)
                if (!isTerminal) {
                    item {
                        SQCard(shapeRadius = 24.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "COUNTER VERIFICATION PASS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = BentoPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Show this QR to the front desk when your token is called",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                    color = Color.White
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                            .border(2.dp, Color(0xFF1B1B1F), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.QrCode,
                                                contentDescription = "Token QR",
                                                tint = Color(0xFF1B1B1F),
                                                modifier = Modifier.size(80.dp)
                                            )
                                            Text(
                                                text = displayedToken.tokenNumber,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                color = Color(0xFF1B1B1F)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Action Buttons (Cancel / Call Support / Back Home)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isTerminal) {
                            SQButton(
                                text = "Cancel Token",
                                onClick = { showCancelDialog = true },
                                variant = SQButtonVariant.DANGER,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Close
                            )
                            SQButton(
                                text = "Contact Desk",
                                onClick = { viewModel.showToast("Contacting ${displayedToken.businessName} desk...") },
                                variant = SQButtonVariant.OUTLINE,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Phone
                            )
                        } else {
                            SQButton(
                                text = "Explore Queues",
                                onClick = onBack,
                                variant = SQButtonVariant.PRIMARY,
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.ConfirmationNumber
                            )
                        }
                    }
                }
            }
        }
    }

    // Cancel Token Confirmation Dialog
    if (showCancelDialog && displayedToken != null) {
        AlertDialog(
            onDismissRequest = { if (!isCancelling) showCancelDialog = false },
            title = { Text("Cancel Queue Token?") },
            text = {
                Column {
                    Text("Are you sure you want to release token ${displayedToken.tokenNumber}? You will lose your current position (#${displayedToken.sequenceNumber}) in line.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Reason for cancellation:", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isCancelling
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCancelling,
                    onClick = {
                        isCancelling = true
                        viewModel.cancelToken(displayedToken.id, cancelReason)
                        showCancelDialog = false
                        isCancelling = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Confirm Cancel")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCancelling,
                    onClick = { showCancelDialog = false }
                ) {
                    Text("Keep Token")
                }
            }
        )
    }
}

@Composable
fun QueueTimelineStep(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    when {
                        isCompleted -> AccentEmerald
                        isActive -> BentoPrimary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else if (isActive) {
                SQLiveDot(color = Color.White)
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isActive) BentoPrimary else if (isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
