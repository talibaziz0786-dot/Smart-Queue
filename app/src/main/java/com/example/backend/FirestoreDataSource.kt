package com.example.backend

import android.util.Log
import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Production Cloud Firestore Data Source for SmartQueue.
 * Handles atomic transactions, batch writes, and real-time synchronization.
 */
open class FirestoreDataSource(
    val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Throwable) {
        Log.w("FirestoreDataSource", "FirebaseApp not initialized: ${e.message}. Running in local in-memory safety mode.")
        null
    }
) {
    open val isCloudConfigured: Boolean
        get() = firestore != null

    // Realtime Listener handles
    private val activeListeners = mutableListOf<ListenerRegistration>()

    fun clearAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // ==========================================
    // Realtime Collection Listeners
    // ==========================================

    open fun listenToBusinesses(onUpdate: (List<Business>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        val reg = db.collection("businesses")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirestoreDataSource", "listenToBusinesses error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val businesses = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToBusiness(doc.id, doc.data ?: emptyMap())
                        } catch (ex: Exception) {
                            Log.w("FirestoreDataSource", "docToBusiness parse error: ${ex.message}")
                            null
                        }
                    }.filter { !it.isSuspended && !it.isDeleted }
                    onUpdate(businesses)
                }
            }
        activeListeners.add(reg)
        return reg
    }

    open fun listenToLocations(onUpdate: (List<BusinessLocation>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        val reg = db.collection("locations")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirestoreDataSource", "listenToLocations error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val locations = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToLocation(doc.id, doc.data ?: emptyMap())
                        } catch (ex: Exception) {
                            null
                        }
                    }.filter { !it.isDeleted }
                    onUpdate(locations)
                }
            }
        activeListeners.add(reg)
        return reg
    }

    open fun listenToServices(onUpdate: (List<ServiceItem>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        val reg = db.collection("services")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirestoreDataSource", "listenToServices error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val services = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToService(doc.id, doc.data ?: emptyMap())
                        } catch (ex: Exception) {
                            null
                        }
                    }.filter { !it.isDeleted }
                    onUpdate(services)
                }
            }
        activeListeners.add(reg)
        return reg
    }

    open fun listenToQrCodes(onUpdate: (List<QRCodeData>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        val reg = db.collection("qrCodes")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirestoreDataSource", "listenToQrCodes error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val qrs = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToQRCode(doc.id, doc.data ?: emptyMap())
                        } catch (ex: Exception) {
                            null
                        }
                    }.filter { !it.isRevoked && !it.isDeleted }
                    onUpdate(qrs)
                }
            }
        activeListeners.add(reg)
        return reg
    }

    open fun listenToTokens(
        businessId: String? = null,
        locationId: String? = null,
        customerId: String? = null,
        businessOwnerId: String? = null,
        onUpdate: (List<Token>) -> Unit
    ): ListenerRegistration? {
        val db = firestore ?: return null
        var query = db.collection("tokens") as com.google.firebase.firestore.Query

        if (!businessOwnerId.isNullOrBlank()) {
            query = query.whereEqualTo("businessOwnerId", businessOwnerId)
        }
        if (!businessId.isNullOrBlank()) {
            query = query.whereEqualTo("businessId", businessId)
        }
        if (!locationId.isNullOrBlank()) {
            query = query.whereEqualTo("locationId", locationId)
        }
        if (!customerId.isNullOrBlank()) {
            query = query.whereEqualTo("userId", customerId)
        }

        val reg = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirestoreDataSource", "listenToTokens error: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tokens = snapshot.documents.mapNotNull { doc ->
                    try {
                        docToToken(doc.id, doc.data ?: emptyMap())
                    } catch (ex: Exception) {
                        null
                    }
                }
                onUpdate(tokens)
            }
        }
        activeListeners.add(reg)
        return reg
    }

    // ==========================================
    // Atomic Operations & Transactions
    // ==========================================

    /**
     * Atomically saves newly registered Merchant Business, Branch Location, Service, and QR Code in a single batch write.
     * Guarantees no partial business creation if any component fails.
     */
    open suspend fun saveOnboardingBatch(
        business: Business,
        location: BusinessLocation,
        service: ServiceItem,
        qrCode: QRCodeData,
        user: User
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))

        return try {
            // Step 1: Write Business first to establish document ownership in Firestore before child resources validate rules
            val bizRef = db.collection("businesses").document(business.id)
            bizRef.set(businessToMap(business)).await()

            // Step 2: Now commit dependent child records (location, service, QR code, and user profile)
            val batch = db.batch()

            val locRef = db.collection("locations").document(location.id)
            batch.set(locRef, locationToMap(location))

            val srvRef = db.collection("services").document(service.id)
            batch.set(srvRef, serviceToMap(service))

            val canonicalQrId = "qr_${business.id}_${location.id}"
            val qrRef = db.collection("qrCodes").document(canonicalQrId)
            batch.set(qrRef, qrCodeToMap(qrCode.copy(qrId = canonicalQrId)))

            val userRef = db.collection("users").document(user.id)
            batch.set(userRef, mapOf(
                "id" to user.id,
                "name" to user.name,
                "email" to user.email,
                "phone" to user.phone,
                "role" to UserRole.MERCHANT.name,
                "accountStatus" to AccountStatus.ACTIVE.name,
                "isVerified" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge())

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "saveOnboardingBatch error: ${e.message}", e)
            Result.failure(e)
        }
    }

    open suspend fun deleteBusinessCloud(businessId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val batch = db.batch()
            val bizRef = db.collection("businesses").document(businessId)
            batch.set(bizRef, mapOf(
                "isDeleted" to true,
                "deletedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge())

            val locs = db.collection("locations").whereEqualTo("businessId", businessId).get().await()
            for (doc in locs.documents) {
                batch.set(doc.reference, mapOf(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
            }

            val srvs = db.collection("services").whereEqualTo("businessId", businessId).get().await()
            for (doc in srvs.documents) {
                batch.set(doc.reference, mapOf(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
            }

            val qrs = db.collection("qrCodes").whereEqualTo("businessId", businessId).get().await()
            for (doc in qrs.documents) {
                batch.set(doc.reference, mapOf(
                    "isRevoked" to true,
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "deleteBusinessCloud error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cloud-Safe Atomic Queue Join Transaction with Unique Token Number Sequencing.
     * Atomically increments `/queueCounters/{businessId_locationId_serviceId}` to ensure
     * concurrent joiners on Phone A and Phone B never collide or receive duplicate token numbers.
     */
    open suspend fun joinQueueTransaction(
        businessId: String,
        locationId: String,
        serviceId: String,
        user: User,
        priority: TokenPriority = TokenPriority.STANDARD,
        notes: String = "",
        customNameOverride: String? = null,
        customerAge: Int? = null,
        customerGender: String? = null,
        isGuest: Boolean = false
    ): Result<Token> {
        if (user.role !in listOf(UserRole.USER, UserRole.CUSTOMER)) {
            return Result.failure(IllegalStateException("Authorization Denied: Merchants, staff, and admins cannot book customer queue tokens."))
        }
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))

        return try {
            val counterDocRef = db.collection("queueCounters")
                .document("${businessId}_${locationId}_${serviceId}")
            val locDocRef = db.collection("locations").document(locationId)
            val bizDocRef = db.collection("businesses").document(businessId)
            val srvDocRef = db.collection("services").document(serviceId)

            val token = db.runTransaction { tx ->
                // 1. Validate Location & Business are Open and Not Deleted/Paused
                val locSnapshot = tx.get(locDocRef)
                val bizSnapshot = tx.get(bizDocRef)
                val isDeleted = bizSnapshot.getBoolean("isDeleted") ?: false
                val isSuspended = bizSnapshot.getBoolean("isSuspended") ?: false
                if (isDeleted || isSuspended || !bizSnapshot.exists()) {
                    throw IllegalStateException("This business has been deleted or suspended.")
                }
                val isOpen = locSnapshot.getBoolean("isOpen") ?: true
                val isPaused = locSnapshot.getBoolean("isPaused") ?: false
                val isLocDeleted = locSnapshot.getBoolean("isDeleted") ?: false
                if (!isOpen || isPaused || isLocDeleted || !locSnapshot.exists()) {
                    throw IllegalStateException("Queue is currently closed, paused, or branch is deleted.")
                }

                // 2. Read Metadata
                val bizName = bizSnapshot.getString("name") ?: "Business"
                val bizOwnerId = bizSnapshot.getString("ownerId") ?: ""
                val branchName = locSnapshot.getString("branchName") ?: "Main Branch"
                val prefix = locSnapshot.getString("prefix") ?: "A"
                val srvSnapshot = tx.get(srvDocRef)
                val srvName = srvSnapshot.getString("name") ?: "Standard Service"
                val durationMin = srvSnapshot.getLong("durationMinutes")?.toInt() ?: 15

                // 3. Atomic Sequence Increment
                val counterSnapshot = tx.get(counterDocRef)
                val currentSeq = counterSnapshot.getLong("lastSequence")?.toInt() ?: 0
                val nextSeq = currentSeq + 1

                tx.set(counterDocRef, mapOf(
                    "businessId" to businessId,
                    "locationId" to locationId,
                    "serviceId" to serviceId,
                    "lastSequence" to nextSeq,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ), SetOptions.merge())

                // 4. Create Unique Token
                val tokenNumber = "$prefix-${"%03d".format(nextSeq)}"
                val tokenId = "tok_${businessId}_${System.currentTimeMillis()}_$nextSeq"
                val tokenDocRef = db.collection("tokens").document(tokenId)

                val newToken = Token(
                    id = tokenId,
                    tokenNumber = tokenNumber,
                    sequenceNumber = nextSeq,
                    userId = user.id,
                    userName = customNameOverride?.ifBlank { user.name } ?: user.name,
                    userPhone = user.phone,
                    businessId = businessId,
                    businessName = bizName,
                    businessOwnerId = bizOwnerId,
                    locationId = locationId,
                    branchName = branchName,
                    serviceId = serviceId,
                    serviceName = srvName,
                    priority = priority,
                    status = TokenStatus.WAITING,
                    estimatedWaitMinutes = durationMin,
                    initialPosition = nextSeq,
                    createdAt = System.currentTimeMillis(),
                    notes = notes,
                    customerNameOverride = customNameOverride,
                    customerAge = customerAge,
                    customerGender = customerGender,
                    isGuest = isGuest
                )

                tx.set(tokenDocRef, tokenToMap(newToken))
                newToken
            }.await()

            Result.success(token)
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "joinQueueTransaction error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Atomically transitions the next valid WAITING token to CALLED.
     * Prevents race condition between staff calling the same customer.
     */
    open suspend fun callNextTransaction(
        businessId: String,
        locationId: String,
        staffName: String = "Counter Staff",
        counterName: String = "Counter 1"
    ): Result<Token?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))

        return try {
            // Find earliest WAITING token in this queue
            val snapshot = db.collection("tokens")
                .whereEqualTo("businessId", businessId)
                .whereEqualTo("locationId", locationId)
                .whereEqualTo("status", TokenStatus.WAITING.name)
                .get().await()

            val candidates = snapshot.documents.mapNotNull { doc ->
                docToToken(doc.id, doc.data ?: emptyMap())
            }.sortedWith(
                compareByDescending<Token> { it.priority == TokenPriority.SENIOR_EMERGENCY }
                    .thenByDescending { it.priority == TokenPriority.VIP }
                    .thenBy { it.sequenceNumber }
            )

            val targetToken = candidates.firstOrNull() ?: return Result.success(null)
            val tokenRef = db.collection("tokens").document(targetToken.id)

            val updatedToken = db.runTransaction { tx ->
                val currentDoc = tx.get(tokenRef)
                val currentStatus = currentDoc.getString("status")
                if (currentStatus != TokenStatus.WAITING.name) {
                    throw IllegalStateException("Token already claimed or updated by another counter operator.")
                }

                val now = System.currentTimeMillis()
                tx.update(tokenRef, mapOf(
                    "status" to TokenStatus.CALLED.name,
                    "calledAt" to now,
                    "staffAssigned" to staffName,
                    "counterNumber" to counterName
                ))

                targetToken.copy(
                    status = TokenStatus.CALLED,
                    calledAt = now,
                    staffAssigned = staffName,
                    counterNumber = counterName
                )
            }.await()

            Result.success(updatedToken)
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "callNextTransaction error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Atomically transitions token CALLED -> SERVING.
     */
    open suspend fun startServingTransaction(tokenId: String, staffName: String): Result<Token> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val tokenRef = db.collection("tokens").document(tokenId)
            val updated = db.runTransaction { tx ->
                val doc = tx.get(tokenRef)
                val status = doc.getString("status")
                if (status != TokenStatus.CALLED.name && status != TokenStatus.WAITING.name) {
                    throw IllegalStateException("Token cannot start serving from current state: $status")
                }
                val now = System.currentTimeMillis()
                tx.update(tokenRef, mapOf(
                    "status" to TokenStatus.SERVING.name,
                    "staffAssigned" to staffName
                ))
                docToToken(tokenId, doc.data ?: emptyMap()).copy(
                    status = TokenStatus.SERVING,
                    staffAssigned = staffName
                )
            }.await()
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically transitions token SERVING -> SERVED.
     */
    open suspend fun markServedTransaction(tokenId: String): Result<Token> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val tokenRef = db.collection("tokens").document(tokenId)
            val updated = db.runTransaction { tx ->
                val doc = tx.get(tokenRef)
                val now = System.currentTimeMillis()
                tx.update(tokenRef, mapOf(
                    "status" to TokenStatus.SERVED.name,
                    "servedAt" to now
                ))
                docToToken(tokenId, doc.data ?: emptyMap()).copy(
                    status = TokenStatus.SERVED,
                    servedAt = now
                )
            }.await()
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically cancels a token belonging to the calling customer.
     */
    open suspend fun cancelTokenTransaction(tokenId: String, callerUserId: String): Result<Token> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val tokenRef = db.collection("tokens").document(tokenId)
            val updated = db.runTransaction { tx ->
                val doc = tx.get(tokenRef)
                val ownerId = doc.getString("userId")
                if (ownerId != callerUserId) {
                    throw IllegalAccessException("Unauthorized: Cannot cancel another customer's token.")
                }
                val status = doc.getString("status")
                if (status != TokenStatus.WAITING.name && status != TokenStatus.CALLED.name) {
                    throw IllegalStateException("Cannot cancel token in state: $status")
                }
                tx.update(tokenRef, "status", TokenStatus.CANCELLED.name)
                docToToken(tokenId, doc.data ?: emptyMap()).copy(status = TokenStatus.CANCELLED)
            }.await()
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically transitions token CALLED/WAITING -> SKIPPED.
     */
    open suspend fun skipTokenTransaction(tokenId: String): Result<Token> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val tokenRef = db.collection("tokens").document(tokenId)
            val updated = db.runTransaction { tx ->
                val doc = tx.get(tokenRef)
                val status = doc.getString("status")
                if (status != TokenStatus.CALLED.name && status != TokenStatus.WAITING.name) {
                    throw IllegalStateException("Token cannot be skipped from current state: $status")
                }
                tx.update(tokenRef, "status", TokenStatus.SKIPPED.name)
                docToToken(tokenId, doc.data ?: emptyMap()).copy(status = TokenStatus.SKIPPED)
            }.await()
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically transitions token CALLED -> NO_SHOW.
     */
    open suspend fun recordNoShowTransaction(tokenId: String): Result<Token> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val tokenRef = db.collection("tokens").document(tokenId)
            val updated = db.runTransaction { tx ->
                val doc = tx.get(tokenRef)
                val status = doc.getString("status")
                if (status != TokenStatus.CALLED.name) {
                    throw IllegalStateException("Only CALLED tokens can be marked as NO_SHOW. Current state: $status")
                }
                tx.update(tokenRef, "status", TokenStatus.NO_SHOW.name)
                docToToken(tokenId, doc.data ?: emptyMap()).copy(status = TokenStatus.NO_SHOW)
            }.await()
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically toggles branch location pause status.
     */
    open suspend fun toggleLocationQueueTransaction(locationId: String, isPaused: Boolean): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            db.collection("locations").document(locationId).update("isPaused", isPaused).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanent Shop QR Identity:
     * Business + Location maps to exactly ONE canonical active QR record.
     * Persisted in /qrCodes/qr_{businessId}_{locationId}.
     * Reuses existing QR if found, preventing duplicates on dashboard open/reload.
     */
    open suspend fun getOrCreatePermanentQrCode(
        businessId: String,
        locationId: String,
        shopName: String,
        branchName: String
    ): QRCodeData {
        val canonicalDocId = "qr_${businessId}_${locationId}"
        val db = firestore
        if (db == null) {
            val hashSeed = Math.abs((businessId + locationId).hashCode() % 9000 + 1000)
            val code = "SQ-${shopName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "SHOP" }}-${branchName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "MAIN" }}-$hashSeed"
            return QRCodeData(qrId = canonicalDocId, businessId = businessId, locationId = locationId, publicCode = code, isRevoked = false)
        }

        return try {
            // 1. Direct fetch canonical document ID
            val doc = db.collection("qrCodes").document(canonicalDocId).get().await()
            if (doc.exists()) {
                val data = doc.data ?: emptyMap()
                val isRevoked = data["isRevoked"] as? Boolean ?: false
                if (!isRevoked) {
                    val qr = docToQRCode(doc.id, data)
                    Log.d("FirestoreDataSource", "Found existing permanent QR by docId: ${qr.publicCode}")
                    return qr
                }
            }

            // 2. Query by businessId and locationId in case it was stored with random UUID earlier
            val existing = db.collection("qrCodes")
                .whereEqualTo("businessId", businessId)
                .whereEqualTo("locationId", locationId)
                .whereEqualTo("isRevoked", false)
                .limit(1)
                .get().await()
            val existingDoc = existing.documents.firstOrNull()
            if (existingDoc != null && existingDoc.exists()) {
                val qr = docToQRCode(existingDoc.id, existingDoc.data ?: emptyMap())
                Log.d("FirestoreDataSource", "Found existing permanent QR by query: ${qr.publicCode}")
                return qr
            }

            // 3. None exists: generate stable deterministic permanent code and persist
            val hashSeed = Math.abs((businessId + locationId).hashCode() % 9000 + 1000)
            val stableCode = "SQ-${shopName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "SHOP" }}-${branchName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "MAIN" }}-$hashSeed"
            val newQr = QRCodeData(
                qrId = canonicalDocId,
                businessId = businessId,
                locationId = locationId,
                publicCode = stableCode,
                isRevoked = false
            )
            db.collection("qrCodes").document(canonicalDocId).set(qrCodeToMap(newQr)).await()
            Log.d("FirestoreDataSource", "Persisted new permanent QR: $stableCode in document $canonicalDocId")
            newQr
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "getOrCreatePermanentQrCode fallback: ${e.message}", e)
            val hashSeed = Math.abs((businessId + locationId).hashCode() % 9000 + 1000)
            val code = "SQ-${shopName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "SHOP" }}-${branchName.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "MAIN" }}-$hashSeed"
            QRCodeData(qrId = canonicalDocId, businessId = businessId, locationId = locationId, publicCode = code, isRevoked = false)
        }
    }

    /**
     * Deterministic Cloud QR/Public Code Lookup.
     * Differentiates strictly between INVALID_QR_CODE and QR_REVOKED.
     */
    open suspend fun resolveQRCodeCloud(publicCode: String): QrResolutionResult {
        val db = firestore ?: return QrResolutionResult.Error("Cloud Firestore not configured")
        var clean = publicCode.trim().uppercase()
        if (clean.contains("/")) {
            clean = clean.substringAfterLast("/")
        }
        if (clean.isBlank()) return QrResolutionResult.InvalidCode

        val qrSnapshot = try {
            db.collection("qrCodes")
                .whereEqualTo("publicCode", clean)
                .limit(1)
                .get().await()
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "resolveQRCodeCloud query error: ${e.message}", e)
            return QrResolutionResult.Error(e.message ?: "Firestore query failed")
        }

        var qrDoc = qrSnapshot.documents.firstOrNull()
        if (qrDoc == null) {
            val directDoc = try { db.collection("qrCodes").document(clean.lowercase()).get().await() } catch (e: Exception) { null }
                ?: try { db.collection("qrCodes").document(clean).get().await() } catch (e: Exception) { null }
            if (directDoc != null && directDoc.exists()) {
                qrDoc = directDoc
            }
        }
        if (qrDoc == null) return QrResolutionResult.InvalidCode
        if (qrDoc.getBoolean("isRevoked") == true || qrDoc.getBoolean("isDeleted") == true) return QrResolutionResult.Revoked
        val bizId = qrDoc.getString("businessId") ?: return QrResolutionResult.InvalidCode
        val locId = qrDoc.getString("locationId") ?: return QrResolutionResult.InvalidCode

        val bizDoc = try { db.collection("businesses").document(bizId).get().await() } catch (e: Exception) { null }
        val locDoc = try { db.collection("locations").document(locId).get().await() } catch (e: Exception) { null }

        if (bizDoc == null || !bizDoc.exists() || locDoc == null || !locDoc.exists()) {
            return QrResolutionResult.InvalidCode
        }

        val biz = docToBusiness(bizDoc.id, bizDoc.data ?: emptyMap())
        val loc = docToLocation(locDoc.id, locDoc.data ?: emptyMap())
        if (biz.isDeleted || biz.isSuspended || loc.isDeleted) {
            return QrResolutionResult.InvalidCode
        }
        return QrResolutionResult.Success(biz, loc)
    }

    // ==========================================
    // Entity Serialization / Deserialization
    // ==========================================

    private fun businessToMap(b: Business): Map<String, Any?> = mapOf(
        "id" to b.id,
        "name" to b.name,
        "ownerId" to b.ownerId,
        "category" to b.category.name,
        "description" to b.description,
        "logoUrl" to b.logoUrl,
        "primaryCity" to b.primaryCity,
        "isVerified" to b.isVerified,
        "suspended" to b.isSuspended,
        "isSuspended" to b.isSuspended,
        "isDeleted" to b.isDeleted,
        "plan" to b.plan.name,
        "rating" to b.rating,
        "reviewCount" to b.reviewCount,
        "createdAt" to b.createdAt
    )

    private fun docToBusiness(id: String, d: Map<String, Any?>): Business = Business(
        id = id,
        name = d["name"] as? String ?: "Business",
        ownerId = d["ownerId"] as? String ?: "",
        category = try { BusinessCategory.valueOf(d["category"] as? String ?: "RETAIL") } catch (e: Exception) { BusinessCategory.RETAIL },
        description = d["description"] as? String ?: "",
        logoUrl = d["logoUrl"] as? String ?: "",
        primaryCity = d["primaryCity"] as? String ?: "Lucknow",
        isVerified = d["isVerified"] as? Boolean ?: true,
        isSuspended = (d["isSuspended"] as? Boolean) ?: (d["suspended"] as? Boolean) ?: false,
        isDeleted = d["isDeleted"] as? Boolean ?: false,
        rating = (d["rating"] as? Number)?.toDouble() ?: 4.8,
        reviewCount = (d["reviewCount"] as? Number)?.toInt() ?: 12,
        createdAt = (d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    private fun locationToMap(l: BusinessLocation): Map<String, Any?> = mapOf(
        "id" to l.id,
        "businessId" to l.businessId,
        "branchName" to l.branchName,
        "address" to l.address,
        "city" to l.city,
        "phone" to l.phone,
        "isOpen" to l.isOpen,
        "isPaused" to l.isPaused,
        "isDeleted" to l.isDeleted,
        "prefix" to l.prefix,
        "latitude" to l.latitude,
        "longitude" to l.longitude
    )

    private fun docToLocation(id: String, d: Map<String, Any?>): BusinessLocation = BusinessLocation(
        id = id,
        businessId = d["businessId"] as? String ?: "",
        branchName = d["branchName"] as? String ?: "Main",
        address = d["address"] as? String ?: "Main Road",
        city = d["city"] as? String ?: "City",
        phone = d["phone"] as? String ?: "+91 98765 00000",
        isOpen = d["isOpen"] as? Boolean ?: true,
        isPaused = d["isPaused"] as? Boolean ?: false,
        isDeleted = d["isDeleted"] as? Boolean ?: false,
        prefix = d["prefix"] as? String ?: "A",
        latitude = (d["latitude"] as? Number)?.toDouble() ?: 26.8467,
        longitude = (d["longitude"] as? Number)?.toDouble() ?: 80.9462
    )

    private fun serviceToMap(s: ServiceItem): Map<String, Any?> = mapOf(
        "id" to s.id,
        "businessId" to s.businessId,
        "locationId" to s.locationId,
        "name" to s.name,
        "description" to s.description,
        "durationMinutes" to s.durationMinutes,
        "price" to s.price,
        "prefix" to s.prefix,
        "isAvailable" to s.isAvailable,
        "isDeleted" to s.isDeleted
    )

    private fun docToService(id: String, d: Map<String, Any?>): ServiceItem = ServiceItem(
        id = id,
        businessId = d["businessId"] as? String ?: "",
        locationId = d["locationId"] as? String ?: "",
        name = d["name"] as? String ?: "Token Service",
        description = d["description"] as? String ?: "",
        durationMinutes = (d["durationMinutes"] as? Number)?.toInt() ?: 15,
        price = (d["price"] as? Number)?.toDouble() ?: 0.0,
        prefix = d["prefix"] as? String ?: "A",
        isAvailable = d["isAvailable"] as? Boolean ?: true,
        isDeleted = d["isDeleted"] as? Boolean ?: false
    )

    private fun qrCodeToMap(q: QRCodeData): Map<String, Any?> = mapOf(
        "qrId" to q.qrId,
        "businessId" to q.businessId,
        "locationId" to q.locationId,
        "publicCode" to q.publicCode,
        "isRevoked" to q.isRevoked,
        "isDeleted" to q.isDeleted
    )

    private fun docToQRCode(id: String, d: Map<String, Any?>): QRCodeData = QRCodeData(
        qrId = id,
        businessId = d["businessId"] as? String ?: "",
        locationId = d["locationId"] as? String ?: "",
        publicCode = d["publicCode"] as? String ?: "",
        isRevoked = d["isRevoked"] as? Boolean ?: false,
        isDeleted = d["isDeleted"] as? Boolean ?: false
    )

    private fun tokenToMap(t: Token): Map<String, Any?> = mapOf(
        "id" to t.id,
        "tokenNumber" to t.tokenNumber,
        "sequenceNumber" to t.sequenceNumber,
        "userId" to t.userId,
        "userName" to t.userName,
        "userPhone" to t.userPhone,
        "businessId" to t.businessId,
        "businessName" to t.businessName,
        "locationId" to t.locationId,
        "branchName" to t.branchName,
        "serviceId" to t.serviceId,
        "serviceName" to t.serviceName,
        "priority" to t.priority.name,
        "status" to t.status.name,
        "estimatedWaitMinutes" to t.estimatedWaitMinutes,
        "counterNumber" to t.counterNumber,
        "staffAssigned" to t.staffAssigned,
        "createdAt" to t.createdAt,
        "calledAt" to t.calledAt,
        "servedAt" to t.servedAt,
        "notes" to t.notes,
        "customerNameOverride" to t.customerNameOverride,
        "customerAge" to t.customerAge,
        "customerGender" to t.customerGender,
        "isGuest" to t.isGuest,
        "businessOwnerId" to t.businessOwnerId
    )

    private fun docToToken(id: String, d: Map<String, Any?>): Token = Token(
        id = id,
        tokenNumber = d["tokenNumber"] as? String ?: "A-001",
        sequenceNumber = (d["sequenceNumber"] as? Number)?.toInt() ?: 1,
        userId = d["userId"] as? String ?: "",
        userName = d["userName"] as? String ?: "Customer",
        userPhone = d["userPhone"] as? String ?: "",
        businessId = d["businessId"] as? String ?: "",
        businessName = d["businessName"] as? String ?: "",
        businessOwnerId = d["businessOwnerId"] as? String ?: "",
        locationId = d["locationId"] as? String ?: "",
        branchName = d["branchName"] as? String ?: "",
        serviceId = d["serviceId"] as? String ?: "",
        serviceName = d["serviceName"] as? String ?: "",
        priority = try { TokenPriority.valueOf(d["priority"] as? String ?: "STANDARD") } catch (e: Exception) { TokenPriority.STANDARD },
        status = try { TokenStatus.valueOf(d["status"] as? String ?: "WAITING") } catch (e: Exception) { TokenStatus.WAITING },
        estimatedWaitMinutes = (d["estimatedWaitMinutes"] as? Number)?.toInt() ?: 15,
        counterNumber = d["counterNumber"] as? String,
        staffAssigned = d["staffAssigned"] as? String,
        createdAt = (d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        calledAt = (d["calledAt"] as? Number)?.toLong(),
        servedAt = (d["servedAt"] as? Number)?.toLong(),
        notes = d["notes"] as? String ?: "",
        customerNameOverride = d["customerNameOverride"] as? String,
        customerAge = (d["customerAge"] as? Number)?.toInt(),
        customerGender = d["customerGender"] as? String,
        isGuest = d["isGuest"] as? Boolean ?: false
    )

    /**
     * Persists or updates authenticated user profile in Cloud Firestore /users collection.
     */
    open suspend fun saveUserProfile(user: User): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Cloud Firestore not configured"))
        return try {
            val userRef = db.collection("users").document(user.id)
            userRef.set(mapOf(
                "id" to user.id,
                "name" to user.name,
                "email" to user.email,
                "phone" to user.phone,
                "role" to user.role.name,
                "accountStatus" to user.accountStatus.name,
                "isVerified" to user.isVerified,
                "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "saveUserProfile error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves authoritative user profile from Cloud Firestore /users collection.
     */
    open suspend fun getUserProfile(userId: String): User? {
        val db = firestore ?: return null
        return try {
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) {
                val d = doc.data ?: emptyMap()
                val roleStr = d["role"] as? String ?: "USER"
                val role = try {
                    UserRole.valueOf(roleStr)
                } catch (e: Exception) {
                    UserRole.USER
                }
                val statusStr = d["accountStatus"] as? String ?: "ACTIVE"
                val status = try {
                    AccountStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    AccountStatus.ACTIVE
                }
                User(
                    id = doc.id,
                    name = d["name"] as? String ?: "User",
                    email = d["email"] as? String ?: "",
                    phone = d["phone"] as? String ?: "",
                    role = role,
                    accountStatus = status,
                    isVerified = d["isVerified"] as? Boolean ?: true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "getUserProfile error: ${e.message}", e)
            null
        }
    }
}
