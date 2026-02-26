package com.motebaya.vaulten.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motebaya.vaulten.presentation.theme.VaultThemeExtras

/**
 * Secure display component for showing BIP39 passphrase words.
 * 
 * Features:
 * - Shows words in a readable grid layout
 * - Optional visibility toggle (obscured by default)
 * - Copy to clipboard action
 * - Word numbering for verification
 */
@Composable
fun PassphraseDisplay(
    words: List<String>,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit = {},
    onRegenerate: () -> Unit = {}
) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(true) }
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Header with visibility toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Recovery Passphrase",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (isVisible) "Hide passphrase" else "Show passphrase",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Words grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(skeuoColors.surfaceBase)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            if (isVisible) {
                WordsGrid(words = words)
            } else {
                // Obscured view
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "• • • • • • • • • • • •",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the eye icon to reveal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Copy button
            OutlinedButton(
                onClick = {
                    copyToClipboard(context, words.joinToString(" "))
                    onCopy()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy")
            }
            
            // Regenerate button
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regenerate")
            }
        }
    }
}

@Composable
private fun WordsGrid(words: List<String>) {
    val columns = if (words.size <= 12) 3 else 4
    val rows = (words.size + columns - 1) / columns
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < words.size) {
                        WordChip(
                            number = index + 1,
                            word = words[index],
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChip(
    number: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(skeuoColors.surfaceCard)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Word count selector for 12 or 24 words.
 */
@Composable
fun WordCountSelector(
    selectedCount: Int,
    onCountSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(skeuoColors.surfaceBase)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WordCountOption(
            count = 12,
            isSelected = selectedCount == 12,
            onClick = { onCountSelected(12) },
            modifier = Modifier.weight(1f)
        )
        WordCountOption(
            count = 24,
            isSelected = selectedCount == 24,
            onClick = { onCountSelected(24) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WordCountOption(
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = "$count words",
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Passphrase", text)
    clipboard.setPrimaryClip(clip)
}
