package com.motebaya.vaulten.presentation.screens.export

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private const val TAG = "ExportVaultScreen"

/**
 * Export Vault screen.
 * 
 * CORRECT FLOW:
 * 1. Show encryption info and passphrase field
 * 2. User enters passphrase and taps "Verify & Export"
 * 3. If wrong passphrase: show error, clear field, do NOT open file picker
 * 4. If correct passphrase: open file picker
 * 5. After file picker returns with URI: export vault
 * 6. Show success/error
 * 
 * Keyboard-aware: adjusts for keyboard and dismisses on tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportVaultScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportVaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // SAF Create Document launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.onDestinationSelected(uri)
        } else {
            viewModel.onFilePickerCancelled()
        }
    }
    
    // Launch file picker when passphrase is verified
    LaunchedEffect(uiState.launchFilePicker) {
        if (uiState.launchFilePicker) {
            Log.d(TAG, "LaunchedEffect: launching file picker")
            viewModel.onFilePickerLaunched()
            createDocumentLauncher.launch(uiState.suggestedFileName)
            Log.d(TAG, "LaunchedEffect: file picker launched")
        }
    }
    
    Scaffold(
        modifier = Modifier.imePadding(), // Adjust for keyboard
        topBar = {
            TopAppBar(
                title = { Text("Export Vault") },
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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState.state) {
                is ExportState.Ready, is ExportState.Verifying, is ExportState.PassphraseError -> {
                    PassphraseEntryContent(
                        suggestedFileName = uiState.suggestedFileName,
                        isLoading = state is ExportState.Verifying,
                        error = (state as? ExportState.PassphraseError)?.message,
                        onSubmit = viewModel::onPassphraseSubmit
                    )
                }
                
                is ExportState.PassphraseVerified -> {
                    // Brief loading state while file picker opens
                    WaitingForFilePickerContent()
                }
                
                is ExportState.Exporting -> {
                    ExportingContent(progress = state.progress)
                }
                
                is ExportState.Success -> {
                    SuccessContent(
                        fileName = state.fileName,
                        location = state.location,
                        credentialCount = state.credentialCount,
                        platformCount = state.platformCount,
                        onDone = onNavigateBack
                    )
                }
                
                is ExportState.Error -> {
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
private fun ColumnScope.PassphraseEntryContent(
    suggestedFileName: String,
    isLoading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    
    // Clear passphrase on error
    LaunchedEffect(error) {
        if (error != null) {
            passphrase = ""
        }
    }
    
    Spacer(modifier = Modifier.weight(0.2f))
    
    Icon(
        Icons.Default.CloudUpload,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "Export Your Vault",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Create a secure backup of your vault.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Encryption details card
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
                    text = "Encryption Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Your backup is NOT plaintext. It contains an encrypted vault (vault.enc) that cannot be read without your master passphrase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "• AES-256-GCM encryption\n• PBKDF2-HMAC-SHA256 key derivation\n• 310,000 iterations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // File info card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Backup file:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = suggestedFileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
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
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Your passphrase will be verified before saving.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    if (isLoading) {
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Verifying passphrase...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    Button(
        onClick = { onSubmit(passphrase) },
        modifier = Modifier.fillMaxWidth(),
        enabled = passphrase.isNotEmpty() && !isLoading
    ) {
        Icon(Icons.Default.Security, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Verify & Export")
    }
}

@Composable
private fun ColumnScope.WaitingForFilePickerContent() {
    Spacer(modifier = Modifier.weight(1f))
    
    CircularProgressIndicator()
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Opening file picker...",
        style = MaterialTheme.typography.titleMedium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Select a location to save your backup",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun ColumnScope.ExportingContent(progress: Float) {
    Spacer(modifier = Modifier.weight(1f))
    
    CircularProgressIndicator()
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Exporting...",
        style = MaterialTheme.typography.titleMedium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Encrypting and saving your vault backup",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun ColumnScope.SuccessContent(
    fileName: String,
    location: String,
    credentialCount: Int,
    platformCount: Int,
    onDone: () -> Unit
) {
    Spacer(modifier = Modifier.weight(0.3f))
    
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Export Successful",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(12.dp))
    
    Text(
        text = "Your vault has been securely exported with $credentialCount credential${if (credentialCount != 1) "s" else ""} and $platformCount platform${if (platformCount != 1) "s" else ""}.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // File details card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Saved to:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Security reminder card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Important",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Store this backup in a secure location. You will need your master passphrase to restore it. Never share your backup file or passphrase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    Text(
        text = "You can safely close this page.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Back to Settings")
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
        text = "Export Failed",
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
