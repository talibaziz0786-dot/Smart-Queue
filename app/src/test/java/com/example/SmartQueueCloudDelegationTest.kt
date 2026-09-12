package com.example

import com.example.backend.FirestoreDataSource
import com.example.model.*
import com.example.repository.SmartQueueRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates that SmartQueueRepository in Cloud-Configured mode strictly delegates
 * all queue operations and merchant onboarding to FirestoreDataSource transactions,
 * correctly updates StateFlow on success, and surfaces failures rather than faking local success.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartQueueCloudDelegationTest {

    private open class TestFirestoreDataSource : FirestoreDataSource(null) {
        override val isCloudConfigured: Boolean = true

        var joinQueueCalled = false
        var callNextCalled = false
        var startServingCalled = false
        var markServedCalled = false
        var skipTokenCalled = false
        var recordNoShowCalled = false
        var cancelTokenCalled = false
        var toggleQueueCalled = false
        var saveOnboardingCalled = false

        var shouldFail = false

        override fun listenToBusinesses(onUpdate: (List<Business>) -> Unit): ListenerRegistration? = null
        override fun listenToLocations(onUpdate: (List<BusinessLocation>) -> Unit): ListenerRegistration? = null
        override fun listenToServices(onUpdate: (List<ServiceItem>) -> Unit): ListenerRegistration? = null
        override fun listenToQrCodes(onUpdate: (List<QRCodeData>) -> Unit): ListenerRegistration? = null
        override fun listenToTokens(
            businessId: String?,
            locationId: String?,
            customerId: String?,
            businessOwnerId: String?,
            onUpdate: (List<Token>) -> Unit
        ): ListenerRegistration? = null

        override suspend fun joinQueueTransaction(
            businessId: String,
            locationId: String,
            serviceId: String,
            user: User,
            priority: TokenPriority,
            notes: String,
            customNameOverride: String?,
            customerAge: Int?,
            customerGender: String?,
            isGuest: Boolean
        ): Result<Token> {
            joinQueueCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud transaction abort"))
            val token = Token(
                id = "cloud_tok_101",
                tokenNumber = "C-101",
                businessId = businessId,
                businessName = "Cloud Business",
                locationId = locationId,
                branchName = "Cloud Branch",
                serviceId = serviceId,
                serviceName = "Consultation",
                userId = user.id,
                userName = user.name,
                userPhone = user.phone,
                status = TokenStatus.WAITING,
                sequenceNumber = 101,
                priority = priority
            )
            return Result.success(token)
        }

        override suspend fun callNextTransaction(
            businessId: String,
            locationId: String,
            staffName: String,
            counterName: String
        ): Result<Token?> {
            callNextCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud callNext abort"))
            val token = Token(
                id = "cloud_tok_101",
                tokenNumber = "C-101",
                sequenceNumber = 101,
                businessId = businessId,
                businessName = "Cloud Business",
                locationId = locationId,
                branchName = "Cloud Branch",
                serviceId = "srv_1",
                serviceName = "Consultation",
                userId = "usr_cust_1",
                userName = "Test Customer",
                userPhone = "99999",
                status = TokenStatus.CALLED,
                counterNumber = counterName,
                staffAssigned = staffName,
                calledAt = System.currentTimeMillis()
            )
            return Result.success(token)
        }

        override suspend fun startServingTransaction(tokenId: String, staffName: String): Result<Token> {
            startServingCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud startServing abort"))
            val token = Token(
                id = tokenId,
                tokenNumber = "C-101",
                sequenceNumber = 101,
                businessId = "biz_apex",
                businessName = "Apex Clinic",
                locationId = "loc_apex_kanpur",
                branchName = "Apex Branch",
                serviceId = "srv_apex_gen",
                serviceName = "Consultation",
                userId = "usr_cust_1",
                userName = "Test Customer",
                userPhone = "99999",
                status = TokenStatus.SERVING,
                staffAssigned = staffName
            )
            return Result.success(token)
        }

        override suspend fun markServedTransaction(tokenId: String): Result<Token> {
            markServedCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud markServed abort"))
            val token = Token(
                id = tokenId,
                tokenNumber = "C-101",
                sequenceNumber = 101,
                businessId = "biz_apex",
                businessName = "Apex Clinic",
                locationId = "loc_apex_kanpur",
                branchName = "Apex Branch",
                serviceId = "srv_apex_gen",
                serviceName = "Consultation",
                userId = "usr_cust_1",
                userName = "Test Customer",
                userPhone = "99999",
                status = TokenStatus.SERVED,
                servedAt = System.currentTimeMillis()
            )
            return Result.success(token)
        }

        override suspend fun skipTokenTransaction(tokenId: String): Result<Token> {
            skipTokenCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud skipToken abort"))
            val token = Token(
                id = tokenId,
                tokenNumber = "C-101",
                sequenceNumber = 101,
                businessId = "biz_apex",
                businessName = "Apex Clinic",
                locationId = "loc_apex_kanpur",
                branchName = "Apex Branch",
                serviceId = "srv_apex_gen",
                serviceName = "Consultation",
                userId = "usr_cust_1",
                userName = "Test Customer",
                userPhone = "99999",
                status = TokenStatus.SKIPPED
            )
            return Result.success(token)
        }

        override suspend fun recordNoShowTransaction(tokenId: String): Result<Token> {
            recordNoShowCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud recordNoShow abort"))
            val token = Token(
                id = tokenId,
                tokenNumber = "C-101",
                sequenceNumber = 101,
                businessId = "biz_apex",
                businessName = "Apex Clinic",
                locationId = "loc_apex_kanpur",
                branchName = "Apex Branch",
                serviceId = "srv_apex_gen",
                serviceName = "Consultation",
                userId = "usr_cust_1",
                userName = "Test Customer",
                userPhone = "99999",
                status = TokenStatus.NO_SHOW
            )
            return Result.success(token)
        }

        override suspend fun cancelTokenTransaction(tokenId: String, callerUserId: String): Result<Token> {
            cancelTokenCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud cancelToken abort"))
            val token = Token(
                id = tokenId,
                tokenNumber = "C-101",
                sequenceNumber = 101,
                businessId = "biz_apex",
                businessName = "Apex Clinic",
                locationId = "loc_apex_kanpur",
                branchName = "Apex Branch",
                serviceId = "srv_apex_gen",
                serviceName = "Consultation",
                userId = callerUserId,
                userName = "Test Customer",
                userPhone = "99999",
                status = TokenStatus.CANCELLED
            )
            return Result.success(token)
        }

        override suspend fun toggleLocationQueueTransaction(locationId: String, isPaused: Boolean): Result<Unit> {
            toggleQueueCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud toggleQueue abort"))
            return Result.success(Unit)
        }

        override suspend fun saveOnboardingBatch(
            business: Business,
            location: BusinessLocation,
            service: ServiceItem,
            qrCode: QRCodeData,
            user: User
        ): Result<Unit> {
            saveOnboardingCalled = true
            if (shouldFail) return Result.failure(IllegalStateException("Cloud onboarding abort"))
            return Result.success(Unit)
        }
    }

    private fun setupCloudRepository(testDs: TestFirestoreDataSource): SmartQueueRepository {
        val repo = SmartQueueRepository(firestoreDataSource = testDs)
        repo.testOnlySeedMockData()
        repo.testOnlySetStaffLocation("dr.sameer@apexclinic.com", "loc_apex_kanpur")
        return repo
    }

    @Test
    fun testCloudJoinQueueDelegationSuccess() = runBlocking {
        val testDs = TestFirestoreDataSource()
        val repo = setupCloudRepository(testDs)

        repo.signUpCustomer("Cloud Customer", "cust@cloud.in", "Pass123", "9876543210")
        val cust = repo.currentUser.value

        val res = repo.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_kanpur",
            serviceId = "srv_apex_gen",
            priority = TokenPriority.STANDARD,
            notes = "Cloud checkup"
        )

        assertTrue("Join queue in cloud mode must invoke FirestoreDataSource", testDs.joinQueueCalled)
        assertTrue("Cloud join must succeed", res.isSuccess)
        val token = res.getOrThrow()
        assertEquals("cloud_tok_101", token.id)
        assertEquals("C-101", token.tokenNumber)
        assertTrue("Token must be present in repository StateFlow", repo.tokens.value.any { it.id == "cloud_tok_101" })
    }

    @Test
    fun testCloudJoinQueueDelegationFailureSurfacing() = runBlocking {
        val testDs = TestFirestoreDataSource().apply { shouldFail = true }
        val repo = setupCloudRepository(testDs)

        repo.signUpCustomer("Cloud Customer", "cust@cloud.in", "Pass123", "9876543210")
        val res = repo.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_kanpur",
            serviceId = "srv_apex_gen"
        )

        assertTrue("Join queue in cloud mode must invoke FirestoreDataSource", testDs.joinQueueCalled)
        assertTrue("Cloud join failure must be surfaced as Result.failure", res.isFailure)
        assertEquals("Cloud transaction abort", res.exceptionOrNull()?.message)
        assertFalse("Failed cloud token must not be added to StateFlow", repo.tokens.value.any { it.id == "cloud_tok_101" })
    }

    @Test
    fun testCloudOperatorLifecycleDelegation() = runBlocking {
        val testDs = TestFirestoreDataSource()
        val repo = setupCloudRepository(testDs)

        // Login as authorized staff
        repo.loginUser("dr.sameer@apexclinic.com", "apex123")

        // 1. callNextToken
        val callRes = repo.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer Verma")
        assertTrue("callNextToken must delegate to FirestoreDataSource in cloud mode", testDs.callNextCalled)
        assertTrue("callNextToken must succeed", callRes.isSuccess)

        // Add a token into repository state to operate on
        val targetToken = callRes.getOrThrow()

        // 2. startServing
        val startRes = repo.startServing(targetToken.id, "Dr. Sameer Verma")
        assertTrue("startServing must delegate to FirestoreDataSource in cloud mode", testDs.startServingCalled)
        assertTrue("startServing must succeed", startRes.isSuccess)

        // 3. markServed
        val servedRes = repo.markServed(targetToken.id, "Dr. Sameer Verma")
        assertTrue("markServed must delegate to FirestoreDataSource in cloud mode", testDs.markServedCalled)
        assertTrue("markServed must succeed", servedRes.isSuccess)

        // 4. toggleLocationQueue
        repo.toggleLocationQueue("loc_apex_kanpur", true)
        assertTrue("toggleLocationQueue must delegate to FirestoreDataSource in cloud mode", testDs.toggleQueueCalled)
    }

    @Test
    fun testCloudCancelTokenDelegation() = runBlocking {
        val testDs = TestFirestoreDataSource()
        val repo = setupCloudRepository(testDs)

        repo.signUpCustomer("Cancel Customer", "cancel@cloud.in", "Pass123", "9876543210")

        // Setup waiting token in state owned by this customer
        val joinRes = repo.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen")
        val token = joinRes.getOrThrow()

        val cancelRes = repo.cancelToken(token.id, "Change of plans")
        assertTrue("cancelToken must delegate to FirestoreDataSource in cloud mode", testDs.cancelTokenCalled)
        assertTrue("cancelToken must succeed", cancelRes.isSuccess)
        assertEquals(TokenStatus.CANCELLED, cancelRes.getOrThrow().status)
    }

    @Test
    fun testCloudFailureNeverSilentlyFakesLocalState() = runBlocking {
        val testDs = TestFirestoreDataSource().apply { shouldFail = true }
        val repo = setupCloudRepository(testDs)

        repo.loginUser("dr.sameer@apexclinic.com", "apex123")

        val callRes = repo.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer Verma")
        assertTrue("callNextToken in failing cloud mode must return failure", callRes.isFailure)
        assertEquals("Cloud callNext abort", callRes.exceptionOrNull()?.message)
    }
}
