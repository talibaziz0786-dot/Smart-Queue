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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartQueueEngineProductionValidationTest {

    private lateinit var repository: SmartQueueRepository

    @Before
    fun setUp() {
        repository = SmartQueueRepository()
        repository.testOnlySetStaffLocation("dr.sameer@apexclinic.com", "loc_apex_kanpur")
        repository.testOnlySetStaffLocation("dr.ananya@apexclinic.com", "loc_apex_kanpur")
        repository.testOnlySetStaffLocation("rahul.reception@apexclinic.com", "loc_apex_kanpur")
    }

    // =========================================================================
    // 1. REAL-WORLD QUEUE MODEL & COMPLETE LIFECYCLE
    // =========================================================================
    @Test
    fun test01_CompleteRealWorldQueueLifecycle() = runBlocking {
        // Step 1: Customer registers and joins queue
        repository.signUpCustomer(
            name = "Aarav Sharma",
            email = "aarav.sharma@gmail.com",
            pass = "Pass1234",
            phone = "+91 98111 22334"
        )
        val customer = repository.currentUser.value
        assertEquals(UserRole.USER, customer.role)

        val joinResult = repository.joinQueue(
            businessId = "biz_apex",
            locationId = "loc_apex_kanpur",
            serviceId = "srv_apex_gen",
            priority = TokenPriority.STANDARD,
            notes = "General consultation"
        )
        assertTrue("Customer should successfully join queue", joinResult.isSuccess)
        val token = joinResult.getOrThrow()
        assertEquals(TokenStatus.WAITING, token.status)
        assertTrue("Token number must start with service prefix", token.tokenNumber.startsWith("A-"))

        // Step 2: Verify notification was dispatched to customer
        val notifs = repository.notifications.value.filter { it.userId == customer.id }
        assertTrue("Customer must receive TOKEN_CREATED notification", notifs.any { it.type == NotificationType.TOKEN_CREATED })

        // Step 3: Staff logs in and calls next token
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val calledResult = repository.callNextToken(
            locationId = "loc_apex_kanpur",
            counterName = "Counter 1",
            staffName = "Dr. Sameer Verma"
        )
        assertTrue("Staff should successfully call next token", calledResult.isSuccess)
        val calledToken = calledResult.getOrThrow()
        assertEquals(token.id, calledToken.id)
        assertEquals(TokenStatus.CALLED, calledToken.status)
        assertEquals("Counter 1", calledToken.counterNumber)

        // Step 4: Staff starts serving customer
        val servingResult = repository.startServing(calledToken.id, "Dr. Sameer Verma")
        assertTrue("Staff should start serving token", servingResult.isSuccess)
        val servingToken = servingResult.getOrThrow()
        assertEquals(TokenStatus.SERVING, servingToken.status)

        // Step 5: Staff completes service (mark served)
        val servedResult = repository.markServed(servingToken.id, "Dr. Sameer Verma")
        assertTrue("Staff should mark token as served", servedResult.isSuccess)
        val servedToken = servedResult.getOrThrow()
        assertEquals(TokenStatus.SERVED, servedToken.status)
        assertTrue("Served timestamp must be set", (servedToken.servedAt ?: 0L) > 0L)

        // Step 6: Verify customer received completion notification
        val updatedNotifs = repository.notifications.value.filter { it.userId == customer.id }
        assertTrue("Customer must receive TOKEN_SERVED notification", updatedNotifs.any { it.type == NotificationType.TOKEN_SERVED })
    }

    // =========================================================================
    // 2. TOKEN STATE MACHINE & TRANSITION VALIDATION
    // =========================================================================
    @Test
    fun test02_TokenStateMachine_LegalTransitions() {
        // Legal transitions
        assertTrue("WAITING -> CALLED must be valid", repository.isValidTransition(TokenStatus.WAITING, TokenStatus.CALLED))
        assertTrue("CALLED -> SERVING must be valid", repository.isValidTransition(TokenStatus.CALLED, TokenStatus.SERVING))
        assertTrue("SERVING -> SERVED must be valid", repository.isValidTransition(TokenStatus.SERVING, TokenStatus.SERVED))
        assertTrue("WAITING -> CANCELLED must be valid", repository.isValidTransition(TokenStatus.WAITING, TokenStatus.CANCELLED))
        assertTrue("WAITING -> NO_SHOW must be valid", repository.isValidTransition(TokenStatus.WAITING, TokenStatus.NO_SHOW))
        assertTrue("WAITING -> SKIPPED must be valid", repository.isValidTransition(TokenStatus.WAITING, TokenStatus.SKIPPED))
        assertTrue("CALLED -> SKIPPED must be valid", repository.isValidTransition(TokenStatus.CALLED, TokenStatus.SKIPPED))
        assertTrue("SKIPPED -> WAITING must be valid", repository.isValidTransition(TokenStatus.SKIPPED, TokenStatus.WAITING))
        assertTrue("SKIPPED -> CALLED must be valid", repository.isValidTransition(TokenStatus.SKIPPED, TokenStatus.CALLED))
    }

    @Test
    fun test03_TokenStateMachine_IllegalTransitionsRejectedServerSide() = runBlocking {
        // Illegal transitions must return false
        assertFalse("SERVED -> WAITING must be rejected", repository.isValidTransition(TokenStatus.SERVED, TokenStatus.WAITING))
        assertFalse("CANCELLED -> SERVING must be rejected", repository.isValidTransition(TokenStatus.CANCELLED, TokenStatus.SERVING))
        assertFalse("NO_SHOW -> SERVING must be rejected", repository.isValidTransition(TokenStatus.NO_SHOW, TokenStatus.SERVING))
        assertFalse("SKIPPED -> SERVING directly must be rejected", repository.isValidTransition(TokenStatus.SKIPPED, TokenStatus.SERVING))
        assertFalse("SERVED -> SERVING must be rejected", repository.isValidTransition(TokenStatus.SERVED, TokenStatus.SERVING))
        assertFalse("EXPIRED -> SERVING must be rejected", repository.isValidTransition(TokenStatus.EXPIRED, TokenStatus.SERVING))

        // Test with live repository state
        repository.testOnlySetStaffLocation("dr.sameer@apexclinic.com", "loc_apex_hazratganj")
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val servedToken = repository.tokens.value.first { it.status == TokenStatus.SERVED }

        val illegalServeResult = repository.startServing(servedToken.id, "Dr. Sameer Verma")
        assertTrue("Illegal transition on served token must fail", illegalServeResult.isFailure)
    }

    // =========================================================================
    // 3. CALL NEXT VALIDATION & STRICT ORDERING
    // =========================================================================
    @Test
    fun test04_CallNextValidationAndOrdering() = runBlocking {
        // Clean queue for a new branch
        repository.loginAsMasterAdmin()
        val newBranch = BusinessLocation(
            id = "loc_apex_test_order",
            businessId = "biz_apex",
            branchName = "Order Test Branch",
            address = "Test Road",
            city = "Lucknow",
            phone = "+91 99999 00000",
            isOpen = true,
            prefix = "T"
        )
        repository.adminAddBusiness("Test Shop", BusinessCategory.RETAIL, "Desc", "Lucknow", SubscriptionPlan.PRO)

        // Customer 1 joins
        repository.signUpCustomer("Customer A", "custA@test.com", "Pass1234", "+91 90000 00001")
        val tokenA = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()

        // Customer 2 joins
        repository.signUpCustomer("Customer B", "custB@test.com", "Pass1234", "+91 90000 00002")
        val tokenB = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()

        // Customer 3 joins
        repository.signUpCustomer("Customer C", "custC@test.com", "Pass1234", "+91 90000 00003")
        val tokenC = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()

        assertTrue("Token A sequence must be less than Token B", tokenA.sequenceNumber < tokenB.sequenceNumber)
        assertTrue("Token B sequence must be less than Token C", tokenB.sequenceNumber < tokenC.sequenceNumber)

        // Staff calls next -> must get Token A first
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val called1 = repository.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer").getOrThrow()
        assertEquals("First called token must be Token A", tokenA.id, called1.id)

        // Serve Token A
        repository.startServing(called1.id, "Dr. Sameer")
        repository.markServed(called1.id, "Dr. Sameer")

        // Staff calls next -> must get Token B
        val called2 = repository.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer").getOrThrow()
        assertEquals("Second called token must be Token B", tokenB.id, called2.id)

        // Serve Token B
        repository.startServing(called2.id, "Dr. Sameer")
        repository.markServed(called2.id, "Dr. Sameer")

        // Staff calls next -> must get Token C
        val called3 = repository.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer").getOrThrow()
        assertEquals("Third called token must be Token C", tokenC.id, called3.id)
    }

    // =========================================================================
    // 4. DOUBLE CLICK & RAPID ACTION IDEMPOTENCY PROTECTION
    // =========================================================================
    @Test
    fun test05_DoubleClickProtection_IdempotentMutations() = runBlocking {
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        
        // Add a customer
        repository.signUpCustomer("Rapid User", "rapid@test.com", "Pass1234", "+91 91111 22222")
        val token = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()

        // Merchant calls token
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val called = repository.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer").getOrThrow()

        // Merchant taps START SERVING 10 times in rapid succession
        val startServingResults = (1..10).map {
            repository.startServing(called.id, "Dr. Sameer")
        }
        assertTrue("All rapid start-serving taps must succeed safely", startServingResults.all { it.isSuccess })
        assertEquals("Token status must remain SERVING", TokenStatus.SERVING, repository.tokens.value.first { it.id == called.id }.status)

        // Merchant taps MARK SERVED 10 times in rapid succession
        val markServedResults = (1..10).map {
            repository.markServed(called.id, "Dr. Sameer")
        }
        assertTrue("All rapid mark-served taps must succeed safely (idempotent)", markServedResults.all { it.isSuccess })
        assertEquals("Token status must remain SERVED", TokenStatus.SERVED, repository.tokens.value.first { it.id == called.id }.status)
    }

    // =========================================================================
    // 5. MULTI-STAFF QUEUE & MULTI-COUNTER CONCURRENCY
    // =========================================================================
    @Test
    fun test06_MultiStaffMultiCounterQueueAllocation() = runBlocking {
        // Enqueue 5 customers
        val tokens = mutableListOf<Token>()
        for (i in 1..5) {
            repository.signUpCustomer("Cust $i", "cust$i@multistaff.com", "Pass1234", "+91 93333 0000$i")
            val tok = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()
            tokens.add(tok)
        }

        // 3 staff counters call next simultaneously
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val call1 = repository.callNextToken("loc_apex_kanpur", "Counter 1", "Dr. Sameer").getOrThrow()
        val call2 = repository.callNextToken("loc_apex_kanpur", "Counter 2", "Dr. Ananya").getOrThrow()
        val call3 = repository.callNextToken("loc_apex_kanpur", "Counter 3", "Rahul Sharma").getOrThrow()

        // Assert all 3 assigned tokens are distinct
        assertNotEquals("Counter 1 and 2 must not get same token", call1.id, call2.id)
        assertNotEquals("Counter 2 and 3 must not get same token", call2.id, call3.id)
        assertNotEquals("Counter 1 and 3 must not get same token", call1.id, call3.id)

        assertEquals("Counter 1", call1.counterNumber)
        assertEquals("Counter 2", call2.counterNumber)
        assertEquals("Counter 3", call3.counterNumber)

        val calledIds = setOf(call1.id, call2.id, call3.id)
        assertEquals("Exactly 3 distinct tokens must be called", 3, calledIds.size)
    }

    // =========================================================================
    // 6 & 7. QUEUE PAUSE & RESUME
    // =========================================================================
    @Test
    fun test07_QueuePauseAndResumeLifecycle() = runBlocking {
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        
        // Pause the queue for Kanpur branch
        repository.toggleLocationQueue("loc_apex_kanpur", isPaused = true)
        val pausedLoc = repository.locations.value.first { it.id == "loc_apex_kanpur" }
        assertTrue("Branch queue must be marked paused", pausedLoc.isPaused)

        // Customer attempts to join while paused -> must fail
        repository.signUpCustomer("Paused Attempt", "pause@test.com", "Pass1234", "+91 94444 11111")
        val joinResult = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen")
        assertTrue("Joining a paused queue must fail", joinResult.isFailure)
        assertTrue(joinResult.exceptionOrNull()?.message?.contains("closed or paused") == true)

        // Resume the queue
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        repository.toggleLocationQueue("loc_apex_kanpur", isPaused = false)
        val resumedLoc = repository.locations.value.first { it.id == "loc_apex_kanpur" }
        assertFalse("Branch queue must not be paused", resumedLoc.isPaused)

        // Customer attempts to join after resume -> must succeed
        val successfulJoin = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen")
        assertTrue("Joining a resumed queue must succeed", successfulJoin.isSuccess)
    }

    // =========================================================================
    // 8. BUSINESS CLOSE VALIDATION
    // =========================================================================
    @Test
    fun test08_BusinessCloseRejectsNewTokensPreservesHistory() = runBlocking {
        repository.testOnlySetStaffLocation("dr.sameer@apexclinic.com", "loc_apex_hazratganj")
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        
        // Pause/close Hazratganj queue
        repository.toggleLocationQueue("loc_apex_hazratganj", isPaused = true)

        // Customer attempts to join while paused -> must fail
        repository.signUpCustomer("Close Attempt User", "close@test.com", "Pass1234", "+91 94444 88888")
        val joinResult = repository.joinQueue("biz_apex", "loc_apex_hazratganj", "srv_apex_gen")
        assertTrue("Joining a closed queue must fail", joinResult.isFailure)

        // History of past tokens is preserved
        val pastTokens = repository.tokens.value.filter { it.locationId == "loc_apex_hazratganj" }
        assertTrue("Past tokens must be preserved when business closes", pastTokens.isNotEmpty())
    }

    // =========================================================================
    // 9. CUSTOMER CANCELLATION & ACCESS CONTROL
    // =========================================================================
    @Test
    fun test09_CustomerCancellation_SecurityAndIdempotency() = runBlocking {
        // Customer A creates token
        repository.signUpCustomer("Cust A", "custa_cancel@test.com", "Pass1234", "+91 95555 11111")
        val userA = repository.currentUser.value
        val tokenA = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()

        // Customer B creates token
        repository.signUpCustomer("Cust B", "custb_cancel@test.com", "Pass1234", "+91 95555 22222")
        val tokenB = repository.joinQueue("biz_apex", "loc_apex_kanpur", "srv_apex_gen").getOrThrow()

        // Customer B attempts to cancel Customer A's token -> Must be rejected with 403 SecurityException
        val attackCancel = repository.cancelToken(tokenA.id, "Malicious cancellation")
        assertTrue("Customer B must NOT be allowed to cancel Customer A's token", attackCancel.isFailure)
        assertTrue("Must throw SecurityException or 403", attackCancel.exceptionOrNull() is SecurityException)

        // Customer B cancels their own token -> Must succeed
        val selfCancel = repository.cancelToken(tokenB.id, "Change of plans")
        assertTrue("Customer B must be able to cancel own token", selfCancel.isSuccess)
        assertEquals(TokenStatus.CANCELLED, repository.tokens.value.first { it.id == tokenB.id }.status)

        // Customer B repeats cancellation -> Must be idempotent (returns success safely)
        val repeatCancel = repository.cancelToken(tokenB.id, "Repeated tap")
        assertTrue("Repeated cancellation must safely return success (idempotent)", repeatCancel.isSuccess)

        // Attempt to cancel already SERVED token -> Must fail
        val servedToken = repository.tokens.value.first { it.status == TokenStatus.SERVED }
        val cancelServed = repository.cancelToken(servedToken.id, "Try cancel served")
        assertTrue("Cancelling already served token must fail", cancelServed.isFailure)
    }

    // =========================================================================
    // 10 & 11. LIVE POSITION & ESTIMATED WAIT CALCULATION
    // =========================================================================
    @Test
    fun test10_LivePositionAndWaitTimeCalculation() = runBlocking {
        // For Apex Hazratganj: A-018 served, A-019 serving, A-020..A-026 waiting, A-027 (user token)
        val userToken = repository.tokens.value.first { it.tokenNumber == "A-027" }
        
        val peopleAhead = repository.getWaitingAheadCount("loc_apex_hazratganj", userToken)
        assertEquals("There should be exactly 7 waiting tokens ahead of A-027", 7, peopleAhead)

        val estimatedWait = repository.getEstimatedWaitMinutes("loc_apex_hazratganj", userToken, avgServiceMinutes = 4)
        assertEquals("Estimated wait should be (7+1)*4 = 32 minutes", 32, estimatedWait)

        // Cancel one token ahead (A-020)
        val token20 = repository.tokens.value.first { it.tokenNumber == "A-020" }
        repository.testOnlySetStaffLocation("dr.sameer@apexclinic.com", "loc_apex_hazratganj")
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        repository.cancelToken(token20.id, "Cancelled by staff")

        // Re-calculate people ahead
        val updatedAhead = repository.getWaitingAheadCount("loc_apex_hazratganj", userToken)
        assertEquals("Cancelled tokens must NOT count as waiting ahead", 6, updatedAhead)
    }

    // =========================================================================
    // 12 & 13. MULTI-BRANCH ISOLATION
    // =========================================================================
    @Test
    fun test11_MultiBranchIsolation() = runBlocking {
        val hazratganjTokens = repository.tokens.value.filter { it.locationId == "loc_apex_hazratganj" }
        val kanpurTokens = repository.tokens.value.filter { it.locationId == "loc_apex_kanpur" }

        // Assert tokens in Hazratganj do not bleed into Kanpur
        assertTrue("Hazratganj tokens must exist", hazratganjTokens.isNotEmpty())
        assertTrue("Branches must have separated token lists", hazratganjTokens.none { it.locationId == "loc_apex_kanpur" })
        assertTrue("Branches must have separated token lists", kanpurTokens.none { it.locationId == "loc_apex_hazratganj" })
    }

    // =========================================================================
    // 14. CONCURRENT QUEUE JOINS (10 & 100 SIMULTANEOUS JOINS)
    // =========================================================================
    @Test
    fun test12_ConcurrentQueueJoins_100SimultaneousRequests() = runBlocking {
        repository.loginAsMasterAdmin()
        val newBiz = repository.adminAddBusiness(
            name = "Express Lab",
            category = BusinessCategory.HEALTHCARE,
            description = "High load test lab",
            primaryCity = "Lucknow",
            plan = SubscriptionPlan.ENTERPRISE
        )
        val loc = repository.locations.value.first { it.businessId == newBiz.id }
        val srv = repository.services.value.first { it.businessId == newBiz.id }

        // 100 simultaneous concurrent joins with distinct customer accounts
        val deferredJoins = (1..100).map { i ->
            async {
                val customerUser = User(
                    id = "usr_concurrent_$i",
                    name = "Customer $i",
                    email = "customer$i@expresslab.com",
                    phone = "+91 98000 ${10000 + i}",
                    role = UserRole.USER,
                    accountStatus = AccountStatus.ACTIVE,
                    isVerified = true
                )
                repository.joinQueue(
                    businessId = newBiz.id,
                    locationId = loc.id,
                    serviceId = srv.id,
                    priority = if (i % 10 == 0) TokenPriority.VIP else TokenPriority.STANDARD,
                    notes = "Concurrent customer #$i",
                    customUser = customerUser
                )
            }
        }

        val results = deferredJoins.awaitAll()
        val successfulTokens = results.mapNotNull { it.getOrNull() }
        assertEquals("All 100 concurrent joins must succeed", 100, successfulTokens.size)

        val uniqueTokenNumbers = successfulTokens.map { it.tokenNumber }.toSet()
        assertEquals("All 100 generated token numbers must be 100% unique (zero collisions)", 100, uniqueTokenNumbers.size)

        val sequences = successfulTokens.map { it.sequenceNumber }.sorted()
        assertEquals("Sequences must be 1 to 100 strictly continuous", (1..100).toList(), sequences)
    }

    // =========================================================================
    // 15. CONCURRENT MERCHANT ACTIONS (CALL NEXT + SERVE RACE CONDITIONS)
    // =========================================================================
    @Test
    fun test13_ConcurrentMerchantActions_RaceConditionSafety() = runBlocking {
        repository.loginAsMasterAdmin()
        val biz = repository.adminAddBusiness("Race Condition Shop", BusinessCategory.REPAIR, "Desc", "Lucknow", SubscriptionPlan.PRO)
        val loc = repository.locations.value.first { it.businessId == biz.id }
        val srv = repository.services.value.first { it.businessId == biz.id }

        // Enqueue 10 tokens
        for (i in 1..10) {
            repository.signUpCustomer("Race User $i", "race$i@test.com", "Pass1234", "+91 97777 0000$i")
            repository.joinQueue(biz.id, loc.id, srv.id)
        }

        // Two staff members call next simultaneously
        repository.loginAsMasterAdmin() // Admin has full access
        val callDeferred1 = async { repository.callNextToken(loc.id, "Counter 1", "Staff 1") }
        val callDeferred2 = async { repository.callNextToken(loc.id, "Counter 2", "Staff 2") }

        val res1 = callDeferred1.await()
        val res2 = callDeferred2.await()

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)

        val tok1 = res1.getOrThrow()
        val tok2 = res2.getOrThrow()

        assertNotEquals("Concurrent call next must not assign same token to both staff", tok1.id, tok2.id)
    }

    // =========================================================================
    // 16. END-TO-END ACCEPTANCE TEST (POINT 30 IN SPECIFICATION)
    // =========================================================================
    @Test
    fun test14_FinalEndToEndAcceptanceScenario() = runBlocking {
        // 1. Merchant logs in and opens business
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val locId = "loc_apex_kanpur"

        // 2. Customers join: Customer A, Customer B, Customer C, Customer D
        repository.signUpCustomer("Customer A", "accept_a@test.com", "Pass1234", "+91 96666 00001")
        val tokA = repository.joinQueue("biz_apex", locId, "srv_apex_gen").getOrThrow()

        repository.signUpCustomer("Customer B", "accept_b@test.com", "Pass1234", "+91 96666 00002")
        val tokB = repository.joinQueue("biz_apex", locId, "srv_apex_gen").getOrThrow()

        repository.signUpCustomer("Customer C", "accept_c@test.com", "Pass1234", "+91 96666 00003")
        val tokC = repository.joinQueue("biz_apex", locId, "srv_apex_gen").getOrThrow()

        repository.signUpCustomer("Customer D", "accept_d@test.com", "Pass1234", "+91 96666 00004")
        val tokD = repository.joinQueue("biz_apex", locId, "srv_apex_gen").getOrThrow()

        // 3. Merchant calls next -> A becomes CALLED
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val calledA = repository.callNextToken(locId, "Counter 1", "Dr. Sameer").getOrThrow()
        assertEquals(tokA.id, calledA.id)
        assertEquals(TokenStatus.CALLED, calledA.status)

        // 4. Merchant starts serving A -> A becomes SERVING
        val servingA = repository.startServing(calledA.id, "Dr. Sameer").getOrThrow()
        assertEquals(TokenStatus.SERVING, servingA.status)

        // 5. Merchant marks A as served -> A becomes SERVED
        val servedA = repository.markServed(servingA.id, "Dr. Sameer").getOrThrow()
        assertEquals(TokenStatus.SERVED, servedA.status)

        // 6. Merchant calls next -> B becomes CALLED
        val calledB = repository.callNextToken(locId, "Counter 1", "Dr. Sameer").getOrThrow()
        assertEquals(tokB.id, calledB.id)
        assertEquals(TokenStatus.CALLED, calledB.status)

        // 7. Customer C logs in and cancels -> C becomes CANCELLED
        repository.loginUser("accept_c@test.com", "Pass1234")
        val cancelC = repository.cancelToken(tokC.id, "Cancelled by user").getOrThrow()
        assertEquals(TokenStatus.CANCELLED, cancelC.status)

        // 8. Queue recalculates correctly for Customer D
        val peopleAheadD = repository.getWaitingAheadCount(locId, tokD)
        assertEquals("People ahead of D should be 0 because A & B are called/served and C is cancelled", 0, peopleAheadD)

        // 9. Merchant skips next eligible token (D)
        repository.loginUser("dr.sameer@apexclinic.com", "apex123")
        val skippedD = repository.skipToken(tokD.id, "Dr. Sameer").getOrThrow()
        assertEquals(TokenStatus.SKIPPED, skippedD.status)
    }
}
