package com.example.nestup.auth.platform

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val USERS_COLLECTION = "users"
private const val PROFILE_IMAGES_FOLDER = "profilePictures"
private const val AUTH_TAG = "NestUpAuth"

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
        Log.d(AUTH_TAG, "Requesting OTP verification")

        suspendCancellableCoroutine { continuation ->
            var resumed = false

            fun resumeOnce(action: () -> Unit) {
                if (resumed) return
                resumed = true
                action()
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(AUTH_TAG, "Phone verification completed instantly")
                    instantCredential = credential
                    resumeOnce { continuation.resume(Unit) }
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    Log.e(AUTH_TAG, "Phone verification request failed", exception)
                    resumeOnce { continuation.resumeWithException(exception) }
                }

                override fun onCodeSent(
                    verificationIdValue: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    Log.d(AUTH_TAG, "OTP code sent successfully")
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
        Log.d(AUTH_TAG, "OTP verified, loading Firestore profile")

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
        ensureFirestoreIsReachable("saving registration")
        Log.d(AUTH_TAG, "Creating profile for user ${currentUser.uid}")

        val userDocumentReference = firestore.collection(USERS_COLLECTION)
            .document(currentUser.uid)
        val existingSnapshot = userDocumentReference
            .get()
            .await()

        val profilePhotoUrl = uploadProfilePhotoOrNull(
            userId = currentUser.uid,
            imageBytes = details.profilePhotoBytes,
        )
        val now = System.currentTimeMillis()
        val createdAt = existingSnapshot.getLong("createdAtEpochMillis") ?: now
        val userDocument = mapOf(
            "userId" to currentUser.uid,
            "phoneNumber" to phoneNumber,
            "name" to details.name,
            "role" to details.role.name,
            "gender" to details.gender.name,
            "city" to details.city.name,
            "profilePhotoUrl" to profilePhotoUrl,
            "createdAtEpochMillis" to createdAt,
            "updatedAtEpochMillis" to now,
            "isProfileComplete" to true,
        )

        userDocumentReference
            .set(userDocument, SetOptions.merge())
            .await()

        when (val session = resolveSessionState(currentUser.uid, phoneNumber)) {
            is AuthSessionState.Authenticated -> session.profile
            is AuthSessionState.NeedsRegistration -> {
                throw IllegalStateException("Profile data was saved but could not be loaded.")
            }
            AuthSessionState.SignedOut -> {
                throw IllegalStateException("Profile was saved, but the user session was lost.")
            }
        }
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
        ensureFirestoreIsReachable("loading profile")
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

    private suspend fun ensureFirestoreIsReachable(operation: String) {
        if (!activity.isNetworkAvailable()) {
            throw IllegalStateException(
                "No internet connection on this phone. Connect the device and try again.",
            )
        }

        runCatching { firestore.enableNetwork().await() }
            .onFailure { throwable ->
                Log.w(AUTH_TAG, "Firestore network could not be re-enabled before $operation", throwable)
            }
    }

    private suspend fun uploadProfilePhotoOrNull(
        userId: String,
        imageBytes: ByteArray,
    ): String? {
        val reference = storage.reference
            .child(PROFILE_IMAGES_FOLDER)
            .child(userId)
            .child("${System.currentTimeMillis()}.jpg")

        return try {
            reference.putBytes(imageBytes).await()
            reference.downloadUrl.await().toString()
        } catch (exception: StorageException) {
            Log.e(AUTH_TAG, "Profile image upload failed", exception)
            when (exception.errorCode) {
                StorageException.ERROR_OBJECT_NOT_FOUND,
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND -> null
                else -> throw exception
            }
        }
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
            Log.e(AUTH_TAG, "Firebase auth flow failed", throwable)
            throw IllegalStateException(throwable.toReadableMessage(), throwable)
        }
    }
}

private fun Throwable.toReadableMessage(): String {
    return when (this) {
        is FirebaseAuthInvalidCredentialsException -> "The OTP is invalid or has expired."
        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait and try again."
        is FirebaseNetworkException -> "Network error. Check your internet connection and try again."
        is StorageException -> when (errorCode) {
            StorageException.ERROR_BUCKET_NOT_FOUND,
            StorageException.ERROR_PROJECT_NOT_FOUND -> {
                "Firebase Storage is not configured for this project. Open Firebase Console > Storage and complete setup."
            }
            StorageException.ERROR_OBJECT_NOT_FOUND -> {
                "Profile photo upload failed because Firebase Storage is not ready for this bucket."
            }
            else -> localizedMessage ?: "Unable to upload the profile photo right now."
        }
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> {
                "OTP verification finished, but Firestore is offline. Check device internet and confirm Firestore is reachable."
            }
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                "Firestore denied access. Check your Firestore security rules."
            }
            FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                "Firestore is not ready for this project. Create the Firestore Database in Firebase console and try again."
            }
            else -> localizedMessage ?: "Unable to reach Firestore right now."
        }
        is FirebaseException -> localizedMessage ?: "Firebase rejected the request."
        else -> message ?: "Something went wrong."
    }
}

private fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun Context.findActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
