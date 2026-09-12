package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.repository.SmartQueueRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConcurrencyTestResult(
    val isRunning: Boolean = false,
    val totalRequests: Int = 100,
    val successfulJoins: Int = 0,
    val uniqueTokensGenerated: Int = 0,
    val duplicateCollisions: Int = 0,
    val executionTimeMs: Long = 0,
    val summary: String = ""
)

data class SmartQueueUiState(
    val currentUser: User,
    val userRole: UserRole = UserRole.CUSTOMER,
    val activeUserToken: Token? = null,
    val allUsers: List<User> = emptyList(),
    val businesses: List<Business> = emptyList(),
    val merchantBusinesses: List<Business> = emptyList(),
    val locations: List<BusinessLocation> = emptyList(),
    val services: List<ServiceItem> = emptyList(),
    val tokens: List<Token> = emptyList(),
    val staffMembers: List<StaffMember> = emptyList(),
    val qrCodes: List<QRCodeData> = emptyList(),
    val abuseRecords: List<AbuseRecord> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val planPrices: Map<SubscriptionPlan, Int> = emptyMap(),
    val isLoggedIn: Boolean = false,
    val selectedBusiness: Business? = null,
    val selectedLocation: BusinessLocation? = null,
    val exploreSearchQuery: String = "",
    val selectedCategory: BusinessCategory? = null,
    val isDarkMode: Boolean = true,
    val toastMessage: String? = null,
    val concurrencyTest: ConcurrencyTestResult = ConcurrencyTestResult(),
    val isCloudConnected: Boolean = false
)

