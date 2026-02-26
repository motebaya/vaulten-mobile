package com.motebaya.vaulten.domain.repository

import android.net.Uri
import com.motebaya.vaulten.domain.entity.VaultInfo
import com.motebaya.vaulten.domain.entity.VaultResult
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Preview information for a backup file.
 */
data class BackupPreview(
    val credentialCount: Int,
    val platformCount: Int,
    val exportedAt: Instant?,
    val formatVersion: Int
)

/**
 * Metadata from backup file (parsed from metadata.json WITHOUT decrypting vault.enc).
 * 
 * Used to show preview statistics before requiring passphrase.
 */
data class BackupMetadata(
    /** Number of credentials in backup (-1 if unknown) */
    val credentialCount: Int,
    /** Number of platforms in backup (-1 if unknown) */
    val platformCount: Int,
    /** When the backup was exported (null if unknown) */
    val exportedAt: Instant?,
    /** Vault format version */
    val version: Int,
    /** Container format (e.g., "VLT1") */
    val format: String
)

/**
 * Result of a successful vault export.
 */
data class ExportResult(
    /** Name of the exported file */
    val fileName: String,
    /** Number of credentials exported */
    val credentialCount: Int,
    /** Number of platforms exported */
    val platformCount: Int
)

/**
 * Repository interface for vault-level operations.
 * 
 * Handles vault creation, import/export, and metadata.
 * 
 * SECURITY: Import/export operations use the passphrase-derived KEK,
 * not the device-bound KEK. This ensures cross-platform compatibility.
 */
interface VaultRepository {
    
    /**
     * Check if a vault exists on this device.
     * 
     * @return true if vault exists and is initialized
     */
    suspend fun vaultExists(): Boolean
    
    /**
     * Get vault metadata.
     * 
     * @return Vault info, or null if no vault exists
     */
    suspend fun getVaultInfo(): VaultInfo?
    
    /**
     * Create a new vault with the given passphrase.
     * 
     * This will:
     * 1. Generate a random DEK
     * 2. Derive KEK from passphrase
     * 3. Wrap DEK with KEK for backup purposes
     * 4. Store wrapped DEK in device keystore for daily access
     * 
     * @param passphrase The master passphrase
     * @return Success or error
     */
    suspend fun createVault(passphrase: String): VaultResult<Unit>
    
    /**
     * Export vault to vault.enc format.
     * 
     * The passphrase is required to derive the KEK for encryption.
     * Output format is compatible with the desktop app.
     * 
     * @param passphrase The master passphrase (for KEK derivation)
     * @param outputStream Where to write the encrypted vault
     * @return Success or error
     */
    suspend fun exportVault(
        passphrase: String,
        outputStream: OutputStream
    ): VaultResult<Unit>
    
    /**
     * Export vault to a URI location (Android SAF compatible).
     * 
     * @param destinationUri The URI to write the backup file to
     * @param passphrase The master passphrase for encryption
     * @return Result containing ExportResult with filename and statistics on success
     */
    suspend fun exportVault(destinationUri: Uri, passphrase: String): Result<ExportResult>
    
    /**
     * Import vault from vault.enc format.
     * 
     * This will:
     * 1. Parse the container header
     * 2. Derive KEK from passphrase
     * 3. Decrypt the vault data
     * 4. Replace local vault with imported data
     * 5. Re-wrap DEK with device keystore
     * 
     * @param passphrase The passphrase used to encrypt the vault
     * @param inputStream The encrypted vault file
     * @return Success or error
     */
    suspend fun importVault(
        passphrase: String,
        inputStream: InputStream
    ): VaultResult<Unit>
    
    /**
     * Import vault from a URI location (Android SAF compatible).
     * 
     * @param sourceUri The URI of the backup file to import
     * @param passphrase The master passphrase used when the backup was created
     * @return Result indicating success or failure
     */
    suspend fun importVault(sourceUri: Uri, passphrase: String): Result<Unit>
    
