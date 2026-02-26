package com.motebaya.vaulten.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.presentation.theme.VaultThemeExtras

/**
 * Reusable numeric keypad component for PIN entry.
 * 
 * Layout: 1 2 3 / 4 5 6 / 7 8 9 / DEL 0 OK(✔)
 * 
 * Features:
 * - 6-digit PIN input with dot indicators
 * - Optional biometric button (replaces DEL in bottom-left)
 * - OK button (checkmark) in bottom-right
 * - Consistent styling with existing keypad in UnlockScreen
 * 
 * @param pin Current PIN value (0-6 digits)
 * @param onPinChange Called when PIN changes
 * @param onSubmit Called when OK is pressed (requires 6 digits)
 * @param enabled Whether the keypad is interactive
 * @param showBiometric Whether to show biometric option
 * @param onBiometric Called when biometric button is pressed
 * @param maxLength Maximum PIN length (default 6)
 */
@Composable
fun NumericKeypad(
    pin: String,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    showBiometric: Boolean = false,
    onBiometric: (() -> Unit)? = null,
    maxLength: Int = 6,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.5f
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // PIN dot indicators
        PinDotRow(
            length = pin.length,
            maxLength = maxLength,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Keypad grid
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.alpha(alpha)
        ) {
            // Row 1: 1, 2, 3
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                KeypadButton(text = "1", onClick = { appendDigit(pin, "1", maxLength, onPinChange) }, enabled = enabled)
                KeypadButton(text = "2", onClick = { appendDigit(pin, "2", maxLength, onPinChange) }, enabled = enabled)
                KeypadButton(text = "3", onClick = { appendDigit(pin, "3", maxLength, onPinChange) }, enabled = enabled)
            }
            // Row 2: 4, 5, 6
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                KeypadButton(text = "4", onClick = { appendDigit(pin, "4", maxLength, onPinChange) }, enabled = enabled)
                KeypadButton(text = "5", onClick = { appendDigit(pin, "5", maxLength, onPinChange) }, enabled = enabled)
                KeypadButton(text = "6", onClick = { appendDigit(pin, "6", maxLength, onPinChange) }, enabled = enabled)
            }
            // Row 3: 7, 8, 9
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                KeypadButton(text = "7", onClick = { appendDigit(pin, "7", maxLength, onPinChange) }, enabled = enabled)
                KeypadButton(text = "8", onClick = { appendDigit(pin, "8", maxLength, onPinChange) }, enabled = enabled)
                KeypadButton(text = "9", onClick = { appendDigit(pin, "9", maxLength, onPinChange) }, enabled = enabled)
            }
            // Row 4: DEL/Biometric, 0, OK
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (showBiometric && onBiometric != null) {
                    KeypadIconButton(
                        icon = Icons.Default.Fingerprint,
                        contentDescription = "Use fingerprint",
                        onClick = onBiometric,
                        enabled = enabled,
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    KeypadIconButton(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                        onClick = { if (pin.isNotEmpty()) onPinChange(pin.dropLast(1)) },
                        enabled = enabled && pin.isNotEmpty(),
                        isSpecial = true
                    )
                }
                KeypadButton(text = "0", onClick = { appendDigit(pin, "0", maxLength, onPinChange) }, enabled = enabled)
                KeypadIconButton(
                    icon = Icons.Default.Check,
                    contentDescription = "Confirm",
                    onClick = onSubmit,
                    enabled = enabled && pin.length == maxLength,
                    tint = if (pin.length == maxLength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

/**
 * Simplified keypad for setup screens with DEL and OK buttons.
 * No biometric option.
 */
@Composable
fun NumericKeypadSimple(
    pin: String,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    maxLength: Int = 6,
    modifier: Modifier = Modifier
) {
    NumericKeypad(
        pin = pin,
        onPinChange = onPinChange,
        onSubmit = onSubmit,
        enabled = enabled,
        showBiometric = false,
        onBiometric = null,
        maxLength = maxLength,
        modifier = modifier
    )
}

@Composable
private fun PinDotRow(
    length: Int,
    maxLength: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        repeat(maxLength) { index ->
            PinDot(filled = index < length)
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
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSpecial: Boolean = false
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

@Composable
private fun KeypadIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSpecial: Boolean = false,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
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
                tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun appendDigit(currentPin: String, digit: String, maxLength: Int, onPinChange: (String) -> Unit) {
    if (currentPin.length < maxLength) {
        onPinChange(currentPin + digit)
    }
}
