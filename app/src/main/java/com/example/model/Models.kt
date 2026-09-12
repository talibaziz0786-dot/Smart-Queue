package com.example.model

import java.util.UUID

enum class UserRole {
    USER,
    MERCHANT,
    STAFF,
    ADMIN,
    SUPER_ADMIN;

    companion object {
        // Aliases for seamless backward compatibility
        val CUSTOMER = USER
        val BUSINESS_OWNER = MERCHANT
    }
}

enum class AccountStatus {
    ACTIVE,
    PENDING,
    SUSPENDED,
    BLOCKED,
    DELETED
}

enum class MerchantVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}

data class User(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val phone: String,
    val role: UserRole = UserRole.USER,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val avatarUrl: String = "",
    val isVerified: Boolean = true,
    val isRestricted: Boolean = false,
    val isBlocked: Boolean = false,
    val restrictionReason: String? = null,
    val violationCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class BusinessCategory(val title: String, val iconName: String) {
    HEALTHCARE("Clinic & Healthcare", "health_and_safety"),
    SALON_BEAUTY("Salon & Beauty", "content_cut"),
    RESTAURANT("Restaurant & Cafe", "restaurant"),
    REPAIR("Tech & Repair", "build"),
    CONSULTANT("Consultancy & Legal", "business_center"),
    GOVERNMENT("Govt / Public Counter", "account_balance"),
    RETAIL("Retail & Banking", "storefront")
}

data class BusinessHours(
    val dayOfWeek: String,
    val openTime: String = "09:00 AM",
    val closeTime: String = "08:00 PM",
    val isClosed: Boolean = false
)

data class BusinessLocation(
    val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val branchName: String,
    val address: String,
    val city: String,
    val phone: String,
    val isOpen: Boolean = true,
    val isPaused: Boolean = false,
    val prefix: String = "A",
    val latitude: Double = 26.8467,
    val longitude: Double = 80.9462,
    val isDeleted: Boolean = false
)

data class ServiceItem(
    val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val locationId: String,
    val name: String,
    val description: String,
    val durationMinutes: Int = 15,
    val price: Double = 0.0,
    val prefix: String = "A",
    val isAvailable: Boolean = true,
    val isDeleted: Boolean = false
)

data class Business(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ownerId: String,
    val category: BusinessCategory,
    val description: String,
    val logoUrl: String = "",
    val coverUrl: String = "",
    val professionalName: String = "",
    val experienceYears: Int = 0,
    val qualifications: String = "",
    val specialties: String = "",
    val languages: String = "English, Hindi",
    val websiteUrl: String = "",
    val portfolioPhotoUrls: List<String> = emptyList(),
    val verificationStatus: MerchantVerificationStatus = MerchantVerificationStatus.APPROVED,
    val isVerified: Boolean = true,
    val isSuspended: Boolean = false,
    val suspensionReason: String? = null,
    val isDeleted: Boolean = false,
    val rating: Double = 4.8,
    val reviewCount: Int = 128,
    val primaryCity: String = "Lucknow",
    val operatingHours: List<BusinessHours> = listOf(
        BusinessHours("Monday"),
        BusinessHours("Tuesday"),
        BusinessHours("Wednesday"),
        BusinessHours("Thursday"),
        BusinessHours("Friday"),
        BusinessHours("Saturday"),
        BusinessHours("Sunday", isClosed = true)
    ),
    val plan: SubscriptionPlan = SubscriptionPlan.PRO,
    val subscriptionExpiresAt: Long = System.currentTimeMillis() + (45L * 24 * 60 * 60 * 1000L),
    val createdAt: Long = System.currentTimeMillis()
)

enum class TokenStatus {
    CREATED,
    WAITING,
    CALLED,
    SERVING,
    SERVED,
    SKIPPED,
    CANCELLED,
    EXPIRED,
    NO_SHOW,
    BLOCKED
}

enum class TokenPriority {
    STANDARD,
    VIP,
    SENIOR_EMERGENCY
}

data class Token(
    val id: String = UUID.randomUUID().toString(),
    val tokenNumber: String, // e.g. "A-027"
    val sequenceNumber: Int,
    val userId: String,
    val userName: String,
    val userPhone: String,
    val businessId: String,
    val businessName: String,
    val locationId: String,
    val branchName: String,
    val serviceId: String,
    val serviceName: String,
    val priority: TokenPriority = TokenPriority.STANDARD,
    val status: TokenStatus = TokenStatus.WAITING,
    val estimatedWaitMinutes: Int = 20,
    val initialPosition: Int = 1,
    val counterNumber: String? = null,
    val staffAssigned: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val calledAt: Long? = null,
    val servedAt: Long? = null,
    val notes: String = "",
    val customerNameOverride: String? = null,
    val customerAge: Int? = null,
    val customerGender: String? = null,
    val isGuest: Boolean = false,
    val businessOwnerId: String = ""
)

enum class StaffRole {
    COUNTER_OPERATOR,
    SUPERVISOR,
    RECEPTIONIST,
    BRANCH_MANAGER
}

data class StaffMember(
    val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val locationId: String,
    val name: String,
    val email: String,
    val role: StaffRole = StaffRole.COUNTER_OPERATOR,
    val assignedCounter: String = "Counter 1",
    val isActive: Boolean = true,
    val joinedAt: Long = System.currentTimeMillis()
)

data class QRCodeData(
    val qrId: String = UUID.randomUUID().toString(),
    val businessId: String,
    val locationId: String,
    val publicCode: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false,
    val scanCount: Int = 0,
    val isDeleted: Boolean = false
)

sealed class QrResolutionResult {
    data class Success(val business: Business, val location: BusinessLocation) : QrResolutionResult()
    object InvalidCode : QrResolutionResult() // INVALID_QR_CODE
    object Revoked : QrResolutionResult()     // QR_REVOKED
    data class Error(val message: String) : QrResolutionResult()
}

enum class SubscriptionPlan(
    val title: String,
    val priceMonthlyInr: Int,
    val maxLocations: Int,
    val maxStaff: Int,
    val maxServices: Int,
    val hasAnalytics: Boolean,
    val hasCustomBranding: Boolean,
    val hasPrioritySupport: Boolean,
    val hasSmsAlerts: Boolean
) {
    FREE("Free Starter", 0, 1, 2, 5, false, false, false, false),
    PRO("Professional", 199, 3, 10, 20, true, true, false, true),
    BUSINESS("Business Scale", 499, 10, 50, 100, true, true, true, true),
    ENTERPRISE("Enterprise Ultra", 999, 999, 999, 999, true, true, true, true)
}

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}

