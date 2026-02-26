package com.motebaya.vaulten.presentation.screens.setup

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.presentation.components.*
import com.motebaya.vaulten.presentation.theme.VaultThemeExtras
import com.motebaya.vaulten.security.BiometricAuthHelper
import com.motebaya.vaulten.security.keystore.KeystoreManager
import com.motebaya.vaulten.util.findActivity

private const val TAG = "SetupScreen"

/**
 * Setup screen for first-time vault creation.
 * 
 * Guides user through:
 * 1. Generating BIP39 passphrase (FIRST STEP - mandatory)
 * 2. Confirming passphrase saved
 * 3. Setting up 6-digit PIN
 * 4. Optional biometric enrollment
 */
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    onImportVault: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    // Use findActivity() extension to properly unwrap context to FragmentActivity
    val activity = context.findActivity()
    
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onSetupComplete()
        }
    }
    
    // Handle biometric prompt trigger during setup
    // Use key with timestamp to force re-trigger when showBiometricPrompt becomes true
    val showBiometricPrompt = uiState.showBiometricPrompt
    
    LaunchedEffect(showBiometricPrompt) {
        Log.d(TAG, "LaunchedEffect triggered: showBiometricPrompt=$showBiometricPrompt, activity=${activity != null}")
        
        if (showBiometricPrompt) {
            if (activity == null) {
                Log.e(TAG, "Activity is null - cannot show BiometricPrompt")
                viewModel.onBiometricSetupFailed("Cannot access activity for biometric prompt")
                return@LaunchedEffect
            }
            
            Log.d(TAG, "Creating BiometricAuthHelper and showing prompt")
            val keystoreManager = viewModel.getKeystoreManager()
            val biometricHelper = BiometricAuthHelper(activity, keystoreManager)
            
            biometricHelper.authenticate(
                title = "Verify Fingerprint",
                subtitle = "Confirm your fingerprint to enable biometric unlock",
                negativeButtonText = "Cancel",
                onSuccess = { 
                    Log.d(TAG, "Biometric success callback")
                    viewModel.onBiometricVerified() 
                },
                onError = { errorMessage -> 
                    Log.e(TAG, "Biometric error: $errorMessage")
                    viewModel.onBiometricSetupFailed(errorMessage) 
                },
                onCancel = { 
                    Log.d(TAG, "Biometric cancelled")
                    viewModel.onBiometricSetupCancelled() 
                }
            )
        }
    }
    
    // Different layouts based on step
    when (uiState.step) {
        SetupStep.GENERATE_PASSPHRASE, SetupStep.PASSPHRASE -> {
            // Scrollable content for passphrase steps
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Add top spacer to push title down
                Spacer(modifier = Modifier.height(32.dp))
                
                // Header
                Text(
                    text = when (uiState.step) {
                        SetupStep.GENERATE_PASSPHRASE -> "Create Your Vault"
                        SetupStep.PASSPHRASE -> "Enter Passphrase"
                        else -> ""
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Step indicator
                StepIndicator(currentStep = uiState.step)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                when (uiState.step) {
                    SetupStep.GENERATE_PASSPHRASE -> PassphraseGeneratorStep(
                        generatedWords = uiState.generatedWords,
                        selectedWordCount = uiState.selectedWordCount,
                        hasConfirmedSaved = uiState.hasUserConfirmedSaved,
                        onWordCountChanged = viewModel::onWordCountChanged,
                        onRegenerate = viewModel::onRegeneratePassphrase,
                        onCopied = viewModel::onPassphraseCopied,
                        onConfirmationChanged = viewModel::onPassphraseSavedConfirmationChanged,
                        onContinue = viewModel::onGeneratorContinue,
                        onImportExisting = onImportVault,
                        errorMessage = uiState.errorMessage
                    )
                    
                    SetupStep.PASSPHRASE -> ManualPassphraseStep(
                        passphrase = uiState.passphrase,
                        confirmPassphrase = uiState.confirmPassphrase,
                        onPassphraseChange = viewModel::onPassphraseChange,
                        onConfirmPassphraseChange = viewModel::onConfirmPassphraseChange,
                        onNext = viewModel::onPassphraseConfirmed,
                        onBack = viewModel::onBackToGenerator,
                        errorMessage = uiState.errorMessage,
                        isValid = uiState.isPassphraseValid
                    )
                    else -> {}
                }
            }
        }
        
        SetupStep.PIN, SetupStep.BIOMETRIC -> {
            // Centered layout for PIN and Biometric steps
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header
                    Text(
                        text = when (uiState.step) {
                            SetupStep.PIN -> "Create PIN"
                            SetupStep.BIOMETRIC -> "Biometric Setup"
                            else -> ""
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Step indicator
                    StepIndicator(currentStep = uiState.step)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    when (uiState.step) {
                        SetupStep.PIN -> PinStep(
                            pin = uiState.pin,
                            confirmPin = uiState.confirmPin,
                            onPinChange = viewModel::onPinChange,
                            onConfirmPinChange = viewModel::onConfirmPinChange,
                            onNext = viewModel::onPinConfirmed,
                            onBack = viewModel::onBackToGenerator,
                            errorMessage = uiState.errorMessage,
                            isValid = uiState.isPinValid
                        )
                        
                        SetupStep.BIOMETRIC -> BiometricStep(
                            onEnable = viewModel::onEnableBiometricRequested,
                            onSkip = viewModel::onSkipBiometric,
                            isAvailable = uiState.biometricAvailable,
                            errorMessage = uiState.biometricError
                        )
                        else -> {}
                    }
                }
            }
        }
        
        SetupStep.CREATING -> {
            // Centered loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Creating Vault",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    StepIndicator(currentStep = uiState.step)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    CreatingStep()
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: SetupStep) {
    val steps = listOf(
        SetupStep.GENERATE_PASSPHRASE,
        SetupStep.PIN,
        SetupStep.BIOMETRIC
    )
    val currentIndex = when (currentStep) {
        SetupStep.GENERATE_PASSPHRASE, SetupStep.PASSPHRASE -> 0
        SetupStep.PIN -> 1
        SetupStep.BIOMETRIC, SetupStep.CREATING -> 2
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .size(if (index == currentIndex) 10.dp else 8.dp)
                    .padding(0.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.small,
                    color = if (index <= currentIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                ) {}
            }
        }
    }
}

@Composable
private fun PassphraseGeneratorStep(
    generatedWords: List<String>,
    selectedWordCount: Int,
    hasConfirmedSaved: Boolean,
    onWordCountChanged: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onCopied: () -> Unit,
    onConfirmationChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onImportExisting: () -> Unit,
    errorMessage: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Security Warning Banner
        SkeuoWarningBanner(
            message = "The app NEVER stores your passphrase. It is fully owned by you. " +
                    "If you lose it, you may lose access to your vault exports, imports, and recovery. " +
                    "Write it down and store it safely!",
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        // Word count selector
        Text(
            text = "Select passphrase length:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        WordCountSelector(
            selectedCount = selectedWordCount,
            onCountSelected = onWordCountChanged
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Passphrase display
        if (generatedWords.isNotEmpty()) {
            PassphraseDisplay(
                words = generatedWords,
                onCopy = onCopied,
                onRegenerate = onRegenerate
            )
        } else {
            CircularProgressIndicator()
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Confirmation checkbox
        SkeuoCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasConfirmedSaved,
                    onCheckedChange = onConfirmationChanged
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I have securely saved my recovery passphrase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Continue button
        SkeuoButton(
            onClick = onContinue,
            enabled = hasConfirmedSaved && generatedWords.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Continue",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Import existing vault option
        TextButton(onClick = onImportExisting) {
            Text("Import existing vault instead")
        }
    }
}

@Composable
private fun ManualPassphraseStep(
    passphrase: String,
    confirmPassphrase: String,
    onPassphraseChange: (String) -> Unit,
    onConfirmPassphraseChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    errorMessage: String?,
    isValid: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter your existing passphrase or create a custom one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = confirmPassphrase,
            onValueChange = onConfirmPassphraseChange,
            label = { Text("Confirm Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null
        )
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            
            Button(
                onClick = onNext,
                enabled = isValid,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun PinStep(
    pin: String,
    confirmPin: String,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    errorMessage: String?,
    isValid: Boolean
) {
    // Track which field we're entering: true = PIN, false = confirmPIN
    var isEnteringPin by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isEnteringPin) 
                "Create a 6-digit PIN for quick daily access to your vault."
            else 
                "Confirm your 6-digit PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Show which step we're on
        Text(
            text = if (isEnteringPin) "Step 1 of 2: Enter PIN" else "Step 2 of 2: Confirm PIN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Numeric keypad for PIN entry
        NumericKeypadSimple(
            pin = if (isEnteringPin) pin else confirmPin,
            onPinChange = { newPin ->
                if (isEnteringPin) onPinChange(newPin) else onConfirmPinChange(newPin)
            },
            onSubmit = {
                if (isEnteringPin && pin.length == 6) {
                    isEnteringPin = false
                } else if (!isEnteringPin && confirmPin.length == 6) {
                    onNext()
                }
            },
            enabled = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = {
                    if (!isEnteringPin) {
                        // Go back to entering first PIN
                        isEnteringPin = true
                        onConfirmPinChange("") // Clear confirm PIN
                    } else {
                        onBack()
                    }
                }
            ) {
                Text(if (!isEnteringPin) "Re-enter PIN" else "Back")
            }
        }
    }
}

@Composable
private fun BiometricStep(
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    isAvailable: Boolean,
    errorMessage: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fingerprint icon
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = if (isAvailable) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isAvailable) {
                "Use your fingerprint for quick and secure access."
            } else {
                "Biometric authentication is not available on this device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isAvailable) {
            SkeuoButton(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enable Fingerprint",
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAvailable) "Skip for now" else "Continue")
        }
    }
}

@Composable
private fun CreatingStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Creating secure vault...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This may take a moment",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
