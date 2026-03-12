package com.motebaya.vaulten.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.BuildConfig
import com.motebaya.vaulten.presentation.components.PageScaffold
import com.motebaya.vaulten.security.BiometricAuthHelper
import com.motebaya.vaulten.util.findActivity

/**
 * Settings screen for vault configuration.
 * Hosted inside MainScreen's HorizontalPager.
 */
@Composable
fun SettingsScreen(
    onExportVault: () -> Unit,
    onImportVault: () -> Unit,
    onChangePin: () -> Unit,
    onLock: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    
    // Handle biometric prompt for enable
    LaunchedEffect(uiState.showBiometricPromptForEnable) {
        if (uiState.showBiometricPromptForEnable && activity != null) {
            val keystoreManager = viewModel.getKeystoreManager()
            val biometricHelper = BiometricAuthHelper(activity, keystoreManager)
            
            biometricHelper.authenticate(
                title = "Enable Biometric Unlock",
                subtitle = "Verify your fingerprint to enable biometric unlock",
                negativeButtonText = "Cancel",
                onSuccess = { viewModel.onBiometricVerifiedForEnable() },
                onError = { errorMessage -> viewModel.onBiometricFailedForEnable(errorMessage) },
                onCancel = { viewModel.onBiometricCancelledForEnable() }
            )
        }
    }
    
    // PIN verification dialog for biometric toggle
    if (uiState.showPinVerificationForBiometric) {
        PinVerificationDialog(
            isLoading = uiState.isPinVerifying,
            error = uiState.pinVerificationError,
            onConfirm = { pin -> viewModel.onVerifyPinForBiometric(pin) },
            onDismiss = { viewModel.onDismissPinVerification() }
        )
    }
    
    PageScaffold(
        title = "Settings",
        onLock = onLock
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Security Section
            SettingsSection(title = "Security") {
                SettingsItem(
                    title = "Change PIN",
                    subtitle = "Update your 6-digit PIN",
                    onClick = onChangePin
                )
                
                // REMOVED: "Change Master Passphrase" - Passphrase is permanent
                
                SettingsToggle(
                    title = "Biometric Unlock",
                    subtitle = when {
                        !uiState.biometricAvailable -> "Not available on this device"
                        uiState.biometricEnabled -> "Enabled"
                        else -> "Disabled"
                    },
                    checked = uiState.biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // Request PIN verification before enabling
                            viewModel.onRequestBiometricEnable()
                        } else {
                            viewModel.onBiometricToggle(false)
                        }
                    },
                    enabled = uiState.biometricAvailable
                )
                
                SettingsItem(
                    title = "Auto-lock Timeout",
                    subtitle = "Lock after ${uiState.autoLockTimeoutMinutes} minutes of inactivity",
                    onClick = { /* Read-only for now */ }
                )
            }
            
            // Backup Section
            SettingsSection(title = "Backup & Restore") {
                SettingsItem(
                    title = "Export Vault",
                    subtitle = "Save encrypted backup file",
                    onClick = onExportVault
                )
                
                SettingsItem(
                    title = "Import Vault",
                    subtitle = "Restore from backup file",
                    onClick = onImportVault
                )
            }
            
            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                    title = "Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Dialog for PIN verification before enabling biometric.
 */
@Composable
private fun PinVerificationDialog(
    isLoading: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Verify PIN") },
        text = {
            Column {
                Text("Enter your PIN to enable biometric unlock.")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it.filter { c -> c.isDigit() } },
                    label = { Text("PIN") },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPin) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPin) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = pin.length == 6 && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
