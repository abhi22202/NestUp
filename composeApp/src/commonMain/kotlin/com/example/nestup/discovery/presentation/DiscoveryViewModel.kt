package com.example.nestup.discovery.presentation

import com.example.nestup.auth.domain.Gender
import com.example.nestup.auth.domain.IndianCity
import com.example.nestup.auth.domain.UserProfile
import com.example.nestup.auth.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveryUiState(
    val profiles: List<UserProfile> = emptyList(),
    val currentProfileIndex: Int = 0,
    val isLoading: Boolean = false,
)

class DiscoveryViewModel {
    private val _uiState = MutableStateFlow(DiscoveryUiState(isLoading = true))
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        loadMockProfiles()
    }

    private fun loadMockProfiles() {
        // Mocking data for discovery
        val mockProfiles = listOf(
            UserProfile(
                id = "1",
                phoneNumber = "+919876543210",
                name = "Aravind Sharma",
                role = UserRole.LOOKING_FOR_FLATMATE,
                gender = Gender.MALE,
                city = IndianCity.BENGALURU,
                profilePhotoUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d",
                createdAtEpochMillis = System.currentTimeMillis()
            ),
            UserProfile(
                id = "2",
                phoneNumber = "+919876543211",
                name = "Priya Kapoor",
                role = UserRole.LOOKING_FOR_FLAT,
                gender = Gender.FEMALE,
                city = IndianCity.MUMBAI,
                profilePhotoUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330",
                createdAtEpochMillis = System.currentTimeMillis()
            ),
            UserProfile(
                id = "3",
                phoneNumber = "+919876543212",
                name = "Rohan Das",
                role = UserRole.LOOKING_FOR_FLATMATE,
                gender = Gender.MALE,
                city = IndianCity.PUNE,
                profilePhotoUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e",
                createdAtEpochMillis = System.currentTimeMillis()
            ),
            UserProfile(
                id = "4",
                phoneNumber = "+919876543213",
                name = "Isha Singh",
                role = UserRole.FLAT_OWNER,
                gender = Gender.FEMALE,
                city = IndianCity.NEW_DELHI,
                profilePhotoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
        _uiState.update { it.copy(profiles = mockProfiles, isLoading = false) }
    }

    fun onNextProfile() {
        _uiState.update { current ->
            if (current.currentProfileIndex < current.profiles.size - 1) {
                current.copy(currentProfileIndex = current.currentProfileIndex + 1)
            } else {
                current.copy(currentProfileIndex = 0) // Loop for demo
            }
        }
    }
}
