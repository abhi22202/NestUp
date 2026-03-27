package com.example.nestup.auth.domain

enum class UserRole(val title: String, val description: String) {
    FLAT_OWNER(
        title = "Flat Owner",
        description = "I have a flat to list.",
    ),
    PG_OWNER(
        title = "PG Owner",
        description = "I manage or list a PG.",
    ),
    LOOKING_FOR_FLATMATE(
        title = "Looking for Flatmate",
        description = "I want to share a place.",
    ),
    LOOKING_FOR_FLAT(
        title = "Looking for Flat",
        description = "I need a rental flat.",
    ),
    LOOKING_FOR_PG(
        title = "Looking for PG",
        description = "I need a PG stay.",
    ),
}

enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary"),
    OTHER("Other"),
    PREFER_NOT_TO_SAY("Prefer not to say"),
}

enum class IndianCity(val displayName: String) {
    AHMEDABAD("Ahmedabad"),
    BENGALURU("Bengaluru"),
    BHUBANESWAR("Bhubaneswar"),
    CHANDIGARH("Chandigarh"),
    CHENNAI("Chennai"),
    GOA("Goa"),
    GURUGRAM("Gurugram"),
    HYDERABAD("Hyderabad"),
    INDORE("Indore"),
    JAIPUR("Jaipur"),
    KOCHI("Kochi"),
    KOLKATA("Kolkata"),
    LUCKNOW("Lucknow"),
    MUMBAI("Mumbai"),
    NAGPUR("Nagpur"),
    NEW_DELHI("New Delhi"),
    NOIDA("Noida"),
    PATNA("Patna"),
    PUNE("Pune"),
    SURAT("Surat"),
    VISAKHAPATNAM("Visakhapatnam"),
}

data class UserProfile(
    val id: String,
    val phoneNumber: String,
    val name: String,
    val role: UserRole,
    val gender: Gender,
    val city: IndianCity,
    val profilePhotoUrl: String?,
    val createdAtEpochMillis: Long,
)

data class RegistrationDetails(
    val name: String,
    val role: UserRole,
    val gender: Gender,
    val city: IndianCity,
    val profilePhotoBytes: ByteArray,
)

sealed interface AuthSessionState {
    data object SignedOut : AuthSessionState

    data class NeedsRegistration(
        val userId: String,
        val phoneNumber: String,
    ) : AuthSessionState

    data class Authenticated(
        val profile: UserProfile,
    ) : AuthSessionState
}
