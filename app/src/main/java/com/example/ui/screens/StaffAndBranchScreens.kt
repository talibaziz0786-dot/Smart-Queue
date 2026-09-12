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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StaffRole
import com.example.ui.components.*
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.PrimaryCyan
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var staffName by remember { mutableStateOf("") }
    var staffEmail by remember { mutableStateOf("") }
    var staffCounter by remember { mutableStateOf("Counter 1") }
    var staffRole by remember { mutableStateOf(StaffRole.COUNTER_OPERATOR) }

    val currentBiz = uiState.selectedBusiness ?: uiState.businesses.firstOrNull()
    val currentLoc = uiState.selectedLocation ?: uiState.locations.firstOrNull()
    val staffList = uiState.staffMembers.filter { it.businessId == currentBiz?.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff & Role Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Staff", tint = PrimaryCyan)
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
                        Text(
                            text = "Granular Access Control",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Assign roles per location branch. Counter Operators can call and serve tokens; Supervisors can manage queue flows and override.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(staffList) { staff ->
                SQCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = staff.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = PrimaryCyan.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = staff.role.name,
                                        color = PrimaryCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${staff.email} • ${staff.assignedCounter}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { viewModel.removeStaff(staff.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = AccentRose)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Invite Staff Member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = staffName,
                        onValueChange = { staffName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = staffEmail,
                        onValueChange = { staffEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = staffCounter,
                        onValueChange = { staffCounter = it },
                        label = { Text("Assigned Counter") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Select Role:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = staffRole == StaffRole.COUNTER_OPERATOR,
                            onClick = { staffRole = StaffRole.COUNTER_OPERATOR },
                            label = { Text("Operator") }
                        )
                        FilterChip(
                            selected = staffRole == StaffRole.SUPERVISOR,
                            onClick = { staffRole = StaffRole.SUPERVISOR },
                            label = { Text("Supervisor") }
                        )
                    }
                }
            },
            confirmButton = {
                SQButton(
                    text = "Save Staff",
                    onClick = {
                        if (staffName.isNotBlank() && staffEmail.isNotBlank()) {
                            viewModel.addStaff(staffName, staffEmail, staffRole, staffCounter)
                            staffName = ""
                            staffEmail = ""
                            showAddDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiBranchScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit
) {
    var showAddBranchDialog by remember { mutableStateOf(false) }
    var branchName by remember { mutableStateOf("") }
    var branchAddress by remember { mutableStateOf("") }
    var branchCity by remember { mutableStateOf("Lucknow") }
    var branchPhone by remember { mutableStateOf("") }
    var branchPrefix by remember { mutableStateOf("L") }

    val currentBiz = uiState.selectedBusiness ?: uiState.businesses.firstOrNull()
    val branches = uiState.locations.filter { it.businessId == currentBiz?.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-Branch Architecture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddBranchDialog = true }) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Branch", tint = PrimaryCyan)
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
                        Text(
                            text = "Centralized Business, Independent Queues",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Each branch operates with its own independent sequence counter prefix, operating hours, staff assignments, and QR code pass.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(branches) { branch ->
                val branchTokens = uiState.tokens.filter { it.locationId == branch.id }
                val waiting = branchTokens.count { it.status == com.example.model.TokenStatus.WAITING }
                val served = branchTokens.count { it.status == com.example.model.TokenStatus.SERVED }

                SQCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = branch.branchName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = AccentEmerald.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Prefix: ${branch.prefix}",
                                        color = AccentEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${branch.address}, ${branch.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Phone: ${branch.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("⏳ $waiting waiting", style = MaterialTheme.typography.labelSmall)
                                Text("✅ $served served today", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleLocationQueue(branch.id, !branch.isPaused) }
                        ) {
                            Icon(
                                imageVector = if (branch.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Toggle Pause",
                                tint = if (branch.isPaused) AccentEmerald else AccentRose
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddBranchDialog) {
        AlertDialog(
            onDismissRequest = { showAddBranchDialog = false },
            title = { Text("Add Location Branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Branch Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = branchAddress,
                        onValueChange = { branchAddress = it },
                        label = { Text("Street Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = branchCity,
                        onValueChange = { branchCity = it },
                        label = { Text("City") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = branchPhone,
                        onValueChange = { branchPhone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = branchPrefix,
                        onValueChange = { branchPrefix = it },
                        label = { Text("Token Prefix (e.g. L, K, D)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                SQButton(
                    text = "Create Branch",
                    onClick = {
                        if (branchName.isNotBlank() && branchAddress.isNotBlank()) {
                            viewModel.addBranch(branchName, branchAddress, branchCity, branchPhone, branchPrefix)
                            branchName = ""
                            branchAddress = ""
                            showAddBranchDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddBranchDialog = false }) { Text("Cancel") }
            }
        )
    }
}
