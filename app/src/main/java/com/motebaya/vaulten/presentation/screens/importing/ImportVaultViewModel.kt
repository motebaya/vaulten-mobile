package com.motebaya.vaulten.presentation.screens.importing

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.data.session.PendingExportImportFlow
import com.motebaya.vaulten.data.session.SessionManager
import com.motebaya.vaulten.domain.repository.BackupMetadata
import com.motebaya.vaulten.domain.repository.VaultRepository
import com.motebaya.vaulten.security.keystore.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State for the Import Vault screen.
 * 
 * CORRECT FLOW ORDER:
 * 1. Ready - Select file
 * 2. Validating - Validate zip structure
 * 3. ValidationError - Invalid file
 * 4. Preview - Show metadata (WITHOUT passphrase, from metadata.json only)
 * 5. PassphraseRequired - User enters passphrase on preview screen
 * 6. Decrypting - Decrypting vault.enc
 * 7. PassphraseError - Wrong passphrase (stays on preview screen with counter)
 * 8. Importing - Replacing database
 * 9. Success/Error - Final state
 */
sealed class ImportState {
    /** Initial state - ready to select file */
    data object Ready : ImportState()
    
    /** Validating the selected file structure */
    data class Validating(val sourceUri: Uri) : ImportState()
    
    /** File validation failed */
    data class ValidationError(val message: String) : ImportState()
    
    /** File validated, showing preview from metadata.json (NO passphrase yet) */
    data class Preview(
        val sourceUri: Uri,
        val metadata: BackupMetadata
    ) : ImportState()
    
    /** Decrypting vault.enc with passphrase */
    data class Decrypting(val sourceUri: Uri) : ImportState()
    
    /** Passphrase verification failed */
    data class PassphraseError(
        val sourceUri: Uri,
        val metadata: BackupMetadata,
        val message: String,
        val failureCount: Int
    ) : ImportState()
    
    /** Import in progress */
    data class Importing(val progress: Float = 0f) : ImportState()
    
    /** Import completed successfully */
    data object Success : ImportState()
    
    /** Import failed */
    data class Error(val message: String) : ImportState()
}

/**
 * UI state for the Import Vault screen.
 */
data class ImportUiState(
    val state: ImportState = ImportState.Ready,
    val selectedFileName: String = "",
    /** Number of failed passphrase attempts */
    val failureCount: Int = 0
)

/**
 * ViewModel for the Import Vault screen.
 * 
 * CORRECT FLOW:
 * 1. User selects backup file
 * 2. Validate zip structure (vault.enc exists)
 * 3. Parse metadata.json for preview WITHOUT decrypting
 * 4. Show preview with statistics + passphrase field + Load/Cancel buttons
 * 5. User enters passphrase and taps Load
 * 6. Decrypt vault.enc and import
 * 7. If wrong passphrase: increment counter, show error, clear field
 * 8. After 2 failures: show warning about creating new vault
 */