class SmartQueueViewModel(
    private val repository: SmartQueueRepository = SmartQueueRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SmartQueueUiState(
            currentUser = repository.currentUser.value,
            isCloudConnected = repository.isCloudConnected
        )
    )
    val uiState: StateFlow<SmartQueueUiState> = _uiState.asStateFlow()

    init {
        // Collect repository states reactively with role-based multi-tenant security filtering
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.businesses,
                repository.locations,
                repository.staffMembers
            ) { user, rawBusinesses, rawLocations, rawStaff ->
                val myBusinesses = when (user.role) {
                    UserRole.ADMIN, UserRole.SUPER_ADMIN -> rawBusinesses
                    UserRole.MERCHANT, UserRole.BUSINESS_OWNER -> rawBusinesses.filter { it.ownerId == user.id }
                    UserRole.STAFF -> {
                        val staff = rawStaff.find { it.email.equals(user.email, ignoreCase = true) }
                        rawBusinesses.filter { it.id == staff?.businessId }
                    }
                    else -> emptyList()
                }

                val currentSelectedBiz = _uiState.value.selectedBusiness
                val resolvedSelectedBiz = if (currentSelectedBiz != null && rawBusinesses.any { it.id == currentSelectedBiz.id }) {
                    currentSelectedBiz
                } else {
                    myBusinesses.firstOrNull() ?: rawBusinesses.firstOrNull()
                }

                val currentSelectedLoc = _uiState.value.selectedLocation
                val resolvedSelectedLoc = if (currentSelectedLoc != null && rawLocations.any { it.id == currentSelectedLoc.id }) {
                    currentSelectedLoc
                } else {
                    rawLocations.firstOrNull { it.businessId == resolvedSelectedBiz?.id } ?: rawLocations.firstOrNull()
                }

                _uiState.update {
                    it.copy(
                        currentUser = user,
                        userRole = user.role,
                        businesses = rawBusinesses,
                        merchantBusinesses = myBusinesses,
                        locations = rawLocations,
                        selectedBusiness = resolvedSelectedBiz,
                        selectedLocation = resolvedSelectedLoc,
                        isLoggedIn = user.id != "usr_guest"
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            repository.allUsers.collect { users ->
                _uiState.update { it.copy(allUsers = users) }
            }
        }
        viewModelScope.launch {
            repository.services.collect { list ->
                _uiState.update { it.copy(services = list) }
            }
        }
        viewModelScope.launch {
            repository.tokens.collect { tokList ->
                val activeToken = tokList.find {
                    it.userId == _uiState.value.currentUser.id &&
                    it.status in listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING)
                }
                _uiState.update { it.copy(tokens = tokList, activeUserToken = activeToken) }
            }
        }
        viewModelScope.launch {
            repository.staffMembers.collect { list ->
                _uiState.update { it.copy(staffMembers = list) }
            }
        }
        viewModelScope.launch {
            repository.qrCodes.collect { list ->
                _uiState.update { it.copy(qrCodes = list) }
            }
        }
        viewModelScope.launch {
            repository.abuseRecords.collect { list ->
                _uiState.update { it.copy(abuseRecords = list) }
            }
        }
        viewModelScope.launch {
            repository.notifications.collect { list ->
                _uiState.update { it.copy(notifications = list) }
            }
        }
        viewModelScope.launch {
            repository.auditLogs.collect { list ->
                _uiState.update { it.copy(auditLogs = list) }
            }
        }
        viewModelScope.launch {
            repository.payments.collect { list ->
                _uiState.update { it.copy(payments = list) }
            }
        }
        viewModelScope.launch {
            repository.planPrices.collect { map ->
                _uiState.update { it.copy(planPrices = map) }
            }
        }
    }

    fun selectBusiness(business: Business) {
        val firstLoc = _uiState.value.locations.find { it.businessId == business.id }
        _uiState.update { it.copy(selectedBusiness = business, selectedLocation = firstLoc) }
    }

    fun selectLocation(location: BusinessLocation) {
        _uiState.update { it.copy(selectedLocation = location) }
    }

    fun setExploreSearch(query: String) {
        _uiState.update { it.copy(exploreSearchQuery = query) }
    }

    fun setCategoryFilter(category: BusinessCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleDarkMode() {
        _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    fun switchRole(role: UserRole) {
        repository.switchUserRole(role)
        showToast("Switched viewpoint to ${role.name}")
    }

    fun joinQueue(
        businessId: String,
        locationId: String,
        serviceId: String,
        priority: TokenPriority = TokenPriority.STANDARD,
        notes: String = "",
        customerNameOverride: String? = null,
        customerAge: Int? = null,
        customerGender: String? = null,
        isGuest: Boolean = false,
        onSuccess: (Token) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.joinQueue(
                businessId = businessId,
                locationId = locationId,
                serviceId = serviceId,
                priority = priority,
                notes = notes,
                customerNameOverride = customerNameOverride,
                customerAge = customerAge,
                customerGender = customerGender,
                isGuest = isGuest
            )
            result.onSuccess { token ->
                showToast("🎉 Token ${token.tokenNumber} confirmed! Position #${token.initialPosition}")
                onSuccess(token)
            }.onFailure { err ->
                showToast("❌ ${err.message ?: "Failed to join queue"}")
                onFailure(err)
            }
        }
    }

    fun updateMerchantProfile(
        businessId: String,
        professionalName: String,
        experienceYears: Int,
        qualifications: String,
        specialties: String,
        languages: String,
        websiteUrl: String,
        description: String,
        about: String,
        logoUrl: String
    ) {
        viewModelScope.launch {
            val res = repository.updateMerchantProfile(businessId, professionalName, experienceYears, qualifications, specialties, languages, websiteUrl, description, about, logoUrl)
            res.onSuccess { showToast("✅ Merchant profile updated successfully") }
                .onFailure { showToast("❌ ${it.message}") }
        }
    }

    fun submitForVerification(businessId: String) {
        viewModelScope.launch {
            val res = repository.submitForVerification(businessId)
            res.onSuccess { showToast("📤 Verification request submitted to Admin") }
                .onFailure { showToast("❌ ${it.message}") }
        }
    }

    fun adminApproveVerification(businessId: String) {
        viewModelScope.launch {
            val res = repository.adminApproveVerification(businessId)
            res.onSuccess { showToast("✓ Business verified successfully") }
                .onFailure { showToast("❌ ${it.message}") }
        }
    }

    fun adminRejectVerification(businessId: String, reason: String) {
        viewModelScope.launch {
            val res = repository.adminRejectVerification(businessId, reason)
            res.onSuccess { showToast("Verification rejected") }
                .onFailure { showToast("❌ ${it.message}") }
        }
    }

    fun updateTokenCustomerDetails(tokenId: String, name: String?, age: Int?, gender: String?) {
        viewModelScope.launch {
            val res = repository.updateTokenCustomerDetails(tokenId, name, age, gender)
            res.onSuccess { showToast("✓ Token details updated") }
                .onFailure { showToast("❌ ${it.message}") }
        }
    }

    fun callNextToken(
        locationId: String,
        counterName: String,
        staffName: String,
        onSuccess: ((Token) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.callNextToken(locationId, counterName, staffName)
            result.onSuccess {
                showToast("📢 Called Token ${it.tokenNumber} to $counterName")
                onSuccess?.invoke(it)
            }.onFailure {
                showToast("⚠️ ${it.message}")
                onFailure?.invoke(it)
            }
        }
    }

    fun startServing(
        tokenId: String,
        staffName: String,
        onSuccess: ((Token) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.startServing(tokenId, staffName)
            result.onSuccess {
                showToast("▶️ Serving started for ${it.tokenNumber}")
                onSuccess?.invoke(it)
            }.onFailure {
                showToast("⚠️ ${it.message}")
                onFailure?.invoke(it)
            }
        }
    }

    fun markServed(
        tokenId: String,
        staffName: String,
        onSuccess: ((Token) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.markServed(tokenId, staffName)
            result.onSuccess {
                showToast("✅ Token ${it.tokenNumber} marked as SERVED")
                onSuccess?.invoke(it)
            }.onFailure {
                showToast("⚠️ ${it.message}")
                onFailure?.invoke(it)
            }
        }
    }

    fun skipToken(
        tokenId: String,
        staffName: String,
        onSuccess: ((Token) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.skipToken(tokenId, staffName)
            result.onSuccess {
                showToast("⏭️ Token ${it.tokenNumber} skipped")
                onSuccess?.invoke(it)
            }.onFailure {
                showToast("⚠️ ${it.message}")
                onFailure?.invoke(it)
            }
        }
    }

    fun recordNoShow(
        tokenId: String,
        staffName: String,
        onSuccess: ((Token) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.recordNoShow(tokenId, staffName)
            result.onSuccess {
                showToast("🚫 Recorded NO-SHOW for ${it.tokenNumber}")
                onSuccess?.invoke(it)
            }.onFailure {
                showToast("⚠️ ${it.message}")
                onFailure?.invoke(it)
            }
        }
    }

    fun cancelToken(tokenId: String, reason: String) {
        viewModelScope.launch {
            val result = repository.cancelToken(tokenId, reason)
            result.onSuccess {
                showToast("Token ${it.tokenNumber} cancelled successfully")
            }.onFailure {
                showToast("⚠️ ${it.message}")
            }
        }
    }

    fun toggleLocationQueue(locationId: String, isPaused: Boolean) {
        viewModelScope.launch {
            repository.toggleLocationQueue(locationId, isPaused)
            showToast(if (isPaused) "Queue PAUSED for branch" else "Queue RESUMED for branch")
        }
    }

    fun scanQrCode(publicCode: String, onResolved: (Business, BusinessLocation) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.resolveQRCodeAsync(publicCode)) {
                is QrResolutionResult.Success -> {
                    selectBusiness(res.business)
                    selectLocation(res.location)
                    onResolved(res.business, res.location)
                    showToast("✅ Verified Shop QR: ${res.business.name}")
                }
                is QrResolutionResult.InvalidCode -> {
                    showToast("❌ Public code '$publicCode' was not found in active shops.")
                }
                is QrResolutionResult.Revoked -> {
                    showToast("⚠️ This QR code has been revoked by the merchant.")
                }
                is QrResolutionResult.Error -> {
                    showToast("⚠️ QR Verification Error: ${res.message}")
                }
            }
        }
    }

    fun ensurePermanentQrCode(businessId: String, locationId: String, shopName: String, branchName: String) {
        viewModelScope.launch {
            repository.ensurePermanentQrCode(businessId, locationId, shopName, branchName)
        }
    }

    fun generateOrRevokeQr(locationId: String, action: String) {
        viewModelScope.launch {
            val newQr = repository.generateOrRevokeQr(locationId, action)
            showToast("QR code updated: ${newQr.publicCode}")
        }
    }

    fun upgradePlan(businessId: String, plan: SubscriptionPlan) {
        viewModelScope.launch {
            val res = repository.upgradeSubscription(businessId, plan)
            res.onSuccess {
                showToast("✨ Subscribed to ${plan.title}! Entitlements updated via Razorpay.")
            }.onFailure {
                showToast("❌ Payment failed: ${it.message}")
            }
        }
    }

    fun dismissAbuseViolation(recordId: String) {
        viewModelScope.launch {
            repository.dismissAbuseViolation(recordId)
            showToast("Violation record dismissed")
        }
    }

    fun appealAbuseRestriction(message: String) {
        viewModelScope.launch {
            repository.appealAbuseRestriction(_uiState.value.currentUser.id, message)
            showToast("Appeal submitted to SuperAdmin review board")
        }
    }

    fun addStaff(name: String, email: String, role: StaffRole, counter: String) {
        viewModelScope.launch {
            val loc = _uiState.value.selectedLocation ?: return@launch
            val biz = _uiState.value.selectedBusiness ?: return@launch
            repository.addStaffMember(biz.id, loc.id, name, email, role, counter)
            showToast("Added $name as ${role.name}")
        }
    }

    fun removeStaff(staffId: String) {
        viewModelScope.launch {
            repository.removeStaffMember(staffId)
            showToast("Staff member removed")
        }
    }

    fun addBranch(branchName: String, address: String, city: String, phone: String, prefix: String) {
        viewModelScope.launch {
            val biz = _uiState.value.selectedBusiness ?: return@launch
            repository.addLocationBranch(biz.id, branchName, address, city, phone, prefix)
            showToast("Branch '$branchName' created")
        }
    }

    fun addService(name: String, desc: String, duration: Int, price: Double, prefix: String) {
        viewModelScope.launch {
            val biz = _uiState.value.selectedBusiness ?: return@launch
            val loc = _uiState.value.selectedLocation ?: return@launch
            repository.addService(biz.id, loc.id, name, desc, duration, price, prefix)
            showToast("Service '$name' added to queue")
        }
    }

    // Automated 100 Simultaneous Concurrent Request Stress Test
    fun runConcurrencyStressTest(businessId: String, locationId: String, serviceId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    concurrencyTest = ConcurrencyTestResult(
                        isRunning = true,
                        totalRequests = 100,
                        summary = "Dispatching 100 concurrent async coroutines..."
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            val deferredTokens = (1..100).map { i ->
                async {
                    // Create simulated unique customer join
                    repository.joinQueue(
                        businessId = businessId,
                        locationId = locationId,
                        serviceId = serviceId,
                        priority = if (i % 15 == 0) TokenPriority.VIP else TokenPriority.STANDARD,
                        notes = "Concurrency Stress Test Worker #$i"
                    )
                }
            }

            val results = deferredTokens.awaitAll()
            val elapsed = System.currentTimeMillis() - startTime
            val successTokens = results.mapNotNull { it.getOrNull() }
            val uniqueTokenNumbers = successTokens.map { it.tokenNumber }.toSet()
            val collisions = successTokens.size - uniqueTokenNumbers.size

            val summaryMsg = if (collisions == 0 && successTokens.isNotEmpty()) {
                "PASS: 100 concurrent requests processed in ${elapsed}ms. Generated ${uniqueTokenNumbers.size} 100% unique sequence tokens with ZERO collisions."
            } else {
                "COMPLETED: ${successTokens.size} processed in ${elapsed}ms. Collisions: $collisions."
            }

            _uiState.update {
                it.copy(
                    concurrencyTest = ConcurrencyTestResult(
                        isRunning = false,
                        totalRequests = 100,
                        successfulJoins = successTokens.size,
                        uniqueTokensGenerated = uniqueTokenNumbers.size,
                        duplicateCollisions = collisions,
                        executionTimeMs = elapsed,
                        summary = summaryMsg
                    )
                )
            }
            showToast(summaryMsg)
        }
    }

    fun deleteBusiness(businessId: String) {
        viewModelScope.launch {
            val targetName = _uiState.value.businesses.find { it.id == businessId }?.name ?: "Shop"
            val success = repository.deleteBusiness(businessId)
            if (success) {
                if (_uiState.value.selectedBusiness?.id == businessId) {
                    val remaining = _uiState.value.businesses.filter { it.id != businessId }
                    val nextBiz = remaining.firstOrNull()
                    val nextLoc = nextBiz?.let { biz -> _uiState.value.locations.find { it.businessId == biz.id } }
                    _uiState.update { it.copy(selectedBusiness = nextBiz, selectedLocation = nextLoc) }
                }
                showToast("🗑️ '$targetName' deleted successfully")
            } else {
                showToast("⚠️ Could not delete shop")
            }
        }
    }

    // ==========================================
    // SUPER ADMIN VIEWMODEL ACTIONS
    // ==========================================

    fun verifyAdminCredentials(email: String, pass: String): Boolean {
        val isValid = repository.verifyAdminCredentials(email, pass)
        if (isValid) {
            _uiState.update { it.copy(isLoggedIn = true, userRole = UserRole.ADMIN) }
            showToast("👑 SuperAdmin Root Access Verified (Talib Aziz)")
        } else {
            showToast("❌ Access Denied: Unauthorized Administrator Credentials")
        }
        return isValid
    }

    fun adminAddUser(name: String, email: String, phone: String, role: UserRole) {
        viewModelScope.launch {
            val user = repository.adminAddUser(name, email, phone, role)
            showToast("✅ Registered new user: ${user.name} (${user.role.name})")
        }
    }

    fun adminBlockUser(userId: String, reason: String) {
        viewModelScope.launch {
            repository.adminBlockUser(userId, reason)
            showToast("🚫 User blocked & restricted successfully")
        }
    }

    fun adminUnblockUser(userId: String) {
        viewModelScope.launch {
            repository.adminUnblockUser(userId)
            showToast("✅ User unblocked & restored to good standing")
        }
    }

    fun adminDeleteUser(userId: String) {
        viewModelScope.launch {
            repository.adminDeleteUser(userId)
            showToast("🗑️ User removed from platform")
        }
    }

    fun adminUpdateUserRole(userId: String, role: UserRole) {
        viewModelScope.launch {
            repository.adminUpdateUserRole(userId, role)
            showToast("👑 User role updated to ${role.name}")
        }
    }

    fun adminResetUserViolations(userId: String) {
        viewModelScope.launch {
            repository.adminResetUserViolations(userId)
            showToast("✨ User violations and strikes reset to 0")
        }
    }

    fun adminExtendBusinessSubscription(businessId: String, daysToAdd: Int) {
        viewModelScope.launch {
            repository.adminExtendBusinessSubscription(businessId, daysToAdd)
            showToast("⚡ Extended subscription by +$daysToAdd days (Database Updated)")
        }
    }

    fun adminSetBusinessSubscriptionExpiry(businessId: String, exactTimestamp: Long) {
        viewModelScope.launch {
            repository.adminSetBusinessSubscriptionExpiry(businessId, exactTimestamp)
            showToast("📅 Expiration date updated in database")
        }
    }

    fun adminResetTrial(businessId: String, trialDays: Int = 14) {
        viewModelScope.launch {
            repository.adminResetTrial(businessId, trialDays)
            showToast("🎁 Reset and granted $trialDays-day trial in database")
        }
    }

    fun adminSetBusinessPlan(businessId: String, plan: SubscriptionPlan) {
        viewModelScope.launch {
            repository.adminSetBusinessPlan(businessId, plan)
            showToast("💎 Plan tier upgraded to ${plan.title} (Database Updated)")
        }
    }

    fun adminToggleBusinessSuspension(businessId: String, isSuspended: Boolean, reason: String? = null) {
        viewModelScope.launch {
            repository.adminToggleBusinessSuspension(businessId, isSuspended, reason)
            showToast(if (isSuspended) "🔒 Business suspended & queues locked" else "🟢 Business active and operational")
        }
    }

    fun adminToggleBusinessVerification(businessId: String) {
        viewModelScope.launch {
            repository.adminToggleBusinessVerification(businessId)
            showToast("Badge verification status toggled")
        }
    }

    fun adminAddBusiness(name: String, category: BusinessCategory, description: String, city: String, plan: SubscriptionPlan) {
        viewModelScope.launch {
            val biz = repository.adminAddBusiness(name, category, description, city, plan)
            showToast("🏢 Business '${biz.name}' onboarded successfully")
        }
    }

    fun adminForceServeToken(tokenId: String) {
        viewModelScope.launch {
            repository.adminForceServeToken(tokenId)
            showToast("✅ Token force marked as SERVED by Admin")
        }
    }

    fun adminCancelToken(tokenId: String, reason: String) {
        viewModelScope.launch {
            repository.adminCancelToken(tokenId, reason)
            showToast("🛑 Token force cancelled by Admin")
        }
    }

    fun adminEmergencyPauseAll(isPaused: Boolean) {
        viewModelScope.launch {
            repository.adminEmergencyPauseAll(isPaused)
            showToast(if (isPaused) "🚨 EMERGENCY: All platform queues PAUSED" else "🟢 All platform queues RESUMED")
        }
    }

    fun adminPurgeCompletedTokens() {
        viewModelScope.launch {
            repository.adminPurgeCompletedTokens()
            showToast("🧹 Cleaned up finished & cancelled tokens")
        }
    }

    fun loginUser(email: String, pass: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val (success, errMsg) = repository.loginUser(email, pass)
            if (success) {
                _uiState.update { it.copy(isLoggedIn = true, userRole = repository.currentUser.value.role) }
                showToast("🔓 Logged in successfully as ${_uiState.value.currentUser.name}")
                onResult(true, null)
            } else {
                val msg = errMsg ?: "Login failed. Check credentials."
                showToast("❌ $msg")
                onResult(false, msg)
            }
        }
    }

    fun signUpCustomer(
        name: String,
        email: String,
        pass: String,
        phone: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val (success, errMsg) = repository.signUpCustomer(name, email, pass, phone)
            if (success) {
                _uiState.update { it.copy(isLoggedIn = true, userRole = repository.currentUser.value.role) }
                showToast("✨ Customer account created. Welcome, ${_uiState.value.currentUser.name}!")
                onResult(true, null)
            } else {
                val msg = errMsg ?: "Sign up failed."
                showToast("❌ $msg")
                onResult(false, msg)
            }
        }
    }

    fun signUpMerchant(
        ownerName: String,
        businessName: String,
        category: BusinessCategory,
        phone: String,
        email: String,
        pass: String,
        address: String,
        city: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val (success, errMsg) = repository.signUpMerchant(
                ownerName = ownerName,
                businessName = businessName,
                category = category,
                phone = phone,
                email = email,
                pass = pass,
                address = address,
                city = city
            )
            if (success) {
                val newBiz = repository.businesses.value.firstOrNull { it.ownerId == repository.currentUser.value.id }
                if (newBiz != null) {
                    selectBusiness(newBiz)
                }
                _uiState.update { it.copy(isLoggedIn = true, userRole = UserRole.MERCHANT) }
                showToast("🎉 Business '$businessName' registered! Verification is pending.")
                onResult(true, null)
            } else {
                val msg = errMsg ?: "Merchant registration failed."
                showToast("❌ $msg")
                onResult(false, msg)
            }
        }
    }

    fun signUpUser(
        name: String,
        email: String,
        pass: String,
        phone: String,
        role: UserRole = UserRole.USER,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        signUpCustomer(name, email, pass, phone, onResult)
    }

    fun loginAsMasterAdmin() {
        viewModelScope.launch {
            repository.loginAsMasterAdmin()
            _uiState.update { it.copy(isLoggedIn = true, userRole = UserRole.ADMIN) }
            showToast("👑 Logged in as Master SuperAdmin (Talib Aziz)")
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            repository.forgotPassword(email)
            showToast("✉️ Password reset instructions sent to $email")
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.signOutUser()
            _uiState.update { it.copy(isLoggedIn = false, userRole = UserRole.CUSTOMER) }
            showToast("🔒 Logged out successfully")
        }
    }

    fun registerMerchantShop(
        shopName: String,
        category: BusinessCategory,
        counterName: String,
        address: String,
        city: String,
        phone: String,
        avgDurationMinutes: Int,
        onSuccess: (Business) -> Unit
    ) {
        viewModelScope.launch {
            val biz = repository.registerMerchantShop(
                shopName = shopName,
                category = category,
                counterName = counterName,
                address = address,
                city = city,
                phone = phone,
                avgDurationMinutes = avgDurationMinutes
            )
            selectBusiness(biz)
            showToast("🎉 '${biz.name}' registered! Printable Barcode & QR Poster generated.")
            onSuccess(biz)
        }
    }

    fun updatePlanPrice(plan: SubscriptionPlan, newPrice: Int) {
        viewModelScope.launch {
            repository.updatePlanPrice(plan, newPrice)
            showToast("💰 ${plan.title} rate updated to ₹$newPrice/month")
        }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun completeMerchantOnboarding(
        shopName: String,
        category: BusinessCategory,
        description: String,
        phone: String,
        websiteUrl: String,
        branchName: String,
        address: String,
        city: String,
        state: String,
        country: String,
        serviceName: String,
        serviceDesc: String,
        serviceDuration: Int,
        servicePrice: Double,
        operatingHours: List<BusinessHours>,
        professionalName: String,
        experienceYears: Int,
        qualifications: String,
        specialties: String,
        languages: String,
        about: String,
        onSuccess: (Business) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val biz = repository.completeMerchantOnboarding(
                    shopName = shopName,
                    category = category,
                    description = description,
                    phone = phone,
                    websiteUrl = websiteUrl,
                    branchName = branchName,
                    address = address,
                    city = city,
                    state = state,
                    country = country,
                    serviceName = serviceName,
                    serviceDesc = serviceDesc,
                    serviceDuration = serviceDuration,
                    servicePrice = servicePrice,
                    operatingHours = operatingHours,
                    professionalName = professionalName,
                    experienceYears = experienceYears,
                    qualifications = qualifications,
                    specialties = specialties,
                    languages = languages,
                    about = about
                )
                selectBusiness(biz)
                showToast("🎉 Onboarding complete! '${biz.name}' setup successfully.")
                onSuccess(biz)
            } catch (e: Exception) {
                showToast("❌ Onboarding failed: ${e.message}")
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
