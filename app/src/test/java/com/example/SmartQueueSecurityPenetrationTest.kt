package com.example

import com.example.model.*
import com.example.repository.SmartQueueRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartQueueSecurityPenetrationTest {

    private lateinit var repository: SmartQueueRepository

    @Before
    fun setUp() {
        repository = SmartQueueRepository()
    }

    @Test
    fun testAttackA_CustomerRegistrationStrictlyAssignsUserRole() = runBlocking {
        // Customer registers via public signup
        val (success, err) = repository.signUpCustomer(
            name = "Attacker User",
            email = "attacker@gmail.com",
            pass = "Pass1234",
            phone = "+91 99999 88888"
        )
        assertTrue(success)
        assertNull(err)

        val currentUser = repository.currentUser.value
        assertEquals("Registered user role must strictly be USER or CUSTOMER", UserRole.USER, currentUser.role)
        assertNotEquals(UserRole.ADMIN, currentUser.role)
        assertNotEquals(UserRole.SUPER_ADMIN, currentUser.role)
        assertNotEquals(UserRole.MERCHANT, currentUser.role)
    }

    @Test
    fun testAttackB_CustomerCannotEscalateRoleDirectly() = runBlocking {
        repository.signUpCustomer(
            name = "Test User",
            email = "user@gmail.com",
            pass = "Pass1234",
            phone = "+91 99999 77777"
        )
        val currentUser = repository.currentUser.value
        assertEquals(UserRole.USER, currentUser.role)

        // Attempt client-side role escalation
        try {
            repository.switchUserRole(UserRole.ADMIN)
            fail("Expected SecurityException when non-admin attempts to switch user role")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Forbidden") == true)
        }

        assertEquals(UserRole.USER, repository.currentUser.value.role)
    }

    @Test
    fun testAttackC_RegularUserCannotCallAdminApis() = runBlocking {
        repository.signUpCustomer(
            name = "Normal User",
            email = "normal@gmail.com",
            pass = "Pass1234",
            phone = "+91 99999 66666"
        )

        // Attempt 1: adminUpdateUserRole
        try {
            repository.adminUpdateUserRole(repository.currentUser.value.id, UserRole.ADMIN)
            fail("Expected SecurityException on unauthorized adminUpdateUserRole")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Forbidden") == true)
        }

        // Attempt 2: adminBlockUser
        try {
            repository.adminBlockUser("usr_cust_01", "Unauthorized block")
            fail("Expected SecurityException on unauthorized adminBlockUser")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Forbidden") == true)
        }

        // Attempt 3: updatePlanPrice
        try {
            repository.updatePlanPrice(SubscriptionPlan.PRO, 0)
            fail("Expected SecurityException on unauthorized updatePlanPrice")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Forbidden") == true)
        }
    }

    @Test
    fun testAttackD_MerchantIsolationEnforcement() = runBlocking {
        // Merchant A creates a shop
        repository.signUpMerchant(
            ownerName = "Dr. Alice",
            businessName = "Alice Dental",
            category = BusinessCategory.HEALTHCARE,
            phone = "+91 91111 22222",
            email = "alice@clinic.com",
            pass = "AlicePass123",
            address = "Hazratganj Road",
            city = "Lucknow"
        )
        val merchantAUser = repository.currentUser.value
        assertEquals(UserRole.MERCHANT, merchantAUser.role)
        val bizA = repository.businesses.value.first { it.ownerId == merchantAUser.id }

        // Merchant B tries to modify Merchant A's branch
        repository.signUpMerchant(
            ownerName = "Bob Barber",
            businessName = "Bob Salon",
            category = BusinessCategory.SALON_BEAUTY,
            phone = "+91 93333 44444",
            email = "bob@salon.com",
            pass = "BobPass123",
            address = "Gomti Nagar",
            city = "Lucknow"
        )

        // Bob tries to add staff to Alice's business
        try {
            repository.addStaffMember(
                businessId = bizA.id,
                locationId = "loc_${bizA.id}",
                name = "Intruder Staff",
                email = "intruder@malicious.com",
                role = StaffRole.COUNTER_OPERATOR,
                counter = "Counter 99"
            )
            fail("Expected SecurityException when Merchant B modifies Merchant A data")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("isolation violation") == true || e.message?.contains("Forbidden") == true)
        }
    }

    @Test
    fun testAttackE_CustomerCannotMarkThemselvesServed() = runBlocking {
        repository.signUpCustomer(
            name = "Customer John",
            email = "john@gmail.com",
            pass = "JohnPass123",
            phone = "+91 98888 11111"
        )

        // Join queue
        val joinResult = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            serviceId = "srv_apex_gen"
        )
        assertTrue(joinResult.isSuccess)
        val token = joinResult.getOrThrow()

        // Attempt to mark own token as served
        try {
            repository.markServed(token.id, "Malicious Staff")
            fail("Expected SecurityException when customer attempts operator markServed")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Forbidden") == true || e.message?.contains("isolation violation") == true)
        }
    }

    @Test
    fun testAttackF_CustomerCannotCancelAnotherUsersToken() = runBlocking {
        val user1Result = repository.signUpCustomer(
            name = "User One",
            email = "user1@gmail.com",
            pass = "Pass1234",
            phone = "+91 91111 00001"
        )
        assertTrue(user1Result.first)
        val token1 = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            serviceId = "srv_apex_gen"
        ).getOrThrow()

        // Switch to user 2
        repository.signUpCustomer(
            name = "User Two",
            email = "user2@gmail.com",
            pass = "Pass1234",
            phone = "+91 92222 00002"
        )

        // User 2 attempts to cancel User 1's token
        val cancelResult = repository.cancelToken(token1.id, "Malicious cancel")
        assertTrue(cancelResult.isFailure)
        assertTrue(cancelResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun testConcurrency_SimultaneousQueueJoins_UniqueTokens() = runBlocking {
        // Concurrency test: Multiple users joining queue concurrently
        val branchId = "loc_apex_hazratganj"
        val bizId = "biz_apex"
        val serviceId = "srv_apex_gen"

        val jobs = (1..20).map { i ->
            async {
                val repo = repository
                val user = User(
                    id = "usr_concurrent_$i",
                    name = "Customer $i",
                    email = "cust$i@gmail.com",
                    phone = "+91 90000 ${String.format("%05d", i)}",
                    role = UserRole.USER,
                    isVerified = true
                )
                // Direct join call simulation with lock
                repo.joinQueue(
                    businessId = bizId,
                    locationId = branchId,
                    serviceId = serviceId
                )
            }
        }

        val results = jobs.awaitAll()
        val successfulTokens = results.filter { it.isSuccess }.map { it.getOrThrow() }

        // All sequence numbers must be distinct
        val sequenceNumbers = successfulTokens.map { it.sequenceNumber }
        assertEquals(sequenceNumbers.size, sequenceNumbers.toSet().size)

        // All token strings must be distinct
        val tokenNumbers = successfulTokens.map { it.tokenNumber }
        assertEquals(tokenNumbers.size, tokenNumbers.toSet().size)
    }

    @Test
    fun testAbusePolicy_RestrictedUserBlockedFromQueue() = runBlocking {
        // Customer signs up
        repository.signUpCustomer(
            name = "Bad Actor",
            email = "badactor@gmail.com",
            pass = "Pass1234",
            phone = "+91 99999 12345"
        )
        val badUser = repository.currentUser.value

        // Admin logs in and blocks user
        repository.verifyAdminCredentials("talibaziz0786@gmail.com", "AdminPass123")
        repository.adminBlockUser(badUser.id, "Repeated no-shows")

        // Blocked user signs back in
        repository.loginUser(badUser.email, "Pass1234")
        assertTrue(repository.currentUser.value.isBlocked)

        // Blocked user attempts to join queue
        val joinResult = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            serviceId = "srv_apex_gen"
        )
        assertTrue(joinResult.isFailure)
        assertTrue(joinResult.exceptionOrNull()?.message?.contains("restricted or blocked") == true)
    }

    @Test
    fun testBusinessStatus_ClosedOrPausedQueueRejectsJoins() = runBlocking {
        // Operator pauses location
        repository.verifyAdminCredentials("talibaziz0786@gmail.com", "AdminPass123")
        repository.toggleLocationQueue("loc_apex_kanpur", isPaused = true)

        val joinResult = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_kanpur",
            serviceId = "srv_apex_gen"
        )
        assertTrue(joinResult.isFailure)
        assertTrue(joinResult.exceptionOrNull()?.message?.contains("closed or paused") == true)
    }

    @Test
    fun testAuthenticationBypass_ArbitraryCredentialsFail() = runBlocking {
        // Attempt login with arbitrary unregistered credentials xyz / xyz
        val (success, errorMsg) = repository.loginUser("xyz", "xyz")
        assertFalse(success)
        assertNotNull(errorMsg)
        
        // Ensure user is not authenticated and remains Guest
        assertEquals("usr_guest", repository.currentUser.value.id)
        assertEquals("Guest", repository.currentUser.value.name)
    }

    @Test
    fun testDashboard_MerchantA_CannotSee_BusinessB_And_ViewModelFiltersCorrectly() = runBlocking {
        // Authenticate as Merchant A (Dr. Rajeshwar Singhania, owner of biz_apex)
        val loginRes = repository.loginUser("dr.rajeshwar@apexclinic.com", "Pass1234")
        assertTrue(loginRes.first)
        val currentUser = repository.currentUser.value
        assertEquals("usr_owner_01", currentUser.id)
        assertEquals(UserRole.BUSINESS_OWNER, currentUser.role)

        // Instantiate the ViewModel using the secure repository
        val viewModel = com.example.viewmodel.SmartQueueViewModel(repository)
        val uiState = viewModel.uiState.value
        assertNotNull(uiState)
        
        // Ensure businesses in uiState only contain usr_owner_01's business (biz_apex) and NOT biz_vogue
        assertTrue(uiState.merchantBusinesses.isNotEmpty())
        assertTrue(uiState.merchantBusinesses.all { it.ownerId == "usr_owner_01" })
        assertTrue(uiState.merchantBusinesses.any { it.id == "biz_apex" })
        assertFalse(uiState.merchantBusinesses.any { it.id == "biz_vogue" })

        // Ensure locations only belong to biz_apex
        val merchantLocations = uiState.locations.filter { loc -> uiState.merchantBusinesses.any { it.id == loc.businessId } }
        assertTrue(merchantLocations.isNotEmpty())
        assertTrue(merchantLocations.all { it.businessId == "biz_apex" })
    }

    @Test
    fun testDashboard_StaffMember_IsIsolatedToHiredLocation() = runBlocking {
        // Authenticate as Staff Member (stf_01: Dr. Sameer Verma)
        val loginRes = repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        assertTrue(loginRes.first)
        val currentUser = repository.currentUser.value
        assertEquals("stf_01", currentUser.id)
        assertEquals(UserRole.STAFF, currentUser.role)

        // Instantiate the ViewModel
        val viewModel = com.example.viewmodel.SmartQueueViewModel(repository)
        val uiState = viewModel.uiState.value
        assertNotNull(uiState)

        // Staff 1 is assigned to biz_apex and loc_apex_hazratganj
        // Verify that merchantBusinesses only contains biz_apex
        assertTrue(uiState.merchantBusinesses.isNotEmpty())
        assertTrue(uiState.merchantBusinesses.all { it.id == "biz_apex" })
        
        // Verify staff member location assignment
        val staff = uiState.staffMembers.find { it.email.equals(currentUser.email, ignoreCase = true) }
        assertNotNull(staff)
        assertEquals("loc_apex_hazratganj", staff?.locationId)
    }

    @Test
    fun testOperations_UnauthorizedMerchant_CannotOperateAnotherTenantQueue() = runBlocking {
        // Create a token for Business B (biz_vogue, branch: loc_vogue_gomtinagar)
        // Log in as customer to create it
        repository.signUpCustomer("Client", "client@mail.com", "ClientPass123", "+91 99999 55555")
        val joinRes = repository.joinQueue(
            businessId = "biz_vogue",
            locationId = "loc_vogue_gomtinagar",
            serviceId = "srv_vogue_hair"
        )
        assertTrue(joinRes.isSuccess)
        val token = joinRes.getOrThrow()

        // Log in as Merchant A
        repository.loginUser("dr.rajeshwar@apexclinic.com", "Pass1234")

        // Merchant A attempts to start serving the token of Business B
        try {
            repository.startServing(token.id, "Dr. Rajeshwar")
            fail("Expected SecurityException when Merchant A attempts to operate Merchant B's queue")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("isolation violation") == true || e.message?.contains("Forbidden") == true)
        }
    }

    @Test
    fun testPhase4_MerchantOnboarding_FlowAndSecurityIntegrity() = runBlocking {
        // 1. Sign up as a new merchant
        val (signupSuccess, err) = repository.signUpCustomer(
            name = "Dr. New Merchant",
            email = "newmerchant@clinic.com",
            pass = "Pass1234",
            phone = "+91 95555 11111"
        )
        assertTrue(signupSuccess)
        assertNull(err)

        val user = repository.currentUser.value
        assertEquals(UserRole.USER, user.role) // Initially just a basic user

        // 2. Perform and complete step-by-step onboarding
        val customHours = listOf(
            BusinessHours("Monday", "10:00 AM", "06:00 PM", false),
            BusinessHours("Sunday", isClosed = true)
        )

        val biz = repository.completeMerchantOnboarding(
            shopName = "Lancer Clinic",
            category = BusinessCategory.HEALTHCARE,
            description = "Specialized dental care clinic.",
            phone = "+91 95555 11111",
            websiteUrl = "https://lancerclinic.com",
            branchName = "Hazratganj Main Outlet",
            address = "Suite 101, Lancer Plaza",
            city = "Lucknow",
            state = "Uttar Pradesh",
            country = "India",
            serviceName = "Orthodontic Consultation",
            serviceDesc = "Expert braces and alignment checkup",
            serviceDuration = 30,
            servicePrice = 500.0,
            operatingHours = customHours,
            professionalName = "Dr. New Merchant",
            experienceYears = 8,
            qualifications = "BDS, MDS Orthodontics",
            specialties = "Orthodontics, Clear Aligners",
            languages = "English, Hindi, Urdu",
            about = "Dedicated to crafting premium, beautiful smiles with specialized digital orthodontic care."
        )

        // 3. Verify Business Creation and all parameters are saved
        assertNotNull(biz)
        assertEquals("Lancer Clinic", biz.name)
        assertEquals(BusinessCategory.HEALTHCARE, biz.category)
        assertEquals("Specialized dental care clinic.", biz.description)
        assertEquals("https://lancerclinic.com", biz.websiteUrl)

        // 4. Verify user role transitions to MERCHANT
        val updatedUser = repository.currentUser.value
        assertEquals(UserRole.MERCHANT, updatedUser.role)

        // 5. Verify local location is created correctly
        val locations = repository.locations.value.filter { it.businessId == biz.id }
        assertEquals(1, locations.size)
        val branch = locations.first()
        assertEquals("Hazratganj Main Outlet", branch.branchName)
        assertEquals("Suite 101, Lancer Plaza", branch.address)
        assertEquals("Lucknow", branch.city)

        // 6. Verify custom service was populated correctly
        val services = repository.services.value.filter { it.businessId == biz.id }
        assertEquals(1, services.size)
        val service = services.first()
        assertEquals("Orthodontic Consultation", service.name)
        assertEquals("Expert braces and alignment checkup", service.description)
        assertEquals(30, service.durationMinutes)
        assertEquals(500.0, service.price, 0.01)

        // 7. Verify operating hours are stored correctly
        assertEquals(2, biz.operatingHours.size)
        assertEquals("Monday", biz.operatingHours[0].dayOfWeek)
        assertEquals("10:00 AM", biz.operatingHours[0].openTime)
        assertFalse(biz.operatingHours[0].isClosed)
        assertTrue(biz.operatingHours[1].isClosed)

        // 8. Verify public professional profile matches
        assertEquals("Dr. New Merchant", biz.professionalName)
        assertEquals(8, biz.experienceYears)
        assertEquals("BDS, MDS Orthodontics", biz.qualifications)
        assertEquals("Orthodontics, Clear Aligners", biz.specialties)
        assertEquals("English, Hindi, Urdu", biz.languages)

        // 9. SECURITY ASSERTION: Verification starts as PENDING and isVerified = false (Server/Repo authoritative)
        assertEquals(MerchantVerificationStatus.PENDING, biz.verificationStatus)
        assertFalse(biz.isVerified)

        // 10. Verify secure queue QR is automatically generated
        val qrCodes = repository.qrCodes.value.filter { it.businessId == biz.id }
        assertEquals(1, qrCodes.size)
        assertTrue(qrCodes.first().publicCode.startsWith("SQ-"))
    }

    @Test
    fun testPhase5_CustomerJourney_SecurityAndDiscoveryBoundary() = runBlocking {
        // 1. Discovery and Privacy Check
        // Guests can query public business info but have no exposure to private account variables
        val publicBusinesses = repository.businesses.value
        assertTrue(publicBusinesses.isNotEmpty())
        for (biz in publicBusinesses) {
            // Publicly exposed fields must be safe
            assertFalse(biz.name.isBlank())
            assertNotNull(biz.category)
            // No password/internal secrets in Business model!
        }

        // 2. Branch Isolation Check
        // If a client attempts to join a mismatched branch that does not belong to the business, it fails.
        // Let's sign up a customer session
        repository.signUpCustomer("Secure Customer", "sec_cust@mail.com", "Pass1234", "+91 94444 22222")
        val invalidJoin = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_vogue_gomtinagar", // Mismatched location (belongs to biz_vogue)
            serviceId = "srv_apex_gen"
        )
        assertTrue("Joining mismatched location must fail to prevent tenant cross-contamination", invalidJoin.isFailure)

        // 3. Service Validation Check
        // Client cannot join using a service not belonging to that business/branch
        val invalidServiceJoin = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            serviceId = "srv_vogue_hair" // Mismatched service (belongs to salon)
        )
        assertTrue("Joining mismatched service must fail", invalidServiceJoin.isFailure)

        // 4. QR Isolation Check
        // Resolving a public QR code strictly maps to its owned business/location details
        val publicQrCode = repository.qrCodes.value.first { it.businessId == "biz_apex" }
        val resolved = repository.resolveQRCode(publicQrCode.publicCode)
        assertNotNull(resolved)
        assertEquals("biz_apex", resolved!!.first.id)
        assertEquals(publicQrCode.locationId, resolved.second.id)

        // Resolving a bogus code returns null cleanly without exposing internal data
        val bogusResolved = repository.resolveQRCode("Z")
        assertNull(bogusResolved)

        // 5. Token Ownership Check
        // Join queue cleanly to get an authorized token
        val validJoin = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_hazratganj",
            serviceId = "srv_apex_gen"
        )
        assertTrue(validJoin.isSuccess)
        val token = validJoin.getOrThrow()

        // Create a different customer session
        repository.signUpCustomer("Different Customer", "diff_cust@mail.com", "Pass1234", "+91 95555 33333")
        
        // This different customer tries to cancel the first customer's token
        val crossCancel = repository.cancelToken(token.id, "Malicious Cancel attempt")
        assertTrue("Different customer must be forbidden from cancelling another's token", crossCancel.isFailure)
    }
}

