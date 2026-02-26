package com.motebaya.vaulten.data.session

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the vault session state and lifecycle.
 * 
 * CRITICAL SECURITY: This class does NOT hold encryption keys.
 * It only tracks whether the vault is unlocked and manages session state.
 * 
 * The actual DEK (Data Encryption Key) is held by DekManager and is
 * immediately cleared when the vault is locked.
 * 
 * Session states:
 * - LOCKED: Vault is locked, no access to data
 * - UNLOCKED: User has authenticated, data is accessible
 * - SETUP_REQUIRED: No vault exists, need first-time setup
 */
@Singleton
class SessionManager @Inject constructor() {
    
    companion object {
        private const val TAG = "SessionManager"
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Locked)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _pendingImportUri = MutableStateFlow<Uri?>(null)
    val pendingImportUri: StateFlow<Uri?> = _pendingImportUri.asStateFlow()
    
    /**
     * Pending export/import flow to restore after unlock.
     * This allows resuming export/import if the app locks during file picker.
     */
    private val _pendingExportImport = MutableStateFlow<PendingExportImportFlow?>(null)
    val pendingExportImport: StateFlow<PendingExportImportFlow?> = _pendingExportImport.asStateFlow()
    
    /**
     * Flag indicating whether the app should skip auto-lock because
     * a file picker is open (export/import flow in progress).
     */
    private val _skipAutoLock = MutableStateFlow(false)
    val skipAutoLock: StateFlow<Boolean> = _skipAutoLock.asStateFlow()

    private val isLocking = AtomicBoolean(false)

    // Idle timeout in milliseconds (5 minutes)
    private val idleTimeoutMs = 5 * 60 * 1000L
    private var lastActivityTime = System.currentTimeMillis()

    /**
     * Lock the vault immediately.
     * This should be called on:
     * - Screen off
     * - App going to background
     * - User pressing home
     * - Idle timeout
     * - User explicit lock
     */
    fun lockVault() {
        Log.d(TAG, "lockVault called - stack: ${Thread.currentThread().stackTrace.take(8).drop(2).joinToString(" <- ") { it.methodName }}")
        if (isLocking.compareAndSet(false, true)) {
            try {
                Log.d(TAG, "lockVault: executing lock")
                // Notify listeners to clear their DEK references
                _sessionState.value = SessionState.Locking
                
                // Clear any pending operations
                // The actual DEK clearing happens in DekManager which observes this state
                
                _sessionState.value = SessionState.Locked
            } finally {
                isLocking.set(false)
            }
        } else {
            Log.d(TAG, "lockVault: already locking, skipped")
        }
    }

    /**
     * Unlock the vault after successful authentication.
     * Called after PIN/biometric verification and DEK unwrapping.
     */
    fun unlockVault() {
        _sessionState.value = SessionState.Unlocked
        resetActivityTimer()
    }

    /**
     * Mark that setup is required (no vault exists).
     */
    fun setSetupRequired() {
        _sessionState.value = SessionState.SetupRequired
    }

    /**
     * Called when the app comes to foreground.
     */
    fun onAppForeground() {
        // Check if session has expired due to idle timeout
        if (_sessionState.value == SessionState.Unlocked) {
            if (System.currentTimeMillis() - lastActivityTime > idleTimeoutMs) {
                lockVault()
            }
        }
    }

    /**
     * Called when the app goes to background.
     * SECURITY: Lock immediately - never leave vault unlocked in background.
     */
    fun onAppBackground() {
        lockVault()
    }

    /**
     * Called when activity is paused.
     */
    fun onActivityPaused() {
        // Could start a short timer here if we want to allow brief pauses
        // For maximum security, we lock immediately in onStop instead
    }

    /**
     * Called when activity is resumed.
     */
    fun onActivityResumed() {
        Log.d(TAG, "onActivityResumed: sessionState=${_sessionState.value}, skipAutoLock=${_skipAutoLock.value}")
        resetActivityTimer()
        
        // Check for idle timeout - but NOT during export/import flows
        if (_sessionState.value == SessionState.Unlocked && !_skipAutoLock.value) {
            val elapsed = System.currentTimeMillis() - lastActivityTime
            if (elapsed > idleTimeoutMs) {
                Log.d(TAG, "onActivityResumed: locking due to idle timeout (elapsed=${elapsed}ms)")
                lockVault()
            }
        }
    }

    /**
     * Reset the activity timer (user is actively using the app).
     */
    fun resetActivityTimer() {
        lastActivityTime = System.currentTimeMillis()
    }

