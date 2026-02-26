package com.motebaya.vaulten.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Custom shapes for the Vault app.
 * 
 * Uses rounded corners for a soft, approachable feel
 * while maintaining a professional appearance.
 */
val VaultShapes = Shapes(
    // Small components like chips, small buttons
    small = RoundedCornerShape(8.dp),
    
    // Medium components like cards, text fields
    medium = RoundedCornerShape(12.dp),
    
    // Large components like dialogs, bottom sheets
    large = RoundedCornerShape(16.dp),
    
    // Extra large for full-screen cards
    extraLarge = RoundedCornerShape(24.dp)
)

// Custom shapes for specific components
val PinButtonShape = RoundedCornerShape(50) // Circular
val CredentialCardShape = RoundedCornerShape(16.dp)
val InputFieldShape = RoundedCornerShape(12.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