data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val businessId: String,
    val plan: SubscriptionPlan,
    val amountInr: Double,
    val razorpayPaymentId: String = "pay_${UUID.randomUUID().toString().take(10)}",
    val razorpayOrderId: String = "order_${UUID.randomUUID().toString().take(10)}",
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AbuseViolationType(val description: String, val severityPoints: Int) {
    NO_SHOW_STREAK("Consecutive No-Show after token called", 1),
    RAPID_TOKEN_CANCELLATION("High frequency queue joins and rapid cancellations", 1),
    MULTI_QUEUE_SPAM("Multiple simultaneous tokens across non-related locations", 2)
}

enum class AbuseStatus {
    ACTIVE_RESTRICTION,
    WARNING,
    DISMISSED,
    APPEAL_PENDING
}

data class AbuseRecord(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val violationType: AbuseViolationType,
    val businessId: String,
    val status: AbuseStatus = AbuseStatus.ACTIVE_RESTRICTION,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "Automated policy trigger"
)

enum class NotificationType {
    TOKEN_CREATED,
    POSITION_UPDATED,
    ALMOST_TURN,
    TOKEN_CALLED,
    QUEUE_PAUSED,
    QUEUE_RESUMED,
    TOKEN_SERVED,
    TOKEN_CANCELLED,
    SECURITY_ALERT
}

data class NotificationItem(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedTokenId: String? = null
)

data class AuditLog(
    val id: String = UUID.randomUUID().toString(),
    val actorName: String,
    val actorRole: String,
    val action: String,
    val target: String,
    val timestamp: Long = System.currentTimeMillis()
)
