package com.motebaya.vaulten.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.domain.validation.CredentialValidators
import com.motebaya.vaulten.domain.validation.PasswordStrength

/**
 * Password strength meter with color-coded bar.
 * 
 * Colors:
 * - Weak (0-39): Red
 * - Medium (40-69): Yellow/Amber
 * - Strong (70+): Green
 * 
 * Matches web app password strength algorithm for parity.
 */
@Composable
fun PasswordStrengthMeter(
    password: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val strengthResult = remember(password) {
        CredentialValidators.calculatePasswordStrength(password)
    }
    
    // Animate the progress bar
    val progress by animateFloatAsState(
        targetValue = strengthResult.score / 100f,
        animationSpec = tween(300),
        label = "strength-progress"
    )
    
    // Determine color based on strength
    val targetColor = when (strengthResult.strength) {
        PasswordStrength.WEAK -> Color(0xFFEF4444)   // Red
        PasswordStrength.MEDIUM -> Color(0xFFF59E0B) // Yellow/Amber
        PasswordStrength.STRONG -> Color(0xFF22C55E) // Green
    }
    
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "strength-color"
    )
    
    val strengthLabel = when (strengthResult.strength) {
        PasswordStrength.WEAK -> "Weak"
        PasswordStrength.MEDIUM -> "Medium"
        PasswordStrength.STRONG -> "Strong"
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        
        // Label
        if (showLabel && password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strengthLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
                Text(
                    text = "${strengthResult.score}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
