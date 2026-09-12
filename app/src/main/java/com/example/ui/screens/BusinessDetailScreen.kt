package com.example.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDetailScreen(
    business: Business,
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit,
    onNavigateToToken: () -> Unit
) {
    val branches = uiState.locations.filter { it.businessId == business.id }
    var selectedBranch by remember { mutableStateOf(branches.firstOrNull()) }
    val branchServices = uiState.services.filter {
        it.businessId == business.id && (selectedBranch == null || it.locationId == selectedBranch?.id)
    }

    var selectedServiceToJoin by remember { mutableStateOf<ServiceItem?>(null) }
    var joinPriority by remember { mutableStateOf(TokenPriority.STANDARD) }
    var joinNotes by remember { mutableStateOf("") }
    var isJoiningQueue by remember { mutableStateOf(false) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }

    var showOptionalDetailsDialog by remember { mutableStateOf(false) }
    var optName by remember { mutableStateOf("") }
    var optAge by remember { mutableStateOf("") }
    var optGender by remember { mutableStateOf("Prefer not to say") }
    var serviceForDetailsDialog by remember { mutableStateOf<ServiceItem?>(null) }
    var hasConsentedToPrivacy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(business.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showToast("Saved to Favorites") }) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark")
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header Hero Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        PrimaryIndigo.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(54.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = business.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (business.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = "Verified",
                                                    tint = PrimaryCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = business.category.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(AccentAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${business.rating}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AccentAmber
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = business.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (business.professionalName.isNotBlank() || business.qualifications.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        if (business.professionalName.isNotBlank()) {
                                            Text(
                                                text = business.professionalName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (business.qualifications.isNotBlank()) {
                                            Text(
                                                text = business.qualifications,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (business.experienceYears > 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${business.experienceYears} yrs exp",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (business.verificationStatus == MerchantVerificationStatus.APPROVED) Icons.Default.Verified else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (business.verificationStatus == MerchantVerificationStatus.APPROVED) PrimaryCyan else AccentAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (business.verificationStatus) {
                                        MerchantVerificationStatus.APPROVED -> "✓ Verified Professional / Business"
                                        MerchantVerificationStatus.PENDING -> "Verification pending admin review"
                                        else -> "Information provided by business (Unverified)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Multi-Branch Selection
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select Branch / Location",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(branches) { branch ->
                            val isSelected = selectedBranch?.id == branch.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBranch = branch
                                    viewModel.selectLocation(branch)
                                },
                                label = { Text(branch.branchName) },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Live Queue Status for Selected Branch
            selectedBranch?.let { branch ->
                val branchTokens = uiState.tokens.filter { it.locationId == branch.id }
                val currentServing = branchTokens.find { it.status == TokenStatus.SERVING }?.tokenNumber ?: "None"
                val waitingCount = branchTokens.count { it.status == TokenStatus.WAITING }
                val isQueueOpen = branch.isOpen && !branch.isPaused

                item {
                    SQCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SQLiveDot(color = if (isQueueOpen) AccentEmerald else AccentRose)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isQueueOpen) "QUEUE OPEN & SERVING" else "QUEUE CLOSED",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isQueueOpen) AccentEmerald else AccentRose
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${branch.city} Hub",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = branch.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SQStatCard(
                                    title = "Serving Now",
                                    value = currentServing,
                                    icon = Icons.Default.ConfirmationNumber,
                                    color = AccentEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                                SQStatCard(
                                    title = "People In Queue",
                                    value = "$waitingCount",
                                    icon = Icons.Default.Groups,
                                    color = AccentAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                SQStatCard(
                                    title = "Avg Wait",
                                    value = "${waitingCount * 15}m",
                                    icon = Icons.Default.Timer,
                                    color = PrimaryCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Available Services with Instant Join
            item {
                Text(
                    text = "Choose Service to Join",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (branchServices.isEmpty()) {
                item {
                    Text(
                        text = "No services configured for this location branch yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(branchServices) { service ->
                    val isBranchOpen = selectedBranch?.isOpen == true && selectedBranch?.isPaused != true

                    SQCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = service.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = service.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "⏱️ ~${service.durationMinutes} mins",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (service.price > 0) {
                                        Text(
                                            text = "💳 ₹${service.price.toInt()}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = AccentEmerald
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            val isCustomer = uiState.currentUser.role in listOf(UserRole.USER, UserRole.CUSTOMER)
                            SQButton(
                                text = when {
                                    !isCustomer -> "Merchant View Only"
                                    isBranchOpen -> "Join Queue"
                                    else -> "Closed"
                                },
                                onClick = {
                                    if (isCustomer) {
                                        selectedServiceToJoin = service
                                    }
                                },
                                enabled = isBranchOpen && isCustomer,
                                variant = if (isBranchOpen && isCustomer) SQButtonVariant.PRIMARY else SQButtonVariant.GHOST
                            )
                        }
                    }
                }
            }

            // Operating Hours
            item {
                Text(
                    text = "Weekly Operating Hours",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                SQCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        business.operatingHours.forEach { hours ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = hours.dayOfWeek,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hours.isClosed) "Closed" else "${hours.openTime} - ${hours.closeTime}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (hours.isClosed) FontWeight.Normal else FontWeight.SemiBold
                                    ),
                                    color = if (hours.isClosed) AccentRose else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Join Queue Confirmation Bottom Sheet
    if (selectedServiceToJoin != null && selectedBranch != null) {
        val srv = selectedServiceToJoin!!
        val loc = selectedBranch!!

        AlertDialog(
            onDismissRequest = { selectedServiceToJoin = null },
            title = {
                Text("Confirm Queue Check-in")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "You are requesting a digital token for:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${srv.name} (${srv.durationMinutes} min)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Location: ${loc.branchName}, ${loc.city}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select Priority (if applicable):", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = joinPriority == TokenPriority.STANDARD,
                            onClick = { joinPriority = TokenPriority.STANDARD },
                            label = { Text("Standard") }
                        )
                        FilterChip(
                            selected = joinPriority == TokenPriority.VIP,
                            onClick = { joinPriority = TokenPriority.VIP },
                            label = { Text("VIP / Priority") }
                        )
                        FilterChip(
                            selected = joinPriority == TokenPriority.SENIOR_EMERGENCY,
                            onClick = { joinPriority = TokenPriority.SENIOR_EMERGENCY },
                            label = { Text("Senior / Urgent") }
                        )
                    }
                }
            },
            confirmButton = {
                SQButton(
                    text = "Continue",
                    onClick = {
                        isJoiningQueue = false
                        joinErrorMessage = null
                        serviceForDetailsDialog = selectedServiceToJoin
                        selectedServiceToJoin = null
                        showOptionalDetailsDialog = true
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { selectedServiceToJoin = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Optional Customer Token Details Dialog
    if (showOptionalDetailsDialog && selectedBranch != null) {
        val srv = serviceForDetailsDialog ?: branchServices.firstOrNull()
        val loc = selectedBranch!!
        val isUserGuest = uiState.currentUser.id == "usr_guest"

        AlertDialog(
            onDismissRequest = { 
                if (!isJoiningQueue) {
                    showOptionalDetailsDialog = false
                    serviceForDetailsDialog = null
                }
            },
            title = { Text("Help Business Identify Pass (Optional)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isJoiningQueue) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    joinErrorMessage?.let { errMsg ->
                        Text(
                            text = errMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = "These optional details help the business identify your queue pass. They are visible only to the business and authorized staff managing this queue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = optName,
                        onValueChange = { optName = it },
                        label = { Text("Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isJoiningQueue
                    )

                    OutlinedTextField(
                        value = optAge,
                        onValueChange = { optAge = it },
                        label = { Text("Age (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isJoiningQueue
                    )

                    Text("Gender (Optional):", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Male", "Female", "Other", "Prefer not to say").forEach { g ->
                            FilterChip(
                                selected = optGender == g,
                                onClick = { if (!isJoiningQueue) optGender = g },
                                label = { Text(g, style = MaterialTheme.typography.labelSmall) },
                                enabled = !isJoiningQueue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isJoiningQueue) { hasConsentedToPrivacy = !hasConsentedToPrivacy }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = hasConsentedToPrivacy,
                            onCheckedChange = { hasConsentedToPrivacy = it },
                            modifier = Modifier.testTag("privacy_consent_checkbox"),
                            enabled = !isJoiningQueue
                        )
                        Text(
                            text = "I consent to sharing my queue details with this business for the purpose of queue management.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = hasConsentedToPrivacy && !isJoiningQueue,
                        onClick = {
                            isJoiningQueue = true
                            joinErrorMessage = null
                            srv?.let { s ->
                                viewModel.joinQueue(
                                    businessId = business.id,
                                    locationId = loc.id,
                                    serviceId = s.id,
                                    priority = joinPriority,
                                    notes = joinNotes,
                                    isGuest = isUserGuest,
                                    onSuccess = {
                                        isJoiningQueue = false
                                        showOptionalDetailsDialog = false
                                        serviceForDetailsDialog = null
                                        onNavigateToToken()
                                    },
                                    onFailure = { err ->
                                        isJoiningQueue = false
                                        joinErrorMessage = err.message ?: "Failed to join queue"
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Skip")
                    }
                    SQButton(
                        text = "Get My Token",
                        enabled = hasConsentedToPrivacy && !isJoiningQueue,
                        isLoading = isJoiningQueue,
                        onClick = {
                            isJoiningQueue = true
                            joinErrorMessage = null
                            srv?.let { s ->
                                viewModel.joinQueue(
                                    businessId = business.id,
                                    locationId = loc.id,
                                    serviceId = s.id,
                                    priority = joinPriority,
                                    notes = joinNotes,
                                    customerNameOverride = optName.ifBlank { null },
                                    customerAge = optAge.toIntOrNull(),
                                    customerGender = optGender,
                                    isGuest = isUserGuest,
                                    onSuccess = {
                                        isJoiningQueue = false
                                        showOptionalDetailsDialog = false
                                        serviceForDetailsDialog = null
                                        onNavigateToToken()
                                    },
                                    onFailure = { err ->
                                        isJoiningQueue = false
                                        joinErrorMessage = err.message ?: "Failed to join queue"
                                    }
                                )
                            }
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isJoiningQueue,
                    onClick = { 
                        showOptionalDetailsDialog = false
                        serviceForDetailsDialog = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
