package com.example.nestup.auth.platform

import androidx.compose.runtime.Composable
import com.example.nestup.auth.domain.AuthRepository

interface ProfileImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberAuthRepository(): AuthRepository

@Composable
expect fun rememberProfileImagePicker(
    onImagePicked: (ByteArray?) -> Unit,
): ProfileImagePickerLauncher
