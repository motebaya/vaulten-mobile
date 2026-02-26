package com.motebaya.vaulten.presentation.screens.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.data.session.PendingExportImportFlow
import com.motebaya.vaulten.data.session.SessionManager
import com.motebaya.vaulten.domain.repository.VaultRepository
import com.motebaya.vaulten.presentation.components.ToastManager
import com.motebaya.vaulten.security.keystore.KeystoreManager
import com.motebaya.vaulten.util.UriUtils
import com.motebaya.vaulten.util.VaultDateFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * State for the Export Vault screen.
 * 
 * CORRECT FLOW ORDER:
 * 1. Ready - Show passphrase field
 * 2. Verifying - Verify passphrase
 * 3. PassphraseError - Wrong passphrase (stays on same screen)
 * 4. PassphraseVerified - Opens file picker
 * 5. Exporting - Writing zip
 * 6. Success/Error - Final state
 */
sealed class ExportState {
    /** Initial state - passphrase entry required first */
    data object Ready : ExportState()
    
    /** Verifying passphrase before opening file picker */
    data object Verifying : ExportState()
    
    /** Passphrase verification failed - stay on passphrase screen */
    data class PassphraseError(val message: String) : ExportState()
    
    /** Passphrase verified - file picker should be opened */
    data object PassphraseVerified : ExportState()
    
    /** Export in progress */
    data class Exporting(val progress: Float = 0f) : ExportState()
    
    /** Export completed successfully */
    data class Success(
        val fileName: String, 
        val location: String,
        val credentialCount: Int,
        val platformCount: Int
    ) : ExportState()
    
    /** Export failed */
    data class Error(val message: String) : ExportState()
}

/**
 * UI state for the Export Vault screen.
 */
data class ExportUiState(
    val state: ExportState = ExportState.Ready,
    val suggestedFileName: String = "",
    /** Signal to UI to launch file picker */
    val launchFilePicker: Boolean = false
)

/**
 * ViewModel for the Export Vault screen.
 * 
 * CORRECT FLOW:
 * 1. User enters passphrase and taps "Verify & Export"
 * 2. If passphrase wrong: show error, clear field, do NOT open file picker
 * 3. If passphrase correct: open file picker
 * 4. After file picker returns with URI: perform export
 * 5. Show success/error
 * 
 * This prevents opening the file picker with wrong passphrase,
 * which would create an empty/corrupt file.
 */
