package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BusinessCategory
import com.example.model.UserRole
import com.example.ui.theme.*
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel

enum class LoginType {
    CUSTOMER, MERCHANT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onLoginSuccess: () -> Unit
) {
    var selectedLoginType by remember { mutableStateOf(LoginType.CUSTOMER) }
    var isMerchantSignUp by remember { mutableStateOf(false) }
    var isCustomerSignUp by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Customer fields
    var customerName by remember { mutableStateOf("") }
    var customerEmail by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerPassword by remember { mutableStateOf("") }
    var customerPasswordVisible by remember { mutableStateOf(false) }

    // Merchant fields
    var merchantName by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(BusinessCategory.HEALTHCARE) }
    var counterName by remember { mutableStateOf("") }
    var merchantAddress by remember { mutableStateOf("") }
    var merchantCity by remember { mutableStateOf("") }
    var merchantPhone by remember { mutableStateOf("") }
    var merchantEmail by remember { mutableStateOf("") }
    var merchantPassword by remember { mutableStateOf("") }
    var merchantPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Branding Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimaryCyan.copy(alpha = 0.15f))
                    .border(2.dp, PrimaryCyan, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "SmartQueue",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Secure Firebase Authentication & Live Queue Platform",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Role Selector Tabs (Customer vs Merchant vs Admin)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Customer Tab
                    FilterChip(
                        selected = selectedLoginType == LoginType.CUSTOMER,
                        onClick = {
                            selectedLoginType = LoginType.CUSTOMER
                            authError = null
                        },
                        label = {
                            Text("👤 Customer (ग्राहक)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Merchant Tab
                    FilterChip(
                        selected = selectedLoginType == LoginType.MERCHANT,
                        onClick = {
                            selectedLoginType = LoginType.MERCHANT
                            authError = null
                        },
                        label = {
                            Text("🏪 Merchant (दुकान)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Error Banner
            if (authError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AccentRose)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = authError ?: "",
                            color = AccentRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Main Auth Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedLoginType) {
                        LoginType.CUSTOMER -> {
                            Text(
                                text = if (isCustomerSignUp) "Customer Registration (नया खाता)" else "Customer Login (ग्राहक लॉगिन)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isCustomerSignUp)
                                    "Create your customer account to book digital queues, manage appointments and track live queue passes."
                                else
                                    "Log in to your account to book tokens and monitor live queue positions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isCustomerSignUp) {
                                OutlinedTextField(
                                    value = customerName,
                                    onValueChange = { customerName = it; authError = null },
                                    label = { Text("Full Name (नाम) *") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it; authError = null },
                                    label = { Text("Mobile Number (मोबाइल नंबर)") },
                                    placeholder = { Text("+91 98765 43210") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            OutlinedTextField(
                                value = customerEmail,
                                onValueChange = { customerEmail = it; authError = null },
                                label = { Text("Email Address (ईमेल) *") },
                                placeholder = { Text("user@smartqueue.in") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = customerPassword,
                                onValueChange = { customerPassword = it; authError = null },
                                label = { Text("Password (पासवर्ड) *") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { customerPasswordVisible = !customerPasswordVisible }) {
                                        Icon(
                                            if (customerPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (customerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (customerEmail.isBlank() || customerPassword.isBlank()) {
                                        authError = "Please fill in Email and Password."
                                        return@Button
                                    }
                                    if (isCustomerSignUp && customerName.isBlank()) {
                                        authError = "Please enter your full name."
                                        return@Button
                                    }

                                    isLoading = true
                                    authError = null
                                    if (isCustomerSignUp) {
                                        viewModel.signUpCustomer(
                                            name = customerName,
                                            email = customerEmail,
                                            pass = customerPassword,
                                            phone = customerPhone.ifBlank { "+91 98765 00000" }
                                        ) { success, err ->
                                            isLoading = false
                                            if (success) onLoginSuccess() else authError = err
                                        }
                                    } else {
                                        viewModel.loginUser(
                                            email = customerEmail,
                                            pass = customerPassword
                                        ) { success, err ->
                                            isLoading = false
                                            if (success) onLoginSuccess() else authError = err
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isCustomerSignUp) "Create Customer Account" else "Customer Log In (लॉगिन करें)",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(onClick = {
                                    isCustomerSignUp = !isCustomerSignUp
                                    authError = null
                                }) {
                                    Text(
                                        text = if (isCustomerSignUp) "Already have an account? Log In" else "New customer? Register account (खाता बनाएं)",
                                        fontSize = 12.sp,
                                        color = PrimaryCyan
                                    )
                                }
                            }
                        }

                        LoginType.MERCHANT -> {
                            Text(
                                text = if (isMerchantSignUp) "Register Shop & Merchant Account (दुकान जोड़ें)" else "Merchant Login (दुकानदार लॉगिन)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isMerchantSignUp)
                                    "Create your store/clinic queue, get unique barcode and printable QR standee poster."
                                else
                                    "Access your live counter controller, call tokens and manage your queues.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isMerchantSignUp) {
                                OutlinedTextField(
                                    value = shopName,
                                    onValueChange = { shopName = it; authError = null },
                                    label = { Text("Shop / Clinic Name (दुकान/क्लीनिक का नाम) *") },
                                    placeholder = { Text("e.g. Apex Health Clinic") },
                                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = merchantName,
                                    onValueChange = { merchantName = it; authError = null },
                                    label = { Text("Owner / Doctor Name (मालिक/डॉक्टर का नाम) *") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Category selector
                                Text("Select Category (श्रेणी):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BusinessCategory.values().forEach { cat ->
                                        FilterChip(
                                            selected = selectedCategory == cat,
                                            onClick = { selectedCategory = cat },
                                            label = { Text(cat.title, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = counterName,
                                    onValueChange = { counterName = it; authError = null },
                                    label = { Text("Counter / Cabin Name (e.g. Counter 1, Dr. Cabin)") },
                                    leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = merchantAddress,
                                    onValueChange = { merchantAddress = it; authError = null },
                                    label = { Text("Address / City (पता / शहर)") },
                                    placeholder = { Text("Main Market, Lucknow") },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = merchantPhone,
                                    onValueChange = { merchantPhone = it; authError = null },
                                    label = { Text("Contact Phone (फ़ोन नंबर)") },
                                    placeholder = { Text("+91 98765 00000") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            OutlinedTextField(
                                value = merchantEmail,
                                onValueChange = { merchantEmail = it; authError = null },
                                label = { Text("Merchant Account Email *") },
                                placeholder = { Text("merchant@smartqueue.in") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = merchantPassword,
                                onValueChange = { merchantPassword = it; authError = null },
                                label = { Text("Password (पासवर्ड) *") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { merchantPasswordVisible = !merchantPasswordVisible }) {
                                        Icon(
                                            if (merchantPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (merchantPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (merchantEmail.isBlank() || merchantPassword.isBlank()) {
                                        authError = "Please enter merchant email and password."
                                        return@Button
                                    }

                                    isLoading = true
                                    authError = null
                                    if (isMerchantSignUp) {
                                        if (shopName.isBlank()) {
                                            authError = "Please enter your Shop/Clinic name."
                                            isLoading = false
                                            return@Button
                                        }

                                        viewModel.signUpMerchant(
                                            ownerName = merchantName.ifBlank { "Merchant Owner" },
                                            businessName = shopName,
                                            category = selectedCategory,
                                            phone = merchantPhone.ifBlank { "+91 98765 00000" },
                                            email = merchantEmail,
                                            pass = merchantPassword,
                                            address = merchantAddress.ifBlank { "Main Market Road" },
                                            city = merchantCity.ifBlank { "Lucknow" }
                                        ) { success, err ->
                                            isLoading = false
                                            if (success) {
                                                onLoginSuccess()
                                            } else {
                                                authError = err
                                            }
                                        }
                                    } else {
                                        viewModel.loginUser(
                                            email = merchantEmail,
                                            pass = merchantPassword
                                        ) { success, err ->
                                            isLoading = false
                                            if (success) onLoginSuccess() else authError = err
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isMerchantSignUp) "Register Shop & Enter (दुकान जोड़ें)" else "Open Merchant Dashboard",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(onClick = {
                                    isMerchantSignUp = !isMerchantSignUp
                                    authError = null
                                }) {
                                    Text(
                                        text = if (isMerchantSignUp) "Already have merchant account? Log In" else "New shop or clinic? Register here (दुकान जोड़ें)",
                                        fontSize = 12.sp,
                                        color = PrimaryCyan
                                    )
                                }
                            }
                        }


                    }
                }
            }
        }
    }
}


