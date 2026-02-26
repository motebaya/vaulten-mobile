package com.motebaya.vaulten.presentation.screens.changepin

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for changing the PIN with passphrase confirmation.
 * 
 * Flow:
 * 1. Enter current PIN
 * 2. Enter new PIN
 * 3. Confirm new PIN
 * 4. Enter Master Passphrase to confirm
 */
@Composable
fun ChangePinScreen(
    onNavigateBack: () -> Unit,
    onPinChanged: () -> Unit,
    viewModel: ChangePinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onPinChanged()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Settings")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress indicator
            ChangePinProgressIndicator(currentStep = uiState.step)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Step content
            when (uiState.step) {
                ChangePinStep.CURRENT_PIN -> CurrentPinStepContent(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onSubmit = { viewModel.onCurrentPinSubmit(it) }
                )
                
                ChangePinStep.NEW_PIN -> NewPinStepContent(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onBack = { viewModel.goBack() },
                    onSubmit = { viewModel.onNewPinSubmit(it) }
                )
                
                ChangePinStep.CONFIRM_PIN -> ConfirmPinStepContent(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onBack = { viewModel.goBack() },
                    onSubmit = { viewModel.onConfirmPinSubmit(it) }
                )
                
                ChangePinStep.PASSPHRASE -> PassphraseStepContent(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onBack = { viewModel.goBack() },
                    onSubmit = { viewModel.onPassphraseSubmit(it) }
                )
                
                ChangePinStep.SUCCESS -> SuccessStepContent()
            }
        }
    }
}

@Composable
private fun ChangePinProgressIndicator(currentStep: ChangePinStep) {
    val steps = listOf(
        ChangePinStep.CURRENT_PIN to "Current",
        ChangePinStep.NEW_PIN to "New",
        ChangePinStep.CONFIRM_PIN to "Confirm",
        ChangePinStep.PASSPHRASE to "Verify"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (step, _) ->
            val isActive = currentStep.ordinal >= step.ordinal
            val isCompleted = currentStep.ordinal > step.ordinal
            
            Surface(
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCompleted) {
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
            
            if (index < steps.size - 1) {
                Surface(
                    modifier = Modifier
                        .width(30.dp)
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
private fun CurrentPinStepContent(
    isLoading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Enter Current PIN",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Verify your identity with your current PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("Current PIN") },
            singleLine = true,
            enabled = !isLoading,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(
                        if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onSubmit(pin) },
            enabled = pin.length == 6 && !isLoading,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Continue")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun NewPinStepContent(
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.LockReset,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Create New PIN",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Enter a new 6-digit PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("New PIN") },
            singleLine = true,
            enabled = !isLoading,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(
                        if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isLoading
            ) {
                Text("Back")
            }
            
            Button(
                onClick = { onSubmit(pin) },
                enabled = pin.length == 6 && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun ConfirmPinStepContent(
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Confirm New PIN",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Re-enter your new PIN to confirm",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("Confirm PIN") },
            singleLine = true,
            enabled = !isLoading,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(
                        if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isLoading
            ) {
                Text("Back")
            }
            
            Button(
                onClick = { onSubmit(pin) },
                enabled = pin.length == 6 && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun PassphraseStepContent(
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Verify Passphrase",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Enter your master passphrase to confirm the PIN change",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Warning
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "After changing your PIN, the app will lock and you'll need to use your new PIN to unlock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Master Passphrase") },
            enabled = !isLoading,
            isError = error != null,
            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                    Icon(
                        if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassphrase) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f),
            minLines = 2,
            maxLines = 3
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isLoading
            ) {
                Text("Back")
            }
            
            Button(
                onClick = { onSubmit(passphrase) },
                enabled = passphrase.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Change PIN")
                }
            }
        }
    }
}

@Composable
private fun SuccessStepContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "PIN Changed Successfully!",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Locking app...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}
