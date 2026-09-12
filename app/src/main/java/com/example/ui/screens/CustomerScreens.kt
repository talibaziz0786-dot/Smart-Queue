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
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onNavigateToToken: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToBusiness: (Business) -> Unit
) {
    var directShopCodeInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)
    ) {
        // Top Welcoming Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaryCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SMARTQUEUE PASS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp
                            ),
                            color = PrimaryCyan
                        )
                        Text(
                            text = "Namaste, ${uiState.currentUser.name.split(" ").firstOrNull() ?: "User"}! 👋",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Scan QR Button
                    FilledTonalIconButton(
                        onClick = onNavigateToScan,
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = PrimaryCyan.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Active Token Live Card (If customer has active waiting or serving token)
        if (uiState.activeUserToken != null) {
            val token = uiState.activeUserToken
            val locationTokens = uiState.tokens.filter { it.locationId == token.locationId }
            val currentServing = locationTokens.find { it.status == TokenStatus.SERVING }
                ?: locationTokens.find { it.status == TokenStatus.CALLED }

            val peopleAhead = locationTokens.count {
                it.status == TokenStatus.WAITING && it.sequenceNumber < token.sequenceNumber
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigateToToken() }
                        .testTag("active_token_hero_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, PrimaryCyan)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AAPKA LIVE TOKEN PASS (चालू टोकन)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 11.sp
                                ),
                                color = PrimaryCyan
                            )
                            SQStatusBadge(status = token.status)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = token.tokenNumber,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 52.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = PrimaryCyan
                        )

                        Text(
                            text = "${token.businessName} • ${token.serviceName}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats: Serving now vs Ahead of you
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("NOW SERVING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currentServing?.tokenNumber ?: "None", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentEmerald)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("AHEAD OF YOU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (peopleAhead == 0) "Your Turn Next!" else "$peopleAhead in queue",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (peopleAhead == 0) AccentEmerald else AccentAmber
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToToken,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Live Pass Details", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.cancelToken(token.id, "Cancelled by user") },
                                modifier = Modifier.weight(0.7f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", color = AccentRose, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Two Prominent Action Cards: 1. Scan QR Poster, 2. Book New Slot
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scan Shop QR Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToScan() },
                    colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.15f)),
                    border = BorderStroke(1.5.dp, PrimaryCyan)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(PrimaryCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("SCAN QR CODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan, letterSpacing = 1.sp)
                            Text("दुकान का QR स्कैन करें", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Explore Clinics / Shops Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToExplore() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(AccentEmerald.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("FIND SHOPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentEmerald, letterSpacing = 1.sp)
                            Text("क्लीनिक व दुकानें खोजें", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Direct Shop Code / Barcode Lookup Bar (Connects any merchant shop across devices instantly)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "JOIN BY SHOP CODE (दुकान कोड द्वारा जुड़ें)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = PrimaryCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = directShopCodeInput,
                            onValueChange = { directShopCodeInput = it },
                            placeholder = { Text("Enter Code (e.g. SQ-CLIN-1024 or Shop Name)", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                if (directShopCodeInput.isNotBlank()) {
                                    viewModel.scanQrCode(directShopCodeInput) { biz, loc ->
                                        viewModel.selectBusiness(biz)
                                        viewModel.selectLocation(loc)
                                        onNavigateToBusiness(biz)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Book (टोकन)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SELECT CATEGORY (श्रेणी चुनें)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BusinessCategory.values()) { cat ->
                        FilterChip(
                            selected = uiState.selectedCategory == cat,
                            onClick = {
                                viewModel.setCategoryFilter(if (uiState.selectedCategory == cat) null else cat)
                            },
                            label = { Text(cat.title, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (cat) {
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

        // List of Available Clinics & Shops with 1-Tap "Book Token"
        item {
            Text(
                text = "AVAILABLE LIVE QUEUES (टोकन बुक करें)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val filteredBiz = uiState.businesses.filter { biz ->
            uiState.selectedCategory == null || biz.category == uiState.selectedCategory
        }

        items(filteredBiz) { business ->
            val branch = uiState.locations.find { it.businessId == business.id }
            val branchTokens = if (branch != null) uiState.tokens.filter { it.locationId == branch.id } else emptyList()
            val waitingCount = branchTokens.count { it.status == TokenStatus.WAITING }
            val currentServing = branchTokens.find { it.status == TokenStatus.SERVING }?.tokenNumber ?: "None"
            val isOpen = branch?.isOpen == true && branch.isPaused != true

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.selectBusiness(business)
                        if (branch != null) {
                            viewModel.selectLocation(branch)
                        }
                        onNavigateToBusiness(business)
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                                    .size(38.dp)
                                    .background(PrimaryCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (business.category) {
                                        BusinessCategory.HEALTHCARE -> Icons.Default.LocalHospital
                                        BusinessCategory.SALON_BEAUTY -> Icons.Default.ContentCut
                                        BusinessCategory.REPAIR -> Icons.Default.Build
                                        BusinessCategory.RESTAURANT -> Icons.Default.Restaurant
                                        else -> Icons.Default.Store
                                    },
                                    contentDescription = null,
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = business.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    text = "${business.category.title} • ${branch?.branchName ?: business.primaryCity}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (isOpen) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isOpen) "OPEN" else "CLOSED",
                                color = if (isOpen) AccentEmerald else AccentRose,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("👥 $waitingCount waiting", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentAmber)
                            Text("⚡ Now: $currentServing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryCyan)
                        }

                        // Direct 1-Tap "Book Token" Button -> canonically goes to business details
                        Button(
                            onClick = {
                                viewModel.selectBusiness(business)
                                if (branch != null) {
                                    viewModel.selectLocation(branch)
                                }
                                onNavigateToBusiness(business)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Book Token (टोकन लें)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onNavigateToBusiness: (Business) -> Unit
) {
    var searchQuery by remember { mutableStateOf(uiState.exploreSearchQuery) }
    var openOnlyFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Explore Clinics & Shops",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.setExploreSearch(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("explore_search_input"),
            placeholder = { Text("Search doctor, salon, repair, shop name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        viewModel.setExploreSearch("")
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = openOnlyFilter,
                onClick = { openOnlyFilter = !openOnlyFilter },
                shape = RoundedCornerShape(12.dp),
                label = { Text("Open Now Only") },
                leadingIcon = {
                    if (openOnlyFilter) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            if (uiState.selectedCategory != null) {
                InputChip(
                    selected = true,
                    onClick = { viewModel.setCategoryFilter(null) },
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(uiState.selectedCategory.title) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear category", modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filtered businesses list
        val filtered = uiState.businesses.filter { biz ->
            val matchQuery = searchQuery.isBlank() ||
                biz.name.contains(searchQuery, ignoreCase = true) ||
                biz.description.contains(searchQuery, ignoreCase = true) ||
                biz.primaryCity.contains(searchQuery, ignoreCase = true)
            val matchCat = uiState.selectedCategory == null || biz.category == uiState.selectedCategory
            val matchOpen = !openOnlyFilter || uiState.locations.any { it.businessId == biz.id && it.isOpen && !it.isPaused }
            matchQuery && matchCat && matchOpen
        }

        if (filtered.isEmpty()) {
            SQEmptyState(
                title = "No Shops or Clinics Found",
                description = "Try searching for a different shop name or category.",
                icon = Icons.Default.SearchOff,
                actionLabel = "Reset Filters",
                onAction = {
                    searchQuery = ""
                    viewModel.setExploreSearch("")
                    viewModel.setCategoryFilter(null)
                    openOnlyFilter = false
                }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(filtered) { business ->
                    val branches = uiState.locations.filter { it.businessId == business.id }
                    val activeTokens = uiState.tokens.filter { it.businessId == business.id }
                    val totalWaiting = activeTokens.count { it.status == TokenStatus.WAITING }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectBusiness(business)
                                onNavigateToBusiness(business)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = business.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (business.isVerified) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Verified",
                                                tint = PrimaryCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = business.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${business.rating} (${business.reviewCount})", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.People, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("$totalWaiting waiting", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
