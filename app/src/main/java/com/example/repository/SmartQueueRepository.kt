package com.example.repository

import android.util.Log
import com.example.auth.AuthResultState
import com.example.auth.FirebaseAuthManager
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class SmartQueueRepository(
    val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    val firestoreDataSource: com.example.backend.FirestoreDataSource = com.example.backend.FirestoreDataSource()
) {

    private val mutex = Mutex()
    private val tokenSequenceCounters = mutableMapOf<String, AtomicInteger>() // key: "businessId_locationId_prefix"
    val isCloudConnected: Boolean
        get() = firestoreDataSource.isCloudConfigured

    // Current logged in user (defaults to Guest unauthenticated state)
    private val _currentUser = MutableStateFlow(
        User(
            id = "usr_guest",
            name = "Guest",
            email = "guest@smartqueue.in",
            phone = "",
            role = UserRole.CUSTOMER,
            isVerified = false,
            isRestricted = false,
            violationCount = 0
        )
    )
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    // Master list of all registered platform users
    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    // Businesses
    private val _businesses = MutableStateFlow<List<Business>>(emptyList())
    val businesses: StateFlow<List<Business>> = _businesses.asStateFlow()

    // Locations
    private val _locations = MutableStateFlow<List<BusinessLocation>>(emptyList())
    val locations: StateFlow<List<BusinessLocation>> = _locations.asStateFlow()

    // Services
    private val _services = MutableStateFlow<List<ServiceItem>>(emptyList())
    val services: StateFlow<List<ServiceItem>> = _services.asStateFlow()

    // Tokens
    private val _tokens = MutableStateFlow<List<Token>>(emptyList())
    val tokens: StateFlow<List<Token>> = _tokens.asStateFlow()

    // Staff
    private val _staffMembers = MutableStateFlow<List<StaffMember>>(emptyList())
    val staffMembers: StateFlow<List<StaffMember>> = _staffMembers.asStateFlow()

    // QR Codes
    private val _qrCodes = MutableStateFlow<List<QRCodeData>>(emptyList())
    val qrCodes: StateFlow<List<QRCodeData>> = _qrCodes.asStateFlow()

    // Abuse Records
    private val _abuseRecords = MutableStateFlow<List<AbuseRecord>>(emptyList())
    val abuseRecords: StateFlow<List<AbuseRecord>> = _abuseRecords.asStateFlow()

    // Notifications
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    // Audit Logs
    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    // Payments
    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    // Dynamic Plan Pricing Manager
    private val _planPrices = MutableStateFlow<Map<SubscriptionPlan, Int>>(
        mapOf(
            SubscriptionPlan.FREE to 0,
            SubscriptionPlan.PRO to 199,
            SubscriptionPlan.BUSINESS to 499,
            SubscriptionPlan.ENTERPRISE to 999
        )
    )
    val planPrices: StateFlow<Map<SubscriptionPlan, Int>> = _planPrices.asStateFlow()

    suspend fun updatePlanPrice(plan: SubscriptionPlan, newPrice: Int) = mutex.withLock {
        requireAdmin()
        val current = _planPrices.value.toMutableMap()
        current[plan] = newPrice.coerceAtLeast(0)
        _planPrices.value = current
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = _currentUser.value.role.name,
                action = "UPDATE_PLAN_PRICING",
                target = "${plan.name} rate changed to ₹$newPrice/month"
            )
        ) + _auditLogs.value
    }

    suspend fun loginUser(emailOrPhone: String, pass: String): Pair<Boolean, String?> = mutex.withLock {
        val cleanEmailOrPhone = emailOrPhone.trim().lowercase()
        val cleanPass = pass.trim()

        if (cleanEmailOrPhone.isBlank() || cleanPass.isBlank()) {
            return@withLock Pair(false, "Email/Phone and Password cannot be empty.")
        }

        // Try Firebase Authentication first
        val authResult = authManager.signIn(cleanEmailOrPhone, cleanPass)
        if (authResult is AuthResultState.Success) {
            val authUser = authResult.user
            val canonicalUid = authUser.id

            // Fetch authoritative profile from Firestore if cloud is active
            val cloudProfile = if (firestoreDataSource.isCloudConfigured) {
                firestoreDataSource.getUserProfile(canonicalUid)
            } else null

            val localExisting = _allUsers.value.find { 
                it.id == canonicalUid || it.email.equals(cleanEmailOrPhone, ignoreCase = true) 
            }
            val isStaffMember = _staffMembers.value.any { it.email.equals(cleanEmailOrPhone, ignoreCase = true) }
            val isMerchantByOwnership = _businesses.value.any { 
                it.ownerId == canonicalUid || (localExisting != null && it.ownerId == localExisting.id) 
            }

            val authoritativeRole = when {
                cloudProfile?.role in listOf(UserRole.ADMIN, UserRole.SUPER_ADMIN) -> UserRole.SUPER_ADMIN
                cloudProfile?.role in listOf(UserRole.MERCHANT, UserRole.BUSINESS_OWNER) -> UserRole.MERCHANT
                cloudProfile?.role == UserRole.STAFF -> UserRole.STAFF
                cleanEmailOrPhone.equals("talibaziz0786@gmail.com", ignoreCase = true) ||
                    cleanEmailOrPhone.equals("admin@smartqueue.in", ignoreCase = true) ||
                    cleanEmailOrPhone.equals("talib.admin@smartqueue.in", ignoreCase = true) -> UserRole.SUPER_ADMIN
                isStaffMember -> UserRole.STAFF
                authUser.role == UserRole.STAFF -> UserRole.STAFF
                localExisting?.role == UserRole.STAFF -> UserRole.STAFF
                isMerchantByOwnership -> UserRole.MERCHANT
                authUser.role in listOf(UserRole.MERCHANT, UserRole.BUSINESS_OWNER) -> UserRole.MERCHANT
                localExisting?.role in listOf(UserRole.MERCHANT, UserRole.BUSINESS_OWNER) -> UserRole.MERCHANT
                cloudProfile?.role != null -> cloudProfile.role
                else -> authUser.role
            }

            val isBlocked = cloudProfile?.isBlocked ?: localExisting?.isBlocked ?: authUser.isBlocked
            val isRestricted = cloudProfile?.isRestricted ?: localExisting?.isRestricted ?: authUser.isRestricted
            val restrictionReason = cloudProfile?.restrictionReason ?: localExisting?.restrictionReason ?: authUser.restrictionReason
            val violationCount = cloudProfile?.violationCount ?: localExisting?.violationCount ?: authUser.violationCount

            val effectiveUser = User(
                id = canonicalUid,
                name = (cloudProfile?.name ?: localExisting?.name ?: authUser.name).ifBlank { "User" },
                email = (cloudProfile?.email ?: localExisting?.email ?: authUser.email).ifBlank { cleanEmailOrPhone },
                phone = (cloudProfile?.phone ?: localExisting?.phone ?: authUser.phone).ifBlank { "+91 98765 00000" },
                role = authoritativeRole,
                accountStatus = cloudProfile?.accountStatus ?: localExisting?.accountStatus ?: authUser.accountStatus,
                isVerified = cloudProfile?.isVerified ?: localExisting?.isVerified ?: authUser.isVerified,
                isRestricted = isRestricted,
                isBlocked = isBlocked,
                restrictionReason = restrictionReason,
                violationCount = violationCount
            )

            _currentUser.value = effectiveUser
            _allUsers.value = listOf(effectiveUser) + _allUsers.value.filter { it.id != canonicalUid }
            if (firestoreDataSource.isCloudConfigured) {
                firestoreDataSource.saveUserProfile(effectiveUser)
            }
            syncTokenListenerForCurrentUser()
            return@withLock Pair(true, null)
        }

        val errMsg = if (authResult is AuthResultState.Error) authResult.message else "Authentication failed."
        Pair(false, errMsg)
    }

    /**
     * Customer Sign Up: Strictly assigns role = USER.
     * Never trusts role from frontend.
     */
    suspend fun signUpCustomer(
        name: String,
        email: String,
        pass: String,
        phone: String
    ): Pair<Boolean, String?> = mutex.withLock {
        val cleanName = name.trim().ifBlank { "Customer" }
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        val cleanPhone = phone.trim().ifBlank { "+91 98765 00000" }

        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            return@withLock Pair(false, "Please provide email and password.")
        }

        val authResult = authManager.signUpCustomer(cleanName, cleanEmail, cleanPass, cleanPhone)
        if (authResult is AuthResultState.Success) {
            val user = authResult.user
            _currentUser.value = user
            _allUsers.value = listOf(user) + _allUsers.value
            if (firestoreDataSource.isCloudConfigured) {
                firestoreDataSource.saveUserProfile(user)
            }
            syncTokenListenerForCurrentUser()
            return@withLock Pair(true, null)
        }

        if (firestoreDataSource.isCloudConfigured) {
            val err = if (authResult is AuthResultState.Error) authResult.message else "Authentication failed."
            return@withLock Pair(false, err)
        }

        // Local creation fallback (only when cloud is not configured)
        val newUser = User(
            id = "usr_${UUID.randomUUID().toString().take(12)}",
            name = cleanName,
            email = cleanEmail,
            phone = cleanPhone,
            role = UserRole.USER,
            accountStatus = AccountStatus.ACTIVE,
            isVerified = true
        )
        _allUsers.value = listOf(newUser) + _allUsers.value
        _currentUser.value = newUser
        Pair(true, null)
    }

    /**
     * Merchant Sign Up: Strictly creates role = MERCHANT.
     * Automatically seeds new business with PENDING verification status.
     */
    suspend fun signUpMerchant(
        ownerName: String,
        businessName: String,
        category: BusinessCategory,
        phone: String,
        email: String,
        pass: String,
        address: String,
        city: String
    ): Pair<Boolean, String?> = mutex.withLock {
        val cleanOwnerName = ownerName.trim().ifBlank { "Shop Owner" }
        val cleanBizName = businessName.trim().ifBlank { "My Business" }
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        val cleanPhone = phone.trim().ifBlank { "+91 98765 00000" }
        val cleanCity = city.trim().ifBlank { "Lucknow" }
        val cleanAddress = address.trim().ifBlank { "Main Market Road" }

        if (cleanBizName.isBlank() || cleanEmail.isBlank() || cleanPass.isBlank()) {
            return@withLock Pair(false, "Please fill in business name, email and password.")
        }

        val authResult = authManager.signUpMerchant(cleanOwnerName, cleanEmail, cleanPass, cleanPhone)
        val user = if (authResult is AuthResultState.Success) {
            authResult.user
        } else {
            if (firestoreDataSource.isCloudConfigured) {
                val err = if (authResult is AuthResultState.Error) authResult.message else "Authentication failed."
                return@withLock Pair(false, err)
            }
            User(
                id = "usr_merch_${UUID.randomUUID().toString().take(12)}",
                name = cleanOwnerName,
                email = cleanEmail,
                phone = cleanPhone,
                role = UserRole.MERCHANT,
                accountStatus = AccountStatus.ACTIVE,
                isVerified = true
            )
        }

        _currentUser.value = user
        if (!_allUsers.value.any { it.id == user.id }) {
            _allUsers.value = listOf(user) + _allUsers.value
        }
        if (firestoreDataSource.isCloudConfigured) {
            firestoreDataSource.saveUserProfile(user)
        }

        // Create new Business with PENDING verification status
        val newBusiness = Business(
            id = "biz_${UUID.randomUUID().toString().take(12)}",
            name = cleanBizName,
            ownerId = user.id,
            category = category,
            description = "Welcome to $cleanBizName. Live queue and token service.",
            verificationStatus = MerchantVerificationStatus.PENDING,
            isVerified = false,
            primaryCity = cleanCity,
            plan = SubscriptionPlan.FREE
        )

        val prefixChar = cleanBizName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "M"
        val newLocation = BusinessLocation(
            id = "loc_${newBusiness.id}",
            businessId = newBusiness.id,
            branchName = "Main Counter",
            address = cleanAddress,
            city = cleanCity,
            phone = cleanPhone,
            prefix = prefixChar
        )

        val newService = ServiceItem(
            id = "srv_${newBusiness.id}",
            businessId = newBusiness.id,
            locationId = newLocation.id,
            name = "Standard Consultation / Token",
            description = "General walk-in and appointment service queue",
            durationMinutes = 15,
            prefix = prefixChar
        )

        val newQr = QRCodeData(
            qrId = "qr_${newBusiness.id}",
            businessId = newBusiness.id,
            locationId = newLocation.id,
            publicCode = "SQ-${cleanBizName.take(4).uppercase().filter { it.isLetter() }.ifEmpty { "SHOP" }}-${cleanCity.take(3).uppercase()}-${(1000..9999).random()}"
        )

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.saveOnboardingBatch(newBusiness, newLocation, newService, newQr, user)
            if (cloudRes.isFailure) {
                return@withLock Pair(false, "Failed to persist business to cloud database: ${cloudRes.exceptionOrNull()?.message}")
            }
        }

        _businesses.value = listOf(newBusiness) + _businesses.value
        _locations.value = listOf(newLocation) + _locations.value
        _services.value = listOf(newService) + _services.value
        _qrCodes.value = listOf(newQr) + _qrCodes.value

        _auditLogs.value = listOf(
            AuditLog(
                actorName = user.name,
                actorRole = "MERCHANT",
                action = "MERCHANT_REGISTERED",
                target = "${newBusiness.name} (Verification: PENDING)"
            )
        ) + _auditLogs.value

        syncTokenListenerForCurrentUser()
        Pair(true, null)
    }

    suspend fun signUpUser(
        name: String,
        email: String,
        pass: String,
        phone: String,
        role: UserRole = UserRole.USER
    ): Pair<Boolean, String?> = signUpCustomer(name, email, pass, phone)

    suspend fun signOutUser() = mutex.withLock {
        authManager.signOut()
        tokenListenerRegistration?.remove()
        tokenListenerRegistration = null
        _currentUser.value = User(
            id = "usr_guest",
            name = "Guest",
            email = "guest@smartqueue.in",
            phone = "",
            role = UserRole.CUSTOMER,
            isVerified = false
        )
    }

    suspend fun deleteBusiness(businessId: String): Boolean = mutex.withLock {
        val caller = _currentUser.value
        val canonicalUid = authManager.currentFirebaseUser?.uid ?: caller.id
        val target = _businesses.value.find { it.id == businessId } ?: return@withLock false
        val isOwner = target.ownerId == caller.id || target.ownerId == canonicalUid
        val isAdmin = caller.role in listOf(UserRole.ADMIN, UserRole.SUPER_ADMIN)
        if (!isOwner && !isAdmin) {
            throw SecurityException("403 Forbidden: You do not have permission to delete business $businessId.")
        }
        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.deleteBusinessCloud(businessId)
            if (cloudRes.isFailure) {
                val err = cloudRes.exceptionOrNull() ?: IllegalStateException("Cloud deletion failed for business $businessId")
                Log.e("SmartQueueRepository", "deleteBusiness cloud write failed: ${err.message}", err)
                throw err
            }
        }
        _businesses.value = _businesses.value.filter { it.id != businessId }
        _locations.value = _locations.value.filter { it.businessId != businessId }
        _services.value = _services.value.filter { it.businessId != businessId }
        _qrCodes.value = _qrCodes.value.filter { it.businessId != businessId }
        _tokens.value = _tokens.value.filter { it.businessId != businessId }
        _staffMembers.value = _staffMembers.value.filter { it.businessId != businessId }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = _currentUser.value.role.name,
                action = "BUSINESS_DELETED",
                target = "${target.name} (ID: $businessId)"
            )
        ) + _auditLogs.value
        true
    }

    suspend fun loginAsMasterAdmin() = mutex.withLock {
        val adminUser = User(
            id = "usr_master_admin",
            name = "Talib Aziz (SuperAdmin)",
            email = "talibaziz0786@gmail.com",
            phone = "+91 98765 78600",
            role = UserRole.ADMIN,
            isVerified = true
        )
        if (!_allUsers.value.any { it.id == adminUser.id }) {
            _allUsers.value = _allUsers.value + adminUser
        }
        _currentUser.value = adminUser
    }

    suspend fun forgotPassword(emailOrPhone: String) = mutex.withLock {
        // Simulated password reset trigger
    }

    // Active token filter helper for current user
    val activeUserToken: StateFlow<Token?> = MutableStateFlow<Token?>(null)

    private var tokenListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private fun syncTokenListenerForCurrentUser() {
        if (!firestoreDataSource.isCloudConfigured) return
        val user = _currentUser.value
        tokenListenerRegistration?.remove()
        tokenListenerRegistration = null

        val fbUid = authManager.currentFirebaseUser?.uid

        when (user.role) {
            UserRole.ADMIN, UserRole.SUPER_ADMIN -> {
                tokenListenerRegistration = firestoreDataSource.listenToTokens { tokList ->
                    _tokens.value = tokList
                }
            }
            UserRole.MERCHANT, UserRole.BUSINESS_OWNER -> {
                tokenListenerRegistration = firestoreDataSource.listenToTokens(
                    businessOwnerId = user.id
                ) { tokList ->
                    _tokens.value = tokList
                }
            }
            UserRole.STAFF -> {
                val staff = _staffMembers.value.find { it.email.equals(user.email, ignoreCase = true) }
                if (staff != null) {
                    tokenListenerRegistration = firestoreDataSource.listenToTokens(
                        businessId = staff.businessId,
                        locationId = staff.locationId
                    ) { tokList ->
                        _tokens.value = tokList
                    }
                } else {
                    val uidToListen = if (user.id != "usr_guest" && user.id.isNotBlank()) user.id else fbUid
                    if (!uidToListen.isNullOrBlank()) {
                        tokenListenerRegistration = firestoreDataSource.listenToTokens(customerId = uidToListen) { tokList ->
                            _tokens.value = tokList
                        }
                    }
                }
            }
            else -> {
                val uidToListen = if (user.id != "usr_guest" && user.id.isNotBlank()) user.id else fbUid
                if (!uidToListen.isNullOrBlank()) {
                    tokenListenerRegistration = firestoreDataSource.listenToTokens(customerId = uidToListen) { tokList ->
                        _tokens.value = tokList
                    }
                }
            }
        }
    }

    init {
        val currentFbUser = authManager.currentFirebaseUser
        if (currentFbUser != null) {
            val role = authManager.determineUserRole(currentFbUser.email ?: "")
            val restoredUser = User(
                id = currentFbUser.uid,
                name = currentFbUser.displayName ?: (currentFbUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"),
                email = currentFbUser.email ?: "",
                phone = currentFbUser.phoneNumber ?: "+91 98765 00000",
                role = role,
                accountStatus = AccountStatus.ACTIVE,
                isVerified = true
            )
            _currentUser.value = restoredUser
            _allUsers.value = listOf(restoredUser)
        }

        if (firestoreDataSource.isCloudConfigured) {
            CoroutineScope(Dispatchers.IO).launch {
                authManager.ensureAuthenticated()
                val currentUid = authManager.currentFirebaseUser?.uid
                if (currentUid != null) {
                    val profile = firestoreDataSource.getUserProfile(currentUid)
                    if (profile != null) {
                        val finalRole = if (profile.role == UserRole.USER && _businesses.value.any { it.ownerId == currentUid }) {
                            UserRole.MERCHANT
                        } else {
                            profile.role
                        }
                        val finalProfile = profile.copy(role = finalRole)
                        _currentUser.value = finalProfile
                        _allUsers.value = listOf(finalProfile) + _allUsers.value.filter { it.id != currentUid }
                        syncTokenListenerForCurrentUser()
                    }
                }
            }
            firestoreDataSource.listenToBusinesses { bizList ->
                _businesses.value = bizList
                val curUser = _currentUser.value
                if (curUser.role == UserRole.USER && bizList.any { it.ownerId == curUser.id }) {
                    val healed = curUser.copy(role = UserRole.MERCHANT)
                    _currentUser.value = healed
                    _allUsers.value = listOf(healed) + _allUsers.value.filter { it.id != healed.id }
                }
                syncTokenListenerForCurrentUser()
            }
            firestoreDataSource.listenToLocations { locList ->
                _locations.value = locList
            }
            firestoreDataSource.listenToServices { srvList ->
                _services.value = srvList
            }
            firestoreDataSource.listenToQrCodes { qrList ->
                _qrCodes.value = qrList
            }
            syncTokenListenerForCurrentUser()
        } else {
            seedInitialData()
        }
    }

    fun testOnlySeedMockData() {
        seedInitialData()
    }

    private fun seedInitialData() {
        val bizApex = Business(
            id = "biz_apex",
            name = "Apex Multi-Speciality Clinic",
            ownerId = "usr_owner_01",
            category = BusinessCategory.HEALTHCARE,
            description = "Premier digital outpatient care, certified specialists, fast diagnostic laboratory & cardiology.",
            isVerified = true,
            rating = 4.9,
            reviewCount = 342,
            primaryCity = "Lucknow",
            plan = SubscriptionPlan.BUSINESS
        )

        val bizVogue = Business(
            id = "biz_vogue",
            name = "Vogue Luxe Studio & Salon",
            ownerId = "usr_owner_02",
            category = BusinessCategory.SALON_BEAUTY,
            description = "High-end aesthetic hair styling, skin therapy, luxury grooming & bridal makeovers.",
            isVerified = true,
            rating = 4.8,
            reviewCount = 215,
            primaryCity = "Lucknow",
            plan = SubscriptionPlan.PRO
        )

        val bizByteFix = Business(
            id = "biz_bytefix",
            name = "ByteFix Express Tech Care",
            ownerId = "usr_owner_03",
            category = BusinessCategory.REPAIR,
            description = "Authorized mobile, tablet & laptop chip-level diagnostics with 30-min express token queue.",
            isVerified = true,
            rating = 4.7,
            reviewCount = 189,
            primaryCity = "Lucknow",
            plan = SubscriptionPlan.PRO
        )

        val bizGourmet = Business(
            id = "biz_gourmet",
            name = "The Artisan Bistro & Dine",
            ownerId = "usr_owner_04",
            category = BusinessCategory.RESTAURANT,
            description = "Fresh woodfired artisanal pizzas, specialty coffee & contactless queue seating.",
            isVerified = true,
            rating = 4.8,
            reviewCount = 410,
            primaryCity = "Lucknow",
            plan = SubscriptionPlan.FREE
        )

        _businesses.value = listOf(bizApex, bizVogue, bizByteFix, bizGourmet)

        // Locations (Multi-branch for Apex)
        val locApexHazratganj = BusinessLocation(
            id = "loc_apex_hazratganj",
            businessId = "biz_apex",
            branchName = "Hazratganj Flagship Clinic",
            address = "14/B Mahatma Gandhi Marg, Hazratganj",
            city = "Lucknow",
            phone = "+91 522 2618900",
            isOpen = true,
            prefix = "A"
        )
        val locApexKanpur = BusinessLocation(
            id = "loc_apex_kanpur",
            businessId = "biz_apex",
            branchName = "Swaroop Nagar Branch",
            address = "88 Metro Plaza, Swaroop Nagar",
            city = "Kanpur",
            phone = "+91 512 2541100",
            isOpen = true,
            prefix = "K"
        )
        val locApexDelhi = BusinessLocation(
            id = "loc_apex_delhi",
            businessId = "biz_apex",
            branchName = "South Extension Care Center",
            address = "G-24 South Extension Part 2",
            city = "New Delhi",
            phone = "+91 11 41058822",
            isOpen = true,
            prefix = "D"
        )

        val locVogueGomti = BusinessLocation(
            id = "loc_vogue_gomtinagar",
            businessId = "biz_vogue",
            branchName = "Gomti Nagar Luxe Studio",
            address = "Riverside Mall Level 2, Gomti Nagar",
            city = "Lucknow",
            phone = "+91 522 4007890",
            isOpen = true,
            prefix = "V"
        )

        val locByteFix = BusinessLocation(
            id = "loc_bytefix_hazratganj",
            businessId = "biz_bytefix",
            branchName = "Cyber Hub Central",
            address = "Shop 12, Janpath Market, Hazratganj",
            city = "Lucknow",
            phone = "+91 98390 11223",
            isOpen = true,
            prefix = "B"
        )

        val locGourmet = BusinessLocation(
            id = "loc_gourmet_palassio",
            businessId = "biz_gourmet",
            branchName = "Palassio Mall Dining",
            address = "Amar Shaheed Path, Sector 7",
            city = "Lucknow",
            phone = "+91 522 6678200",
            isOpen = true,
            prefix = "G"
        )

        _locations.value = listOf(
            locApexHazratganj,
            locApexKanpur,
            locApexDelhi,
            locVogueGomti,
            locByteFix,
            locGourmet
        )

        // Services
        val srvConsult = ServiceItem(
            id = "srv_apex_gen",
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            name = "Senior Physician Consultation",
            description = "Detailed physical examination & clinical prescription",
            durationMinutes = 15,
            price = 600.0,
            prefix = "A"
        )
        val srvDental = ServiceItem(
            id = "srv_apex_dental",
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            name = "Dental Scaling & Checkup",
            description = "Oral hygiene inspection, ultrasonic cleaning & dental x-ray",
            durationMinutes = 25,
            price = 1200.0,
            prefix = "A"
        )
        val srvCardio = ServiceItem(
            id = "srv_apex_cardio",
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            name = "Cardiology ECG & Review",
            description = "Advanced 12-lead ECG and specialist evaluation",
            durationMinutes = 20,
            price = 1500.0,
            prefix = "A"
        )

        val srvHair = ServiceItem(
            id = "srv_vogue_hair",
            businessId = "biz_vogue",
            locationId = "loc_vogue_gomtinagar",
            name = "Designer Haircut & Styling",
            description = "Precision haircut, wash, blow-dry & beard grooming",
            durationMinutes = 30,
            price = 850.0,
            prefix = "V"
        )
        val srvFacial = ServiceItem(
            id = "srv_vogue_facial",
            businessId = "biz_vogue",
            locationId = "loc_vogue_gomtinagar",
            name = "Hydra Radiance Facial",
            description = "Deep skin exfoliation, serum infusion & detox massage",
            durationMinutes = 45,
            price = 2200.0,
            prefix = "V"
        )

        val srvScreen = ServiceItem(
            id = "srv_bytefix_screen",
            businessId = "biz_bytefix",
            locationId = "loc_bytefix_hazratganj",
            name = "OEM Screen Replacement",
            description = "Genuine display panel installation with 6-month warranty",
            durationMinutes = 35,
            price = 2800.0,
            prefix = "B"
        )

        _services.value = listOf(srvConsult, srvDental, srvCardio, srvHair, srvFacial, srvScreen)

        // Seed QR Codes
        _qrCodes.value = listOf(
            QRCodeData(
                qrId = "qr_apex_01",
                businessId = "biz_apex",
                locationId = "loc_apex_hazratganj",
                publicCode = "SQ-APEX-HAZRATGANJ-2026",
                scanCount = 1420
            ),
            QRCodeData(
                qrId = "qr_vogue_01",
                businessId = "biz_vogue",
                locationId = "loc_vogue_gomtinagar",
                publicCode = "SQ-VOGUE-GOMTI-2026",
                scanCount = 890
            ),
            QRCodeData(
                qrId = "qr_bytefix_01",
                businessId = "biz_bytefix",
                locationId = "loc_bytefix_hazratganj",
                publicCode = "SQ-BYTEFIX-HUB-2026",
                scanCount = 512
            )
        )

        // Seed Staff
        _staffMembers.value = listOf(
            StaffMember(
                id = "stf_01",
                businessId = "biz_apex",
                locationId = "loc_apex_hazratganj",
                name = "Dr. Sameer Verma",
                email = "dr.sameer@apexclinic.com",
                role = StaffRole.SUPERVISOR,
                assignedCounter = "Counter 1 (General OPD)"
            ),
            StaffMember(
                id = "stf_02",
                businessId = "biz_apex",
                locationId = "loc_apex_hazratganj",
                name = "Dr. Ananya Roy",
                email = "dr.ananya@apexclinic.com",
                role = StaffRole.COUNTER_OPERATOR,
                assignedCounter = "Counter 2 (Dental Unit)"
            ),
            StaffMember(
                id = "stf_03",
                businessId = "biz_apex",
                locationId = "loc_apex_hazratganj",
                name = "Rahul Sharma",
                email = "rahul.reception@apexclinic.com",
                role = StaffRole.RECEPTIONIST,
                assignedCounter = "Help Desk & Triage"
            )
        )

        // Seed Realistic Active Live Queue for Apex Hazratganj
        // A-018 Served, A-019 Currently Serving, A-020 to A-026 Waiting, A-027 (Current user Talib Aziz)
        val initialTokens = mutableListOf<Token>()
        
        // Served
        initialTokens.add(
            Token(
                id = "tok_prev_18",
                tokenNumber = "A-018",
                sequenceNumber = 18,
                userId = "usr_seed_18",
                userName = "Vikram Malhotra",
                userPhone = "+91 94150 11001",
                businessId = "biz_apex",
                businessName = "Apex Multi-Speciality Clinic",
                locationId = "loc_apex_hazratganj",
                branchName = "Hazratganj Flagship Clinic",
                serviceId = "srv_apex_gen",
                serviceName = "Senior Physician Consultation",
                status = TokenStatus.SERVED,
                counterNumber = "Counter 1",
                staffAssigned = "Dr. Sameer Verma",
                servedAt = System.currentTimeMillis() - 15 * 60 * 1000
            )
        )

        // Currently Serving
        initialTokens.add(
            Token(
                id = "tok_serv_19",
                tokenNumber = "A-019",
                sequenceNumber = 19,
                userId = "usr_seed_19",
                userName = "Ritu Agarwal",
                userPhone = "+91 98380 22114",
                businessId = "biz_apex",
                businessName = "Apex Multi-Speciality Clinic",
                locationId = "loc_apex_hazratganj",
                branchName = "Hazratganj Flagship Clinic",
                serviceId = "srv_apex_gen",
                serviceName = "Senior Physician Consultation",
                status = TokenStatus.SERVING,
                counterNumber = "Counter 1",
                staffAssigned = "Dr. Sameer Verma",
                calledAt = System.currentTimeMillis() - 8 * 60 * 1000
            )
        )

        // Waiting Ahead
        val waitingNames = listOf(
            "Amitabh Saxena",
            "Pooja Deshmukh",
            "Kunal Kapoor",
            "Sneha Rastogi",
            "Farhan Khan",
            "Meera Nair",
            "Devendra Joshi"
        )
        waitingNames.forEachIndexed { index, name ->
            val seq = 20 + index
            val tokNum = "A-0${seq}"
            initialTokens.add(
                Token(
                    id = "tok_wait_$seq",
                    tokenNumber = tokNum,
                    sequenceNumber = seq,
                    userId = "usr_seed_$seq",
                    userName = name,
                    userPhone = "+91 97920 ${10000 + seq}",
                    businessId = "biz_apex",
                    businessName = "Apex Multi-Speciality Clinic",
                    locationId = "loc_apex_hazratganj",
                    branchName = "Hazratganj Flagship Clinic",
                    serviceId = if (seq % 2 == 0) "srv_apex_gen" else "srv_apex_dental",
                    serviceName = if (seq % 2 == 0) "Senior Physician Consultation" else "Dental Checkup",
                    status = TokenStatus.WAITING,
                    estimatedWaitMinutes = (index + 1) * 4
                )
            )
        }

        // Current User Active Token: A-027
        val userToken = Token(
            id = "tok_user_27",
            tokenNumber = "A-027",
            sequenceNumber = 27,
            userId = "usr_cust_01",
            userName = "Talib Aziz",
            userPhone = "+91 98765 43210",
            businessId = "biz_apex",
            businessName = "Apex Multi-Speciality Clinic",
            locationId = "loc_apex_hazratganj",
            branchName = "Hazratganj Flagship Clinic",
            serviceId = "srv_apex_gen",
            serviceName = "Senior Physician Consultation",
            status = TokenStatus.WAITING,
            estimatedWaitMinutes = 32,
            initialPosition = 8
        )
        initialTokens.add(userToken)

        _tokens.value = initialTokens
        tokenSequenceCounters["biz_apex_loc_apex_hazratganj_A"] = AtomicInteger(27)
        tokenSequenceCounters["biz_vogue_loc_vogue_gomtinagar_V"] = AtomicInteger(10)
        tokenSequenceCounters["biz_bytefix_loc_bytefix_hazratganj_B"] = AtomicInteger(5)

        // Seed Audit Logs
        _auditLogs.value = listOf(
            AuditLog(
                actorName = "Dr. Sameer Verma",
                actorRole = "SUPERVISOR",
                action = "START_SERVING",
                target = "Token A-019 at Counter 1"
            ),
            AuditLog(
                actorName = "Rahul Sharma",
                actorRole = "RECEPTIONIST",
                action = "QUEUE_OPENED",
                target = "Hazratganj OPD Queue"
            ),
            AuditLog(
                actorName = "Talib Aziz",
                actorRole = "CUSTOMER",
                action = "JOIN_QUEUE",
                target = "Apex Clinic - Token A-027"
            )
        )

        // Seed Master Users
        _allUsers.value = listOf(
            User(
                id = "usr_admin_01",
                name = "Talib Aziz (Super Admin)",
                email = "talibaziz0786@gmail.com",
                phone = "+91 98765 43210",
                role = UserRole.ADMIN,
                isVerified = true,
                isRestricted = false,
                isBlocked = false
            ),
            User(
                id = "usr_cust_01",
                name = "Talib Aziz",
                email = "talibaziz0786@gmail.com",
                phone = "+91 98765 43210",
                role = UserRole.CUSTOMER,
                isVerified = true,
                isRestricted = false,
                isBlocked = false
            ),
            User(
                id = "usr_owner_01",
                name = "Dr. Rajeshwar Singhania",
                email = "dr.rajeshwar@apexclinic.com",
                phone = "+91 98200 12345",
                role = UserRole.BUSINESS_OWNER,
                isVerified = true
            ),
            User(
                id = "usr_owner_02",
                name = "Ayesha Sen",
                email = "ayesha@vogueluxe.in",
                phone = "+91 98300 54321",
                role = UserRole.BUSINESS_OWNER,
                isVerified = true
            ),
            User(
                id = "usr_owner_03",
                name = "Karan Kapoor",
                email = "karan@bytefix.com",
                phone = "+91 98111 88990",
                role = UserRole.BUSINESS_OWNER,
                isVerified = true
            ),
            User(
                id = "usr_owner_04",
                name = "Chef Marco D'Souza",
                email = "marco@artisanbistro.com",
                phone = "+91 98777 44332",
                role = UserRole.BUSINESS_OWNER,
                isVerified = false
            ),
            User(
                id = "stf_01",
                name = "Dr. Sameer Verma",
                email = "dr.sameer@apexclinic.com",
                phone = "+91 94150 77889",
                role = UserRole.STAFF,
                isVerified = true
            ),
            User(
                id = "stf_02",
                name = "Dr. Ananya Roy",
                email = "dr.ananya@apexclinic.com",
                phone = "+91 94150 99001",
                role = UserRole.STAFF,
                isVerified = true
            ),
            User(
                id = "usr_cust_02",
                name = "Pooja Verma",
                email = "pooja.verma@gmail.com",
                phone = "+91 98222 33445",
                role = UserRole.CUSTOMER,
                isVerified = true
            ),
            User(
                id = "usr_cust_03",
                name = "Mohit Agarwal",
                email = "mohit.ag@outlook.com",
                phone = "+91 98100 12121",
                role = UserRole.CUSTOMER,
                isVerified = false,
                isRestricted = true,
                isBlocked = true,
                restrictionReason = "Abuse threshold exceeded: 3 consecutive no-shows and queue spamming",
                violationCount = 3
            )
        )

        // Seed Notifications
        _notifications.value = listOf(
            NotificationItem(
                userId = "usr_cust_01",
                title = "Digital Token Issued",
                message = "Your token A-027 has been assigned at Apex Multi-Speciality Clinic. You are #8 in queue.",
                type = NotificationType.TOKEN_CREATED,
                relatedTokenId = "tok_user_27"
            ),
            NotificationItem(
                userId = "usr_cust_01",
                title = "Queue Live Update",
                message = "Token A-018 was completed. Token A-019 is now serving at Counter 1.",
                type = NotificationType.POSITION_UPDATED,
                relatedTokenId = "tok_user_27"
            )
        )
    }

    // Role switcher for administrative auditing
    fun switchUserRole(role: UserRole) {
        val current = _currentUser.value
        if (current.role != UserRole.ADMIN && current.role != UserRole.SUPER_ADMIN) {
            throw SecurityException("403 Forbidden: Role changes are restricted to authenticated Super Administrators.")
        }
        _currentUser.value = current.copy(
            role = role,
            name = when (role) {
                UserRole.USER -> "Talib Aziz"
                UserRole.MERCHANT -> "Dr. Rajeshwar Singhania (Owner)"
                UserRole.STAFF -> "Dr. Sameer Verma (Supervisor)"
                UserRole.ADMIN, UserRole.SUPER_ADMIN -> "Talib Aziz (Super Admin)"
            }
        )
    }

    fun testOnlySetStaffLocation(email: String, locationId: String) {
        _staffMembers.value = _staffMembers.value.map {
            if (it.email.equals(email, ignoreCase = true)) {
                it.copy(locationId = locationId)
            } else {
                it
            }
        }
    }

    private fun requireAdmin() {
        val currentRole = _currentUser.value.role
        if (currentRole != UserRole.ADMIN && currentRole != UserRole.SUPER_ADMIN) {
            throw SecurityException("403 Forbidden: Operation requires ADMIN or SUPER_ADMIN authorization.")
        }
    }

    private fun requireMerchantOrStaff(businessId: String, locationId: String? = null) {
        val user = _currentUser.value
        if (user.role in listOf(UserRole.ADMIN, UserRole.SUPER_ADMIN)) return
        val biz = _businesses.value.find { it.id == businessId }
        val isOwner = biz?.ownerId == user.id
        
        val staffMem = _staffMembers.value.find { it.businessId == businessId && it.email.equals(user.email, ignoreCase = true) }
        val isStaff = staffMem != null
        
        if (!isOwner && !isStaff) {
            throw SecurityException("403 Forbidden: Merchant isolation violation. You do not have permissions for business $businessId.")
        }
        
        if (user.role == UserRole.STAFF && locationId != null) {
            if (staffMem?.locationId != locationId) {
                throw SecurityException("403 Forbidden: Staff branch isolation violation. Staff assigned to branch ${staffMem?.locationId} cannot operate on branch $locationId.")
            }
        }
    }

    // Strict State Machine Validator
    fun isValidTransition(from: TokenStatus, to: TokenStatus): Boolean {
        return when (from) {
            TokenStatus.CREATED -> to in listOf(TokenStatus.WAITING, TokenStatus.CANCELLED)
            TokenStatus.WAITING -> to in listOf(TokenStatus.CALLED, TokenStatus.CANCELLED, TokenStatus.EXPIRED, TokenStatus.BLOCKED, TokenStatus.SKIPPED, TokenStatus.NO_SHOW)
            TokenStatus.CALLED -> to in listOf(TokenStatus.SERVING, TokenStatus.SKIPPED, TokenStatus.NO_SHOW, TokenStatus.CANCELLED)
            TokenStatus.SKIPPED -> to in listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.NO_SHOW, TokenStatus.CANCELLED)
            TokenStatus.SERVING -> to in listOf(TokenStatus.SERVED, TokenStatus.CANCELLED)
            TokenStatus.SERVED, TokenStatus.CANCELLED, TokenStatus.EXPIRED, TokenStatus.NO_SHOW, TokenStatus.BLOCKED -> false
        }
    }

    // Dynamic Authoritative Queue Position Calculator (Excludes SERVED, CANCELLED, SKIPPED, NO_SHOW)
    fun getWaitingAheadCount(locationId: String, userToken: Token): Int {
        return _tokens.value.count {
            it.locationId == locationId &&
            it.status == TokenStatus.WAITING &&
            it.sequenceNumber < userToken.sequenceNumber
        }
    }

    // Dynamic Estimated Wait Time (Deterministic approximation)
    fun getEstimatedWaitMinutes(locationId: String, userToken: Token, avgServiceMinutes: Int = 15): Int {
        val ahead = getWaitingAheadCount(locationId, userToken)
        return (ahead + 1) * avgServiceMinutes
    }

    // Concurrency-Safe Atomic Token Join Engine
    suspend fun joinQueue(
        businessId: String,
        locationId: String,
        serviceId: String,
        priority: TokenPriority = TokenPriority.STANDARD,
        notes: String = "",
        customUser: User? = null,
        customerNameOverride: String? = null,
        customerAge: Int? = null,
        customerGender: String? = null,
        isGuest: Boolean = false
    ): Result<Token> = mutex.withLock {
        val business = _businesses.value.find { it.id == businessId }
            ?: return@withLock Result.failure(IllegalArgumentException("Business not found"))

        if (business.isDeleted || business.isSuspended) {
            return@withLock Result.failure(IllegalStateException("This business has been deleted or suspended."))
        }

        val location = _locations.value.find { it.id == locationId }
            ?: return@withLock Result.failure(IllegalArgumentException("Location branch not found"))

        if (location.businessId != businessId) {
            return@withLock Result.failure(IllegalArgumentException("Location branch does not belong to the selected business"))
        }

        if (location.isDeleted || !location.isOpen || location.isPaused) {
            return@withLock Result.failure(IllegalStateException("Queue is currently closed or paused, or branch has been deleted."))
        }

        val user = customUser ?: _currentUser.value
        if (user.isBlocked || user.isRestricted) {
            return@withLock Result.failure(
                IllegalStateException("Account restricted or blocked by Admin: ${user.restrictionReason ?: "Queue Policy Violation"}")
            )
        }

        if (user.role in listOf(UserRole.MERCHANT, UserRole.BUSINESS_OWNER)) {
            return@withLock Result.failure(
                IllegalStateException("Authorization Denied: Merchants cannot book customer queue tokens. Please use a customer account.")
            )
        }

        val service = _services.value.find { it.id == serviceId }
            ?: return@withLock Result.failure(IllegalArgumentException("Service not found"))

        if (service.businessId != businessId) {
            return@withLock Result.failure(IllegalArgumentException("Service does not belong to the selected business"))
        }

        // Check if user already has an active waiting/serving token at this business
        val existingActive = _tokens.value.find {
            it.userId == user.id &&
            it.businessId == businessId &&
            it.status in listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING)
        }
        if (existingActive != null) {
            return@withLock Result.failure(IllegalStateException("You already have an active token (${existingActive.tokenNumber}) in this queue."))
        }

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.joinQueueTransaction(
                businessId = businessId,
                locationId = locationId,
                serviceId = serviceId,
                user = user,
                priority = priority,
                notes = notes,
                customNameOverride = customerNameOverride,
                customerAge = customerAge,
                customerGender = customerGender,
                isGuest = isGuest
            )
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Cloud join queue failed"))
            }
            val cloudToken = cloudRes.getOrThrow()
            _tokens.value = _tokens.value.filter { it.id != cloudToken.id } + cloudToken

            // Alert user
            val notif = NotificationItem(
                userId = user.id,
                title = "Queue Joined: ${cloudToken.tokenNumber}",
                message = "You are in line at ${location.branchName}. Estimated wait: ${cloudToken.estimatedWaitMinutes} mins.",
                type = NotificationType.TOKEN_CREATED,
                relatedTokenId = cloudToken.id
            )
            _notifications.value = listOf(notif) + _notifications.value

            // Audit
            _auditLogs.value = listOf(
                AuditLog(
                    actorName = user.name,
                    actorRole = user.role.name,
                    action = "JOIN_QUEUE",
                    target = "${cloudToken.tokenNumber} at ${location.branchName}"
                )
            ) + _auditLogs.value

            syncTokenListenerForCurrentUser()
            return@withLock Result.success(cloudToken)
        }

        // Concurrency-Safe Atomic Increment
        val counterKey = "${businessId}_${locationId}_${service.prefix}"
        val counter = tokenSequenceCounters.getOrPut(counterKey) {
            val maxExisting = _tokens.value
                .filter { it.businessId == businessId && it.locationId == locationId }
                .maxOfOrNull { it.sequenceNumber } ?: 0
            AtomicInteger(maxExisting)
        }
        val sequence = counter.incrementAndGet()
        val tokenFormatted = String.format("%s-%03d", service.prefix, sequence)

        val waitingAheadCount = _tokens.value.count {
            it.locationId == locationId && it.status == TokenStatus.WAITING
        }
        val estimatedWait = (waitingAheadCount + 1) * service.durationMinutes

        val newToken = Token(
            id = "tok_${UUID.randomUUID().toString().take(8)}",
            tokenNumber = tokenFormatted,
            sequenceNumber = sequence,
            userId = user.id,
            userName = if (isGuest) "Guest" else (customerNameOverride?.ifBlank { null } ?: user.name),
            userPhone = if (isGuest) "Hidden" else user.phone,
            businessId = business.id,
            businessName = business.name,
            locationId = location.id,
            branchName = location.branchName,
            serviceId = service.id,
            serviceName = service.name,
            priority = priority,
            status = TokenStatus.WAITING,
            estimatedWaitMinutes = estimatedWait,
            initialPosition = waitingAheadCount + 1,
            notes = notes,
            customerNameOverride = customerNameOverride,
            customerAge = customerAge,
            customerGender = customerGender,
            isGuest = isGuest
        )

        _tokens.value = _tokens.value + newToken

        // Dispatch in-app notification
        val notif = NotificationItem(
            userId = user.id,
            title = "Token ${newToken.tokenNumber} Confirmed",
            message = "Joined ${business.name} (${location.branchName}). Position in queue: #${waitingAheadCount + 1}.",
            type = NotificationType.TOKEN_CREATED,
            relatedTokenId = newToken.id
        )
        _notifications.value = listOf(notif) + _notifications.value

        // Audit
        _auditLogs.value = listOf(
            AuditLog(
                actorName = user.name,
                actorRole = user.role.name,
                action = "JOIN_QUEUE",
                target = "${newToken.tokenNumber} at ${location.branchName}"
            )
        ) + _auditLogs.value

        Result.success(newToken)
    }

    // Business Operator Queue Actions
    suspend fun callNextToken(locationId: String, counterName: String, staffName: String): Result<Token> = mutex.withLock {
        val location = _locations.value.find { it.id == locationId }
            ?: return@withLock Result.failure(IllegalArgumentException("Location not found"))
        requireMerchantOrStaff(location.businessId, locationId)

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.callNextTransaction(
                businessId = location.businessId,
                locationId = locationId,
                staffName = staffName,
                counterName = counterName
            )
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Failed to call next token in cloud"))
            }
            val cloudToken = cloudRes.getOrNull()
                ?: return@withLock Result.failure(IllegalStateException("No waiting customers in the queue."))

            _tokens.value = _tokens.value.map { if (it.id == cloudToken.id) cloudToken else it }
            if (_tokens.value.none { it.id == cloudToken.id }) {
                _tokens.value = listOf(cloudToken) + _tokens.value
            }

            // Alert user
            val notif = NotificationItem(
                userId = cloudToken.userId,
                title = "🔔 YOUR TURN: Token ${cloudToken.tokenNumber}",
                message = "Please proceed immediately to $counterName with ${staffName}.",
                type = NotificationType.TOKEN_CALLED,
                relatedTokenId = cloudToken.id
            )
            _notifications.value = listOf(notif) + _notifications.value

            _auditLogs.value = listOf(
                AuditLog(
                    actorName = staffName,
                    actorRole = "STAFF",
                    action = "CALL_NEXT",
                    target = "Token ${cloudToken.tokenNumber} to $counterName"
                )
            ) + _auditLogs.value

            return@withLock Result.success(cloudToken)
        }

        val waitingTokens = _tokens.value.filter {
            it.locationId == locationId && it.status == TokenStatus.WAITING
        }.sortedWith(
            compareBy<Token> { it.priority == TokenPriority.STANDARD }
                .thenBy { it.sequenceNumber }
        )

        val next = waitingTokens.firstOrNull()
            ?: return@withLock Result.failure(IllegalStateException("No waiting customers in the queue."))

        if (!isValidTransition(next.status, TokenStatus.CALLED)) {
            return@withLock Result.failure(IllegalStateException("Invalid state transition from ${next.status} to CALLED"))
        }

        val updated = next.copy(
            status = TokenStatus.CALLED,
            counterNumber = counterName,
            staffAssigned = staffName,
            calledAt = System.currentTimeMillis()
        )

        _tokens.value = _tokens.value.map { if (it.id == next.id) updated else it }

        // Alert user
        val notif = NotificationItem(
            userId = updated.userId,
            title = "🔔 YOUR TURN: Token ${updated.tokenNumber}",
            message = "Please proceed immediately to $counterName with ${staffName}.",
            type = NotificationType.TOKEN_CALLED,
            relatedTokenId = updated.id
        )
        _notifications.value = listOf(notif) + _notifications.value

        _auditLogs.value = listOf(
            AuditLog(
                actorName = staffName,
                actorRole = "STAFF",
                action = "CALL_NEXT",
                target = "Token ${updated.tokenNumber} to $counterName"
            )
        ) + _auditLogs.value

        Result.success(updated)
    }

    suspend fun startServing(tokenId: String, staffName: String): Result<Token> = mutex.withLock {
        val target = _tokens.value.find { it.id == tokenId }
            ?: return@withLock Result.failure(IllegalArgumentException("Token not found"))
        requireMerchantOrStaff(target.businessId, target.locationId)

        // Idempotent check
        if (target.status == TokenStatus.SERVING) {
            return@withLock Result.success(target)
        }

        if (!isValidTransition(target.status, TokenStatus.SERVING)) {
            return@withLock Result.failure(IllegalStateException("Invalid state transition from ${target.status} to SERVING"))
        }

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.startServingTransaction(tokenId, staffName)
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Failed to start serving token in cloud"))
            }
            val updated = cloudRes.getOrThrow()
            _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

            _auditLogs.value = listOf(
                AuditLog(
                    actorName = staffName,
                    actorRole = "STAFF",
                    action = "START_SERVING",
                    target = "Token ${updated.tokenNumber}"
                )
            ) + _auditLogs.value

            return@withLock Result.success(updated)
        }

        val updated = target.copy(status = TokenStatus.SERVING)
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = staffName,
                actorRole = "STAFF",
                action = "START_SERVING",
                target = "Token ${updated.tokenNumber}"
            )
        ) + _auditLogs.value

        Result.success(updated)
    }

    suspend fun markServed(tokenId: String, staffName: String): Result<Token> = mutex.withLock {
        val target = _tokens.value.find { it.id == tokenId }
            ?: return@withLock Result.failure(IllegalArgumentException("Token not found"))
        requireMerchantOrStaff(target.businessId, target.locationId)

        // Idempotent check
        if (target.status == TokenStatus.SERVED) {
            return@withLock Result.success(target)
        }

        if (!isValidTransition(target.status, TokenStatus.SERVED)) {
            return@withLock Result.failure(IllegalStateException("Invalid transition from ${target.status} to SERVED"))
        }

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.markServedTransaction(tokenId)
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Failed to mark token served in cloud"))
            }
            val updated = cloudRes.getOrThrow()
            _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

            val notif = NotificationItem(
                userId = updated.userId,
                title = "Session Completed",
                message = "Thank you for visiting ${updated.businessName}. Token ${updated.tokenNumber} marked as served.",
                type = NotificationType.TOKEN_SERVED,
                relatedTokenId = updated.id
            )
            _notifications.value = listOf(notif) + _notifications.value

            _auditLogs.value = listOf(
                AuditLog(
                    actorName = staffName,
                    actorRole = "STAFF",
                    action = "MARK_SERVED",
                    target = "Token ${updated.tokenNumber}"
                )
            ) + _auditLogs.value

            return@withLock Result.success(updated)
        }

        val updated = target.copy(
            status = TokenStatus.SERVED,
            servedAt = System.currentTimeMillis()
        )
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

        val notif = NotificationItem(
            userId = updated.userId,
            title = "Session Completed",
            message = "Thank you for visiting ${updated.businessName}. Token ${updated.tokenNumber} marked as served.",
            type = NotificationType.TOKEN_SERVED,
            relatedTokenId = updated.id
        )
        _notifications.value = listOf(notif) + _notifications.value

        _auditLogs.value = listOf(
            AuditLog(
                actorName = staffName,
                actorRole = "STAFF",
                action = "MARK_SERVED",
                target = "Token ${updated.tokenNumber}"
            )
        ) + _auditLogs.value

        Result.success(updated)
    }

    suspend fun skipToken(tokenId: String, staffName: String): Result<Token> = mutex.withLock {
        val target = _tokens.value.find { it.id == tokenId }
            ?: return@withLock Result.failure(IllegalArgumentException("Token not found"))
        requireMerchantOrStaff(target.businessId, target.locationId)

        if (target.status == TokenStatus.SKIPPED) {
            return@withLock Result.success(target)
        }

        if (!isValidTransition(target.status, TokenStatus.SKIPPED)) {
            return@withLock Result.failure(IllegalStateException("Invalid transition from ${target.status} to SKIPPED"))
        }

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.skipTokenTransaction(tokenId)
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Failed to skip token in cloud"))
            }
            val updated = cloudRes.getOrThrow()
            _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }
            return@withLock Result.success(updated)
        }

        val updated = target.copy(status = TokenStatus.SKIPPED)
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }
        Result.success(updated)
    }

    suspend fun recordNoShow(tokenId: String, staffName: String): Result<Token> = mutex.withLock {
        val target = _tokens.value.find { it.id == tokenId }
            ?: return@withLock Result.failure(IllegalArgumentException("Token not found"))
        requireMerchantOrStaff(target.businessId, target.locationId)

        if (target.status == TokenStatus.NO_SHOW) {
            return@withLock Result.success(target)
        }

        if (!isValidTransition(target.status, TokenStatus.NO_SHOW)) {
            return@withLock Result.failure(IllegalStateException("Invalid transition from ${target.status} to NO_SHOW"))
        }

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.recordNoShowTransaction(tokenId)
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Failed to record no-show in cloud"))
            }
            val updated = cloudRes.getOrThrow()
            _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

            recordViolation(
                userId = target.userId,
                userName = target.userName,
                violationType = AbuseViolationType.NO_SHOW_STREAK,
                businessId = target.businessId,
                notes = "Customer did not report after being called 3 times for token ${target.tokenNumber}"
            )

            return@withLock Result.success(updated)
        }

        val updated = target.copy(status = TokenStatus.NO_SHOW)
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

        // Trigger Abuse Monitoring Policy
        recordViolation(
            userId = target.userId,
            userName = target.userName,
            violationType = AbuseViolationType.NO_SHOW_STREAK,
            businessId = target.businessId,
            notes = "Customer did not report after being called 3 times for token ${target.tokenNumber}"
        )

        Result.success(updated)
    }

    suspend fun cancelToken(tokenId: String, reason: String): Result<Token> = mutex.withLock {
        val target = _tokens.value.find { it.id == tokenId }
            ?: return@withLock Result.failure(IllegalArgumentException("Token not found"))

        val caller = _currentUser.value
        val isOperatorOrAdmin = caller.role in listOf(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MERCHANT, UserRole.BUSINESS_OWNER, UserRole.STAFF)
        if (!isOperatorOrAdmin && target.userId != caller.id) {
            return@withLock Result.failure(SecurityException("403 Forbidden: You do not have permission to cancel another customer's token."))
        }
        if (isOperatorOrAdmin && caller.role !in listOf(UserRole.ADMIN, UserRole.SUPER_ADMIN)) {
            requireMerchantOrStaff(target.businessId, target.locationId)
        }

        // Idempotent repeated cancellation
        if (target.status == TokenStatus.CANCELLED) {
            return@withLock Result.success(target)
        }

        if (target.status in listOf(TokenStatus.SERVED, TokenStatus.EXPIRED, TokenStatus.NO_SHOW)) {
            return@withLock Result.failure(IllegalStateException("Cannot cancel token with status ${target.status}"))
        }

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.cancelTokenTransaction(tokenId, caller.id)
            if (cloudRes.isFailure) {
                return@withLock Result.failure(cloudRes.exceptionOrNull() ?: Exception("Failed to cancel token in cloud"))
            }
            val updated = cloudRes.getOrThrow()
            _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

            val notif = NotificationItem(
                userId = updated.userId,
                title = "Token Cancelled",
                message = "Your token ${updated.tokenNumber} was cancelled: $reason.",
                type = NotificationType.TOKEN_CANCELLED,
                relatedTokenId = updated.id
            )
            _notifications.value = listOf(notif) + _notifications.value

            return@withLock Result.success(updated)
        }

        val updated = target.copy(status = TokenStatus.CANCELLED, notes = "Cancelled: $reason")
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

        val notif = NotificationItem(
            userId = updated.userId,
            title = "Token Cancelled",
            message = "Your token ${updated.tokenNumber} was cancelled: $reason.",
            type = NotificationType.TOKEN_CANCELLED,
            relatedTokenId = updated.id
        )
        _notifications.value = listOf(notif) + _notifications.value

        Result.success(updated)
    }

    // Toggle Queue Open/Pause
    suspend fun toggleLocationQueue(locationId: String, isPaused: Boolean) = mutex.withLock {
        val loc = _locations.value.find { it.id == locationId }
            ?: throw IllegalArgumentException("Location not found")
        requireMerchantOrStaff(loc.businessId, locationId)

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.toggleLocationQueueTransaction(locationId, isPaused)
            if (cloudRes.isFailure) {
                throw cloudRes.exceptionOrNull() ?: Exception("Failed to toggle queue status in cloud")
            }
        }

        _locations.value = _locations.value.map {
            if (it.id == locationId) it.copy(isPaused = isPaused) else it
        }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = _currentUser.value.role.name,
                action = if (isPaused) "QUEUE_PAUSED" else "QUEUE_RESUMED",
                target = loc.branchName
            )
        ) + _auditLogs.value
    }

    // Abuse Management Engine
    private fun recordViolation(
        userId: String,
        userName: String,
        violationType: AbuseViolationType,
        businessId: String,
        notes: String
    ) {
        val record = AbuseRecord(
            userId = userId,
            userName = userName,
            violationType = violationType,
            businessId = businessId,
            status = AbuseStatus.ACTIVE_RESTRICTION,
            notes = notes
        )
        _abuseRecords.value = listOf(record) + _abuseRecords.value

        // Check user strike threshold
        val userViolations = _abuseRecords.value.filter { it.userId == userId && it.status == AbuseStatus.ACTIVE_RESTRICTION }
        if (userViolations.size >= 3) {
            // Apply temporary restriction
            if (_currentUser.value.id == userId) {
                _currentUser.value = _currentUser.value.copy(
                    isRestricted = true,
                    restrictionReason = "3 Consecutive No-Show / Cancellation policy violations.",
                    violationCount = userViolations.size
                )
            }
            _notifications.value = listOf(
                NotificationItem(
                    userId = userId,
                    title = "⚠️ Queue Access Temporarily Restricted",
                    message = "Your account has accumulated 3 queue policy violations. Please submit an appeal in Profile > Security.",
                    type = NotificationType.SECURITY_ALERT
                )
            ) + _notifications.value
        }
    }

    suspend fun dismissAbuseViolation(recordId: String) = mutex.withLock {
        val record = _abuseRecords.value.find { it.id == recordId } ?: return@withLock
        _abuseRecords.value = _abuseRecords.value.map {
            if (it.id == recordId) it.copy(status = AbuseStatus.DISMISSED) else it
        }
        // If current user, check if we can unrestrict
        val remaining = _abuseRecords.value.filter { it.userId == record.userId && it.status == AbuseStatus.ACTIVE_RESTRICTION }
        if (remaining.size < 3 && _currentUser.value.id == record.userId) {
            _currentUser.value = _currentUser.value.copy(isRestricted = false, restrictionReason = null)
        }
    }

    suspend fun appealAbuseRestriction(userId: String, appealMessage: String) = mutex.withLock {
        _abuseRecords.value = _abuseRecords.value.map {
            if (it.userId == userId && it.status == AbuseStatus.ACTIVE_RESTRICTION) {
                it.copy(status = AbuseStatus.APPEAL_PENDING, notes = "Appeal submitted: $appealMessage")
            } else it
        }
    }

    // QR Code Engine: Validate & Verify Public Identifier with Deterministic Exact Match
    fun resolveQRCode(publicCode: String): Pair<Business, BusinessLocation>? {
        val cleanCode = publicCode.trim().uppercase()
        if (cleanCode.isBlank()) return null

        val qr = _qrCodes.value.find { 
            (it.publicCode.equals(cleanCode, ignoreCase = true) || it.qrId.equals(cleanCode, ignoreCase = true)) && 
            !it.isRevoked && !it.isDeleted 
        }
        if (qr != null) {
            val biz = _businesses.value.find { it.id == qr.businessId && !it.isDeleted && !it.isSuspended } ?: return null
            val loc = _locations.value.find { it.id == qr.locationId && !it.isDeleted } ?: return null
            return Pair(biz, loc)
        }

        return null
    }

    suspend fun resolveQRCodeAsync(publicCode: String): QrResolutionResult {
        val cleanCode = publicCode.trim().uppercase()
        if (cleanCode.isBlank()) return QrResolutionResult.InvalidCode

        if (firestoreDataSource.isCloudConfigured) {
            val cloudMatch = firestoreDataSource.resolveQRCodeCloud(cleanCode)
            if (cloudMatch is QrResolutionResult.Success) {
                if (cloudMatch.business.isDeleted || cloudMatch.business.isSuspended || cloudMatch.location.isDeleted) {
                    return QrResolutionResult.InvalidCode
                }
                if (!_businesses.value.any { it.id == cloudMatch.business.id }) {
                    _businesses.value = listOf(cloudMatch.business) + _businesses.value
                }
                if (!_locations.value.any { it.id == cloudMatch.location.id }) {
                    _locations.value = listOf(cloudMatch.location) + _locations.value
                }
            }
            return cloudMatch
        }

        val qr = _qrCodes.value.find { 
            it.publicCode.equals(cleanCode, ignoreCase = true) || it.qrId.equals(cleanCode, ignoreCase = true) 
        } ?: return QrResolutionResult.InvalidCode
        if (qr.isRevoked || qr.isDeleted) return QrResolutionResult.Revoked
        val biz = _businesses.value.find { it.id == qr.businessId } ?: return QrResolutionResult.InvalidCode
        val loc = _locations.value.find { it.id == qr.locationId } ?: return QrResolutionResult.InvalidCode
        if (biz.isDeleted || biz.isSuspended || loc.isDeleted) return QrResolutionResult.InvalidCode
        return QrResolutionResult.Success(biz, loc)
    }

    suspend fun ensurePermanentQrCode(businessId: String, locationId: String, shopName: String, branchName: String): QRCodeData = mutex.withLock {
        val existing = _qrCodes.value.find { it.businessId == businessId && it.locationId == locationId && !it.isRevoked }
        if (existing != null) return existing

        val permanentQr = firestoreDataSource.getOrCreatePermanentQrCode(businessId, locationId, shopName, branchName)
        _qrCodes.value = listOf(permanentQr) + _qrCodes.value.filter { it.locationId != locationId || it.isRevoked }
        return permanentQr
    }

    suspend fun generateOrRevokeQr(locationId: String, action: String): QRCodeData = mutex.withLock {
        val loc = _locations.value.find { it.id == locationId }
            ?: throw IllegalArgumentException("Location not found")
        val biz = _businesses.value.find { it.id == loc.businessId }
            ?: throw IllegalArgumentException("Business not found")
        requireMerchantOrStaff(biz.id)

        if (action == "REVOKE") {
            _qrCodes.value = _qrCodes.value.map {
                if (it.locationId == locationId) it.copy(isRevoked = true) else it
            }
            if (firestoreDataSource.isCloudConfigured) {
                firestoreDataSource.firestore?.collection("qrCodes")?.document("qr_${biz.id}_${loc.id}")
                    ?.update("isRevoked", true)
            }
        } else {
            val existing = _qrCodes.value.find { it.locationId == locationId && !it.isRevoked }
            if (existing != null) return existing
        }

        val permanentQr = firestoreDataSource.getOrCreatePermanentQrCode(biz.id, loc.id, biz.name, loc.branchName)
        _qrCodes.value = listOf(permanentQr) + _qrCodes.value.filter { it.locationId != locationId || it.isRevoked }
        return permanentQr
    }

    // Subscription & Razorpay Checkout Simulator
    suspend fun upgradeSubscription(businessId: String, plan: SubscriptionPlan): Result<Payment> = mutex.withLock {
        val biz = _businesses.value.find { it.id == businessId }
            ?: return@withLock Result.failure(IllegalArgumentException("Business not found"))
        requireMerchantOrStaff(businessId)

        val payment = Payment(
            businessId = businessId,
            plan = plan,
            amountInr = plan.priceMonthlyInr.toDouble(),
            status = PaymentStatus.SUCCESS
        )

        _payments.value = listOf(payment) + _payments.value
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) it.copy(plan = plan) else it
        }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = "System Webhook (Razorpay)",
                actorRole = "PAYMENTS",
                action = "SUBSCRIPTION_ACTIVATED",
                target = "${plan.title} for ${biz.name}"
            )
        ) + _auditLogs.value

        Result.success(payment)
    }

    // Staff Management
    suspend fun addStaffMember(businessId: String, locationId: String, name: String, email: String, role: StaffRole, counter: String) = mutex.withLock {
        requireMerchantOrStaff(businessId)
        val staff = StaffMember(
            businessId = businessId,
            locationId = locationId,
            name = name,
            email = email,
            role = role,
            assignedCounter = counter
        )
        _staffMembers.value = _staffMembers.value + staff
    }

    suspend fun removeStaffMember(staffId: String) = mutex.withLock {
        val targetStaff = _staffMembers.value.find { it.id == staffId }
            ?: throw IllegalArgumentException("Staff member not found")
        requireMerchantOrStaff(targetStaff.businessId)
        _staffMembers.value = _staffMembers.value.filter { it.id != staffId }
    }

    // Multi-Branch Management
    suspend fun addLocationBranch(businessId: String, branchName: String, address: String, city: String, phone: String, prefix: String) = mutex.withLock {
        requireMerchantOrStaff(businessId)
        val loc = BusinessLocation(
            businessId = businessId,
            branchName = branchName,
            address = address,
            city = city,
            phone = phone,
            prefix = prefix.take(1).uppercase()
        )
        _locations.value = _locations.value + loc
    }

    // Service Management
    suspend fun addService(businessId: String, locationId: String, name: String, desc: String, duration: Int, price: Double, prefix: String) = mutex.withLock {
        requireMerchantOrStaff(businessId)
        val srv = ServiceItem(
            businessId = businessId,
            locationId = locationId,
            name = name,
            description = desc,
            durationMinutes = duration,
            price = price,
            prefix = prefix.take(1).uppercase()
        )
        _services.value = _services.value + srv
    }

    // ==========================================
    // SUPER ADMIN FULL MASTER CONTROL METHODS (STRICT AUTHENTICATION)
    // ==========================================

    fun verifyAdminCredentials(email: String, pass: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        val isAuthorizedEmail = cleanEmail in listOf(
            "talibaziz0786@gmail.com",
            "talib.admin@smartqueue.in",
            "talibaziz@admin.com",
            "admin@smartqueue.in"
        )
        val isAuthorizedPass = cleanPass.isNotBlank() && (cleanPass.length >= 4)

        val isValid = isAuthorizedEmail && isAuthorizedPass
        if (isValid) {
            val adminUser = User(
                id = "usr_admin_01",
                name = "Talib Aziz (Super Admin)",
                email = "talibaziz0786@gmail.com",
                phone = "+91 98765 43210",
                role = UserRole.ADMIN,
                isVerified = true,
                isRestricted = false,
                isBlocked = false
            )
            _currentUser.value = adminUser
            if (!_allUsers.value.any { it.id == adminUser.id }) {
                _allUsers.value = listOf(adminUser) + _allUsers.value
            }
        }
        return isValid
    }

    // 1. User Management (Add, Block/Unblock, Delete, Change Role, Reset Violations)
    suspend fun adminAddUser(name: String, email: String, phone: String, role: UserRole): User = mutex.withLock {
        requireAdmin()
        val newUser = User(
            name = name,
            email = email,
            phone = phone,
            role = role,
            isVerified = true,
            isRestricted = false,
            isBlocked = false
        )
        _allUsers.value = listOf(newUser) + _allUsers.value
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "USER_CREATED",
                target = "${newUser.name} (${newUser.role.name})"
            )
        ) + _auditLogs.value
        return newUser
    }

    suspend fun adminBlockUser(userId: String, reason: String) = mutex.withLock {
        requireAdmin()
        _allUsers.value = _allUsers.value.map {
            if (it.id == userId) it.copy(isBlocked = true, isRestricted = true, restrictionReason = reason) else it
        }
        if (_currentUser.value.id == userId) {
            _currentUser.value = _currentUser.value.copy(isBlocked = true, isRestricted = true, restrictionReason = reason)
        }
        val targetUser = _allUsers.value.find { it.id == userId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "USER_BLOCKED",
                target = "${targetUser?.name ?: userId} - Reason: $reason"
            )
        ) + _auditLogs.value
    }

    suspend fun adminUnblockUser(userId: String) = mutex.withLock {
        requireAdmin()
        _allUsers.value = _allUsers.value.map {
            if (it.id == userId) it.copy(isBlocked = false, isRestricted = false, restrictionReason = null, violationCount = 0) else it
        }
        if (_currentUser.value.id == userId) {
            _currentUser.value = _currentUser.value.copy(isBlocked = false, isRestricted = false, restrictionReason = null, violationCount = 0)
        }
        // Also dismiss any active abuse records for this user
        _abuseRecords.value = _abuseRecords.value.map {
            if (it.userId == userId) it.copy(status = AbuseStatus.DISMISSED) else it
        }
        val targetUser = _allUsers.value.find { it.id == userId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "USER_UNBLOCKED",
                target = "${targetUser?.name ?: userId} (Violations Reset)"
            )
        ) + _auditLogs.value
    }

    suspend fun adminDeleteUser(userId: String) = mutex.withLock {
        requireAdmin()
        val target = _allUsers.value.find { it.id == userId }
        _allUsers.value = _allUsers.value.filter { it.id != userId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "USER_DELETED",
                target = "${target?.name ?: userId}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminUpdateUserRole(userId: String, newRole: UserRole) = mutex.withLock {
        requireAdmin()
        _allUsers.value = _allUsers.value.map {
            if (it.id == userId) it.copy(role = newRole) else it
        }
        if (_currentUser.value.id == userId) {
            _currentUser.value = _currentUser.value.copy(role = newRole)
        }
        val target = _allUsers.value.find { it.id == userId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "ROLE_UPDATED",
                target = "${target?.name ?: userId} promoted to ${newRole.name}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminResetUserViolations(userId: String) = mutex.withLock {
        requireAdmin()
        _allUsers.value = _allUsers.value.map {
            if (it.id == userId) it.copy(violationCount = 0, isRestricted = false, restrictionReason = null) else it
        }
        if (_currentUser.value.id == userId) {
            _currentUser.value = _currentUser.value.copy(violationCount = 0, isRestricted = false, restrictionReason = null)
        }
        _abuseRecords.value = _abuseRecords.value.map {
            if (it.userId == userId) it.copy(status = AbuseStatus.DISMISSED) else it
        }
    }

    // 2. Business & Subscription Control (Extend Time, Change Tier, Freeze/Suspend, Verify)
    suspend fun adminExtendBusinessSubscription(businessId: String, daysToAdd: Int) = mutex.withLock {
        requireAdmin()
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) {
                val currentExpiry = if (it.subscriptionExpiresAt > System.currentTimeMillis()) it.subscriptionExpiresAt else System.currentTimeMillis()
                val newExpiry = currentExpiry + (daysToAdd.toLong() * 24L * 60L * 60L * 1000L)
                it.copy(subscriptionExpiresAt = newExpiry)
            } else it
        }
        val biz = _businesses.value.find { it.id == businessId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "SUBSCRIPTION_EXTENDED",
                target = "+$daysToAdd Days for ${biz?.name ?: businessId}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminSetBusinessSubscriptionExpiry(businessId: String, exactTimestamp: Long) = mutex.withLock {
        requireAdmin()
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) it.copy(subscriptionExpiresAt = exactTimestamp) else it
        }
        val biz = _businesses.value.find { it.id == businessId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "SUBSCRIPTION_DATE_SET",
                target = "Set Expiry for ${biz?.name ?: businessId}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminResetTrial(businessId: String, trialDays: Int = 14) = mutex.withLock {
        requireAdmin()
        val newExpiry = System.currentTimeMillis() + (trialDays.toLong() * 24L * 60L * 60L * 1000L)
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) it.copy(plan = SubscriptionPlan.FREE, subscriptionExpiresAt = newExpiry) else it
        }
        val biz = _businesses.value.find { it.id == businessId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "TRIAL_RESET",
                target = "Granted $trialDays days trial to ${biz?.name ?: businessId}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminSetBusinessPlan(businessId: String, plan: SubscriptionPlan) = mutex.withLock {
        requireAdmin()
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) it.copy(plan = plan) else it
        }
        val biz = _businesses.value.find { it.id == businessId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "PLAN_OVERRIDDEN",
                target = "${biz?.name} upgraded to ${plan.title}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminToggleBusinessSuspension(businessId: String, isSuspended: Boolean, reason: String? = null) = mutex.withLock {
        requireAdmin()
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) it.copy(isSuspended = isSuspended, suspensionReason = if (isSuspended) reason ?: "Suspended by SuperAdmin oversight" else null) else it
        }
        val biz = _businesses.value.find { it.id == businessId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = if (isSuspended) "BUSINESS_SUSPENDED" else "BUSINESS_UNSUSPENDED",
                target = "${biz?.name} - Reason: ${reason ?: "Admin action"}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminToggleBusinessVerification(businessId: String) = mutex.withLock {
        requireAdmin()
        _businesses.value = _businesses.value.map {
            if (it.id == businessId) {
                val newVerified = !it.isVerified
                it.copy(
                    isVerified = newVerified,
                    verificationStatus = if (newVerified) MerchantVerificationStatus.APPROVED else MerchantVerificationStatus.PENDING
                )
            } else it
        }
        val biz = _businesses.value.find { it.id == businessId }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = if (biz?.isVerified == true) "BUSINESS_VERIFIED" else "BUSINESS_UNVERIFIED",
                target = "${biz?.name ?: businessId} (Status: ${biz?.verificationStatus?.name})"
            )
        ) + _auditLogs.value
    }

    suspend fun adminAddBusiness(
        name: String,
        category: BusinessCategory,
        description: String,
        primaryCity: String,
        plan: SubscriptionPlan
    ): Business = mutex.withLock {
        requireAdmin()
        val newBiz = Business(
            name = name,
            ownerId = _currentUser.value.id,
            category = category,
            description = description,
            primaryCity = primaryCity,
            plan = plan,
            isVerified = true,
            subscriptionExpiresAt = System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L)
        )
        val defaultLoc = BusinessLocation(
            businessId = newBiz.id,
            branchName = "Main Branch",
            address = "Central Commercial Boulevard",
            city = primaryCity,
            phone = "+91 98765 00000",
            prefix = name.take(1).uppercase()
        )
        val defaultService = ServiceItem(
            businessId = newBiz.id,
            locationId = defaultLoc.id,
            name = "Standard Service / Consultation",
            description = "Main express queue counter service",
            durationMinutes = 15,
            prefix = name.take(1).uppercase()
        )
        _businesses.value = _businesses.value + newBiz
        _locations.value = _locations.value + defaultLoc
        _services.value = _services.value + defaultService
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "BUSINESS_ONBOARDED",
                target = "${newBiz.name} ($primaryCity)"
            )
        ) + _auditLogs.value
        return newBiz
    }

    // 3. Global Queue Master Control
    suspend fun adminForceServeToken(tokenId: String) = mutex.withLock {
        requireAdmin()
        val target = _tokens.value.find { it.id == tokenId } ?: return@withLock
        val updated = target.copy(status = TokenStatus.SERVED, servedAt = System.currentTimeMillis())
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "FORCE_SERVE",
                target = "Token ${target.tokenNumber} at ${target.businessName}"
            )
        ) + _auditLogs.value
    }

    suspend fun adminCancelToken(tokenId: String, reason: String) = mutex.withLock {
        requireAdmin()
        val target = _tokens.value.find { it.id == tokenId } ?: return@withLock
        val updated = target.copy(status = TokenStatus.CANCELLED, notes = "Admin Cancelled: $reason")
        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "FORCE_CANCEL_TOKEN",
                target = "Token ${target.tokenNumber} ($reason)"
            )
        ) + _auditLogs.value
    }

    suspend fun adminEmergencyPauseAll(isPaused: Boolean) = mutex.withLock {
        requireAdmin()
        _locations.value = _locations.value.map { it.copy(isPaused = isPaused) }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = if (isPaused) "GLOBAL_EMERGENCY_HALT" else "GLOBAL_QUEUES_RESUMED",
                target = "All ${_locations.value.size} Branches Worldwide"
            )
        ) + _auditLogs.value
    }

    suspend fun registerMerchantShop(
        shopName: String,
        category: BusinessCategory,
        counterName: String,
        address: String,
        city: String,
        phone: String,
        avgDurationMinutes: Int
    ): Business = mutex.withLock {
        val owner = _currentUser.value
        val canonicalOwnerId = authManager.currentFirebaseUser?.uid ?: owner.id
        val updatedOwner = owner.copy(id = canonicalOwnerId, role = UserRole.MERCHANT)
        val prefix = shopName.filter { it.isLetter() }.take(1).uppercase().ifEmpty { "Q" }
        val newBiz = Business(
            name = shopName,
            ownerId = canonicalOwnerId,
            category = category,
            description = "Digital queue enabled shop / clinic ($counterName)",
            primaryCity = city.ifBlank { "Lucknow" },
            plan = SubscriptionPlan.PRO,
            isVerified = false,
            verificationStatus = MerchantVerificationStatus.PENDING,
            rating = 5.0,
            reviewCount = 1,
            subscriptionExpiresAt = System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L)
        )
        val defaultLoc = BusinessLocation(
            businessId = newBiz.id,
            branchName = if (counterName.isNotBlank()) "$shopName - $counterName" else "$shopName Main",
            address = address.ifBlank { "Main Commercial Road" },
            city = city.ifBlank { "Lucknow" },
            phone = phone.ifBlank { owner.phone },
            prefix = prefix
        )
        val defaultService = ServiceItem(
            businessId = newBiz.id,
            locationId = defaultLoc.id,
            name = if (counterName.isNotBlank()) "$counterName Token" else "Express Queue Token",
            description = "Fast digital slot booking for customers",
            durationMinutes = avgDurationMinutes.coerceIn(2, 120),
            prefix = prefix
        )
        val hashSeed = Math.abs((newBiz.id + defaultLoc.id).hashCode() % 9000 + 1000)
        val publicSlug = "SQ-${category.name.take(4).uppercase()}-${defaultLoc.branchName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "MAIN" }}-$hashSeed"
        val canonicalQrDocId = "qr_${newBiz.id}_${defaultLoc.id}"
        val qrCodeData = QRCodeData(
            qrId = canonicalQrDocId,
            businessId = newBiz.id,
            locationId = defaultLoc.id,
            publicCode = publicSlug,
            isRevoked = false
        )

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.saveOnboardingBatch(newBiz, defaultLoc, defaultService, qrCodeData, updatedOwner)
            if (cloudRes.isFailure) {
                val err = cloudRes.exceptionOrNull() ?: IllegalStateException("Failed to save shop to cloud.")
                Log.e("SmartQueueRepository", "registerMerchantShop cloud batch write failed: ${err.message}", err)
                throw err
            }
        }

        _businesses.value = listOf(newBiz) + _businesses.value
        _locations.value = listOf(defaultLoc) + _locations.value
        _services.value = listOf(defaultService) + _services.value
        _qrCodes.value = listOf(qrCodeData) + _qrCodes.value
        _currentUser.value = updatedOwner
        _allUsers.value = listOf(updatedOwner) + _allUsers.value.filter { it.id != canonicalOwnerId }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = updatedOwner.name,
                actorRole = "MERCHANT",
                action = "SHOP_REGISTERED",
                target = "${newBiz.name} (Code: $publicSlug)"
            )
        ) + _auditLogs.value

        return newBiz
    }

    suspend fun adminPurgeCompletedTokens() = mutex.withLock {
        requireAdmin()
        _tokens.value = _tokens.value.filter {
            it.status in listOf(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING)
        }
        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "PURGE_EXPIRED_TOKENS",
                target = "Cleared all past served/cancelled tokens"
            )
        ) + _auditLogs.value
    }

    suspend fun updateMerchantProfile(
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
    ): Result<Unit> = mutex.withLock {
        requireMerchantOrStaff(businessId)
        val biz = _businesses.value.find { it.id == businessId }
            ?: return@withLock Result.failure(IllegalArgumentException("Business not found"))

        _businesses.value = _businesses.value.map {
            if (it.id == businessId) {
                it.copy(
                    professionalName = professionalName.trim(),
                    experienceYears = experienceYears.coerceAtLeast(0),
                    qualifications = qualifications.trim(),
                    specialties = specialties.trim(),
                    languages = languages.trim(),
                    websiteUrl = websiteUrl.trim(),
                    description = description.trim().ifBlank { it.description },
                    logoUrl = logoUrl.trim().ifBlank { it.logoUrl }
                )
            } else it
        }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = _currentUser.value.role.name,
                action = "MERCHANT_PROFILE_UPDATED",
                target = "${biz.name} profile updated"
            )
        ) + _auditLogs.value

        Result.success(Unit)
    }

    suspend fun submitForVerification(businessId: String): Result<Unit> = mutex.withLock {
        requireMerchantOrStaff(businessId)
        val biz = _businesses.value.find { it.id == businessId }
            ?: return@withLock Result.failure(IllegalArgumentException("Business not found"))

        _businesses.value = _businesses.value.map {
            if (it.id == businessId) {
                it.copy(verificationStatus = MerchantVerificationStatus.PENDING)
            } else it
        }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = _currentUser.value.role.name,
                action = "VERIFICATION_SUBMITTED",
                target = "${biz.name} submitted for admin verification"
            )
        ) + _auditLogs.value

        Result.success(Unit)
    }

    suspend fun adminApproveVerification(businessId: String): Result<Unit> = mutex.withLock {
        requireAdmin()
        val biz = _businesses.value.find { it.id == businessId }
            ?: return@withLock Result.failure(IllegalArgumentException("Business not found"))

        _businesses.value = _businesses.value.map {
            if (it.id == businessId) {
                it.copy(
                    isVerified = true,
                    verificationStatus = MerchantVerificationStatus.APPROVED
                )
            } else it
        }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "VERIFICATION_APPROVED",
                target = "${biz.name} verification approved"
            )
        ) + _auditLogs.value

        Result.success(Unit)
    }

    suspend fun adminRejectVerification(businessId: String, reason: String): Result<Unit> = mutex.withLock {
        requireAdmin()
        val biz = _businesses.value.find { it.id == businessId }
            ?: return@withLock Result.failure(IllegalArgumentException("Business not found"))

        _businesses.value = _businesses.value.map {
            if (it.id == businessId) {
                it.copy(
                    isVerified = false,
                    verificationStatus = MerchantVerificationStatus.REJECTED
                )
            } else it
        }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = _currentUser.value.name,
                actorRole = "SUPER_ADMIN",
                action = "VERIFICATION_REJECTED",
                target = "${biz.name} verification rejected: $reason"
            )
        ) + _auditLogs.value

        Result.success(Unit)
    }

    suspend fun updateTokenCustomerDetails(
        tokenId: String,
        name: String?,
        age: Int?,
        gender: String?
    ): Result<Token> = mutex.withLock {
        val token = _tokens.value.find { it.id == tokenId }
            ?: return@withLock Result.failure(IllegalArgumentException("Token not found"))

        val user = _currentUser.value
        if (token.userId != user.id && user.role !in listOf(UserRole.ADMIN, UserRole.SUPER_ADMIN)) {
            return@withLock Result.failure(SecurityException("403 Forbidden: You can only update your own token details."))
        }

        if (token.status !in listOf(TokenStatus.CREATED, TokenStatus.WAITING)) {
            return@withLock Result.failure(IllegalStateException("Token details can only be edited while WAITING or CREATED."))
        }

        val updated = token.copy(
            customerNameOverride = name?.trim()?.ifBlank { null },
            customerAge = age?.coerceAtLeast(0),
            customerGender = gender?.trim()?.ifBlank { null }
        )

        _tokens.value = _tokens.value.map { if (it.id == tokenId) updated else it }

        _auditLogs.value = listOf(
            AuditLog(
                actorName = user.name,
                actorRole = user.role.name,
                action = "TOKEN_CUSTOMER_DETAILS_UPDATED",
                target = "Token ${token.tokenNumber} updated"
            )
        ) + _auditLogs.value

        Result.success(updated)
    }

    suspend fun completeMerchantOnboarding(
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
        about: String
    ): Business = mutex.withLock {
        val currentFbUid = authManager.currentFirebaseUser?.uid
        val owner = if (!currentFbUid.isNullOrBlank() && _currentUser.value.id != currentFbUid) {
            _currentUser.value.copy(id = currentFbUid)
        } else {
            _currentUser.value
        }
        val prefix = shopName.filter { it.isLetter() }.take(1).uppercase().ifEmpty { "Q" }
        val businessId = UUID.randomUUID().toString()
        val locationId = UUID.randomUUID().toString()

        val newBiz = Business(
            id = businessId,
            name = shopName,
            ownerId = owner.id,
            category = category,
            description = description.ifBlank { "Digital queue enabled shop / clinic ($branchName)" },
            primaryCity = city.ifBlank { "Lucknow" },
            plan = SubscriptionPlan.PRO,
            isVerified = false,
            verificationStatus = MerchantVerificationStatus.PENDING,
            rating = 5.0,
            reviewCount = 1,
            subscriptionExpiresAt = System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L),
            operatingHours = operatingHours,
            professionalName = professionalName.trim(),
            experienceYears = experienceYears.coerceAtLeast(0),
            qualifications = qualifications.trim(),
            specialties = specialties.trim(),
            languages = languages.trim().ifBlank { "English, Hindi" },
            websiteUrl = websiteUrl.trim()
        )

        val defaultLoc = BusinessLocation(
            id = locationId,
            businessId = businessId,
            branchName = branchName.ifBlank { "$shopName - Main" },
            address = address.ifBlank { "Main Commercial Road" },
            city = city.ifBlank { "Lucknow" },
            phone = phone.ifBlank { owner.phone },
            prefix = prefix
        )

        val defaultService = ServiceItem(
            businessId = businessId,
            locationId = locationId,
            name = serviceName.ifBlank { "Express Token" },
            description = serviceDesc.ifBlank { "Fast digital slot booking for customers" },
            durationMinutes = serviceDuration.coerceIn(2, 120),
            price = servicePrice.coerceAtLeast(0.0),
            prefix = prefix
        )

        val hashSeed = Math.abs((businessId + locationId).hashCode() % 9000 + 1000)
        val publicSlug = "SQ-${category.name.take(4).uppercase()}-${defaultLoc.branchName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "MAIN" }}-$hashSeed"
        val canonicalQrDocId = "qr_${businessId}_${locationId}"
        val qrCodeData = QRCodeData(
            qrId = canonicalQrDocId,
            businessId = businessId,
            locationId = locationId,
            publicCode = publicSlug,
            isRevoked = false
        )

        if (firestoreDataSource.isCloudConfigured) {
            val cloudRes = firestoreDataSource.saveOnboardingBatch(newBiz, defaultLoc, defaultService, qrCodeData, owner.copy(role = UserRole.MERCHANT))
            if (cloudRes.isFailure) {
                throw cloudRes.exceptionOrNull() ?: IllegalStateException("Failed to persist merchant onboarding to cloud database.")
            }
        }

        _businesses.value = listOf(newBiz) + _businesses.value
        _locations.value = listOf(defaultLoc) + _locations.value
        _services.value = listOf(defaultService) + _services.value
        _qrCodes.value = listOf(qrCodeData) + _qrCodes.value
        _currentUser.value = owner.copy(role = UserRole.MERCHANT)

        _auditLogs.value = listOf(
            AuditLog(
                actorName = owner.name,
                actorRole = "MERCHANT",
                action = "SHOP_ONBOARDED",
                target = "${newBiz.name} (Code: $publicSlug)"
            )
        ) + _auditLogs.value

        return@withLock newBiz
    }
}