@HiltViewModel
class ImportVaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val sessionManager: SessionManager,
    private val keystoreManager: KeystoreManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ImportVault"
        private const val MAX_FAILURES_BEFORE_WARNING = 2
    }
    
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    
    // Secure storage using CharArray
    private var pendingPassphrase: CharArray = charArrayOf()
    private var pendingDecryptedData: ByteArray? = null
    private var pendingUri: Uri? = null
    private var currentMetadata: BackupMetadata? = null
    private var failureCount = 0
    
    /**
     * Called before opening the file picker.
     * Prevents auto-lock while picker is open.
     * @param isFromSetup Whether import is from setup screen
     */
    fun onFilePickerOpening(isFromSetup: Boolean = false) {
        Log.d(TAG, "onFilePickerOpening: setting skip auto-lock BEFORE picker opens (isFromSetup=$isFromSetup)")
        // CRITICAL: Set skipAutoLock FIRST, then set pending flow
        sessionManager.setSkipAutoLock(true)
        sessionManager.setPendingExportImport(
            PendingExportImportFlow(route = "import", isFromSetup = isFromSetup)
        )
        Log.d(TAG, "onFilePickerOpening: lock exclusion set, safe to open picker")
    }
    
    /**
     * Called when file picker is cancelled without selection.
     */
    fun onFilePickerCancelled() {
        Log.d(TAG, "File picker cancelled")
        sessionManager.setSkipAutoLock(false)
        sessionManager.clearPendingExportImport()
    }
    
    /**
     * User selected a backup file.
     * Validate the file structure, then parse metadata for preview.
     */
    fun onFileSelected(uri: Uri, fileName: String) {
        Log.d(TAG, "File selected: $fileName")
        pendingUri = uri
        
        _uiState.value = ImportUiState(
            state = ImportState.Validating(uri),
            selectedFileName = fileName
        )
        
        // CRITICAL: Use NonCancellable to ensure validation completes
        viewModelScope.launch {
            withContext(NonCancellable) {
                // 1. Validate file structure
                val validationResult = vaultRepository.validateBackupFile(uri, fileName)
                
                validationResult.fold(
                    onSuccess = {
                        Log.d(TAG, "File validation passed")
                        
                        // 2. Parse metadata.json for preview (NO decryption)
                        val metadataResult = vaultRepository.parseBackupMetadata(uri)
                        
                        metadataResult.fold(
                            onSuccess = { metadata ->
                                Log.d(TAG, "Metadata parsed: $metadata")
                                currentMetadata = metadata
                                _uiState.value = _uiState.value.copy(
                                    state = ImportState.Preview(uri, metadata)
                                )
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Metadata parsing failed: ${error.message}")
                                // Even if metadata.json is missing, we can still proceed
                                // with unknown stats
                                val defaultMetadata = BackupMetadata(
                                    credentialCount = -1,
                                    platformCount = -1,
                                    exportedAt = null,
                                    version = 1,
                                    format = "VLT1"
                                )
                                currentMetadata = defaultMetadata
                                _uiState.value = _uiState.value.copy(
                                    state = ImportState.Preview(uri, defaultMetadata)
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "File validation failed: ${error.message}")
                        sessionManager.setSkipAutoLock(false)
                        _uiState.value = _uiState.value.copy(
                            state = ImportState.ValidationError(error.message ?: "Invalid backup file")
                        )
                    }
                )
            }
        }
    }
    
    /**
     * User entered passphrase and tapped Load.
     * Decrypt vault.enc and import.
     */
    fun onPassphraseSubmit(passphrase: String) {
        val currentState = _uiState.value.state
        val sourceUri = when (currentState) {
            is ImportState.Preview -> currentState.sourceUri
            is ImportState.PassphraseError -> currentState.sourceUri
            else -> return
        }
        
        pendingPassphrase = passphrase.toCharArray()
        
        _uiState.value = _uiState.value.copy(
            state = ImportState.Decrypting(sourceUri)
        )
        
        // Store encrypted passphrase for potential restore after app lock
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
        val encryptedPassphrase = keystoreManager.wrapWithDeviceKek(passphraseBytes)
        passphraseBytes.fill(0)
        
        sessionManager.setPendingExportImport(
            PendingExportImportFlow(
                route = "import",
                sourceUri = sourceUri,
                encryptedPassphrase = encryptedPassphrase
            )
        )
        
        // CRITICAL: Use NonCancellable to ensure the import completes even if
        // the ViewModel scope is cancelled
        viewModelScope.launch {
            withContext(NonCancellable) {
                val result = vaultRepository.previewBackup(sourceUri, String(pendingPassphrase))
                
                result.fold(
                    onSuccess = { (preview, decryptedData) ->
                        Log.d(TAG, "Decryption successful, starting import")
                        pendingDecryptedData = decryptedData
                        
                        // Proceed to import
                        performImport()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Decryption failed: ${error.message}")
                        pendingPassphrase.fill('0')
                        failureCount++
                        
                        val isPassphraseError = error.message?.contains("passphrase", ignoreCase = true) == true ||
                                                error.message?.contains("Wrong", ignoreCase = true) == true ||
                                                error.message?.contains("decrypt", ignoreCase = true) == true
                        
                        val message = if (isPassphraseError) {
                            "Wrong passphrase. Please try again."
                        } else {
                            "Failed to decrypt: ${error.message}"
                        }
                        
                        val metadata = currentMetadata ?: BackupMetadata(-1, -1, null, 1, "VLT1")
                        
                        _uiState.value = _uiState.value.copy(
                            state = ImportState.PassphraseError(
                                sourceUri = sourceUri,
                                metadata = metadata,
                                message = message,
                                failureCount = failureCount
                            ),
                            failureCount = failureCount
                        )
                    }
                )
            }
        }
    }
    
    /**
     * Perform the actual import after successful decryption.
     */
    private suspend fun performImport() {
        val data = pendingDecryptedData ?: return
        
        _uiState.value = _uiState.value.copy(
            state = ImportState.Importing(0.3f)
        )
        
        val result = vaultRepository.importFromDecryptedData(
            data,
            String(pendingPassphrase)
        )
        
        // Clear sensitive data regardless of result
        clearSensitiveData()
        
        result.fold(
            onSuccess = {
                Log.d(TAG, "Import successful")
                _uiState.value = _uiState.value.copy(
                    state = ImportState.Success
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Import failed: ${error.message}")
                _uiState.value = _uiState.value.copy(
                    state = ImportState.Error("Import failed: ${error.message}")
                )
            }
        )
    }
    
    /**
     * User cancelled from preview.
     * Clear data and return to file selection.
     */
    fun onCancelPreview() {
        Log.d(TAG, "Preview cancelled")
        clearSensitiveData()
        _uiState.value = ImportUiState()
    }
    
    /**
     * Reset state to try again (from error or validation error).
     */
    fun onRetry() {
        Log.d(TAG, "Retrying")
        clearSensitiveData()
        failureCount = 0
        _uiState.value = ImportUiState()
    }
    
    /**
     * Check if warning should be shown (after multiple failures).
     */
    fun shouldShowWarning(): Boolean {
        return failureCount >= MAX_FAILURES_BEFORE_WARNING
    }
    
    /**
     * Securely clear all sensitive data from memory.
     */
    private fun clearSensitiveData() {
        pendingPassphrase.fill('0')
        pendingDecryptedData?.fill(0)
        pendingDecryptedData = null
        pendingUri = null
        currentMetadata = null
        sessionManager.setSkipAutoLock(false)
        sessionManager.clearPendingExportImport()
    }
    
    override fun onCleared() {
        super.onCleared()
        clearSensitiveData()
    }
}
