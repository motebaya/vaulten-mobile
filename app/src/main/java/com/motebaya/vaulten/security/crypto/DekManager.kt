package com.motebaya.vaulten.security.crypto

import com.motebaya.vaulten.security.keystore.KeystoreManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Data Encryption Key (DEK) lifecycle.
 * 
 * SECURITY MODEL:
 * - DEK is a random 256-bit key used to encrypt ALL vault data
 * - DEK is NEVER derived from PIN or passphrase
 * - DEK is wrapped (encrypted) when stored:
 *   - KEK-passphrase: For vault.enc export/import (cross-platform)
 *   - KEK-device: For daily usage (Android Keystore)
 * - DEK is held in memory ONLY when vault is unlocked
 * - DEK is cleared immediately when vault locks
 * 
 * The DEK is the single key that encrypts all credential data.
 * Multiple KEKs (key-encryption-keys) are used to protect the DEK itself.
 */
@Singleton
class DekManager @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val encryptionService: EncryptionService
) {
    companion object {
        private const val DEK_SIZE_BYTES = 32
    }

    private val secureRandom = SecureRandom()
    private val mutex = Mutex()
    
    // The DEK - only present when vault is unlocked
    @Volatile
    private var dek: ByteArray? = null

    /**
     * Generate a new random DEK.
     * Called during vault creation.
     * 
     * @return The newly generated DEK
     */
    fun generateDek(): ByteArray {
        val newDek = ByteArray(DEK_SIZE_BYTES)
        secureRandom.nextBytes(newDek)
        return newDek
    }

    /**
     * Set the DEK (called after successful unlock).
     * 
     * @param key The unwrapped DEK
     */
    suspend fun setDek(key: ByteArray) {
        require(key.size == DEK_SIZE_BYTES) { "DEK must be 32 bytes" }
        mutex.withLock {
            // Clear any existing DEK first
            dek?.let { encryptionService.clearBytes(it) }
            dek = key.copyOf()
        }
    }

    /**
     * Get the DEK for encryption/decryption operations.
     * 
     * @return The DEK, or null if vault is locked
     */
    suspend fun getDek(): ByteArray? {
        return mutex.withLock {
            dek?.copyOf()
        }
    }

    /**
     * Check if the DEK is currently available (vault is unlocked).
     */
    fun isUnlocked(): Boolean = dek != null

    /**
     * Clear the DEK from memory (called when locking vault).
     * 
     * CRITICAL: This must be called whenever the vault locks:
     * - App going to background
     * - Screen off
     * - Idle timeout
     * - User explicit lock
     */
    suspend fun clearDek() {
        mutex.withLock {
            dek?.let { key ->
                encryptionService.clearBytes(key)
            }
            dek = null
        }
    }

    /**
     * Wrap the DEK with the device KEK (for local storage).
     * 
     * @param dekToWrap The DEK to wrap
     * @return Wrapped DEK, or null on failure
     */
    fun wrapDekForDevice(dekToWrap: ByteArray): ByteArray? {
        return keystoreManager.wrapWithDeviceKek(dekToWrap)
    }

    /**
     * Unwrap the DEK using the device KEK.
     * 
     * @param wrappedDek The wrapped DEK from local storage
     * @return The unwrapped DEK, or null on failure
     */
    fun unwrapDekFromDevice(wrappedDek: ByteArray): ByteArray? {
        return keystoreManager.unwrapWithDeviceKek(wrappedDek)
    }

    /**
     * Wrap the DEK with a passphrase-derived KEK (for vault.enc export).
     * 
     * @param dekToWrap The DEK to wrap
     * @param kekPassphrase The KEK derived from the passphrase
     * @return Wrapped DEK (nonce + ciphertext), or null on failure
     */
    fun wrapDekForExport(dekToWrap: ByteArray, kekPassphrase: ByteArray): ByteArray? {
        return encryptionService.encrypt(dekToWrap, kekPassphrase)
    }

    /**
     * Unwrap the DEK using a passphrase-derived KEK (for vault.enc import).
     * 
     * @param wrappedDek The wrapped DEK from vault.enc
     * @param kekPassphrase The KEK derived from the passphrase
     * @return The unwrapped DEK, or null on failure (wrong passphrase)
     */
    fun unwrapDekFromExport(wrappedDek: ByteArray, kekPassphrase: ByteArray): ByteArray? {
        return encryptionService.decrypt(wrappedDek, kekPassphrase)
    }

    /**
     * Encrypt data using the current DEK.
     * 
     * @param plaintext Data to encrypt
     * @return Encrypted data, or null if vault is locked
     */
    suspend fun encrypt(plaintext: ByteArray): ByteArray? {
        val key = getDek() ?: return null
        return try {
            encryptionService.encrypt(plaintext, key)
        } finally {
            encryptionService.clearBytes(key)
        }
    }

    /**
     * Decrypt data using the current DEK.
     * 
     * @param ciphertext Data to decrypt
     * @return Decrypted data, or null if vault is locked or decryption fails
     */
    suspend fun decrypt(ciphertext: ByteArray): ByteArray? {
        val key = getDek() ?: return null
        return try {
            encryptionService.decrypt(ciphertext, key)
        } finally {
            encryptionService.clearBytes(key)
        }
    }
}
