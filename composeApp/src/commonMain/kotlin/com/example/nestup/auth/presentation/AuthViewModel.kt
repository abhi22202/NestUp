package com.example.nestup.auth.presentation

import com.example.nestup.auth.domain.AuthRepository
import com.example.nestup.auth.domain.AuthSessionState
import com.example.nestup.auth.domain.Gender
import com.example.nestup.auth.domain.IndianCity
import com.example.nestup.auth.domain.RegistrationDetails
import com.example.nestup.auth.domain.UserProfile
import com.example.nestup.auth.domain.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthDestination {
    Splash,
    PhoneEntry,
    OtpEntry,
    Registration,
    Home,
}

data class RegistrationFormState(
    val name: String = "",
    val selectedRole: UserRole? = null,
    val selectedGender: Gender? = null,
    val selectedCity: IndianCity? = null,
    val profilePhotoBytes: ByteArray? = null,
)

data class AuthUiState(
    val destination: AuthDestination = AuthDestination.Splash,
    val phoneNumberInput: String = "",
    val pendingPhoneNumber: String? = null,
    val otpInput: String = "",
    val registrationForm: RegistrationFormState = RegistrationFormState(),
    val signedInProfile: UserProfile? = null,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(AuthUiState(isLoading = true))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var initialized = false

    init {
        initialize()
    }

    fun clear() {
        scope.cancel()
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        launchTask(showLoading = true) {
            applySessionState(authRepository.getCurrentSession())
        }
    }

    fun onPhoneNumberChanged(input: String) {
        val digitsOnly = input.filter(Char::isDigit).take(10)
        _uiState.update { current ->
            current.copy(phoneNumberInput = digitsOnly)
        }
    }

    fun onOtpChanged(input: String) {
        val digitsOnly = input.filter(Char::isDigit).take(6)
        _uiState.update { current ->
            current.copy(otpInput = digitsOnly)
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { current ->
            current.copy(
                registrationForm = current.registrationForm.copy(name = name),
            )
        }
    }

    fun onRoleSelected(role: UserRole) {
        _uiState.update { current ->
            current.copy(
                registrationForm = current.registrationForm.copy(selectedRole = role),
            )
        }
    }

    fun onGenderSelected(gender: Gender) {
        _uiState.update { current ->
            current.copy(
                registrationForm = current.registrationForm.copy(selectedGender = gender),
            )
        }
    }

    fun onCitySelected(city: IndianCity) {
        _uiState.update { current ->
            current.copy(
                registrationForm = current.registrationForm.copy(selectedCity = city),
            )
        }
    }

    fun onProfileImageSelected(imageBytes: ByteArray?) {
        _uiState.update { current ->
            current.copy(
                registrationForm = current.registrationForm.copy(profilePhotoBytes = imageBytes),
                snackbarMessage = if (imageBytes == null) {
                    "Unable to read the selected image."
                } else {
                    "Profile photo selected."
                },
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { current ->
            current.copy(snackbarMessage = null)
        }
    }

    fun sendOtp() {
        val formattedPhoneNumber = buildIndianPhoneNumber(_uiState.value.phoneNumberInput)
            ?: run {
                showMessage("Enter a valid 10-digit Indian mobile number.")
                return
            }

        launchTask(showLoading = true) {
            authRepository.requestOtp(formattedPhoneNumber)
            _uiState.update { current ->
                current.copy(
                    destination = AuthDestination.OtpEntry,
                    pendingPhoneNumber = formattedPhoneNumber,
                    otpInput = "",
                    snackbarMessage = "OTP sent to $formattedPhoneNumber",
                )
            }
        }
    }

    fun verifyOtp() {
        launchTask(showLoading = true) {
            val session = authRepository.verifyOtp(_uiState.value.otpInput)
            applySessionState(session)
        }
    }

    fun submitRegistration() {
        val snapshot = _uiState.value.registrationForm
        val photo = snapshot.profilePhotoBytes
        val details = RegistrationDetails(
            name = snapshot.name.trim(),
            role = snapshot.selectedRole ?: run {
                showMessage("Select who you are.")
                return
            },
            gender = snapshot.selectedGender ?: run {
                showMessage("Select a gender.")
                return
            },
            city = snapshot.selectedCity ?: run {
                showMessage("Select a city.")
                return
            },
            profilePhotoBytes = photo ?: run {
                showMessage("Select a profile photo.")
                return
            },
        )

        if (details.name.length < 2) {
            showMessage("Enter your full name.")
            return
        }

        launchTask(showLoading = true) {
            val profile = authRepository.saveRegistration(details)
            _uiState.update { current ->
                current.copy(
                    destination = AuthDestination.Home,
                    signedInProfile = profile,
                    registrationForm = RegistrationFormState(),
                    otpInput = "",
                    snackbarMessage = "Profile created successfully.",
                )
            }
        }
    }

    fun editPhoneNumber() {
        _uiState.update { current ->
            current.copy(
                destination = AuthDestination.PhoneEntry,
                otpInput = "",
                pendingPhoneNumber = null,
            )
        }
    }

    fun signOut() {
        launchTask(showLoading = true) {
            authRepository.signOut()
            _uiState.update { current ->
                current.copy(
                    destination = AuthDestination.PhoneEntry,
                    phoneNumberInput = "",
                    pendingPhoneNumber = null,
                    otpInput = "",
                    registrationForm = RegistrationFormState(),
                    signedInProfile = null,
                    snackbarMessage = "Signed out.",
                )
            }
        }
    }

    private fun applySessionState(sessionState: AuthSessionState) {
        when (sessionState) {
            AuthSessionState.SignedOut -> {
                _uiState.update { current ->
                    current.copy(
                        destination = AuthDestination.PhoneEntry,
                        pendingPhoneNumber = null,
                        signedInProfile = null,
                        otpInput = "",
                        isLoading = false,
                    )
                }
            }

            is AuthSessionState.NeedsRegistration -> {
                _uiState.update { current ->
                    current.copy(
                        destination = AuthDestination.Registration,
                        pendingPhoneNumber = sessionState.phoneNumber,
                        otpInput = "",
                        signedInProfile = null,
                        isLoading = false,
                    )
                }
            }

            is AuthSessionState.Authenticated -> {
                _uiState.update { current ->
                    current.copy(
                        destination = AuthDestination.Home,
                        pendingPhoneNumber = sessionState.profile.phoneNumber,
                        signedInProfile = sessionState.profile,
                        otpInput = "",
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun launchTask(
        showLoading: Boolean,
        block: suspend () -> Unit,
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            if (showLoading) {
                _uiState.update { current -> current.copy(isLoading = true) }
            }

            runCatching { block() }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            snackbarMessage = throwable.message ?: "Something went wrong. Please try again.",
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(isLoading = false)
                    }
                }
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { current ->
            current.copy(snackbarMessage = message)
        }
    }

    private fun buildIndianPhoneNumber(rawInput: String): String? {
        val digits = rawInput.filter(Char::isDigit)
        return if (digits.length == 10) "+91$digits" else null
    }
}
