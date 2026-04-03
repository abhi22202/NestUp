package com.example.nestup.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nestup.auth.domain.Gender
import com.example.nestup.auth.domain.IndianCity
import com.example.nestup.auth.domain.UserProfile
import com.example.nestup.auth.domain.UserRole

import com.example.nestup.discovery.presentation.DiscoveryScreen
import com.example.nestup.discovery.presentation.DiscoveryViewModel

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    discoveryViewModel: DiscoveryViewModel,
    onPickProfileImage: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSnackbar()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF153522),
                        MaterialTheme.colorScheme.background,
                        Color(0xFF040404),
                    ),
                ),
            )
    ) {
        when (uiState.destination) {
            AuthDestination.Home -> DiscoveryScreen(
                viewModel = discoveryViewModel,
                onSignOut = viewModel::signOut,
            )

            else -> {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (uiState.destination) {
                            AuthDestination.Splash -> LoadingContent()
                            AuthDestination.PhoneEntry -> PhoneEntryContent(
                                state = uiState,
                                onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                                onContinue = viewModel::sendOtp,
                            )

                            AuthDestination.OtpEntry -> OtpEntryContent(
                                state = uiState,
                                onOtpChanged = viewModel::onOtpChanged,
                                onVerify = viewModel::verifyOtp,
                                onEditPhone = viewModel::editPhoneNumber,
                            )

                            AuthDestination.Registration -> RegistrationContent(
                                state = uiState,
                                onNameChanged = viewModel::onNameChanged,
                                onRoleSelected = viewModel::onRoleSelected,
                                onGenderSelected = viewModel::onGenderSelected,
                                onCitySelected = viewModel::onCitySelected,
                                onPickProfileImage = onPickProfileImage,
                                onSubmit = viewModel::submitRegistration,
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Preparing NestUp",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun PhoneEntryContent(
    state: AuthUiState,
    onPhoneNumberChanged: (String) -> Unit,
    onContinue: () -> Unit,
) {
    AuthCardScaffold(
        eyebrow = "NestUp",
        title = "Login with your mobile number",
        subtitle = "We’ll send a one-time password to verify your number.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+91",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            OutlinedTextField(
                value = state.phoneNumberInput,
                onValueChange = onPhoneNumberChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("10-digit mobile number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Send OTP")
        }
    }
}

@Composable
private fun OtpEntryContent(
    state: AuthUiState,
    onOtpChanged: (String) -> Unit,
    onVerify: () -> Unit,
    onEditPhone: () -> Unit,
) {
    AuthCardScaffold(
        eyebrow = "Verify",
        title = "Enter the OTP",
        subtitle = "Sent to ${state.pendingPhoneNumber ?: "your phone number"}",
    ) {
        OutlinedTextField(
            value = state.otpInput,
            onValueChange = onOtpChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("6-digit OTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Verify and Continue")
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onEditPhone,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Change mobile number")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegistrationContent(
    state: AuthUiState,
    onNameChanged: (String) -> Unit,
    onRoleSelected: (UserRole) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onCitySelected: (IndianCity) -> Unit,
    onPickProfileImage: () -> Unit,
    onSubmit: () -> Unit,
) {
    val form = state.registrationForm
    AuthCardScaffold(
        eyebrow = "Create Profile",
        title = "Finish your account",
        subtitle = "This profile will be saved in Firebase after your phone number is verified.",
        scrollable = true,
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Full name") },
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Who are you?")
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserRole.entries.forEach { role ->
                SelectionChip(
                    label = role.title,
                    selected = form.selectedRole == role,
                    onClick = { onRoleSelected(role) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Gender")
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Gender.entries.forEach { gender ->
                SelectionChip(
                    label = gender.label,
                    selected = form.selectedGender == gender,
                    onClick = { onGenderSelected(gender) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("City")
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IndianCity.entries.forEach { city ->
                SelectionChip(
                    label = city.displayName,
                    selected = form.selectedCity == city,
                    onClick = { onCitySelected(city) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Profile picture")
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickProfileImage),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            if (form.profilePhotoBytes == null) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (form.profilePhotoBytes == null) "Add" else "Done",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column {
                    Text(
                        text = if (form.profilePhotoBytes == null) {
                            "Choose a profile picture"
                        } else {
                            "Profile picture ready"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Used for your public profile and uploaded to Firebase Storage.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Create my profile")
        }
    }
}

@Composable
private fun AuthCardScaffold(
    eyebrow: String,
    title: String,
    subtitle: String,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(scrollModifier)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(30.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = eyebrow,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

