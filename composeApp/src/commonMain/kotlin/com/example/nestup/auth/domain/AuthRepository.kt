package com.example.nestup.auth.domain

interface AuthRepository {
    suspend fun getCurrentSession(): AuthSessionState

    suspend fun requestOtp(phoneNumber: String)

    suspend fun verifyOtp(otpCode: String): AuthSessionState

    suspend fun saveRegistration(details: RegistrationDetails): UserProfile

    suspend fun signOut()
}