@HiltViewModel
class ExportVaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val sessionManager: SessionManager,
    private val keystoreManager: KeystoreManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ExportVault"
    }
    
    private val _uiState = MutableStateFlow(ExportUiState(
        suggestedFileName = generateBackupFileName()
    ))
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()
    
    // Secure passphrase storage using CharArray
    private var verifiedPassphrase: CharArray = charArrayOf()
    private var pendingUri: Uri? = null
    
    init {
        // Check for pending export flow to restore
        checkPendingExport()
    }
    
    /**
     * Check if there's a pending export to restore after unlock.
     * This handles the case where the app was killed during export and user unlocks again.
     */
    private fun checkPendingExport() {
        val pending = sessionManager.pendingExportImport.value
        if (pending != null && pending.route == "export") {
            Log.d(TAG, "Restoring pending export flow")
            
            // Restore URI
            pendingUri = pending.destinationUri
            
            // Decrypt passphrase if available
            pending.encryptedPassphrase?.let { encrypted ->
                val decrypted = keystoreManager.unwrapWithDeviceKek(encrypted)
                if (decrypted != null) {
                    verifiedPassphrase = String(decrypted, Charsets.UTF_8).toCharArray()
                    decrypted.fill(0)
                    
                    // Auto-resume export if we have both passphrase and URI
                    pending.destinationUri?.let { uri ->
                        Log.d(TAG, "Auto-resuming export")
                        // Only clear pending state when we're actually auto-resuming
                        // because the export will clear it when complete
                        sessionManager.clearPendingExportImport()
                        viewModelScope.launch {
                            withContext(NonCancellable) {
                                performExport(uri, String(verifiedPassphrase))
                            }
                        }
                        return // Exit early - don't clear again below
                    }
                }
            }
            
            // If we get here, we had a pending export but couldn't auto-resume
            // (missing passphrase or URI). This shouldn't happen normally but
            // we clear it to avoid getting stuck.
            // Note: We do NOT clear if just waiting for file picker callback
            if (pending.destinationUri == null && pending.encryptedPassphrase == null) {
                Log.d(TAG, "Pending export has no data to restore, clearing")
                sessionManager.clearPendingExportImport()
            }
        }
    }
    
    /**
     * Generate suggested backup filename with current timestamp.
     */
    private fun generateBackupFileName(): String {
        return VaultDateFormatter.formatForBackupFilename(Instant.now())
    }
    
    /**
     * User submitted passphrase for verification.
     * This is called BEFORE opening the file picker.
     */
    fun onPassphraseSubmit(passphrase: String) {
        if (passphrase.isEmpty()) return
        
        Log.d(TAG, "onPassphraseSubmit: starting verification")
        
        _uiState.value = _uiState.value.copy(
            state = ExportState.Verifying
        )
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "onPassphraseSubmit: calling verifyPassphrase")
                val valid = vaultRepository.verifyPassphrase(passphrase)
                
                if (valid) {
                    Log.d(TAG, "Passphrase verified - setting up lock exclusion")
                    
                    // Store passphrase securely for export
                    verifiedPassphrase = passphrase.toCharArray()
                    
                    // Store encrypted passphrase for potential restore after app lock
                    val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
                    val encryptedPassphrase = keystoreManager.wrapWithDeviceKek(passphraseBytes)
                    passphraseBytes.fill(0)
                    
                    // CRITICAL: Set skip auto-lock BEFORE opening file picker
                    // This must happen before we trigger the UI to open the picker
                    Log.d(TAG, "Setting skipAutoLock=true BEFORE triggering file picker")
                    sessionManager.setSkipAutoLock(true)
                    
                    sessionManager.setPendingExportImport(
                        PendingExportImportFlow(
                            route = "export",
                            encryptedPassphrase = encryptedPassphrase
                        )
                    )
                    
                    Log.d(TAG, "Triggering file picker launch")
                    _uiState.value = _uiState.value.copy(
                        state = ExportState.PassphraseVerified,
                        launchFilePicker = true
                    )
                } else {
                    Log.d(TAG, "Passphrase verification failed")
                    _uiState.value = _uiState.value.copy(
                        state = ExportState.PassphraseError("Invalid passphrase. Please try again.")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Passphrase verification error", e)
                _uiState.value = _uiState.value.copy(
                    state = ExportState.PassphraseError("Verification failed: ${e.message}")
                )
            }
        }
    }
    
    /**
     * Called by UI after file picker launcher has been invoked.
     * Clears the launchFilePicker flag to prevent re-launch on recomposition.
     */
    fun onFilePickerLaunched() {
        _uiState.value = _uiState.value.copy(launchFilePicker = false)
    }
    
    /**
     * Called when file picker is cancelled without selecting a location.
     */
    fun onFilePickerCancelled() {
        Log.d(TAG, "File picker cancelled")
        verifiedPassphrase.fill('0')
        sessionManager.setSkipAutoLock(false)
        sessionManager.clearPendingExportImport()
        _uiState.value = ExportUiState(
            suggestedFileName = generateBackupFileName()
        )
    }
    
    /**
     * User selected a destination for the backup file.
     * Since passphrase was already verified, proceed directly to export.
     */
    fun onDestinationSelected(uri: Uri) {
        Log.d(TAG, "Destination selected: ${uri.path}")
        pendingUri = uri
        
        // Update pending state with URI for potential restore
        val passphraseBytes = String(verifiedPassphrase).toByteArray(Charsets.UTF_8)
        val encryptedPassphrase = keystoreManager.wrapWithDeviceKek(passphraseBytes)
        passphraseBytes.fill(0)
        
        sessionManager.setPendingExportImport(
            PendingExportImportFlow(
                route = "export",
                destinationUri = uri,
                encryptedPassphrase = encryptedPassphrase
            )
        )
        
        // Proceed directly to export since passphrase is already verified
        // CRITICAL: Use NonCancellable to ensure the export completes even if
        // the ViewModel scope is cancelled (e.g., when returning from file picker)
        viewModelScope.launch {
            withContext(NonCancellable) {
                performExport(uri, String(verifiedPassphrase))
            }
        }
    }
    
    /**
     * Perform the actual vault export.
     */
    private suspend fun performExport(destinationUri: Uri, passphrase: String) {
        Log.d(TAG, "Starting export to ${destinationUri.path}")
        
        _uiState.value = _uiState.value.copy(
            state = ExportState.Exporting(0.1f)
        )
        
        try {
            _uiState.value = _uiState.value.copy(
                state = ExportState.Exporting(0.3f)
            )
            
            val result = vaultRepository.exportVault(destinationUri, passphrase)
            
            // Clear passphrase immediately after export completes
            verifiedPassphrase.fill('0')
            
            result.fold(
                onSuccess = { exportResult ->
                    Log.d(TAG, "Export successful: ${exportResult.fileName}")
                    
                    // NOW clear the skip auto-lock flag after successful export
                    sessionManager.setSkipAutoLock(false)
                    
                    // Get human-readable location using UriUtils
                    val location = UriUtils.getLocationPath(context, destinationUri)
                    
                    // Clear pending export flow - we don't need to restore success state anymore
                    // since we're showing it directly without locking
                    sessionManager.clearPendingExportImport()
                    
                    // Show toast notification
                    ToastManager.showSuccess("Vault exported successfully")
                    
                    _uiState.value = _uiState.value.copy(
                        state = ExportState.Success(
                            fileName = exportResult.fileName,
                            location = location,
                            credentialCount = exportResult.credentialCount,
                            platformCount = exportResult.platformCount
                        )
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Export failed: ${error.message}")
                    sessionManager.setSkipAutoLock(false)
                    sessionManager.clearPendingExportImport()
                    _uiState.value = _uiState.value.copy(
                        state = ExportState.Error("Export failed: ${error.message}")
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export exception", e)
            verifiedPassphrase.fill('0')
            sessionManager.setSkipAutoLock(false)
            sessionManager.clearPendingExportImport()
            _uiState.value = _uiState.value.copy(
                state = ExportState.Error("Export failed: ${e.message}")
            )
        }
    }
    
    /**
     * Reset state to try again.
     */
    fun onRetry() {
        verifiedPassphrase.fill('0')
        pendingUri = null
        sessionManager.setSkipAutoLock(false)
        sessionManager.clearPendingExportImport()
        _uiState.value = ExportUiState(
            suggestedFileName = generateBackupFileName()
        )
    }
    
    /**
     * Dismiss any error state.
     */
    fun onDismissError() {
        verifiedPassphrase.fill('0')
        pendingUri = null
        sessionManager.setSkipAutoLock(false)
        sessionManager.clearPendingExportImport()
        _uiState.value = ExportUiState(
            suggestedFileName = generateBackupFileName()
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        verifiedPassphrase.fill('0')
        pendingUri = null
    }
}
