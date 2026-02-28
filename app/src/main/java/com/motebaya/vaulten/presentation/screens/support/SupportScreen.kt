package com.motebaya.vaulten.presentation.screens.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.BuildConfig
import com.motebaya.vaulten.presentation.components.AppScaffold

private const val REPO_URL = "https://github.com/motebaya/vaulten-mobile"

/**
 * Support screen with app information and help.
 * Fully offline - no external links or network calls.
 */
@Composable
fun SupportScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLock: () -> Unit
) {
    AppScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        onLock = onLock,
        title = "Support"
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Info Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Vaulten",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Secure Password Manager",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // About Card - Repository and Credits
            AboutCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Security Features Card
            SupportCard(
                icon = Icons.Default.Shield,
                title = "Security Features",
                items = listOf(
                    "AES-256-GCM encryption for all data",
                    "BIP-39 passphrase generation",
                    "PIN + biometric authentication",
                    "Automatic lock on background",
                    "No cloud sync - 100% offline",
                    "No analytics or tracking"
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // How to Use Card
            SupportCard(
                icon = Icons.Default.Help,
                title = "How to Use",
                items = listOf(
                    "Store your passphrase securely offline",
                    "Use PIN for daily quick access",
                    "Enable biometric for faster unlock",
                    "Add platforms to organize credentials",
                    "Export vault for backup (requires passphrase)"
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Important Notes Card
            SupportCard(
                icon = Icons.Default.Warning,
                title = "Important Notes",
                items = listOf(
                    "Your passphrase is NEVER stored on device",
                    "If you lose your passphrase, data cannot be recovered",
                    "Export regularly to maintain backups",
                    "The vault file (vault.enc) is portable across devices"
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Credential Types Info
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Credential Types",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    CredentialTypeInfo(
                        title = "Standard",
                        description = "Username, password, email, notes"
                    )
                    
                    CredentialTypeInfo(
                        title = "Social Media",
                        description = "Username, email, password, phone, 2FA settings"
                    )
                    
                    CredentialTypeInfo(
                        title = "Crypto Wallet",
                        description = "Account name, private key, seed phrase"
                    )
                    
                    CredentialTypeInfo(
                        title = "Google Account",
                        description = "Full Google-specific fields including recovery codes"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "This app operates 100% offline.\nNo data leaves your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SupportCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(16.dp)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialTypeInfo(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "This project was developed as a privacy-focused solution for personal credential management. " +
                       "It was created out of a preference for local-first security over cloud-based services, " +
                       "without the complexity of self-hosted alternatives.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "If you find this project useful, consider visiting the repository. " +
                       "Contributions and feedback are always welcome.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = REPO_URL.removePrefix("https://"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                        context.startActivity(intent)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Thank you for using this app!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
