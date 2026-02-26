package com.motebaya.vaulten.presentation.screens.unlock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.presentation.components.SkeuoCard
import com.motebaya.vaulten.presentation.theme.VaultThemeExtras
import com.motebaya.vaulten.security.BiometricAuthHelper
import com.motebaya.vaulten.security.keystore.KeystoreManager
import com.motebaya.vaulten.util.findActivity
import javax.inject.Inject

/**
 * Unlock screen for PIN entry and biometric authentication.
 * 
 * Features:
 * - 6-digit PIN pad
 * - Biometric authentication option (fingerprint icon)
 * - Failed attempt tracking with warnings
 * - Countdown timer lockout after max attempts
 */
@Composable
fun UnlockScreen(
    onUnlockSuccess: () -> Unit,
    onForgotPin: () -> Unit,
    onNeedSetup: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    val context = LocalContext.current
    
    // Use findActivity() extension to properly unwrap context to FragmentActivity
    val activity = context.findActivity()
    
    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            onUnlockSuccess()
        }
    }
    
    LaunchedEffect(uiState.needsSetup) {
        if (uiState.needsSetup) {
            onNeedSetup()
        }
    }
    
    // Handle biometric prompt trigger
    LaunchedEffect(uiState.showBiometricPrompt) {
        if (uiState.showBiometricPrompt && activity != null) {
            val keystoreManager = viewModel.getKeystoreManager()
            val biometricHelper = BiometricAuthHelper(activity, keystoreManager)
            
            biometricHelper.authenticate(
                title = "Unlock Vault",
                subtitle = "Use your fingerprint to unlock",
                negativeButtonText = "Use PIN",
                onSuccess = { viewModel.onBiometricSuccess() },
                onError = { errorMessage -> viewModel.onBiometricFailed() },
                onCancel = { viewModel.onBiometricCancelled() }
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo/title
            Text(
                text = "Vaulten",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Secure Password Manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // PIN entry card
            SkeuoCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (uiState.isLockedOut) "Vault Locked" else "Enter your PIN",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (uiState.isLockedOut) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Countdown Timer Display (when locked out)
                    if (uiState.isLockedOut) {
                        CountdownTimer(
                            formattedTime = uiState.formattedCooldown
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Too many failed attempts.\nPlease wait before trying again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // PIN dot indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            repeat(6) { index ->
                                PinDot(filled = index < uiState.pinLength)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Warning message (after 3 failed attempts)
                        if (uiState.showWarning && uiState.remainingAttempts != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Warning: ${uiState.remainingAttempts} attempts remaining",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // Error message
                        if (uiState.errorMessage != null) {
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // Loading indicator
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // PIN pad (disabled during lockout)
            PinPad(
                enabled = !uiState.isLockedOut && !uiState.isLoading,
                onDigitPress = { digit ->
                    viewModel.onDigitEntered(digit)
                },
                onBackspace = {
                    viewModel.onBackspace()
                },
                onBiometric = if (uiState.biometricAvailable && !uiState.isLockedOut) {
                    { viewModel.onBiometricRequested() }
                } else null
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Forgot PIN link
            TextButton(
                onClick = onForgotPin,
                enabled = !uiState.isLoading
            ) {
                Text(
                    text = "Forgot PIN?",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Countdown timer display component.
 */
@Composable
private fun CountdownTimer(formattedTime: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Until unlock available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun PinDot(filled: Boolean) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Surface(
        modifier = Modifier.size(16.dp),
        shape = MaterialTheme.shapes.small,
        color = if (filled) {
            MaterialTheme.colorScheme.primary
        } else {
            skeuoColors.surfaceBase
        },
        border = if (!filled) {
            ButtonDefaults.outlinedButtonBorder
        } else null
    ) {}
}

@Composable
private fun PinPad(
    enabled: Boolean,
    onDigitPress: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val alpha = if (enabled) 1f else 0.5f
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.alpha(alpha)
    ) {
        // Row 1: 1, 2, 3
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PinButton(text = "1", onClick = { onDigitPress(1) }, enabled = enabled)
            PinButton(text = "2", onClick = { onDigitPress(2) }, enabled = enabled)
            PinButton(text = "3", onClick = { onDigitPress(3) }, enabled = enabled)
        }
        // Row 2: 4, 5, 6
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PinButton(text = "4", onClick = { onDigitPress(4) }, enabled = enabled)
            PinButton(text = "5", onClick = { onDigitPress(5) }, enabled = enabled)
            PinButton(text = "6", onClick = { onDigitPress(6) }, enabled = enabled)
        }
        // Row 3: 7, 8, 9
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PinButton(text = "7", onClick = { onDigitPress(7) }, enabled = enabled)
            PinButton(text = "8", onClick = { onDigitPress(8) }, enabled = enabled)
            PinButton(text = "9", onClick = { onDigitPress(9) }, enabled = enabled)
        }
        // Row 4: biometric/empty, 0, backspace
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (onBiometric != null) {
                // Fingerprint icon button instead of "FP" text
                PinButtonWithIcon(
                    icon = Icons.Default.Fingerprint,
                    contentDescription = "Use fingerprint",
                    onClick = onBiometric,
                    enabled = enabled
                )
            } else {
                Spacer(modifier = Modifier.size(64.dp))
            }
            PinButton(text = "0", onClick = { onDigitPress(0) }, enabled = enabled)
            PinButton(text = "DEL", onClick = onBackspace, isSpecial = true, enabled = enabled)
        }
    }
}

@Composable
private fun PinButton(
    text: String,
    onClick: () -> Unit,
    isSpecial: Boolean = false,
    enabled: Boolean = true
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(64.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isSpecial) {
            skeuoColors.surfaceBase
        } else {
            skeuoColors.surfaceCard
        },
        shadowElevation = if (enabled) 4.dp else 0.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = if (isSpecial) 
                    MaterialTheme.typography.labelLarge
                else 
                    MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * PIN pad button with an icon instead of text (used for fingerprint).
 */
@Composable
private fun PinButtonWithIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(64.dp),
        shape = MaterialTheme.shapes.medium,
        color = skeuoColors.surfaceBase,
        shadowElevation = if (enabled) 4.dp else 0.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
