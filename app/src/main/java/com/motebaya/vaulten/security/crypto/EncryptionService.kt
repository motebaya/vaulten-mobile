package com.motebaya.vaulten.security.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides AES-256-GCM encryption/decryption.
 * 
 * SECURITY PARAMETERS:
 * - Algorithm: AES-256-GCM
 * - Key size: 256 bits (32 bytes)
 * - Nonce size: 96 bits (12 bytes)
 * - Auth tag size: 128 bits (16 bytes)
 * 
 * These parameters match the desktop app for vault.enc compatibility.
 */
@Singleton
class EncryptionService @Inject constructor() {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
    }

    private val secureRandom = SecureRandom()

    /**
     * Encrypt data using AES-256-GCM.
     * 
     * @param plaintext Data to encrypt
     * @param key 256-bit encryption key (the DEK)
     * @return Encrypted data (nonce + ciphertext + tag) or null on failure
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray? {
        require(key.size == KEY_SIZE_BYTES) { "Key must be 32 bytes" }
        
        return try {
            val nonce = ByteArray(NONCE_SIZE_BYTES)
            secureRandom.nextBytes(nonce)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(key, "AES")
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            
            val ciphertext = cipher.doFinal(plaintext)
            
            // Return nonce + ciphertext (tag is appended to ciphertext by GCM)
            nonce + ciphertext
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypt data with a provided nonce (for deterministic testing or container format).
     * 
     * WARNING: Only use with unique nonces! Nonce reuse breaks GCM security.
     */
    fun encryptWithNonce(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? {
        require(key.size == KEY_SIZE_BYTES) { "Key must be 32 bytes" }
        require(nonce.size == NONCE_SIZE_BYTES) { "Nonce must be 12 bytes" }
        
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(key, "AES")
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(plaintext)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypt data using AES-256-GCM.
     * 
     * @param ciphertext Encrypted data (nonce + ciphertext + tag)
     * @param key 256-bit encryption key (the DEK)
     * @return Decrypted data or null on failure (wrong key, tampered data)
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray? {
        require(key.size == KEY_SIZE_BYTES) { "Key must be 32 bytes" }
        
        if (ciphertext.size < NONCE_SIZE_BYTES + TAG_SIZE_BITS / 8) {
            return null // Too short to contain nonce + tag
        }
        
        return try {
            val nonce = ciphertext.copyOfRange(0, NONCE_SIZE_BYTES)
            val encrypted = ciphertext.copyOfRange(NONCE_SIZE_BYTES, ciphertext.size)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(key, "AES")
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypt with separate nonce (for container format).
     */
    fun decryptWithNonce(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? {
        require(key.size == KEY_SIZE_BYTES) { "Key must be 32 bytes" }
        require(nonce.size == NONCE_SIZE_BYTES) { "Nonce must be 12 bytes" }
        
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(key, "AES")
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate a random 256-bit key.
     */
    fun generateKey(): ByteArray {
        val key = ByteArray(KEY_SIZE_BYTES)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Generate a random nonce.
     */
    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Securely clear a byte array.
     */
    fun clearBytes(bytes: ByteArray) {
        bytes.fill(0)
    }
}
