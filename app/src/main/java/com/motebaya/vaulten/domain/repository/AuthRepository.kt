package com.motebaya.vaulten.domain.repository

import com.motebaya.vaulten.domain.entity.VaultResult

/**
 * Repository interface for PIN and biometric authentication.
 * 
 * SECURITY MODEL:
 * - PIN is hashed with bcrypt before storage
 * - PIN/biometric are ACCESS GATES, not encryption keys
 * - Successful authentication triggers DEK unwrapping via device keystore
 * - Failed attempts are rate-limited
 */
interface AuthRepository {
    
    /**
     * Check if a PIN has been set up.
     * 
     * @return true if PIN is configured
     */
    suspend fun isPinSetup(): Boolean
    
    /**
     * Check if biometric authentication is enabled.
     * 
     * @return true if biometric is enabled and available
     */
    suspend fun isBiometricEnabled(): Boolean
    
    /**
     * Check if biometric hardware is available.
     * 
     * @return true if device has biometric capability
     */
    suspend fun isBiometricAvailable(): Boolean
    
    /**
     * Set up the PIN for the first time or change it.
     * 
     * @param pin 6-digit PIN
     * @return Success or validation error
     */
    suspend fun setupPin(pin: String): VaultResult<Unit>
    
    /**
     * Verify the PIN and unlock the vault.
     * 
     * On success, triggers DEK unwrapping.
     * On failure, increments attempt counter.
     * 
     * @param pin The PIN to verify
     * @return Success (with session started) or error
     */
    suspend fun verifyPin(pin: String): VaultResult<Unit>
    
    /**
     * Enable biometric authentication.
     * 
     * Must be called after vault is unlocked.
     * Registers a key in Android Keystore that requires biometric auth.
     * 
     * @return Success or error (if biometric unavailable)
     */
    suspend fun enableBiometric(): VaultResult<Unit>
    
    /**
     * Disable biometric authentication.
     * 
     * @return Success
     */
    suspend fun disableBiometric(): VaultResult<Unit>
    
    /**
     * Unlock the vault using biometric authentication.
     * 
     * This should be called AFTER successful BiometricPrompt authentication.
     * It performs the same DEK unwrapping as verifyPin() but without PIN verification
     * since the user has already authenticated via biometric.
     * 
     * @return Success (with DEK loaded) or error
     */
    suspend fun unlockWithBiometric(): VaultResult<Unit>
    
    /**
     * Get the number of failed PIN attempts.
     * 
     * @return Number of consecutive failed attempts
     */
    suspend fun getFailedAttempts(): Int
    
    /**
     * Get the remaining lockout time in milliseconds.
     * 
     * @return Milliseconds until next attempt allowed, or 0 if not locked out
     */
    suspend fun getLockoutTimeRemaining(): Long
    
    /**
     * Reset failed attempt counter.
     * Called after successful authentication.
     */
    suspend fun resetFailedAttempts()
    
    /**
     * Check if currently in lockout period due to too many failed attempts.
     * 
     * @return true if locked out
     */
    suspend fun isLockedOut(): Boolean
}
