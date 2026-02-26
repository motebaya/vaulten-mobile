package com.motebaya.vaulten.presentation.screens.resetpin

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.presentation.components.SkeuoCard
import com.motebaya.vaulten.presentation.components.SkeuoWarningBanner

/**
 * Screen for resetting PIN using passphrase.
 * 
 * Keyboard-aware: adjusts for keyboard and dismisses on tap outside.
 */
@Composable
fun ResetPinScreen(
    onNavigateBack: () -> Unit,
    onResetComplete: () -> Unit,
    viewModel: ResetPinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == ResetPinStep.SUCCESS) {
            // Auto-navigate after success
            kotlinx.coroutines.delay(2000)
            onResetComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // Adjust for keyboard
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button (only on passphrase step)
            if (uiState.currentStep == ResetPinStep.PASSPHRASE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back to Login")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress indicator
            ProgressIndicator(currentStep = uiState.currentStep)

            Spacer(modifier = Modifier.height(32.dp))

            // Step content
            when (uiState.currentStep) {
                ResetPinStep.PASSPHRASE -> PassphraseStep(
                    isLoading = uiState.isLoading,
                    error = uiState.passphraseError,
                    onVerify = { viewModel.verifyPassphrase(it) }
                )

                ResetPinStep.NEW_PIN -> NewPinStep(
                    isLoading = uiState.isLoading,
                    error = uiState.pinError,
                    onBack = { viewModel.goBack() },
                    onSubmit = { newPin, confirmPin ->
                        viewModel.setNewPin(newPin, confirmPin)
                    }
                )

                ResetPinStep.SUCCESS -> SuccessStep()
            }
        }
    }
}

@Composable
private fun ProgressIndicator(currentStep: ResetPinStep) {
    val steps = listOf(
        ResetPinStep.PASSPHRASE to "Verify",
        ResetPinStep.NEW_PIN to "New PIN",
        ResetPinStep.SUCCESS to "Done"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val isActive = currentStep.ordinal >= step.ordinal

            // Step circle
            Surface(
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (currentStep.ordinal > step.ordinal) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Connector line (except after last)
            if (index < steps.size - 1) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp),
                    color = if (currentStep.ordinal > step.ordinal)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }
        }
    }
}

@Composable
private fun PassphraseStep(
    isLoading: Boolean,
    error: String?,
    onVerify: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Reset PIN",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Step 1: Verify your master passphrase",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        SkeuoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Master Passphrase") },
                    placeholder = { Text("Enter your 12 or 24 word passphrase") },
                    visualTransformation = if (showPassphrase) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                if (showPassphrase) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (showPassphrase) "Hide" else "Show"
                            )
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onVerify(passphrase) },
                    enabled = passphrase.isNotBlank() && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Verify Passphrase")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SkeuoWarningBanner(
            message = "Your passphrase is never stored. It is only used temporarily to verify your identity."
        )
    }
}

@Composable
private fun NewPinStep(
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (newPin: String, confirmPin: String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set New PIN",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Step 2: Create a new 6-digit PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        SkeuoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            newPin = it
                        }
                    },
                    label = { Text("New PIN") },
                    placeholder = { Text("Enter 6-digit PIN") },
                    visualTransformation = if (showPin) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                if (showPin) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (showPin) "Hide" else "Show"
                            )
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            confirmPin = it
                        }
                    },
                    label = { Text("Confirm PIN") },
                    placeholder = { Text("Re-enter PIN") },
                    visualTransformation = if (showPin) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = { onSubmit(newPin, confirmPin) },
                        enabled = newPin.length == 6 && confirmPin.length == 6 && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Reset PIN")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Surface(
            modifier = Modifier.size(100.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "PIN Reset Successful!",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your PIN has been reset successfully.\nRedirecting to login...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}
