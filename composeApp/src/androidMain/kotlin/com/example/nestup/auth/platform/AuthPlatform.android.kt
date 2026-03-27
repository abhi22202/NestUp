package com.example.nestup.auth.platform

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.nestup.auth.domain.AuthRepository
import com.example.nestup.auth.domain.AuthSessionState
import com.example.nestup.auth.domain.Gender
import com.example.nestup.auth.domain.IndianCity
import com.example.nestup.auth.domain.RegistrationDetails
import com.example.nestup.auth.domain.UserProfile
import com.example.nestup.auth.domain.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val USERS_COLLECTION = "users"
private const val PROFILE_IMAGES_FOLDER = "profilePictures"

@Composable
actual fun rememberAuthRepository(): AuthRepository {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isFirebaseConfigured = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    return remember(activity) {
        if (activity == null) {
            PreviewAuthRepository()
        } else if (!isFirebaseConfigured) {
            MissingFirebaseConfigAuthRepository()
        } else {
            AndroidFirebaseAuthRepository(activity)
        }
    }
}

@Composable
actual fun rememberProfileImagePicker(
    onImagePicked: (ByteArray?) -> Unit,
): ProfileImagePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val bytes = uri?.let { selectedUri ->
            runCatching {
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    inputStream.readBytes()
                }
            }.getOrNull()
        }
        onImagePicked(bytes)
    }

    return remember(launcher) {
        object : ProfileImagePickerLauncher {
            override fun launch() {
                launcher.launch("image/*")
            }
        }
    }
}

private class PreviewAuthRepository : AuthRepository {
    override suspend fun getCurrentSession(): AuthSessionState = AuthSessionState.SignedOut

    override suspend fun requestOtp(phoneNumber: String) {
        throw IllegalStateException("Phone auth is only available on a running Android device.")
    }

    override suspend fun verifyOtp(otpCode: String): AuthSessionState {
        throw IllegalStateException("Phone auth is only available on a running Android device.")
    }

    override suspend fun saveRegistration(details: RegistrationDetails): UserProfile {
        throw IllegalStateException("Profile registration is only available on a running Android device.")
    }

    override suspend fun signOut() = Unit
}

private class MissingFirebaseConfigAuthRepository : AuthRepository {
    override suspend fun getCurrentSession(): AuthSessionState = AuthSessionState.SignedOut

    override suspend fun requestOtp(phoneNumber: String) {
        throw IllegalStateException(
            "Firebase is not configured. Add composeApp/google-services.json and rebuild.",
        )
    }

    override suspend fun verifyOtp(otpCode: String): AuthSessionState {
        throw IllegalStateException(
            "Firebase is not configured. Add composeApp/google-services.json and rebuild.",
        )
    }

    override suspend fun saveRegistration(details: RegistrationDetails): UserProfile {
        throw IllegalStateException(
            "Firebase is not configured. Add composeApp/google-services.json and rebuild.",
        )
    }

    override suspend fun signOut() = Unit
}

