package com.example.nestup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.nestup.auth.platform.rememberAuthRepository
import com.example.nestup.auth.platform.rememberProfileImagePicker
import com.example.nestup.auth.presentation.AuthScreen
import com.example.nestup.auth.presentation.AuthViewModel
import com.example.nestup.core.designsystem.NestUpTheme
import com.example.nestup.discovery.presentation.DiscoveryViewModel

@Composable
fun App() {
    NestUpTheme {
        val authRepository = rememberAuthRepository()
        val viewModel = remember(authRepository) { AuthViewModel(authRepository) }
        val discoveryViewModel = remember { DiscoveryViewModel() }
        val profileImagePicker = rememberProfileImagePicker(
            onImagePicked = viewModel::onProfileImageSelected,
        )

        DisposableEffect(viewModel) {
            onDispose(viewModel::clear)
        }

        AuthScreen(
            viewModel = viewModel,
            discoveryViewModel = discoveryViewModel,
            onPickProfileImage = profileImagePicker::launch,
        )
    }
}