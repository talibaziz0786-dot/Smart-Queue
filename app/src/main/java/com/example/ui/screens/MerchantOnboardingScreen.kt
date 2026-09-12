package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
fun MerchantOnboardingScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onCancelOnboarding: () -> Unit,
    onOnboardingFinished: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 7

    // --- State variables ---
    // Step 1: Business Type
    var selectedCategory by remember { mutableStateOf<BusinessCategory>(BusinessCategory.HEALTHCARE) }

    // Step 2: Business Details
    var shopName by remember { mutableStateOf("") }
    var shopDescription by remember { mutableStateOf("") }
    var shopPhone by remember { mutableStateOf("") }
    var shopWebsite by remember { mutableStateOf("") }

    // Step 3: Location / Branch
    var branchName by remember { mutableStateOf("Main Branch") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Lucknow") }
    var stateProvince by remember { mutableStateOf("Uttar Pradesh") }
    var country by remember { mutableStateOf("India") }

    // Step 4: Services & Pricing
    var serviceName by remember { mutableStateOf("") }
    var serviceDescription by remember { mutableStateOf("") }
    var serviceDurationInput by remember { mutableStateOf("15") }
    var servicePriceInput by remember { mutableStateOf("0") }

    // Step 5: Operating Hours
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var operatingHoursList by remember {
        mutableStateOf(
            daysOfWeek.map { day ->
                BusinessHours(
                    dayOfWeek = day,
                    openTime = "09:00 AM",
                    closeTime = "08:00 PM",
                    isClosed = day == "Sunday"
                )
            }
        )
    }

    // Step 6: Public Profile
    var professionalName by remember { mutableStateOf("") }
    var experienceInput by remember { mutableStateOf("") }
    var qualifications by remember { mutableStateOf("") }
    var specialties by remember { mutableStateOf("") }
    var languages by remember { mutableStateOf("English, Hindi") }
    var bioAbout by remember { mutableStateOf("") }

    // Step 7: Completed State / QR Code
    var createdBusiness by remember { mutableStateOf<Business?>(null) }

    // General Control States
    var showExitDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Field Validation States
    var shopNameError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var cityError by remember { mutableStateOf<String?>(null) }
    var serviceNameError by remember { mutableStateOf<String?>(null) }
    var serviceDurationError by remember { mutableStateOf<String?>(null) }
    var servicePriceError by remember { mutableStateOf<String?>(null) }

    // Auto-update default service name based on Category
    LaunchedEffect(selectedCategory) {
        if (serviceName.isBlank() || serviceName.contains("Consultation") || serviceName.contains("Haircut") || serviceName.contains("Training") || serviceName.contains("Dining") || serviceName.contains("Service")) {
            serviceName = when (selectedCategory) {
                BusinessCategory.HEALTHCARE -> "General Consultation"
                BusinessCategory.SALON_BEAUTY -> "Standard Haircut"
                BusinessCategory.RESTAURANT -> "Table Reservation"
                else -> "General Service"
            }
        }
        if (professionalName.isBlank()) {
            professionalName = when (selectedCategory) {
                BusinessCategory.HEALTHCARE -> "Dr. " + uiState.currentUser.name
                else -> uiState.currentUser.name
            }
        }
    }

    // Handle Back action
    val handleBack: () -> Unit = {
        if (currentStep > 1 && currentStep < 7) {
            currentStep--
        } else if (currentStep == 7) {
            onOnboardingFinished()
        } else {
            showExitDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Merchant Onboarding",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (currentStep < 7) {
                            Text(
                                text = "Step $currentStep of 6 • ${getStepTitle(currentStep)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Setup Completed 🎉",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentEmerald
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("onboarding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
            ) {
                // Step Indicator Bar
                if (currentStep < 7) {
                    item {
                        LinearProgressIndicator(
                            progress = { (currentStep - 1) / 6f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .testTag("onboarding_progress_bar"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                // Step Contents
                when (currentStep) {
                    1 -> {
                        item {
                            OnboardingStepHeader(
                                title = "Select your Business Type",
                                description = "Choose the category that best describes your shop or clinic to customize your queue flow."
                            )
                        }
                        items(BusinessCategory.values()) { category ->
                            val isSelected = selectedCategory == category
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = category }
                                    .testTag("category_card_${category.name.lowercase()}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
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
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getCategoryIcon(category),
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = getCategoryDescription(category),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        item {
                            OnboardingStepHeader(
                                title = "Tell us about your business",
                                description = "Provide the basic public details of your establishment. Only name is strictly required."
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = shopName,
                                    onValueChange = {
                                        shopName = it
                                        shopNameError = if (it.isBlank()) "Business name cannot be empty" else null
                                    },
                                    label = { Text("Business / Shop Name *") },
                                    isError = shopNameError != null,
                                    supportingText = shopNameError?.let { { Text(it) } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_shop_name"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = shopDescription,
                                    onValueChange = { shopDescription = it },
                                    label = { Text("Short Description") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_shop_description"),
                                    shape = RoundedCornerShape(12.dp),
                                    maxLines = 3,
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = shopPhone,
                                    onValueChange = { shopPhone = it },
                                    label = { Text("Public Phone Number") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_shop_phone"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = shopWebsite,
                                    onValueChange = { shopWebsite = it },
                                    label = { Text("Website Link") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_shop_website"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                                )
                            }
                        }
                    }

                    3 -> {
                        item {
                            OnboardingStepHeader(
                                title = "Configure your first Location",
                                description = "Create your first branch or queue outlet. This coordinates where customers will assemble."
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = branchName,
                                    onValueChange = { branchName = it },
                                    label = { Text("Branch / Counter Label") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_branch_name"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = address,
                                    onValueChange = {
                                        address = it
                                        addressError = if (it.isBlank()) "Address is required" else null
                                    },
                                    label = { Text("Street Address *") },
                                    isError = addressError != null,
                                    supportingText = addressError?.let { { Text(it) } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_branch_address"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = city,
                                    onValueChange = {
                                        city = it
                                        cityError = if (it.isBlank()) "City is required" else null
                                    },
                                    label = { Text("City *") },
                                    isError = cityError != null,
                                    supportingText = cityError?.let { { Text(it) } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_branch_city"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) }
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = stateProvince,
                                        onValueChange = { stateProvince = it },
                                        label = { Text("State") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_branch_state"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = country,
                                        onValueChange = { country = it },
                                        label = { Text("Country") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_branch_country"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    4 -> {
                        item {
                            OnboardingStepHeader(
                                title = "Add your first Service",
                                description = "Provide at least one queue-enabled service that your customers can request when joining."
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = serviceName,
                                    onValueChange = {
                                        serviceName = it
                                        serviceNameError = if (it.isBlank()) "Service name cannot be empty" else null
                                    },
                                    label = { Text("Service Name *") },
                                    isError = serviceNameError != null,
                                    supportingText = serviceNameError?.let { { Text(it) } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_service_name"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.DesignServices, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = serviceDescription,
                                    onValueChange = { serviceDescription = it },
                                    label = { Text("Service Description") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_service_description"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = serviceDurationInput,
                                    onValueChange = {
                                        serviceDurationInput = it
                                        val dur = it.toIntOrNull()
                                        serviceDurationError = when {
                                            dur == null -> "Please enter a valid number"
                                            dur <= 0 -> "Duration must be greater than 0"
                                            dur > 120 -> "Duration cannot exceed 120 minutes"
                                            else -> null
                                        }
                                    },
                                    label = { Text("Est. Duration (Minutes) *") },
                                    isError = serviceDurationError != null,
                                    supportingText = serviceDurationError?.let { { Text(it) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_service_duration"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = servicePriceInput,
                                    onValueChange = {
                                        servicePriceInput = it
                                        val pr = it.toDoubleOrNull()
                                        servicePriceError = when {
                                            pr == null -> "Please enter a valid price"
                                            pr < 0.0 -> "Price cannot be negative"
                                            else -> null
                                        }
                                    },
                                    label = { Text("Service Fee / Price (₹) *") },
                                    isError = servicePriceError != null,
                                    supportingText = servicePriceError?.let { { Text(it) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_service_price"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
                                )
                            }
                        }
                    }

                    5 -> {
                        item {
                            OnboardingStepHeader(
                                title = "Setup Operating Hours",
                                description = "Let customers know when your doors are open. Closed days will block queue entries."
                            )
                        }
                        items(operatingHoursList) { hours ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hours_row_${hours.dayOfWeek.lowercase()}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hours.isClosed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hours.dayOfWeek,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (hours.isClosed) {
                                            Text("Closed", color = AccentRose, style = MaterialTheme.typography.bodySmall)
                                        } else {
                                            Text(
                                                text = "${hours.openTime} – ${hours.closeTime}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Closed",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Switch(
                                            checked = hours.isClosed,
                                            onCheckedChange = { isClosed ->
                                                operatingHoursList = operatingHoursList.map {
                                                    if (it.dayOfWeek == hours.dayOfWeek) it.copy(isClosed = isClosed) else it
                                                }
                                            },
                                            modifier = Modifier.testTag("switch_${hours.dayOfWeek.lowercase()}"),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = AccentRose,
                                                checkedTrackColor = AccentRose.copy(alpha = 0.3f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    6 -> {
                        item {
                            OnboardingStepHeader(
                                title = "Optional Public Profile",
                                description = "Give your customers additional professional details. You can skip this and complete it later."
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = professionalName,
                                    onValueChange = { professionalName = it },
                                    label = { Text("Professional / Practitioner Name") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_prof_name"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = experienceInput,
                                    onValueChange = { experienceInput = it },
                                    label = { Text("Practicing Experience (Years)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_prof_experience"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = qualifications,
                                    onValueChange = { qualifications = it },
                                    label = { Text("Academic Qualifications / Certifications") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_prof_qualifications"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = specialties,
                                    onValueChange = { specialties = it },
                                    label = { Text("Specialties (Comma separated)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_prof_specialties"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = languages,
                                    onValueChange = { languages = it },
                                    label = { Text("Languages spoken") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_prof_languages"),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = bioAbout,
                                    onValueChange = { bioAbout = it },
                                    label = { Text("Short Professional Bio / About") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_prof_bio"),
                                    shape = RoundedCornerShape(12.dp),
                                    maxLines = 4,
                                    leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) }
                                )
                            }
                        }
                    }

                    7 -> {
                        // Success & Live QR page!
                        val biz = createdBusiness
                        val qrData = uiState.qrCodes.find { it.businessId == biz?.id }
                        val publicCode = qrData?.publicCode ?: "SQ-DEMO"

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(AccentEmerald.copy(alpha = 0.2f), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Text(
                                    text = "Your Business is Registered!",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                    textAlign = TextAlign.Center
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Verification: Pending Approval ⏳",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AccentAmber
                                        )
                                        Text(
                                            text = "Your business can continue setup while verification is being reviewed by the administration.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // QR Section
                                Text(
                                    text = "Your Queue QR Standee",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                Card(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .testTag("generated_qr_card"),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.QrCode,
                                                contentDescription = "Queue QR Code",
                                                tint = Color.Black,
                                                modifier = Modifier.size(140.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ) {
                                                Text(
                                                    text = publicCode,
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Customers can scan this code to join your queue directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Stick Action Row
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep < 7) {
                        // Clear Secondary Action
                        OutlinedButton(
                            onClick = handleBack,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("btn_back_step"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // Primary Action Button
                    SQButton(
                        text = when (currentStep) {
                            6 -> "Submit Setup"
                            7 -> "Go to Dashboard"
                            else -> "Continue"
                        },
                        onClick = {
                            if (currentStep == 1) {
                                currentStep++
                            } else if (currentStep == 2) {
                                if (shopName.isBlank()) {
                                    shopNameError = "Business name cannot be empty"
                                } else {
                                    currentStep++
                                }
                            } else if (currentStep == 3) {
                                if (address.isBlank()) {
                                    addressError = "Address is required"
                                } else if (city.isBlank()) {
                                    cityError = "City is required"
                                } else {
                                    currentStep++
                                }
                            } else if (currentStep == 4) {
                                val duration = serviceDurationInput.toIntOrNull()
                                val price = servicePriceInput.toDoubleOrNull()
                                when {
                                    serviceName.isBlank() -> serviceNameError = "Service name cannot be empty"
                                    duration == null || duration <= 0 || duration > 120 -> serviceDurationError = "Enter valid duration (2-120 mins)"
                                    price == null || price < 0.0 -> servicePriceError = "Enter a valid price"
                                    else -> currentStep++
                                }
                            } else if (currentStep == 5) {
                                currentStep++
                            } else if (currentStep == 6) {
                                // Duplicate submission protection & submit onboarding
                                isSubmitting = true
                                viewModel.completeMerchantOnboarding(
                                    shopName = shopName,
                                    category = selectedCategory,
                                    description = shopDescription,
                                    phone = shopPhone,
                                    websiteUrl = shopWebsite,
                                    branchName = branchName,
                                    address = address,
                                    city = city,
                                    state = stateProvince,
                                    country = country,
                                    serviceName = serviceName,
                                    serviceDesc = serviceDescription,
                                    serviceDuration = serviceDurationInput.toIntOrNull() ?: 15,
                                    servicePrice = servicePriceInput.toDoubleOrNull() ?: 0.0,
                                    operatingHours = operatingHoursList,
                                    professionalName = professionalName,
                                    experienceYears = experienceInput.toIntOrNull() ?: 0,
                                    qualifications = qualifications,
                                    specialties = specialties,
                                    languages = languages,
                                    about = bioAbout,
                                    onSuccess = { biz ->
                                        createdBusiness = biz
                                        isSubmitting = false
                                        currentStep = 7
                                    }
                                )
                            } else if (currentStep == 7) {
                                onOnboardingFinished()
                            }
                        },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("btn_primary_step"),
                        enabled = !isSubmitting,
                        isLoading = isSubmitting
                    )
                }
            }
        }
    }

    // Unsaved Changes Leave Onboarding Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Setup?") },
            text = { Text("Are you sure you want to cancel the merchant registration? All currently entered information will be discarded.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onCancelOnboarding()
                    },
                    modifier = Modifier.testTag("dialog_confirm_exit")
                ) {
                    Text("Exit", color = AccentRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue Setup")
                }
            }
        )
    }
}

@Composable
fun OnboardingStepHeader(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getStepTitle(step: Int): String {
    return when (step) {
        1 -> "Business Category"
        2 -> "Basic Profile"
        3 -> "Location / Branch"
        4 -> "Service Setup"
        5 -> "Operating Hours"
        6 -> "Practitioner Details"
        else -> "Completion"
    }
}

private fun getCategoryIcon(category: BusinessCategory): ImageVector {
    return when (category) {
        BusinessCategory.HEALTHCARE -> Icons.Default.MedicalServices
        BusinessCategory.SALON_BEAUTY -> Icons.Default.ContentCut
        BusinessCategory.RESTAURANT -> Icons.Default.Restaurant
        BusinessCategory.REPAIR -> Icons.Default.Build
        BusinessCategory.CONSULTANT -> Icons.Default.BusinessCenter
        BusinessCategory.GOVERNMENT -> Icons.Default.AccountBalance
        BusinessCategory.RETAIL -> Icons.Default.Storefront
    }
}

private fun getCategoryDescription(category: BusinessCategory): String {
    return when (category) {
        BusinessCategory.HEALTHCARE -> "Clinics, dentists, general practitioners, or health consulting."
        BusinessCategory.SALON_BEAUTY -> "Barbershops, beauty parlors, hair stylists, or spa sessions."
        BusinessCategory.RESTAURANT -> "Restaurants, dining-in waiting lists, cafes, or bistros."
        BusinessCategory.REPAIR -> "Automotive repairs, appliance diagnostics, or tech service centers."
        BusinessCategory.CONSULTANT -> "Legal advisors, chartered accountants, or professional consultations."
        BusinessCategory.GOVERNMENT -> "Public administration windows, post offices, or municipal bureaus."
        BusinessCategory.RETAIL -> "Boutiques, grocery counters, banks, or customer support desks."
    }
}