private class AndroidFirebaseAuthRepository(
    private val activity: ComponentActivity,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : AuthRepository {
    private var verificationId: String? = null
    private var instantCredential: PhoneAuthCredential? = null
    private var lastRequestedPhoneNumber: String? = null

    override suspend fun getCurrentSession(): AuthSessionState = runFirebaseOperation {
        val currentUser = auth.currentUser ?: return@runFirebaseOperation AuthSessionState.SignedOut
        val phoneNumber = currentUser.phoneNumber ?: return@runFirebaseOperation AuthSessionState.SignedOut
        resolveSessionState(
            userId = currentUser.uid,
            phoneNumber = phoneNumber,
        )
    }

    override suspend fun requestOtp(phoneNumber: String) = runFirebaseOperation {
        lastRequestedPhoneNumber = phoneNumber
        instantCredential = null
        verificationId = null

        suspendCancellableCoroutine { continuation ->
            var resumed = false

            fun resumeOnce(action: () -> Unit) {
                if (resumed) return
                resumed = true
                action()
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    instantCredential = credential
                    resumeOnce { continuation.resume(Unit) }
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    resumeOnce { continuation.resumeWithException(exception) }
                }

                override fun onCodeSent(
                    verificationIdValue: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    verificationId = verificationIdValue
                    resumeOnce { continuation.resume(Unit) }
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    override suspend fun verifyOtp(otpCode: String): AuthSessionState = runFirebaseOperation {
        val credential = instantCredential ?: run {
            val currentVerificationId = verificationId
                ?: throw IllegalStateException("Request an OTP before verifying.")

            if (otpCode.length != 6) {
                throw IllegalStateException("Enter the 6-digit OTP.")
            }

            PhoneAuthProvider.getCredential(currentVerificationId, otpCode)
        }

        val authResult = auth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user
            ?: throw IllegalStateException("Unable to verify this phone number right now.")
        val phoneNumber = firebaseUser.phoneNumber ?: lastRequestedPhoneNumber
            ?: throw IllegalStateException("Phone number information is missing.")

        instantCredential = null
        verificationId = null

        resolveSessionState(
            userId = firebaseUser.uid,
            phoneNumber = phoneNumber,
        )
    }

    override suspend fun saveRegistration(details: RegistrationDetails): UserProfile = runFirebaseOperation {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Please verify your mobile number again.")
        val phoneNumber = currentUser.phoneNumber ?: lastRequestedPhoneNumber
            ?: throw IllegalStateException("Phone number information is missing.")

        val profilePhotoUrl = uploadProfilePhoto(
            userId = currentUser.uid,
            imageBytes = details.profilePhotoBytes,
        )
        val createdAt = System.currentTimeMillis()
        val userDocument = mapOf(
            "userId" to currentUser.uid,
            "phoneNumber" to phoneNumber,
            "name" to details.name,
            "role" to details.role.name,
            "gender" to details.gender.name,
            "city" to details.city.name,
            "profilePhotoUrl" to profilePhotoUrl,
            "createdAtEpochMillis" to createdAt,
        )

        firestore.collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .set(userDocument)
            .await()

        UserProfile(
            id = currentUser.uid,
            phoneNumber = phoneNumber,
            name = details.name,
            role = details.role,
            gender = details.gender,
            city = details.city,
            profilePhotoUrl = profilePhotoUrl,
            createdAtEpochMillis = createdAt,
        )
    }

    override suspend fun signOut() = runFirebaseOperation {
        auth.signOut()
        verificationId = null
        instantCredential = null
        lastRequestedPhoneNumber = null
    }

    private suspend fun resolveSessionState(
        userId: String,
        phoneNumber: String,
    ): AuthSessionState {
        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .await()

        val profile = snapshot.toUserProfile()
        return if (profile == null) {
            AuthSessionState.NeedsRegistration(
                userId = userId,
                phoneNumber = phoneNumber,
            )
        } else {
            AuthSessionState.Authenticated(profile)
        }
    }

    private suspend fun uploadProfilePhoto(
        userId: String,
        imageBytes: ByteArray,
    ): String {
        val reference = storage.reference
            .child(PROFILE_IMAGES_FOLDER)
            .child(userId)
            .child("${System.currentTimeMillis()}.jpg")

        reference.putBytes(imageBytes).await()
        return reference.downloadUrl.await().toString()
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null

        val phoneNumber = getString("phoneNumber") ?: return null
        val name = getString("name") ?: return null
        val role = getString("role")?.let(UserRole::valueOf) ?: return null
        val gender = getString("gender")?.let(Gender::valueOf) ?: return null
        val city = getString("city")?.let(IndianCity::valueOf) ?: return null
        val profilePhotoUrl = getString("profilePhotoUrl")
        val createdAtEpochMillis = getLong("createdAtEpochMillis") ?: 0L

        return UserProfile(
            id = id,
            phoneNumber = phoneNumber,
            name = name,
            role = role,
            gender = gender,
            city = city,
            profilePhotoUrl = profilePhotoUrl,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    private suspend inline fun <T> runFirebaseOperation(
        crossinline block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            throw IllegalStateException(throwable.toReadableMessage(), throwable)
        }
    }
}

private fun Throwable.toReadableMessage(): String {
    return when (this) {
        is FirebaseAuthInvalidCredentialsException -> "The OTP is invalid or has expired."
        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait and try again."
        is FirebaseNetworkException -> "Network error. Check your internet connection and try again."
        is FirebaseException -> localizedMessage ?: "Firebase rejected the request."
        else -> message ?: "Something went wrong."
    }
}

private fun Context.findActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
