package com.motebaya.vaulten.presentation.components.credential

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.CredentialType
import com.motebaya.vaulten.presentation.components.NumericKeypad
import com.motebaya.vaulten.presentation.components.ToastManager
import kotlinx.coroutines.delay

/**
 * State for the ViewCredentialModal.
 */
sealed class ViewCredentialState {
    /** Initial state - awaiting PIN verification */
    object PinRequired : ViewCredentialState()
    
    /** PIN verification in progress */
    object Verifying : ViewCredentialState()
    
    /** PIN verified, showing credential with countdown */
    data class Viewing(
        val credential: Credential,
        val remainingSeconds: Int = 300 // 5 minutes
    ) : ViewCredentialState()
    
    /** Error during PIN verification */
    data class Error(val message: String) : ViewCredentialState()
}

/**
 * Secure credential viewing modal.
 * 
 * Security features:
 * 1. Double-tap triggers PIN verification
 * 2. After valid PIN: decrypt in memory, display modal with copy buttons
 * 3. 5-minute auto-close countdown timer visible at bottom
 * 4. On close/timeout: wipe decrypted data from memory immediately
 * 5. NEVER write decrypted data to database/cache/logs
 */
@Composable
fun ViewCredentialModal(
    state: ViewCredentialState,
    biometricAvailable: Boolean = false,
    onPinSubmit: (pin: String) -> Unit,
    onBiometricRequested: () -> Unit = {},
    onForgotPin: () -> Unit,
    onDismiss: () -> Unit
) {
    // Use different sizing for PIN verification vs credential viewing
    val isViewingCredential = state is ViewCredentialState.Viewing
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .then(
                    if (isViewingCredential) {
                        Modifier.fillMaxHeight(0.85f)
                    } else {
                        Modifier.wrapContentHeight()
                    }
                ),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            when (state) {
                is ViewCredentialState.PinRequired -> {
                    PinVerificationContent(
                        isLoading = false,
                        error = null,
                        biometricAvailable = biometricAvailable,
                        onSubmit = onPinSubmit,
                        onBiometricRequested = onBiometricRequested,
                        onForgotPin = onForgotPin,
                        onDismiss = onDismiss
                    )
                }
                is ViewCredentialState.Verifying -> {
                    PinVerificationContent(
                        isLoading = true,
                        error = null,
                        biometricAvailable = biometricAvailable,
                        onSubmit = onPinSubmit,
                        onBiometricRequested = onBiometricRequested,
                        onForgotPin = onForgotPin,
                        onDismiss = onDismiss
                    )
                }
                is ViewCredentialState.Error -> {
                    PinVerificationContent(
                        isLoading = false,
                        error = state.message,
                        biometricAvailable = biometricAvailable,
                        onSubmit = onPinSubmit,
                        onBiometricRequested = onBiometricRequested,
                        onForgotPin = onForgotPin,
                        onDismiss = onDismiss
                    )
                }
                is ViewCredentialState.Viewing -> {
                    CredentialViewContent(
                        credential = state.credential,
                        remainingSeconds = state.remainingSeconds,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun PinVerificationContent(
    isLoading: Boolean,
    error: String?,
    biometricAvailable: Boolean = false,
    onSubmit: (String) -> Unit,
    onBiometricRequested: () -> Unit = {},
    onForgotPin: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    
    // Clear PIN when error changes to allow fresh retry
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
        }
    }
    
    Column(
        modifier = Modifier
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Enter PIN to View Credential",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Visible for 5 minutes after unlock",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Numeric keypad for PIN entry
        NumericKeypad(
            pin = pin,
            onPinChange = { pin = it },
            onSubmit = { onSubmit(pin) },
            enabled = !isLoading,
            showBiometric = biometricAvailable,
            onBiometric = onBiometricRequested
        )
        
        if (isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Forgot PIN and Cancel buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onForgotPin,
                enabled = !isLoading
            ) {
                Text(
                    text = "Forgot PIN?",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CredentialViewContent(
    credential: Credential,
    remainingSeconds: Int,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    
    // Format remaining time
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeString = String.format("%d:%02d", minutes, seconds)
    
    // Color based on remaining time
    val timerColor = when {
        remainingSeconds <= 30 -> MaterialTheme.colorScheme.error
        remainingSeconds <= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Credential Details",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Email
            if (!credential.email.isNullOrBlank()) {
                CopyableField(
                    label = "Email",
                    value = credential.email,
                    icon = Icons.Default.Email,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(credential.email))
                        ToastManager.showSuccess("Email copied to clipboard")
                    }
                )
            }
            
            // Username
            if (credential.username.isNotBlank()) {
                CopyableField(
                    label = "Username",
                    value = credential.username,
                    icon = Icons.Default.Person,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(credential.username))
                        ToastManager.showSuccess("Username copied to clipboard")
                    }
                )
            }
            
            // Password
            CopyableSecretField(
                label = "Password",
                value = credential.password,
                icon = Icons.Default.Lock,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(credential.password))
                    ToastManager.showSuccess("Password copied to clipboard")
                }
            )
            
            // Type-specific fields
            when (credential.credentialType) {
                CredentialType.GOOGLE -> {
                    // Account Name (if present)
                    if (!credential.accountName.isNullOrBlank()) {
                        CopyableField(
                            label = "Account Name",
                            value = credential.accountName,
                            icon = Icons.Default.Badge,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.accountName))
                                ToastManager.showSuccess("Account name copied")
                            }
                        )
                    }
                    
                    // Backup email
                    if (!credential.backupEmail.isNullOrBlank()) {
                        CopyableField(
                            label = "Backup Email",
                            value = credential.backupEmail,
                            icon = Icons.Default.AlternateEmail,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.backupEmail))
                                ToastManager.showSuccess("Backup email copied")
                            }
                        )
                    }
                    
                    // Phone
                    if (!credential.phoneNumber.isNullOrBlank()) {
                        CopyableField(
                            label = "Phone Number",
                            value = credential.phoneNumber,
                            icon = Icons.Default.Phone,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.phoneNumber))
                                ToastManager.showSuccess("Phone copied to clipboard")
                            }
                        )
                    }
                    
                    // Birthdate
                    if (!credential.birthdate.isNullOrBlank()) {
                        InfoField(
                            label = "Birthdate",
                            value = credential.birthdate,
                            icon = Icons.Default.Cake
                        )
                    }
                    
                    // 2FA Status
                    InfoField(
                        label = "2FA Enabled",
                        value = if (credential.twoFaEnabled) "Yes" else "No",
                        icon = Icons.Default.Security
                    )
                    
                    // Recovery codes
                    if (!credential.recoveryCodes.isNullOrBlank()) {
                        CopyableSecretField(
                            label = "Recovery Codes",
                            value = credential.recoveryCodes,
                            icon = Icons.Default.Key,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.recoveryCodes))
                                ToastManager.showSuccess("Recovery codes copied")
                            }
                        )
                    }
                }
                CredentialType.WALLET -> {
                    // Account Name (display name for the wallet)
                    if (!credential.accountName.isNullOrBlank()) {
                        CopyableField(
                            label = "Account Name",
                            value = credential.accountName,
                            icon = Icons.Default.Badge,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.accountName))
                                ToastManager.showSuccess("Account name copied to clipboard")
                            }
                        )
                    }
                    
                    // Private Key (from dedicated field)
                    if (!credential.privateKey.isNullOrBlank()) {
                        CopyableSecretField(
                            label = "Private Key",
                            value = credential.privateKey,
                            icon = Icons.Default.Key,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.privateKey))
                                ToastManager.showSuccess("Private key copied to clipboard")
                            }
                        )
                    }
                    
                    // Seed Phrase / Mnemonic (from dedicated field)
                    if (!credential.seedPhrase.isNullOrBlank()) {
                        CopyableSecretField(
                            label = "Seed Phrase / Mnemonic",
                            value = credential.seedPhrase,
                            icon = Icons.Default.Abc,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.seedPhrase))
                                ToastManager.showSuccess("Seed phrase copied to clipboard")
                            }
                        )
                    }
                    
                    // Recovery codes (if any)
                    if (!credential.recoveryCodes.isNullOrBlank()) {
                        CopyableSecretField(
                            label = "Recovery Codes",
                            value = credential.recoveryCodes,
                            icon = Icons.Default.VpnKey,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.recoveryCodes))
                                ToastManager.showSuccess("Recovery codes copied")
                            }
                        )
                    }
                    
                    // Notes (separate from seed phrase)
                    if (credential.notes.isNotBlank()) {
                        CopyableField(
                            label = "Notes",
                            value = credential.notes,
                            icon = Icons.Default.Notes,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.notes))
                                ToastManager.showSuccess("Notes copied to clipboard")
                            },
                            multiLine = true
                        )
                    }
                }
                CredentialType.SOCIAL -> {
                    // Social media credentials
                    // Phone
                    if (!credential.phoneNumber.isNullOrBlank()) {
                        CopyableField(
                            label = "Phone Number",
                            value = credential.phoneNumber,
                            icon = Icons.Default.Phone,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.phoneNumber))
                                ToastManager.showSuccess("Phone copied to clipboard")
                            }
                        )
                    }
                    
                    // Backup email
                    if (!credential.backupEmail.isNullOrBlank()) {
                        CopyableField(
                            label = "Recovery Email",
                            value = credential.backupEmail,
                            icon = Icons.Default.AlternateEmail,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(credential.backupEmail))
                                ToastManager.showSuccess("Recovery email copied")
                            }
                        )
                    }
                    
                    // 2FA Status
                    if (credential.twoFaEnabled) {
                        InfoField(
                            label = "2FA Enabled",
                            value = "Yes",
                            icon = Icons.Default.Security
                        )
                    }
                }
                else -> {
                    // Standard credential - no additional fields
                }
            }
            
            // Notes (skip for WALLET as it's handled above)
            if (credential.notes.isNotBlank() && credential.credentialType != CredentialType.WALLET) {
                CopyableField(
                    label = "Notes",
                    value = credential.notes,
                    icon = Icons.Default.Notes,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(credential.notes))
                        ToastManager.showSuccess("Notes copied to clipboard")
                    },
                    multiLine = true
                )
            }
            
            // Timestamps
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Created: ${formatTimestamp(credential.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Updated: ${formatTimestamp(credential.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Timer footer
        Surface(
            color = timerColor.copy(alpha = 0.1f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = timerColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-closing in $timeString",
                    style = MaterialTheme.typography.labelLarge,
                    color = timerColor
                )
            }
        }
    }
}

@Composable
private fun CopyableField(
    label: String,
    value: String,
    icon: ImageVector,
    onCopy: () -> Unit,
    multiLine: Boolean = false
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = if (multiLine) Alignment.Top else Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CopyableSecretField(
    label: String,
    value: String,
    icon: ImageVector,
    onCopy: () -> Unit
) {
    var showValue by remember { mutableStateOf(false) }
    
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (showValue) value else "•".repeat(minOf(value.length, 20)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            IconButton(onClick = { showValue = !showValue }) {
                Icon(
                    if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showValue) "Hide" else "Show",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String,
    icon: ImageVector
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatTimestamp(instant: java.time.Instant): String {
    val formatter = java.time.format.DateTimeFormatter
        .ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}
