package com.motebaya.vaulten.presentation.screens.importpinsetup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.presentation.components.NumericKeypadSimple
import com.motebaya.vaulten.security.BiometricAuthHelper
import com.motebaya.vaulten.util.findActivity

/**
 * PIN Setup screen shown after importing a vault.
 * 
 * Flow:
 * 1. Enter and confirm 6-digit PIN
 * 2. Optional: Enable biometric authentication
 * 3. Complete - navigate to unlock screen
 */
@Composable
fun ImportPinSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: ImportPinSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onSetupComplete()
        }
    }
    
    // Handle biometric prompt
    LaunchedEffect(uiState.showBiometricPrompt) {
        if (uiState.showBiometricPrompt && activity != null) {
            val keystoreManager = viewModel.getKeystoreManager()
            val biometricHelper = BiometricAuthHelper(activity, keystoreManager)
            
            biometricHelper.authenticate(
                title = "Verify Fingerprint",
                subtitle = "Confirm your fingerprint to enable biometric unlock",
                negativeButtonText = "Cancel",
                onSuccess = { viewModel.onBiometricVerified() },
                onError = { errorMessage -> viewModel.onBiometricFailed(errorMessage) },
                onCancel = { viewModel.onBiometricCancelled() }
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        if (!uiState.showBiometricOption) {
            // PIN Setup Step
            PinSetupContent(
                pin = uiState.pin,
                confirmPin = uiState.confirmPin,
                onPinChange = viewModel::onPinChange,
                onConfirmPinChange = viewModel::onConfirmPinChange,
                onConfirm = viewModel::onPinConfirmed,
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage
            )
        } else {
            // Biometric Setup Step
            BiometricSetupContent(
                onEnable = viewModel::onEnableBiometricRequested,
                onSkip = viewModel::onSkipBiometric,
                errorMessage = uiState.biometricError
            )
        }
    }
}

@Composable
private fun ColumnScope.PinSetupContent(
    pin: String,
    confirmPin: String,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    // Track which field we're entering: true = PIN, false = confirmPIN
    var isEnteringPin by remember { mutableStateOf(true) }
    
    Icon(
        Icons.Default.Lock,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Create Your PIN",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = if (isEnteringPin)
            "Your vault has been imported. Now create a 6-digit PIN for quick daily access."
        else
            "Confirm your 6-digit PIN.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    if (isLoading) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
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
                onConfirm()
            }
        },
        enabled = !isLoading
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Back button to re-enter PIN
    if (!isEnteringPin) {
        TextButton(
            onClick = {
                isEnteringPin = true
                onConfirmPinChange("") // Clear confirm PIN
            }
        ) {
            Text("Re-enter PIN")
        }
    }
}

@Composable
private fun ColumnScope.BiometricSetupContent(
    onEnable: () -> Unit,
    onSkip: () -> Unit,
    errorMessage: String? = null
) {
    Icon(
        Icons.Default.Fingerprint,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Enable Biometrics",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Use your fingerprint for quick and secure access to your vault.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    Spacer(modifier = Modifier.weight(1f))
    
    Button(
        onClick = onEnable,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Fingerprint, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Enable Fingerprint")
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    TextButton(
        onClick = onSkip,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Skip for now")
    }
}
