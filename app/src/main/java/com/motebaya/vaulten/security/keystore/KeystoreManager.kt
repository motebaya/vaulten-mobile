package com.motebaya.vaulten.security.keystore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages Android Keystore operations for hardware-backed key storage.
 *
 * Handles two distinct keys:
 * 1. Device KEK (Key Encryption Key) - wraps/unwraps the DEK for local storage
 * 2. Biometric Key - requires biometric authentication, used for CryptoObject
 */
class KeystoreManager(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_KEK_ALIAS = "vaulten_device_kek"
        private const val BIOMETRIC_KEY_ALIAS = "vaulten_biometric_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // ========================================================================
    // DEVICE KEK OPERATIONS
    // ========================================================================

    /**
     * Check if the device KEK exists in Android Keystore.
     */
    fun hasDeviceKek(): Boolean {
        return try {
            keyStore.containsAlias(DEVICE_KEK_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device KEK existence", e)
            false
        }
    }

    /**
     * Generate a new device KEK in Android Keystore.
     *
     * @param requireAuthentication whether key usage requires user auth
     * @return true if generation succeeded
     */
    fun generateDeviceKek(requireAuthentication: Boolean): Boolean {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                DEVICE_KEK_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(requireAuthentication)

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
            Log.d(TAG, "Device KEK generated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device KEK", e)
            false
        }
    }

    /**
     * Wrap (encrypt) data using the device KEK.
     *
     * @param data plaintext bytes to wrap
     * @return wrapped bytes (IV + ciphertext), or null on failure
     */
    fun wrapWithDeviceKek(data: ByteArray): ByteArray? {
        return try {
            val secretKey = getDeviceKek() ?: return null
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)

            // Prepend IV to ciphertext: [IV (12 bytes)] + [ciphertext + GCM tag]
            ByteArray(iv.size + encrypted.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(encrypted, 0, this, iv.size, encrypted.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wrap data with device KEK", e)
            null
        }
    }

    /**
     * Unwrap (decrypt) data using the device KEK.
     *
     * @param wrappedData previously wrapped bytes (IV + ciphertext)
     * @return original plaintext bytes, or null on failure
     */
    fun unwrapWithDeviceKek(wrappedData: ByteArray): ByteArray? {
        return try {
            if (wrappedData.size < GCM_IV_LENGTH) return null

            val secretKey = getDeviceKek() ?: return null
            val iv = wrappedData.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = wrappedData.copyOfRange(GCM_IV_LENGTH, wrappedData.size)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap data with device KEK", e)
            null
        }
    }

    // ========================================================================
    // BIOMETRIC KEY OPERATIONS
    // ========================================================================

    /**
     * Check if a biometric authentication key exists.
     */
    fun hasBiometricKey(): Boolean {
        return try {
            keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric key existence", e)
            false
        }
    }

    /**
     * Generate a biometric-bound key in Keystore.
     * This key requires biometric auth for every use.
     *
     * @return true if generation succeeded
     */
    fun generateBiometricKey(): Boolean {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0, // require auth every time
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            }

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
            Log.d(TAG, "Biometric key generated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate biometric key", e)
            false
        }
    }

    /**
     * Delete the biometric key from Keystore.
     */
    fun deleteBiometricKey() {
        try {
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                Log.d(TAG, "Biometric key deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete biometric key", e)
        }
    }

    /**
     * Get a Cipher initialized with the biometric key for use with
     * BiometricPrompt.CryptoObject.
     *
     * @return initialized Cipher, or null if key unavailable/invalidated
     */
    fun getBiometricCipher(): Cipher? {
        return try {
            val secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
                ?: return null
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get biometric cipher", e)
            null
        }
    }

    // ========================================================================
    // LIFECYCLE / CLEANUP
    // ========================================================================

    /**
     * Delete ALL keys from Android Keystore (device KEK + biometric key).
     * Used during complete vault deletion/wipe.
     */
    fun deleteAllKeys() {
        try {
            if (keyStore.containsAlias(DEVICE_KEK_ALIAS)) {
                keyStore.deleteEntry(DEVICE_KEK_ALIAS)
                Log.d(TAG, "Device KEK deleted")
            }
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                Log.d(TAG, "Biometric key deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all keys", e)
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private fun getDeviceKek(): SecretKey? {
        return try {
            keyStore.getKey(DEVICE_KEK_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve device KEK", e)
            null
        }
    }
}
