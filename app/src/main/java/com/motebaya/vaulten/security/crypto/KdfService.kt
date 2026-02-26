package com.motebaya.vaulten.security.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom

/**
 * Key Derivation Function service.
 * 
 * Provides PBKDF2-HMAC-SHA256 key derivation compatible with the desktop app.
 * 
 * PARAMETERS (must match desktop for vault.enc compatibility):
 * - Algorithm: PBKDF2-HMAC-SHA256
 * - Iterations: 310,000
 * - Key length: 256 bits (32 bytes)
 * - Salt length: 256 bits (32 bytes)
 */
@Singleton
class KdfService @Inject constructor() {

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 310_000
        const val KEY_LENGTH_BITS = 256
        const val KEY_LENGTH_BYTES = 32
        const val SALT_LENGTH_BYTES = 32
    }

    private val secureRandom = SecureRandom()

    /**
     * Derive a key from a passphrase using PBKDF2.
     * 
     * @param passphrase The user's passphrase
     * @param salt Random salt (must be stored for later derivation)
     * @param iterations Number of PBKDF2 iterations (default 310,000)
     * @return Derived 256-bit key
     */
    fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray {
        require(salt.size == SALT_LENGTH_BYTES) { "Salt must be 32 bytes" }
        
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            iterations,
            KEY_LENGTH_BITS
        )
        
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val key = factory.generateSecret(spec)
            key.encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Derive a key with default iterations.
     * This is the KEK derived from the master passphrase.
     */
    fun deriveKek(passphrase: String, salt: ByteArray): ByteArray {
        return deriveKey(passphrase, salt, DEFAULT_ITERATIONS)
    }

    /**
     * Generate a random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Verify a passphrase by comparing derived keys.
     * 
     * @param passphrase The passphrase to verify
     * @param salt The salt used during original derivation
     * @param expectedKey The expected derived key
     * @return true if the passphrase is correct
     */
    fun verifyPassphrase(
        passphrase: String,
        salt: ByteArray,
        expectedKey: ByteArray
    ): Boolean {
        val derivedKey = deriveKey(passphrase, salt)
        return constantTimeEquals(derivedKey, expectedKey)
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
