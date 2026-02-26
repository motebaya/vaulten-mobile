package com.motebaya.vaulten.presentation.screens.importing

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.domain.repository.BackupMetadata
import com.motebaya.vaulten.util.UriUtils
import com.motebaya.vaulten.util.VaultDateFormatter

private const val TAG = "ImportVaultScreen"

/**
 * Import Vault screen.
 * 
 * CORRECT FLOW:
 * 1. Show import info and "Select Backup File" button
 * 2. User picks .zip file via SAF
 * 3. Validate file structure
 * 4. Show preview from metadata.json (WITHOUT passphrase)
 * 5. Show passphrase field + Load/Cancel buttons on preview screen
 * 6. User enters passphrase and taps Load
 * 7. Decrypt and import
 * 8. If from setup: redirect to PIN setup
 *    If from settings: force re-auth
 * 
 * @param isFromSetup If true, hides overwrite warning and redirects to PIN setup after import
 * @param onNavigateBack Navigate back to previous screen
 * @param onImportComplete Called when import completes (from settings - locks vault)
 * @param onImportCompleteFromSetup Called when import completes from setup (navigates to PIN setup)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportVaultScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit,
    onImportCompleteFromSetup: () -> Unit = {},
    isFromSetup: Boolean = false,
    viewModel: ImportVaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // SAF Open Document launcher for .zip files
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Use UriUtils to get the real filename (not SAF document ID)
            val fileName = UriUtils.getFileName(context, uri)
            Log.d(TAG, "File selected: $fileName (uri: ${uri.path})")
            viewModel.onFileSelected(uri, fileName)
        } else {
            viewModel.onFilePickerCancelled()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Vault") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState.state) {
                is ImportState.Ready -> {
                    ReadyContent(
                        isFromSetup = isFromSetup,
                        onSelectFile = {
                            Log.d(TAG, "onSelectFile: calling onFilePickerOpening before launching picker")
                            viewModel.onFilePickerOpening(isFromSetup)
                            Log.d(TAG, "onSelectFile: launching file picker")
                            openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    )
                }
                
                is ImportState.Validating -> {
                    ValidatingContent()
                }
                
                is ImportState.ValidationError -> {
                    ValidationErrorContent(
                        message = state.message,
                        onRetry = viewModel::onRetry
                    )
                }
                
                is ImportState.Preview -> {
                    PreviewContent(
                        metadata = state.metadata,
                        fileName = uiState.selectedFileName,
                        isFromSetup = isFromSetup,
                        isLoading = false,
                        error = null,
                        failureCount = uiState.failureCount,
                        onSubmit = viewModel::onPassphraseSubmit,
                        onCancel = viewModel::onCancelPreview
                    )
                }
                
                is ImportState.Decrypting -> {
                    val metadata = (uiState.state as? ImportState.Preview)?.metadata
                        ?: BackupMetadata(-1, -1, null, 1, "VLT1")
                    PreviewContent(
                        metadata = metadata,
                        fileName = uiState.selectedFileName,
                        isFromSetup = isFromSetup,
                        isLoading = true,
                        error = null,
                        failureCount = uiState.failureCount,
                        onSubmit = viewModel::onPassphraseSubmit,
                        onCancel = viewModel::onCancelPreview
                    )
                }
                
                is ImportState.PassphraseError -> {
                    PreviewContent(
                        metadata = state.metadata,
                        fileName = uiState.selectedFileName,
                        isFromSetup = isFromSetup,
                        isLoading = false,
                        error = state.message,
                        failureCount = state.failureCount,
                        onSubmit = viewModel::onPassphraseSubmit,
                        onCancel = viewModel::onCancelPreview
                    )
                }
                
                is ImportState.Importing -> {
                    ImportingContent(
                        progress = state.progress,
                        isFromSetup = isFromSetup
                    )
                }
                
                is ImportState.Success -> {
                    SuccessContent(
                        isFromSetup = isFromSetup,
                        onDone = if (isFromSetup) onImportCompleteFromSetup else onImportComplete
                    )
                }
                
                is ImportState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = viewModel::onRetry,
                        onDismiss = onNavigateBack
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ReadyContent(
    isFromSetup: Boolean,
    onSelectFile: () -> Unit
) {
    Spacer(modifier = Modifier.weight(0.2f))
    
    Icon(
        Icons.Default.CloudDownload,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "Import Vault Backup",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Restore your vault from a previously exported backup file.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Danger warning card (only if not from setup)
    if (!isFromSetup) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Warning",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Importing a backup will OVERWRITE all existing data in your vault. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Encryption info card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encryption",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Backups are encrypted with AES-256-GCM. Your master passphrase is required to decrypt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Requirements info card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Requirements:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• File must be a valid backup (.zip)\n• Filename must start with 'vault'\n• Contains encrypted vault.enc",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    Button(
        onClick = onSelectFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Select Backup File")
    }
}

@Composable
private fun ColumnScope.ValidatingContent() {
    Spacer(modifier = Modifier.weight(1f))
    CircularProgressIndicator()
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Validating backup file...",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun ColumnScope.ValidationErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Spacer(modifier = Modifier.weight(0.5f))
    
    Icon(
        Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "Invalid Backup File",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Backup files must:",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Have a filename starting with 'vault'\n• Be a valid .zip archive\n• Contain vault.enc file\n• Contain only vault.enc and metadata.json",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("Try Another File")
    }
}

@Composable
private fun ColumnScope.PreviewContent(
    metadata: BackupMetadata,
    fileName: String,
    isFromSetup: Boolean,
    isLoading: Boolean,
    error: String?,
    failureCount: Int,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    
    // Clear passphrase on error
    LaunchedEffect(error) {
        if (error != null) {
            passphrase = ""
        }
    }
    
    Spacer(modifier = Modifier.weight(0.1f))
    
    Icon(
        Icons.Default.Verified,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(12.dp))
    
    Text(
        text = "Backup Preview",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(4.dp))
    
    Text(
        text = fileName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Statistics Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Backup Contents",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Credentials", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (metadata.credentialCount >= 0) "${metadata.credentialCount}" else "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Platforms", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (metadata.platformCount >= 0) "${metadata.platformCount}" else "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            metadata.exportedAt?.let { exportedAt ->
                Spacer(modifier = Modifier.height(4.dp))
                
                val formattedDate = remember(exportedAt) {
                    VaultDateFormatter.formatShortDateTime(exportedAt)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Exported", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(12.dp))
    
    // Confirmation warning (only if not from setup)
    if (!isFromSetup) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Are you sure?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "This will permanently overwrite your current database. Export a backup first if needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Passphrase field
    OutlinedTextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text("Master Passphrase") },
        singleLine = true,
        enabled = !isLoading,
        visualTransformation = if (showPassphrase) 
            VisualTransformation.None 
        else 
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                Icon(
                    if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassphrase) "Hide" else "Show"
                )
            }
        },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        modifier = Modifier.fillMaxWidth()
    )
    
    // Warning after multiple failures
    if (failureCount >= 2) {
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Forgot your passphrase?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "You can create a new vault now and import later from Settings → Import when you remember your passphrase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
    
    if (isLoading) {
        Spacer(modifier = Modifier.height(12.dp))
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Decrypting backup...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            enabled = !isLoading
        ) {
            Text("Cancel")
        }
        
        Button(
            onClick = { onSubmit(passphrase) },
            modifier = Modifier.weight(1f),
            enabled = passphrase.isNotEmpty() && !isLoading,
            colors = if (!isFromSetup) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(if (!isFromSetup) "Yes, Load" else "Load")
        }
    }
}

@Composable
private fun ColumnScope.ImportingContent(
    progress: Float,
    isFromSetup: Boolean
) {
    Spacer(modifier = Modifier.weight(1f))
    
    CircularProgressIndicator()
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Importing...",
        style = MaterialTheme.typography.titleMedium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = if (isFromSetup) {
            "Restoring your vault, preparing PIN setup..."
        } else {
            "Restoring your vault from backup"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun ColumnScope.SuccessContent(
    isFromSetup: Boolean,
    onDone: () -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))
    
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Import Successful",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = if (isFromSetup) {
            "Your vault has been restored. You will now set up a PIN for quick access."
        } else {
            "Your vault has been restored. You will need to authenticate again."
        },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.weight(1f))
    
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isFromSetup) "Set Up PIN" else "Continue")
    }
}

@Composable
private fun ColumnScope.ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))
    
    Icon(
        Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Import Failed",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.weight(1f))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        ) {
            Text("Cancel")
        }
        
        Button(
            onClick = onRetry,
            modifier = Modifier.weight(1f)
        ) {
            Text("Try Again")
        }
    }
}
