package com.motebaya.vaulten.security.container

import com.motebaya.vaulten.security.crypto.EncryptionService
import com.motebaya.vaulten.security.crypto.KdfService
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles reading and writing the vault.enc container format.
 * 
 * CONTAINER FORMAT (must match desktop app):
 * +------------------+--------+
 * | Field            | Size   |
 * +------------------+--------+
 * | Magic "VLT1"     | 4      |
 * | Version          | 1      |
 * | KDF Type         | 1      |
 * | Salt             | 32     |
 * | Nonce            | 12     |
 * | Ciphertext Len   | 4      |
 * | Ciphertext       | varies |
 * | Auth Tag         | 16     |
 * +------------------+--------+
 * 
 * KDF Types:
 * - 0x01: PBKDF2-HMAC-SHA256 (310,000 iterations)
 * - 0x02: Argon2id (future)
 */
@Singleton
class ContainerService @Inject constructor(
    private val kdfService: KdfService,
    private val encryptionService: EncryptionService
) {
    companion object {
        private val MAGIC = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
        private const val VERSION: Byte = 0x01
        private const val KDF_TYPE_PBKDF2: Byte = 0x01
        private const val KDF_TYPE_ARGON2ID: Byte = 0x02
        
        private const val HEADER_SIZE = 4 + 1 + 1 + 32 + 12 + 4 // 54 bytes
        private const val SALT_SIZE = 32
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
    }

    /**
     * Write vault data to the container format.
     * 
     * @param data The plaintext data to encrypt (typically serialized DB)
     * @param passphrase User's master passphrase
     * @param output Stream to write the container
     * @return true on success
     */
    fun writeContainer(
        data: ByteArray,
        passphrase: String,
        output: OutputStream
    ): Boolean {
        return try {
            // Generate salt and nonce
            val salt = kdfService.generateSalt()
            val nonce = encryptionService.generateNonce()
            
            // Derive KEK from passphrase
            val kek = kdfService.deriveKek(passphrase, salt)
            
            // Encrypt data
            val ciphertext = encryptionService.encryptWithNonce(data, kek, nonce)
                ?: return false
            
            // Write header
            output.write(MAGIC)
            output.write(VERSION.toInt())
            output.write(KDF_TYPE_PBKDF2.toInt())
            output.write(salt)
            output.write(nonce)
            
            // Write ciphertext length (big-endian)
            val lenBuffer = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(ciphertext.size)
                .array()
            output.write(lenBuffer)
            
            // Write ciphertext (includes auth tag in GCM mode)
            output.write(ciphertext)
            
            output.flush()
            
            // Clear sensitive data
            encryptionService.clearBytes(kek)
            
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read and decrypt vault data from the container format.
     * 
     * @param input Stream containing the container
     * @param passphrase User's master passphrase
     * @return Decrypted data, or null on failure (wrong passphrase, corrupted, etc.)
     */
    fun readContainer(
        input: InputStream,
        passphrase: String
    ): ByteArray? {
        return try {
            // Read and verify magic
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || !magic.contentEquals(MAGIC)) {
                return null // Invalid format
            }
            
            // Read version
            val version = input.read()
            if (version != VERSION.toInt()) {
                return null // Unsupported version
            }
            
            // Read KDF type
            val kdfType = input.read()
            if (kdfType != KDF_TYPE_PBKDF2.toInt()) {
                return null // Unsupported KDF (Argon2id not yet implemented)
            }
            
            // Read salt
            val salt = ByteArray(SALT_SIZE)
            if (input.read(salt) != SALT_SIZE) {
                return null
            }
            
            // Read nonce
            val nonce = ByteArray(NONCE_SIZE)
            if (input.read(nonce) != NONCE_SIZE) {
                return null
            }
            
            // Read ciphertext length
            val lenBuffer = ByteArray(4)
            if (input.read(lenBuffer) != 4) {
                return null
            }
            val ciphertextLen = ByteBuffer.wrap(lenBuffer)
                .order(ByteOrder.BIG_ENDIAN)
                .int
            
            if (ciphertextLen <= 0 || ciphertextLen > 100_000_000) { // Max 100MB
                return null
            }
            
            // Read ciphertext
            val ciphertext = ByteArray(ciphertextLen)
            var totalRead = 0
            while (totalRead < ciphertextLen) {
                val read = input.read(ciphertext, totalRead, ciphertextLen - totalRead)
                if (read == -1) return null
                totalRead += read
            }
            
            // Derive KEK from passphrase
            val kek = kdfService.deriveKek(passphrase, salt)
            
            // Decrypt
            val plaintext = encryptionService.decryptWithNonce(ciphertext, kek, nonce)
            
            // Clear sensitive data
            encryptionService.clearBytes(kek)
            
            plaintext
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse container header without decrypting.
     * Used to check format and get KDF parameters.
     */
    fun parseHeader(input: InputStream): ContainerHeader? {
        return try {
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || !magic.contentEquals(MAGIC)) {
                return null
            }
            
            val version = input.read()
            val kdfType = input.read()
            
            val salt = ByteArray(SALT_SIZE)
            if (input.read(salt) != SALT_SIZE) {
                return null
            }
            
            ContainerHeader(
                version = version,
                kdfType = kdfType,
                salt = salt
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Parsed container header.
 */
data class ContainerHeader(
    val version: Int,
    val kdfType: Int,
    val salt: ByteArray
) {
    val kdfName: String get() = when (kdfType) {
        0x01 -> "PBKDF2-HMAC-SHA256"
        0x02 -> "Argon2id"
        else -> "Unknown"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContainerHeader) return false
        return version == other.version && kdfType == other.kdfType && salt.contentEquals(other.salt)
    }
    
    override fun hashCode(): Int {
        var result = version
        result = 31 * result + kdfType
        result = 31 * result + salt.contentHashCode()
        return result
    }
}