    /**
     * Validate a backup file structure without decrypting.
     * 
     * Checks:
     * - Filename starts with "vault"
     * - Valid zip archive
     * - Contains vault.enc
     * - Contains only vault.enc and optionally metadata.json
     * 
     * @param sourceUri The file to validate
     * @param fileName The original filename
     * @return Result indicating valid or error with message
     */
    suspend fun validateBackupFile(sourceUri: Uri, fileName: String): Result<Unit>
    
    /**
     * Preview a backup file without importing.
     * 
     * Decrypts and parses the backup to extract statistics.
     * Holds decrypted data in memory for subsequent confirmation.
     * 
     * @param sourceUri The backup file URI
     * @param passphrase The master passphrase
     * @return Preview info and decrypted data (for confirmation step)
     */
    suspend fun previewBackup(sourceUri: Uri, passphrase: String): Result<Pair<BackupPreview, ByteArray>>
    
    /**
     * Import from already-decrypted data (after preview confirmation).
     * 
     * Includes rollback on failure.
     * 
     * @param data The decrypted database bytes
     * @param passphrase The master passphrase (for re-keying)
     * @return Success or failure with rollback
     */
    suspend fun importFromDecryptedData(data: ByteArray, passphrase: String): Result<Unit>
    
    /**
     * Verify the master passphrase is correct.
     * 
     * @param passphrase The passphrase to verify
     * @return true if correct, false otherwise
     */
    suspend fun verifyPassphrase(passphrase: String): Boolean
    
    /**
     * Verify the PIN is correct.
     * 
     * @param pin The 6-digit PIN to verify
     * @return true if correct, false otherwise
     */
    suspend fun verifyPin(pin: String): Boolean
    
    /**
     * Change the master passphrase.
     * 
     * This re-wraps the DEK with a new KEK derived from the new passphrase.
     * The DEK itself does not change.
     * 
     * @param currentPassphrase The current passphrase
     * @param newPassphrase The new passphrase
     * @return Success or error
     */
    suspend fun changePassphrase(
        currentPassphrase: String,
        newPassphrase: String
    ): VaultResult<Unit>
    
    /**
     * Delete the vault and all data.
     * 
     * DANGER: This is irreversible.
     * 
     * @param passphrase Required for confirmation
     * @return Success or error
     */
    suspend fun deleteVault(passphrase: String): VaultResult<Unit>
    
    /**
     * Change the PIN with passphrase verification.
     * 
     * @param newPin The new 6-digit PIN
     * @param passphrase The master passphrase for verification
     * @return Success or error (WrongPassphrase if invalid)
     */
    suspend fun changePinWithPassphrase(newPin: String, passphrase: String): VaultResult<Unit>
    
    /**
     * Verify passphrase and return the unwrapped DEK.
     * 
     * Used during PIN reset flow to get the DEK for re-wrapping.
     * 
     * SECURITY: The returned DEK must be securely cleared after use.
     * 
     * @param passphrase The master passphrase
     * @return The unwrapped DEK, or error if passphrase is wrong
     */
    suspend fun verifyPassphraseAndGetDek(passphrase: String): VaultResult<ByteArray>
    
    /**
     * Store a device-wrapped DEK.
     * 
     * Used after PIN reset to store the re-wrapped DEK.
     * 
     * @param wrappedDek The DEK wrapped with device KEK
     * @return Success or error
     */
    suspend fun storeWrappedDekDevice(wrappedDek: ByteArray): VaultResult<Unit>
    
    /**
     * Parse backup metadata WITHOUT decrypting vault.enc.
     * 
     * Reads metadata.json from the backup zip to get statistics
     * for preview before requiring passphrase entry.
     * 
     * @param sourceUri The backup file URI
     * @return Metadata with statistics (some fields may be unknown/-1)
     */
    suspend fun parseBackupMetadata(sourceUri: Uri): Result<BackupMetadata>
}
