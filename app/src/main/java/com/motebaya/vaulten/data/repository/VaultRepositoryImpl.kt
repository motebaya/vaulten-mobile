package com.motebaya.vaulten.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.motebaya.vaulten.data.local.VaultDatabase
import com.motebaya.vaulten.data.local.entity.CredentialEntity
import com.motebaya.vaulten.data.local.entity.PlatformEntity
import com.motebaya.vaulten.domain.entity.VaultError
import com.motebaya.vaulten.domain.entity.VaultInfo
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.BackupMetadata
import com.motebaya.vaulten.domain.repository.ExportResult
import com.motebaya.vaulten.domain.repository.BackupPreview
import com.motebaya.vaulten.domain.repository.VaultRepository
import com.motebaya.vaulten.security.container.ContainerService
import com.motebaya.vaulten.security.crypto.DekManager
import com.motebaya.vaulten.security.crypto.KdfService
import com.motebaya.vaulten.security.keystore.KeystoreManager
import com.motebaya.vaulten.util.UriUtils
import com.motebaya.vaulten.util.VaultDateFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Implementation of VaultRepository.
 * 
 * Handles vault-level operations including:
 * - Vault creation with key generation
 * - Import/export in vault.enc format
 * - Passphrase management
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val dekManager: DekManager,
    private val kdfService: KdfService,
    private val keystoreManager: KeystoreManager,
    private val containerService: ContainerService
) : VaultRepository {

    companion object {
        private const val TAG = "VaultRepository"
        private const val PREFS_NAME = "vault_meta"
        private const val AUTH_PREFS_NAME = "vault_auth"
        private const val KEY_VAULT_EXISTS = "vault_exists"
        private const val KEY_VAULT_VERSION = "vault_version"
        private const val KEY_VAULT_CREATED = "vault_created"
        private const val KEY_SALT = "kdf_salt"
        private const val KEY_WRAPPED_DEK_PASSPHRASE = "wrapped_dek_passphrase"
        private const val KEY_WRAPPED_DEK_DEVICE = "wrapped_dek_device"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val VAULT_ENC_FILENAME = "vault.enc"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val authPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            AUTH_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun vaultExists(): Boolean {
        return prefs.getBoolean(KEY_VAULT_EXISTS, false)
    }

    override suspend fun getVaultInfo(): VaultInfo? {
        if (!vaultExists()) return null
        
        val version = prefs.getInt(KEY_VAULT_VERSION, VaultInfo.CURRENT_VERSION)
        val createdAt = prefs.getLong(KEY_VAULT_CREATED, System.currentTimeMillis())
        val count = database.credentialDao().getCredentialCount()
        
        return VaultInfo(
            version = version,
            createdAt = Instant.ofEpochMilli(createdAt),
            lastModifiedAt = Instant.now(),
            credentialCount = count
        )
    }

    override suspend fun createVault(passphrase: String): VaultResult<Unit> {
        if (vaultExists()) {
            return VaultResult.Error(VaultError.VaultAlreadyExists)
        }
        
        return try {
            // 1. Generate random DEK
            val dek = dekManager.generateDek()
            
            // 2. Generate salt for passphrase KDF
            val salt = kdfService.generateSalt()
            
            // 3. Derive KEK from passphrase
            val kekPassphrase = kdfService.deriveKek(passphrase, salt)
            
            // 4. Wrap DEK with passphrase KEK (for export/recovery)
            val wrappedDekPassphrase = dekManager.wrapDekForExport(dek, kekPassphrase)
                ?: return VaultResult.Error(VaultError.EncryptionFailed)
            
            // 5. Generate device KEK in Keystore
            if (!keystoreManager.generateDeviceKek(requireAuthentication = false)) {
                return VaultResult.Error(VaultError.KeystoreUnavailable)
            }
            
            // 6. Wrap DEK with device KEK (for daily usage)
            val wrappedDekDevice = dekManager.wrapDekForDevice(dek)
                ?: return VaultResult.Error(VaultError.KeystoreUnavailable)
            
            // 7. Store metadata
            val now = System.currentTimeMillis()
            prefs.edit()
                .putBoolean(KEY_VAULT_EXISTS, true)
                .putInt(KEY_VAULT_VERSION, VaultInfo.CURRENT_VERSION)
                .putLong(KEY_VAULT_CREATED, now)
                .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK_PASSPHRASE, android.util.Base64.encodeToString(wrappedDekPassphrase, android.util.Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK_DEVICE, android.util.Base64.encodeToString(wrappedDekDevice, android.util.Base64.NO_WRAP))
                .apply()
            
            // 8. Set the DEK as active (vault is now unlocked)
            dekManager.setDek(dek)
            
            // 9. Clear sensitive data
            dek.fill(0)
            kekPassphrase.fill(0)
            
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Failed to create vault"))
        }
    }

    override suspend fun exportVault(passphrase: String, outputStream: OutputStream): VaultResult<Unit> {
        // Verify passphrase first
        if (!verifyPassphrase(passphrase)) {
            return VaultResult.Error(VaultError.WrongPassphrase)
        }
        
        return try {
            // Export database to bytes
            // In a real implementation, this would serialize the database
            // For now, we'll use a placeholder
            val databaseBytes = serializeDatabase()
            
            // Write container
            if (containerService.writeContainer(databaseBytes, passphrase, outputStream)) {
                VaultResult.Success(Unit)
            } else {
                VaultResult.Error(VaultError.EncryptionFailed)
            }
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Export failed"))
        }
    }

    override suspend fun importVault(passphrase: String, inputStream: InputStream): VaultResult<Unit> {
        return try {
            // Read and decrypt container
            val data = containerService.readContainer(inputStream, passphrase)
                ?: return VaultResult.Error(VaultError.WrongPassphrase)
            
            // Deserialize and replace database - this returns the DEK from the backup
            val importedDek = deserializeDatabase(data)
            
            // CRITICAL: Use the imported DEK, not a new one
            // The credential fields are encrypted with this specific DEK
            val dek = importedDek 
                ?: return VaultResult.Error(VaultError.Unknown(
                    "Backup does not contain DEK. May be from older version."
                ))
            
            // Generate new salt and wrap the imported DEK with device keys
            val salt = kdfService.generateSalt()
            val kekPassphrase = kdfService.deriveKek(passphrase, salt)
            
            val wrappedDekPassphrase = dekManager.wrapDekForExport(dek, kekPassphrase)
                ?: return VaultResult.Error(VaultError.EncryptionFailed)
            
            if (!keystoreManager.generateDeviceKek(requireAuthentication = false)) {
                return VaultResult.Error(VaultError.KeystoreUnavailable)
            }
            
            val wrappedDekDevice = dekManager.wrapDekForDevice(dek)
                ?: return VaultResult.Error(VaultError.KeystoreUnavailable)
            
            val now = System.currentTimeMillis()
            prefs.edit()
                .putBoolean(KEY_VAULT_EXISTS, true)
                .putInt(KEY_VAULT_VERSION, VaultInfo.CURRENT_VERSION)
                .putLong(KEY_VAULT_CREATED, now)
                .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK_PASSPHRASE, android.util.Base64.encodeToString(wrappedDekPassphrase, android.util.Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK_DEVICE, android.util.Base64.encodeToString(wrappedDekDevice, android.util.Base64.NO_WRAP))
                .apply()
            
            dekManager.setDek(dek)
            dek.fill(0)
            kekPassphrase.fill(0)
            
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Import failed"))
        }
    }

    override suspend fun verifyPassphrase(passphrase: String): Boolean {
        val saltStr = prefs.getString(KEY_SALT, null) ?: return false
        val wrappedDekStr = prefs.getString(KEY_WRAPPED_DEK_PASSPHRASE, null) ?: return false
        
        val salt = android.util.Base64.decode(saltStr, android.util.Base64.NO_WRAP)
        val wrappedDek = android.util.Base64.decode(wrappedDekStr, android.util.Base64.NO_WRAP)
        
        val kek = kdfService.deriveKek(passphrase, salt)
        val dek = dekManager.unwrapDekFromExport(wrappedDek, kek)
        
        kek.fill(0)
        dek?.fill(0)
        
        return dek != null
    }

    override suspend fun changePassphrase(currentPassphrase: String, newPassphrase: String): VaultResult<Unit> {
        if (!verifyPassphrase(currentPassphrase)) {
            return VaultResult.Error(VaultError.WrongPassphrase)
        }
        
        return try {
            // Get current DEK
            val dek = dekManager.getDek()
                ?: return VaultResult.Error(VaultError.VaultLocked)
            
            // Generate new salt
            val newSalt = kdfService.generateSalt()
            
            // Derive new KEK
            val newKek = kdfService.deriveKek(newPassphrase, newSalt)
            
            // Wrap DEK with new KEK
            val newWrappedDek = dekManager.wrapDekForExport(dek, newKek)
                ?: return VaultResult.Error(VaultError.EncryptionFailed)
            
            // Store new wrapped DEK and salt
            prefs.edit()
                .putString(KEY_SALT, android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK_PASSPHRASE, android.util.Base64.encodeToString(newWrappedDek, android.util.Base64.NO_WRAP))
                .apply()
            
            newKek.fill(0)
            
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Failed to change passphrase"))
        }
    }

    override suspend fun deleteVault(passphrase: String): VaultResult<Unit> {
        if (!verifyPassphrase(passphrase)) {
            return VaultResult.Error(VaultError.WrongPassphrase)
        }
        
        return try {
            // Clear DEK
            dekManager.clearDek()
            
            // Delete Keystore keys
            keystoreManager.deleteAllKeys()
            
            // Clear database
            database.credentialDao().deleteAllCredentials()
            database.platformDao().deleteAllPlatforms()
            
            // Clear preferences
            prefs.edit().clear().apply()
            
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Failed to delete vault"))
        }
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val storedHash = authPrefs.getString(KEY_PIN_HASH, null) ?: return false
        return try {
            BCrypt.checkpw(pin, storedHash)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exportVault(destinationUri: Uri, passphrase: String): Result<ExportResult> {
        return withContext(Dispatchers.IO + NonCancellable) {
            try {
                Log.d(TAG, "Export started")
                
                // 1. Verify passphrase first
                if (!verifyPassphrase(passphrase)) {
                    Log.e(TAG, "Export failed: Invalid passphrase")
                    return@withContext Result.failure(Exception("Invalid passphrase"))
                }
                Log.d(TAG, "Passphrase verified")
                
                // NOTE: The vault may be "locked" (DEK cleared) during file picker.
                // We need to temporarily unwrap the DEK using the passphrase for serialization.
                // The passphrase was just verified, so this is safe.
                val dekResult = verifyPassphraseAndGetDek(passphrase)
                if (dekResult !is VaultResult.Success) {
                    Log.e(TAG, "Export failed: Could not get DEK")
                    return@withContext Result.failure(Exception("Could not retrieve encryption key"))
                }
                val dek = dekResult.data
                
                // Temporarily set DEK for serialization (it may have been cleared)
                dekManager.setDek(dek)
                
                // 2. Serialize database content (includes DEK for import)
                val databaseBytes = serializeDatabase()
                if (databaseBytes.isEmpty()) {
                    Log.e(TAG, "Export failed: No data to export")
                    return@withContext Result.failure(Exception("No data to export"))
                }
                Log.d(TAG, "Database serialized: ${databaseBytes.size} bytes")
                
                // 3. Encrypt using ContainerService (creates proper VLT1 format)
                val vaultEncStream = ByteArrayOutputStream()
                if (!containerService.writeContainer(databaseBytes, passphrase, vaultEncStream)) {
                    Log.e(TAG, "Export failed: Encryption failed")
                    return@withContext Result.failure(Exception("Encryption failed"))
                }
                
                val encryptedContent = vaultEncStream.toByteArray()
                if (encryptedContent.size < 50) { // Minimum header size
                    Log.e(TAG, "Export failed: Invalid encryption output")
                    return@withContext Result.failure(Exception("Encryption produced invalid output"))
                }
                Log.d(TAG, "Encryption complete: ${encryptedContent.size} bytes")
                
                // 4. Get statistics for metadata
                val platforms = database.platformDao().getAllPlatformsSync()
                val credentials = database.credentialDao().getAllCredentialsSync()
                
                // 5. Write to destination URI
                Log.d(TAG, "Writing zip to destination")
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: return@withContext Result.failure(Exception("Cannot open destination file"))
                
                try {
                    BufferedOutputStream(outputStream).use { bos ->
                        ZipOutputStream(bos).use { zipOut ->
                            // Add vault.enc (encrypted)
                            zipOut.putNextEntry(ZipEntry(VAULT_ENC_FILENAME))
                            zipOut.write(encryptedContent)
                            zipOut.closeEntry()
                            
                            // Add metadata.json
                            zipOut.putNextEntry(ZipEntry("metadata.json"))
                            val metadata = JSONObject().apply {
                                put("version", VaultInfo.CURRENT_VERSION)
                                put("exportedAt", Instant.now().toString())
                                put("format", "VLT1")
                                put("stats", JSONObject().apply {
                                    put("credentialCount", credentials.size)
                                    put("platformCount", platforms.size)
                                })
                            }.toString(2)
                            zipOut.write(metadata.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                            
                            zipOut.finish()
                            bos.flush()
                        }
                    }
                } finally {
                    try {
                        outputStream.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing output stream: ${e.message}")
                    }
                }
                Log.d(TAG, "Zip finished successfully")
                
                // Use UriUtils to get the real filename (not SAF document ID)
                val fileName = UriUtils.getFileName(context, destinationUri)
                
                Log.i(TAG, "Export completed: $fileName with ${credentials.size} credentials, ${platforms.size} platforms")
                Result.success(ExportResult(
                    fileName = fileName,
                    credentialCount = credentials.size,
                    platformCount = platforms.size
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Export exception: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun importVault(sourceUri: Uri, passphrase: String): Result<Unit> {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return Result.failure(Exception("Cannot open backup file"))
            
            var vaultEncBytes: ByteArray? = null
            var metadataJson: String? = null
            
            // Read zip contents
            inputStream.use { ins ->
                ZipInputStream(ins).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            VAULT_ENC_FILENAME -> {
                                vaultEncBytes = zipIn.readBytes()
                            }
                            "metadata.json" -> {
                                metadataJson = zipIn.readBytes().toString(Charsets.UTF_8)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            
            if (vaultEncBytes == null) {
                return Result.failure(Exception("Invalid backup file: missing vault.enc"))
            }
            
            // Parse metadata to get salt and wrapped DEK
            // For now, we'll use the stream-based import
            val vaultInputStream = ByteArrayInputStream(vaultEncBytes)
            val result = importVault(passphrase, vaultInputStream)
            
            when (result) {
                is VaultResult.Success -> Result.success(Unit)
                is VaultResult.Error -> Result.failure(Exception(result.error.toString()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun validateBackupFile(sourceUri: Uri, fileName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Filename must start with "vault"
                if (!fileName.lowercase().startsWith("vault")) {
                    return@withContext Result.failure(
                        Exception("Invalid backup: filename must start with 'vault'")
                    )
                }
                
                // 2. Validate zip structure
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(Exception("Cannot open file"))
                
                var hasVaultEnc = false
                var fileCount = 0
                val allowedFiles = setOf("vault.enc", "metadata.json")
                
                inputStream.use { ins ->
                    BufferedInputStream(ins).use { bis ->
                        ZipInputStream(bis).use { zipIn ->
                            var entry = zipIn.nextEntry
                            while (entry != null) {
                                fileCount++
                                
                                if (entry.name == VAULT_ENC_FILENAME) {
                                    hasVaultEnc = true
                                    // Check it's not empty
                                    val bytes = zipIn.readBytes()
                                    if (bytes.isEmpty()) {
                                        return@withContext Result.failure(
                                            Exception("Invalid backup: vault.enc is empty")
                                        )
                                    }
                                }
                                
                                if (entry.name !in allowedFiles) {
                                    return@withContext Result.failure(
                                        Exception("Invalid backup: unexpected file '${entry.name}'")
                                    )
                                }
                                
                                if (fileCount > 2) {
                                    return@withContext Result.failure(
                                        Exception("Invalid backup: too many files in archive")
                                    )
                                }
                                
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                        }
                    }
                }
                
                if (!hasVaultEnc) {
                    return@withContext Result.failure(
                        Exception("Invalid backup: missing vault.enc")
                    )
                }
                
                Result.success(Unit)
            } catch (e: ZipException) {
                Result.failure(Exception("Invalid backup: not a valid zip file"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun previewBackup(
        sourceUri: Uri,
        passphrase: String
    ): Result<Pair<BackupPreview, ByteArray>> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(Exception("Cannot open file"))
                
                var vaultEncBytes: ByteArray? = null
                var metadataJson: String? = null
                
                // Extract zip contents
                inputStream.use { ins ->
                    BufferedInputStream(ins).use { bis ->
                        ZipInputStream(bis).use { zipIn ->
                            var entry = zipIn.nextEntry
                            while (entry != null) {
                                when (entry.name) {
                                    VAULT_ENC_FILENAME -> vaultEncBytes = zipIn.readBytes()
                                    "metadata.json" -> metadataJson = zipIn.readBytes().toString(Charsets.UTF_8)
                                }
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                        }
                    }
                }
                
                if (vaultEncBytes == null || vaultEncBytes!!.isEmpty()) {
                    return@withContext Result.failure(Exception("Invalid backup: missing or empty vault.enc"))
                }
                
                // Decrypt using ContainerService
                val decryptedData = containerService.readContainer(
                    ByteArrayInputStream(vaultEncBytes),
                    passphrase
                ) ?: return@withContext Result.failure(Exception("Wrong passphrase or corrupted backup"))
                
                // Parse statistics without modifying database
                val json = JSONObject(String(decryptedData, Charsets.UTF_8))
                val credentialCount = json.optJSONArray("credentials")?.length() ?: 0
                val platformCount = json.optJSONArray("platforms")?.length() ?: 0
                val exportedAtStr = json.optString("exportedAt", "")
                val exportedAt = exportedAtStr.takeIf { it.isNotEmpty() }?.let { 
                    try { Instant.parse(it) } catch (e: Exception) { null }
                }
                val version = json.optInt("version", 1)
                
                val preview = BackupPreview(
                    credentialCount = credentialCount,
                    platformCount = platformCount,
                    exportedAt = exportedAt,
                    formatVersion = version
                )
                
                Result.success(preview to decryptedData)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun importFromDecryptedData(
        data: ByteArray,
        passphrase: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Store current state for rollback
            var rollbackNeeded = false
            var originalPlatforms: List<PlatformEntity>? = null
            var originalCredentials: List<CredentialEntity>? = null
            val originalVaultExists = prefs.getBoolean(KEY_VAULT_EXISTS, false)
            val originalVaultVersion = prefs.getInt(KEY_VAULT_VERSION, -1)
            val originalSalt = prefs.getString(KEY_SALT, null)
            val originalWrappedDekPassphrase = prefs.getString(KEY_WRAPPED_DEK_PASSPHRASE, null)
            val originalWrappedDekDevice = prefs.getString(KEY_WRAPPED_DEK_DEVICE, null)
            
            try {
                // 1. Backup current state for rollback
                originalPlatforms = database.platformDao().getAllPlatformsSync()
                originalCredentials = database.credentialDao().getAllCredentialsSync()
                
                rollbackNeeded = true
                
                // 2. Deserialize and replace database - this returns the DEK from the backup
                val importedDek = deserializeDatabase(data)
                
                // 3. Use the imported DEK or fail if not present
                // CRITICAL: We must use the SAME DEK that encrypted the credential fields
                val dek = importedDek ?: throw Exception(
                    "Import failed: Backup does not contain DEK. " +
                    "This backup may be from an older version or corrupted."
                )
                
                // 4. Generate new salt and wrap the imported DEK with new keys
                val salt = kdfService.generateSalt()
                val kekPassphrase = kdfService.deriveKek(passphrase, salt)
                
                val wrappedDekPassphrase = dekManager.wrapDekForExport(dek, kekPassphrase)
                if (wrappedDekPassphrase == null) {
                    throw Exception("Failed to wrap DEK for passphrase")
                }
                
                if (!keystoreManager.generateDeviceKek(requireAuthentication = false)) {
                    throw Exception("Failed to generate device KEK")
                }
                
                val wrappedDekDevice = dekManager.wrapDekForDevice(dek)
                if (wrappedDekDevice == null) {
                    throw Exception("Failed to wrap DEK for device")
                }
                
                // 5. Store vault metadata
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putBoolean(KEY_VAULT_EXISTS, true)
                    .putInt(KEY_VAULT_VERSION, VaultInfo.CURRENT_VERSION)
                    .putLong(KEY_VAULT_CREATED, now)
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_WRAPPED_DEK_PASSPHRASE, Base64.encodeToString(wrappedDekPassphrase, Base64.NO_WRAP))
                    .putString(KEY_WRAPPED_DEK_DEVICE, Base64.encodeToString(wrappedDekDevice, Base64.NO_WRAP))
                    .apply()
                
                // 6. Set the DEK in memory (vault is now unlocked)
                dekManager.setDek(dek)
                
                // 7. Secure cleanup
                dek.fill(0)
                kekPassphrase.fill(0)
                
                rollbackNeeded = false
                Result.success(Unit)
                
            } catch (e: Exception) {
                // ROLLBACK on failure
                if (rollbackNeeded) {
                    try {
                        // Restore database
                        if (originalPlatforms != null && originalCredentials != null) {
                            database.credentialDao().deleteAllCredentials()
                            database.platformDao().deleteAllPlatforms()
                            database.platformDao().insertPlatformsReplace(originalPlatforms)
                            database.credentialDao().insertCredentials(originalCredentials)
                        }
                        
                        // Restore preferences
                        val editor = prefs.edit()
                        editor.putBoolean(KEY_VAULT_EXISTS, originalVaultExists)
                        if (originalVaultVersion >= 0) {
                            editor.putInt(KEY_VAULT_VERSION, originalVaultVersion)
                        }
                        originalSalt?.let { editor.putString(KEY_SALT, it) }
                        originalWrappedDekPassphrase?.let { editor.putString(KEY_WRAPPED_DEK_PASSPHRASE, it) }
                        originalWrappedDekDevice?.let { editor.putString(KEY_WRAPPED_DEK_DEVICE, it) }
                        editor.apply()
                        
                        return@withContext Result.failure(Exception("Import failed (rolled back): ${e.message}"))
                    } catch (rollbackError: Exception) {
                        // Rollback itself failed - critical error
                        return@withContext Result.failure(
                            Exception("Import failed and rollback failed. Manual recovery required: ${e.message}")
                        )
                    }
                }
                
                Result.failure(e)
            }
        }
    }

    override suspend fun changePinWithPassphrase(newPin: String, passphrase: String): VaultResult<Unit> {
        // Verify passphrase first
        if (!verifyPassphrase(passphrase)) {
            return VaultResult.Error(VaultError.WrongPassphrase)
        }
        
        // Validate PIN format
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            return VaultResult.Error(VaultError.ValidationError("PIN must be 6 digits"))
        }
        
        return try {
            // Hash new PIN with bcrypt
            val hash = BCrypt.hashpw(newPin, BCrypt.gensalt(12))
            
            // Store new hash
            authPrefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .apply()
            
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Failed to change PIN"))
        }
    }
    
    // Placeholder for database serialization
    /**
     * Serialize all credentials and platforms to JSON format.
     * 
     * Structure:
     * {
     *   "version": 1,
     *   "exportedAt": "2025-01-15T10:30:00Z",
     *   "dek": "base64-encoded DEK",  // CRITICAL: DEK for encrypted fields
     *   "platforms": [...],
     *   "credentials": [...]
     * }
     * 
     * SECURITY NOTE: The DEK is included in plaintext inside the JSON, but the
     * entire JSON is encrypted with the passphrase-derived KEK by ContainerService.
     * This ensures the DEK is preserved for import while remaining secure.
     */
    private suspend fun serializeDatabase(): ByteArray {
        val platforms = database.platformDao().getAllPlatformsSync()
        val credentials = database.credentialDao().getAllCredentialsSync()
        
        // Get the current DEK - CRITICAL for import to work
        val dek = dekManager.getDek()
            ?: throw IllegalStateException("Cannot serialize database: DEK not available")
        
        val root = JSONObject()
        root.put("version", VaultInfo.CURRENT_VERSION)
        root.put("dek", Base64.encodeToString(dek, Base64.NO_WRAP))
        root.put("exportedAt", Instant.now().toString())
        
        // Serialize platforms
        val platformsArray = JSONArray()
        for (platform in platforms) {
            val obj = JSONObject()
            obj.put("id", platform.id)
            obj.put("name", platform.name)
            obj.put("domain", platform.domain)
            obj.put("iconName", platform.iconName)
            obj.put("color", platform.color)
            obj.put("type", platform.type)
            obj.put("isCustom", platform.isCustom)
            obj.put("lastNameEditAt", platform.lastNameEditAt ?: JSONObject.NULL)
            platformsArray.put(obj)
        }
        root.put("platforms", platformsArray)
        
        // Serialize credentials (encrypted fields are stored as Base64)
        val credentialsArray = JSONArray()
        for (cred in credentials) {
            val obj = JSONObject()
            obj.put("id", cred.id)
            obj.put("platformId", cred.platformId)
            obj.put("username", cred.username)
            obj.put("encryptedPassword", Base64.encodeToString(cred.encryptedPassword, Base64.NO_WRAP))
            obj.put("encryptedNotes", Base64.encodeToString(cred.encryptedNotes, Base64.NO_WRAP))
            obj.put("createdAt", cred.createdAt.toEpochMilli())
            obj.put("updatedAt", cred.updatedAt.toEpochMilli())
            obj.put("email", cred.email ?: JSONObject.NULL)
            obj.put("credentialType", cred.credentialType)
            obj.put("encryptedBackupEmail", cred.encryptedBackupEmail?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            obj.put("encryptedPhoneNumber", cred.encryptedPhoneNumber?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            obj.put("birthdate", cred.birthdate ?: JSONObject.NULL)
            obj.put("twoFaEnabled", cred.twoFaEnabled)
            obj.put("encryptedRecoveryCodes", cred.encryptedRecoveryCodes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            obj.put("accountName", cred.accountName ?: JSONObject.NULL)
            obj.put("encryptedPrivateKey", cred.encryptedPrivateKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            obj.put("encryptedSeedPhrase", cred.encryptedSeedPhrase?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            obj.put("lastEditedAt", cred.lastEditedAt ?: JSONObject.NULL)
            credentialsArray.put(obj)
        }
        root.put("credentials", credentialsArray)
        
        return root.toString(2).toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Deserialize JSON data and replace database contents.
     * 
     * @param data The JSON data to deserialize
     * @return The DEK from the backup (if present), or null for legacy backups
     */
    private suspend fun deserializeDatabase(data: ByteArray): ByteArray? {
        val json = String(data, Charsets.UTF_8)
        val root = JSONObject(json)
        
        // Extract DEK from backup (critical for import to work correctly)
        val dekBase64 = if (root.has("dek")) root.optString("dek") else null
        val dek = dekBase64?.takeIf { it.isNotEmpty() }?.let { 
            try { Base64.decode(it, Base64.NO_WRAP) } catch (e: Exception) { null } 
        }
        
        // Clear existing data
        database.credentialDao().deleteAllCredentials()
        database.platformDao().deleteAllPlatforms()
        
        // Parse and insert platforms
        val platformsArray = root.optJSONArray("platforms") ?: JSONArray()
        val platforms = mutableListOf<PlatformEntity>()
        for (i in 0 until platformsArray.length()) {
            val obj = platformsArray.getJSONObject(i)
            platforms.add(
                PlatformEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    domain = obj.getString("domain"),
                    iconName = obj.getString("iconName"),
                    color = obj.getString("color"),
                    type = obj.optString("type", "social"),
                    isCustom = obj.getBoolean("isCustom"),
                    lastNameEditAt = if (obj.isNull("lastNameEditAt")) null else obj.getLong("lastNameEditAt")
                )
            )
        }
        database.platformDao().insertPlatformsReplace(platforms)
        
        // Parse and insert credentials
        val credentialsArray = root.optJSONArray("credentials") ?: JSONArray()
        val credentials = mutableListOf<CredentialEntity>()
        for (i in 0 until credentialsArray.length()) {
            val obj = credentialsArray.getJSONObject(i)
            credentials.add(
                CredentialEntity(
                    id = obj.getString("id"),
                    platformId = obj.getString("platformId"),
                    username = obj.getString("username"),
                    encryptedPassword = Base64.decode(obj.getString("encryptedPassword"), Base64.NO_WRAP),
                    encryptedNotes = Base64.decode(obj.getString("encryptedNotes"), Base64.NO_WRAP),
                    createdAt = Instant.ofEpochMilli(obj.getLong("createdAt")),
                    updatedAt = Instant.ofEpochMilli(obj.getLong("updatedAt")),
                    email = if (obj.isNull("email")) null else obj.getString("email"),
                    credentialType = obj.optString("credentialType", "standard"),
                    encryptedBackupEmail = if (obj.isNull("encryptedBackupEmail")) null else Base64.decode(obj.getString("encryptedBackupEmail"), Base64.NO_WRAP),
                    encryptedPhoneNumber = if (obj.isNull("encryptedPhoneNumber")) null else Base64.decode(obj.getString("encryptedPhoneNumber"), Base64.NO_WRAP),
                    birthdate = if (obj.isNull("birthdate")) null else obj.getString("birthdate"),
                    twoFaEnabled = obj.optBoolean("twoFaEnabled", false),
                    encryptedRecoveryCodes = if (obj.isNull("encryptedRecoveryCodes")) null else Base64.decode(obj.getString("encryptedRecoveryCodes"), Base64.NO_WRAP),
                    accountName = if (obj.isNull("accountName")) null else obj.getString("accountName"),
                    encryptedPrivateKey = if (obj.isNull("encryptedPrivateKey")) null else Base64.decode(obj.getString("encryptedPrivateKey"), Base64.NO_WRAP),
                    encryptedSeedPhrase = if (obj.isNull("encryptedSeedPhrase")) null else Base64.decode(obj.getString("encryptedSeedPhrase"), Base64.NO_WRAP),
                    lastEditedAt = if (obj.isNull("lastEditedAt")) null else obj.getLong("lastEditedAt")
                )
            )
        }
        database.credentialDao().insertCredentials(credentials)
        
        // Return the extracted DEK (null for legacy backups without DEK)
        return dek
    }
    
    /**
     * Verify passphrase and return the unwrapped DEK.
     * 
     * Used during PIN reset to get the DEK for re-wrapping with device KEK.
     */
    override suspend fun verifyPassphraseAndGetDek(passphrase: String): VaultResult<ByteArray> {
        val saltStr = prefs.getString(KEY_SALT, null)
            ?: return VaultResult.Error(VaultError.VaultNotSetup)
        val wrappedDekStr = prefs.getString(KEY_WRAPPED_DEK_PASSPHRASE, null)
            ?: return VaultResult.Error(VaultError.VaultNotSetup)
        
        val salt = android.util.Base64.decode(saltStr, android.util.Base64.NO_WRAP)
        val wrappedDek = android.util.Base64.decode(wrappedDekStr, android.util.Base64.NO_WRAP)
        
        val kek = kdfService.deriveKek(passphrase, salt)
        val dek = dekManager.unwrapDekFromExport(wrappedDek, kek)
        
        kek.fill(0)
        
        return if (dek != null) {
            VaultResult.Success(dek)
        } else {
            VaultResult.Error(VaultError.WrongPassphrase)
        }
    }
    
    /**
     * Store a device-wrapped DEK.
     * 
     * Used after PIN reset to store the re-wrapped DEK.
     */
    override suspend fun storeWrappedDekDevice(wrappedDek: ByteArray): VaultResult<Unit> {
        return try {
            prefs.edit()
                .putString(KEY_WRAPPED_DEK_DEVICE, android.util.Base64.encodeToString(wrappedDek, android.util.Base64.NO_WRAP))
                .apply()
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.Unknown(e.message ?: "Failed to store wrapped DEK"))
        }
    }
    
    /**
     * Parse backup metadata WITHOUT decrypting vault.enc.
     * 
     * Reads metadata.json from the backup zip to get statistics
     * for preview before requiring passphrase entry.
     */
    override suspend fun parseBackupMetadata(sourceUri: Uri): Result<BackupMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Parsing backup metadata from: ${sourceUri.path}")
                
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(Exception("Cannot open backup file"))
                
                var metadataJson: String? = null
                
                inputStream.use { ins ->
                    BufferedInputStream(ins).use { bis ->
                        ZipInputStream(bis).use { zipIn ->
                            var entry = zipIn.nextEntry
                            while (entry != null) {
                                if (entry.name == "metadata.json") {
                                    metadataJson = zipIn.bufferedReader().readText()
                                    break
                                }
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                        }
                    }
                }
                
                if (metadataJson == null) {
                    // No metadata.json found, return defaults
                    Log.d(TAG, "No metadata.json found, returning unknown stats")
                    return@withContext Result.success(BackupMetadata(
                        credentialCount = -1,
                        platformCount = -1,
                        exportedAt = null,
                        version = 1,
                        format = "VLT1"
                    ))
                }
                
                val json = JSONObject(metadataJson!!)
                val stats = json.optJSONObject("stats")
                
                val exportedAtStr = json.optString("exportedAt", "")
                val exportedAt = exportedAtStr.takeIf { it.isNotEmpty() }?.let {
                    try { Instant.parse(it) } catch (e: Exception) { null }
                }
                
                val metadata = BackupMetadata(
                    credentialCount = stats?.optInt("credentialCount", -1) ?: -1,
                    platformCount = stats?.optInt("platformCount", -1) ?: -1,
                    exportedAt = exportedAt,
                    version = json.optInt("version", 1),
                    format = json.optString("format", "VLT1")
                )
                
                Log.d(TAG, "Parsed metadata: $metadata")
                Result.success(metadata)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse backup metadata", e)
                Result.failure(e)
            }
        }
    }
}
