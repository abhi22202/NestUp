package com.example.nestup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.nestup.auth.platform.rememberAuthRepository
import com.example.nestup.auth.platform.rememberProfileImagePicker
import com.example.nestup.auth.presentation.AuthScreen
import com.example.nestup.auth.presentation.AuthViewModel
import com.example.nestup.core.designsystem.NestUpTheme

@Composable
fun App() {
    NestUpTheme {
        val authRepository = rememberAuthRepository()
        val viewModel = remember(authRepository) { AuthViewModel(authRepository) }
        val profileImagePicker = rememberProfileImagePicker(
            onImagePicked = viewModel::onProfileImageSelected,
        )

        DisposableEffect(viewModel) {
            onDispose(viewModel::clear)
        }

        AuthScreen(
            viewModel = viewModel,
            onPickProfileImage = profileImagePicker::launch,
        )
    }
}