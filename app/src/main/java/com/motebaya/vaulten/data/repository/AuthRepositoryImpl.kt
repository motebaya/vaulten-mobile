package com.motebaya.vaulten.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.motebaya.vaulten.domain.entity.VaultError
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.security.crypto.DekManager
import com.motebaya.vaulten.security.keystore.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mindrot.jbcrypt.BCrypt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AuthRepository.
 * 
 * SECURITY:
 * - PIN is hashed with bcrypt (12 rounds) before storage
 * - PIN hash is stored in EncryptedSharedPreferences
 * - Failed attempts are tracked with exponential backoff
 * - Biometric key requires biometric auth via Android Keystore
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val dekManager: DekManager
) : AuthRepository {

    companion object {
        private const val PREFS_NAME = "vault_auth"
        private const val VAULT_META_PREFS_NAME = "vault_meta"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_WRAPPED_DEK_DEVICE = "wrapped_dek_device"
        
        private const val BCRYPT_ROUNDS = 12
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
        private const val LOCKOUT_MULTIPLIER = 2 // Exponential backoff
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

    /**
     * SharedPreferences for vault metadata (where wrapped DEK is stored).
     * This must match the prefs used in VaultRepositoryImpl.
     */
    private val vaultMetaPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            VAULT_META_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun isPinSetup(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    override suspend fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) &&
                keystoreManager.hasBiometricKey()
    }

    override suspend fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun setupPin(pin: String): VaultResult<Unit> {
        // Validate PIN format
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            return VaultResult.Error(VaultError.ValidationError("PIN must be 6 digits"))
        }
        
        // Hash PIN with bcrypt
        val hash = BCrypt.hashpw(pin, BCrypt.gensalt(BCRYPT_ROUNDS))
        
        // Store hash
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .apply()
        
        // Generate device KEK if not present
        if (!keystoreManager.hasDeviceKek()) {
            keystoreManager.generateDeviceKek(requireAuthentication = false)
        }
        
        return VaultResult.Success(Unit)
    }

    override suspend fun verifyPin(pin: String): VaultResult<Unit> {
        // Check lockout
        if (isLockedOut()) {
            return VaultResult.Error(VaultError.TooManyAttempts)
        }
        
        val storedHash = prefs.getString(KEY_PIN_HASH, null)
            ?: return VaultResult.Error(VaultError.VaultNotSetup)
        
        // Verify PIN
        val isValid = BCrypt.checkpw(pin, storedHash)
        
        if (!isValid) {
            incrementFailedAttempts()
            return if (isLockedOut()) {
                VaultResult.Error(VaultError.TooManyAttempts)
            } else {
                VaultResult.Error(VaultError.WrongPin)
            }
        }
        
        // PIN correct - reset attempts and unlock
        resetFailedAttempts()
        
        // BUG FIX #14: Read wrapped DEK from vault_meta prefs (where VaultRepositoryImpl stores it)
        // Previously was reading from wrong SharedPreferences, causing credentials to disappear
        val wrappedDek = vaultMetaPrefs.getString(KEY_WRAPPED_DEK_DEVICE, null)?.let { 
            android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }
        
        if (wrappedDek != null) {
            val dek = dekManager.unwrapDekFromDevice(wrappedDek)
                ?: return VaultResult.Error(VaultError.DecryptionFailed)
            dekManager.setDek(dek)
        } else {
            // No wrapped DEK found - this is a critical error
            return VaultResult.Error(VaultError.DecryptionFailed)
        }
        
        return VaultResult.Success(Unit)
    }

    override suspend fun enableBiometric(): VaultResult<Unit> {
        if (!isBiometricAvailable()) {
            return VaultResult.Error(VaultError.BiometricNotAvailable)
        }
        
        // Generate biometric key
        if (!keystoreManager.generateBiometricKey()) {
            return VaultResult.Error(VaultError.KeyGenerationFailed)
        }
        
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .apply()
        
        return VaultResult.Success(Unit)
    }

    override suspend fun disableBiometric(): VaultResult<Unit> {
        keystoreManager.deleteBiometricKey()
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
        
        return VaultResult.Success(Unit)
    }
    
    override suspend fun unlockWithBiometric(): VaultResult<Unit> {
        // This is called AFTER successful BiometricPrompt authentication.
        // The biometric verification already happened via Android's BiometricPrompt,
        // so we just need to unwrap the DEK (same as the PIN success path).
        
        // Reset any failed attempts since authentication was successful
        resetFailedAttempts()
        
        // Read wrapped DEK from vault_meta prefs (where VaultRepositoryImpl stores it)
        val wrappedDek = vaultMetaPrefs.getString(KEY_WRAPPED_DEK_DEVICE, null)?.let { 
            android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }
        
        if (wrappedDek != null) {
            val dek = dekManager.unwrapDekFromDevice(wrappedDek)
                ?: return VaultResult.Error(VaultError.DecryptionFailed)
            dekManager.setDek(dek)
        } else {
            // No wrapped DEK found - vault may not be set up properly
            return VaultResult.Error(VaultError.DecryptionFailed)
        }
        
        return VaultResult.Success(Unit)
    }

    override suspend fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    override suspend fun getLockoutTimeRemaining(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        val remaining = lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    override suspend fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .apply()
    }

    override suspend fun isLockedOut(): Boolean {
        return getLockoutTimeRemaining() > 0
    }

    private fun incrementFailedAttempts() {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            // Calculate lockout duration with exponential backoff
            val lockoutMultiplier = (attempts - MAX_FAILED_ATTEMPTS + 1)
            val lockoutDuration = LOCKOUT_DURATION_MS * lockoutMultiplier
            val lockoutUntil = System.currentTimeMillis() + lockoutDuration
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
        }
        
        editor.apply()
    }
}
