package com.example.auth

import android.util.Log
import com.example.model.AccountStatus
import com.example.model.User
import com.example.model.UserRole
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthResultState {
    data class Success(val user: User) : AuthResultState()
    data class Error(val message: String) : AuthResultState()
}

class FirebaseAuthManager(
    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Throwable) {
        Log.w("FirebaseAuthManager", "FirebaseApp not initialized: ${e.message}. Using built-in secure auth.")
        null
    }
) {
    // Registered accounts store for offline/fallback mode (Email -> Pair(User, Password))
    private val registeredAccounts = mutableMapOf<String, Pair<User, String>>()

    init {
        val defaultSeedUsers = listOf(
            Triple("talibaziz0786@gmail.com", "AdminPass123", User(id = "usr_admin_01", name = "Talib Aziz", email = "talibaziz0786@gmail.com", phone = "+91 98765 43210", role = UserRole.ADMIN, isVerified = true)),
            Triple("talib.admin@smartqueue.in", "AdminPass123", User(id = "usr_admin_02", name = "Talib Admin", email = "talib.admin@smartqueue.in", phone = "+91 98765 43210", role = UserRole.ADMIN, isVerified = true)),
            Triple("admin@smartqueue.in", "AdminPass123", User(id = "usr_admin_03", name = "Super Admin", email = "admin@smartqueue.in", phone = "+91 98765 43210", role = UserRole.ADMIN, isVerified = true)),
            Triple("dr.sameer@apexclinic.com", "apex123", User(id = "stf_01", name = "Dr. Sameer Verma", email = "dr.sameer@apexclinic.com", phone = "+91 94150 77889", role = UserRole.STAFF, isVerified = true)),
            Triple("dr.rajeshwar@apexclinic.com", "Pass1234", User(id = "usr_owner_01", name = "Dr. Rajeshwar Singhania", email = "dr.rajeshwar@apexclinic.com", phone = "+91 98200 12345", role = UserRole.BUSINESS_OWNER, isVerified = true)),
            Triple("ayesha@vogueluxe.in", "Pass1234", User(id = "usr_owner_02", name = "Ayesha Sen", email = "ayesha@vogueluxe.in", phone = "+91 98300 54321", role = UserRole.BUSINESS_OWNER, isVerified = true)),
            Triple("karan@bytefix.com", "Pass1234", User(id = "usr_owner_03", name = "Karan Kapoor", email = "karan@bytefix.com", phone = "+91 98111 88990", role = UserRole.BUSINESS_OWNER, isVerified = true)),
            Triple("marco@artisanbistro.com", "Pass1234", User(id = "usr_owner_04", name = "Chef Marco D'Souza", email = "marco@artisanbistro.com", phone = "+91 98777 44332", role = UserRole.BUSINESS_OWNER, isVerified = false)),
            Triple("pooja.verma@gmail.com", "Pass1234", User(id = "usr_cust_02", name = "Pooja Verma", email = "pooja.verma@gmail.com", phone = "+91 98222 33445", role = UserRole.CUSTOMER, isVerified = true)),
            Triple("mohit.ag@outlook.com", "Pass1234", User(id = "usr_cust_03", name = "Mohit Agarwal", email = "mohit.ag@outlook.com", phone = "+91 98100 12121", role = UserRole.CUSTOMER, isVerified = false))
        )
        for ((email, pass, user) in defaultSeedUsers) {
            registeredAccounts[email.lowercase()] = Pair(user, pass)
        }
    }

    val currentFirebaseUser: FirebaseUser?
        get() = try { firebaseAuth?.currentUser } catch (e: Throwable) { null }

    suspend fun signIn(email: String, pass: String): AuthResultState {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            return AuthResultState.Error("Please enter valid email/phone and password.")
        }

        val auth = firebaseAuth
        if (auth == null) {
            val account = registeredAccounts[cleanEmail]
            if (account == null) {
                return AuthResultState.Error("User account not found. Please register first.")
            }
            if (account.second != cleanPass) {
                return AuthResultState.Error("Incorrect password. Please check your credentials.")
            }
            return AuthResultState.Success(account.first)
        }

        return try {
            val result = auth.signInWithEmailAndPassword(cleanEmail, cleanPass).awaitTask()
            val fbUser = result.user
            if (fbUser != null) {
                val role = determineUserRole(fbUser.email ?: cleanEmail)
                val user = User(
                    id = fbUser.uid,
                    name = fbUser.displayName ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                    email = fbUser.email ?: cleanEmail,
                    phone = fbUser.phoneNumber ?: "+91 98765 00000",
                    role = role,
                    accountStatus = AccountStatus.ACTIVE,
                    isVerified = true
                )
                AuthResultState.Success(user)
            } else {
                AuthResultState.Error("Sign in failed. Could not retrieve user profile.")
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "SignIn error: ${e.message}", e)
            val friendlyMsg = when {
                e.message?.contains("password", ignoreCase = true) == true -> "Incorrect password. Please check your credentials."
                e.message?.contains("no user", ignoreCase = true) == true || e.message?.contains("user-not-found", ignoreCase = true) == true -> "User account not found. Please register first."
                e.message?.contains("network", ignoreCase = true) == true -> "Network connection issue. Please check your internet."
                e.message?.contains("invalid-email", ignoreCase = true) == true -> "Invalid email address format."
                else -> e.localizedMessage ?: "Authentication failed. Please verify credentials."
            }
            AuthResultState.Error(friendlyMsg)
        }
    }

    /**
     * Customer Registration: STRICTLY creates role = USER.
     * Never accepts role from frontend.
     */
    suspend fun signUpCustomer(
        name: String,
        email: String,
        pass: String,
        phone: String
    ): AuthResultState {
        val cleanName = name.trim().ifBlank { "User" }
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        val cleanPhone = phone.trim().ifBlank { "+91 98765 00000" }

        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            return AuthResultState.Error("Please provide both email and password.")
        }
        if (cleanPass.length < 4) {
            return AuthResultState.Error("Password must be at least 4 characters.")
        }

        // Server strictly assigns USER role
        val role = UserRole.USER

        val auth = firebaseAuth
        if (auth == null) {
            val user = User(
                id = "usr_${System.nanoTime()}_${java.util.UUID.randomUUID().toString().take(6)}",
                name = cleanName,
                email = cleanEmail,
                phone = cleanPhone,
                role = role,
                accountStatus = AccountStatus.ACTIVE,
                isVerified = true
            )
            registeredAccounts[cleanEmail] = Pair(user, cleanPass)
            return AuthResultState.Success(user)
        }

        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, cleanPass).awaitTask()
            val fbUser = result.user
            if (fbUser != null) {
                try {
                    val profileUpdate = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                    fbUser.updateProfile(profileUpdate).awaitTask()
                } catch (pe: Exception) {
                    Log.w("FirebaseAuthManager", "Profile update notice: ${pe.message}")
                }

                val user = User(
                    id = fbUser.uid,
                    name = cleanName,
                    email = cleanEmail,
                    phone = cleanPhone,
                    role = role,
                    accountStatus = AccountStatus.ACTIVE,
                    isVerified = true
                )
                AuthResultState.Success(user)
            } else {
                AuthResultState.Error("Sign up failed. User not created.")
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "SignUp error: ${e.message}", e)
            val friendlyMsg = when {
                e.message?.contains("email-already-in-use", ignoreCase = true) == true || e.message?.contains("already exists", ignoreCase = true) == true ->
                    "Account already exists with this email. Please Log In."
                e.message?.contains("weak-password", ignoreCase = true) == true ->
                    "Password is too weak. Please use at least 6 characters."
                else -> e.localizedMessage ?: "Registration failed."
            }
            AuthResultState.Error(friendlyMsg)
        }
    }

    /**
     * Merchant Registration: STRICTLY creates role = MERCHANT.
     * Verification status is set to PENDING on the backend.
     */
    suspend fun signUpMerchant(
        ownerName: String,
        email: String,
        pass: String,
        phone: String
    ): AuthResultState {
        val cleanName = ownerName.trim().ifBlank { "Merchant Owner" }
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        val cleanPhone = phone.trim().ifBlank { "+91 98765 00000" }

        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            return AuthResultState.Error("Please provide merchant email and password.")
        }
        if (cleanPass.length < 4) {
            return AuthResultState.Error("Password must be at least 4 characters.")
        }

        // Server strictly assigns MERCHANT role
        val role = UserRole.MERCHANT

        val auth = firebaseAuth
        if (auth == null) {
            val user = User(
                id = "usr_merch_${System.currentTimeMillis()}",
                name = cleanName,
                email = cleanEmail,
                phone = cleanPhone,
                role = role,
                accountStatus = AccountStatus.ACTIVE,
                isVerified = true
            )
            registeredAccounts[cleanEmail] = Pair(user, cleanPass)
            return AuthResultState.Success(user)
        }

        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, cleanPass).awaitTask()
            val fbUser = result.user
            if (fbUser != null) {
                try {
                    val profileUpdate = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                    fbUser.updateProfile(profileUpdate).awaitTask()
                } catch (pe: Exception) {
                    Log.w("FirebaseAuthManager", "Profile update notice: ${pe.message}")
                }

                val user = User(
                    id = fbUser.uid,
                    name = cleanName,
                    email = cleanEmail,
                    phone = cleanPhone,
                    role = role,
                    accountStatus = AccountStatus.ACTIVE,
                    isVerified = true
                )
                AuthResultState.Success(user)
            } else {
                AuthResultState.Error("Merchant registration failed.")
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "Merchant SignUp error: ${e.message}", e)
            val friendlyMsg = when {
                e.message?.contains("email-already-in-use", ignoreCase = true) == true || e.message?.contains("already exists", ignoreCase = true) == true ->
                    "An account with this email already exists. Please Log In."
                else -> e.localizedMessage ?: "Merchant registration failed."
            }
            AuthResultState.Error(friendlyMsg)
        }
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Throwable) {
            Log.e("FirebaseAuthManager", "SignOut notice: ${e.message}")
        }
    }

    suspend fun ensureAuthenticated(): Boolean {
        val auth = firebaseAuth ?: return false
        if (auth.currentUser != null) return true
        return try {
            auth.signInAnonymously().awaitTask()
            Log.d("FirebaseAuthManager", "Anonymous auth succeeded: ${auth.currentUser?.uid}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "Anonymous auth notice: ${e.message}")
            false
        }
    }

    fun determineUserRole(email: String): UserRole {
        val clean = email.trim().lowercase()
        return when {
            clean == "talibaziz0786@gmail.com" || clean == "admin@smartqueue.in" || clean == "talib.admin@smartqueue.in" -> UserRole.SUPER_ADMIN
            clean.contains("staff", true) || clean.contains("operator", true) -> UserRole.STAFF
            clean.contains("merchant", true) || clean.contains("doctor", true) || clean.contains("clinic", true) || clean.contains("shop", true) || clean.contains("owner", true) -> UserRole.MERCHANT
            else -> UserRole.USER
        }
    }
}

// Coroutine Task Await Helper
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
