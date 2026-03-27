package com.example.nestup.auth.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.nestup.auth.domain.AuthRepository
import com.example.nestup.auth.domain.AuthSessionState
import com.example.nestup.auth.domain.RegistrationDetails
import com.example.nestup.auth.domain.UserProfile

@Composable
actual fun rememberAuthRepository(): AuthRepository = remember {
    IOSStubAuthRepository()
}

@Composable
actual fun rememberProfileImagePicker(
    onImagePicked: (ByteArray?) -> Unit,
): ProfileImagePickerLauncher = remember {
    object : ProfileImagePickerLauncher {
        override fun launch() = Unit
    }
}

private class IOSStubAuthRepository : AuthRepository {
    override suspend fun getCurrentSession(): AuthSessionState = AuthSessionState.SignedOut

    override suspend fun requestOtp(phoneNumber: String) {
        throw IllegalStateException("Firebase phone auth is only wired on Android right now.")
    }

    override suspend fun verifyOtp(otpCode: String): AuthSessionState {
        throw IllegalStateException("Firebase phone auth is only wired on Android right now.")
    }

    override suspend fun saveRegistration(details: RegistrationDetails): UserProfile {
        throw IllegalStateException("Firebase profile registration is only wired on Android right now.")
    }

    override suspend fun signOut() = Unit
}