    /**
     * Set a pending URI for vault import.
     */
    fun setPendingImportUri(uri: Uri) {
        _pendingImportUri.value = uri
    }

    /**
     * Clear the pending import URI.
     */
    fun clearPendingImportUri() {
        _pendingImportUri.value = null
    }

    /**
     * Clear all session data.
     * Called on app termination.
     */
    fun clearSession() {
        lockVault()
        _pendingImportUri.value = null
    }

    /**
     * Check if the vault is currently unlocked.
     */
    fun isUnlocked(): Boolean = _sessionState.value == SessionState.Unlocked

    /**
     * Check if the vault is currently locked.
     */
    fun isLocked(): Boolean = _sessionState.value == SessionState.Locked
    
    /**
     * Set a pending export/import flow to restore after unlock.
     * 
     * SECURITY: The passphrase is encrypted with device keystore before storage.
     */
    fun setPendingExportImport(flow: PendingExportImportFlow) {
        Log.d(TAG, "setPendingExportImport: route=${flow.route}, hasUri=${flow.destinationUri != null || flow.sourceUri != null}")
        _pendingExportImport.value = flow
    }
    
    /**
     * Clear pending export/import flow.
     */
    fun clearPendingExportImport() {
        Log.d(TAG, "clearPendingExportImport")
        _pendingExportImport.value?.encryptedPassphrase?.fill(0)
        _pendingExportImport.value = null
    }
    
    /**
     * Check if there's a pending export/import flow.
     */
    fun hasPendingExportImport(): Boolean = _pendingExportImport.value != null
    
    /**
     * Set whether auto-lock should be skipped (e.g., during file picker).
     * SECURITY: This is only used temporarily while system file picker is open.
     */
    fun setSkipAutoLock(skip: Boolean) {
        Log.d(TAG, "setSkipAutoLock: $skip (was ${_skipAutoLock.value})")
        _skipAutoLock.value = skip
    }
    
    /**
     * Check if auto-lock should be skipped.
     * Returns true if either skipAutoLock flag is set OR there's a pending export/import flow.
     */
    fun shouldSkipAutoLock(): Boolean {
        val skip = _skipAutoLock.value
        val pending = _pendingExportImport.value
        val result = skip || pending != null
        Log.d(TAG, "shouldSkipAutoLock: result=$result (skipFlag=$skip, pendingFlow=${pending != null})")
        return result
    }
}

/**
 * Data class representing a pending export/import flow.
 * 
 * When the app locks during a file picker, this preserves the state
 * so the user can resume after unlocking.
 */
data class PendingExportImportFlow(
    /** Route to return to: "export" or "import" */
    val route: String,
    /** Passphrase encrypted with device keystore (for auto-resume) */
    val encryptedPassphrase: ByteArray? = null,
    /** Destination URI for export */
    val destinationUri: Uri? = null,
    /** Source URI for import */
    val sourceUri: Uri? = null,
    /** Whether import is from setup screen */
    val isFromSetup: Boolean = false,
    /** Export result for displaying success page after unlock */
    val exportResult: ExportResultData? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingExportImportFlow) return false
        return route == other.route && 
               encryptedPassphrase.contentEquals(other.encryptedPassphrase) &&
               destinationUri == other.destinationUri &&
               sourceUri == other.sourceUri &&
               isFromSetup == other.isFromSetup &&
               exportResult == other.exportResult
    }
    
    override fun hashCode(): Int {
        var result = route.hashCode()
        result = 31 * result + (encryptedPassphrase?.contentHashCode() ?: 0)
        result = 31 * result + (destinationUri?.hashCode() ?: 0)
        result = 31 * result + (sourceUri?.hashCode() ?: 0)
        result = 31 * result + isFromSetup.hashCode()
        result = 31 * result + (exportResult?.hashCode() ?: 0)
        return result
    }
}

/**
 * Data class for storing export result to display after unlock.
 */
data class ExportResultData(
    val fileName: String,
    val location: String,
    val credentialCount: Int,
    val platformCount: Int
)

/**
 * Possible states of the vault session.
 */
sealed class SessionState {
    /** Vault is locked, authentication required */
    data object Locked : SessionState()
    
    /** Vault is in the process of locking (clearing keys) */
    data object Locking : SessionState()
    
    /** Vault is unlocked and accessible */
    data object Unlocked : SessionState()
    
    /** No vault exists, first-time setup required */
    data object SetupRequired : SessionState()
}
