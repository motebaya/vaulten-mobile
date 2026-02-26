package com.motebaya.vaulten.security

import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.motebaya.vaulten.security.keystore.KeystoreManager
import javax.crypto.Cipher

private const val TAG = "BiometricAuthHelper"

/**
 * Helper class for biometric authentication using Android's BiometricPrompt API.
 * 
 * This provides a clean interface for showing biometric prompts throughout the app:
 * - Unlock screen
 * - Setup flow (biometric enrollment verification)
 * - Verification dialogs (view credential, delete, etc.)
 * 
 * The biometric key in Android Keystore requires biometric authentication
 * for every use, ensuring the user has actually authenticated.
 */
class BiometricAuthHelper(
    private val activity: FragmentActivity,
    private val keystoreManager: KeystoreManager
) {
    
    /**
     * Result of biometric authentication attempt.
     */
    sealed class BiometricResult {
        object Success : BiometricResult()
        data class Error(val errorCode: Int, val errorMessage: String) : BiometricResult()
        object Cancelled : BiometricResult()
    }
    
    /**
     * Check if biometric authentication is available on this device.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
    
    /**
     * Show biometric prompt for authentication.
     * 
     * This uses CryptoObject with the biometric key from Android Keystore,
     * ensuring the biometric key is only accessible after successful authentication.
     * 
     * @param title Title shown in the biometric prompt
     * @param subtitle Subtitle/description shown in the prompt
     * @param negativeButtonText Text for the cancel/negative button
     * @param onResult Callback with the authentication result
     */
    fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String = "Cancel",
        onResult: (BiometricResult) -> Unit
    ) {
        Log.d(TAG, "authenticate() called with title='$title'")
        
        val executor = ContextCompat.getMainExecutor(activity)
        Log.d(TAG, "Got executor: $executor")
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "onAuthenticationSucceeded")
                onResult(BiometricResult.Success)
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "onAuthenticationError: code=$errorCode, message=$errString")
                // User cancelled or pressed negative button
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED) {
                    onResult(BiometricResult.Cancelled)
                } else {
                    onResult(BiometricResult.Error(errorCode, errString.toString()))
                }
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "onAuthenticationFailed - biometric doesn't match, user can retry")
                // This is called when biometric doesn't match but user can retry
                // We don't call onResult here - the prompt stays open for retry
            }
        }
        
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        Log.d(TAG, "BiometricPrompt created")
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        Log.d(TAG, "PromptInfo built")
        
        // Try to use CryptoObject for additional security
        // This ensures the biometric key is used and validates the authentication
        val cipher = keystoreManager.getBiometricCipher()
        Log.d(TAG, "Got cipher: ${cipher != null}")
        
        if (cipher != null) {
            try {
                Log.d(TAG, "Attempting to authenticate with CryptoObject")
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                Log.d(TAG, "authenticate() with CryptoObject called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "CryptoObject auth failed, falling back: ${e.message}")
                // If crypto fails (key invalidated, etc.), fall back to non-crypto auth
                biometricPrompt.authenticate(promptInfo)
            }
        } else {
            // No biometric key available, authenticate without crypto
            Log.d(TAG, "No biometric key, authenticating without CryptoObject")
            biometricPrompt.authenticate(promptInfo)
        }
        
        Log.d(TAG, "authenticate() method completed - prompt should be showing")
    }
    
    /**
     * Authenticate with simple callbacks for common use cases.
     */
    fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String = "Cancel",
        onSuccess: () -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCancel: () -> Unit
    ) {
        authenticate(title, subtitle, negativeButtonText) { result ->
            when (result) {
                is BiometricResult.Success -> onSuccess()
                is BiometricResult.Error -> onError(result.errorMessage)
                is BiometricResult.Cancelled -> onCancel()
            }
        }
    }
}
