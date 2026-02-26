package com.motebaya.vaulten.domain.entity

/**
 * Represents the result of a vault operation.
 * 
 * Used throughout the domain layer to handle success/failure cases
 * without throwing exceptions for expected error conditions.
 */
sealed class VaultResult<out T> {
    /**
     * Operation succeeded with a value.
     */
    data class Success<T>(val data: T) : VaultResult<T>()
    
    /**
     * Operation failed with an error.
     */
    data class Error(val error: VaultError) : VaultResult<Nothing>()
    
    /**
     * Check if this result is a success.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Check if this result is an error.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Get the data if successful, or null if error.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    /**
     * Get the error if failed, or null if success.
     */
    fun errorOrNull(): VaultError? = when (this) {
        is Success -> null
        is Error -> error
    }
    
    /**
     * Transform the success value.
     */
    inline fun <R> map(transform: (T) -> R): VaultResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    /**
     * Transform the success value with a function that returns a Result.
     */
    inline fun <R> flatMap(transform: (T) -> VaultResult<R>): VaultResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }

    companion object {
        fun <T> success(data: T): VaultResult<T> = Success(data)
        fun error(error: VaultError): VaultResult<Nothing> = Error(error)
    }
}

/**
 * Possible vault operation errors.
 */
sealed class VaultError(open val message: String) {
    // Authentication errors
    data object WrongPin : VaultError("Incorrect PIN")
    data object WrongPassphrase : VaultError("Incorrect passphrase")
    data object TooManyAttempts : VaultError("Too many failed attempts")
    data object BiometricFailed : VaultError("Biometric authentication failed")
    data object BiometricNotAvailable : VaultError("Biometric not available")
    data object BiometricNotEnrolled : VaultError("No biometrics enrolled")
    data object SessionExpired : VaultError("Session expired, please unlock again")
    
    // Crypto errors
    data object DecryptionFailed : VaultError("Failed to decrypt data")
    data object EncryptionFailed : VaultError("Failed to encrypt data")
    data object KeyGenerationFailed : VaultError("Failed to generate encryption key")
    data object KeystoreUnavailable : VaultError("Hardware keystore unavailable")
    data object KeyNotFound : VaultError("Encryption key not found")
    
    // Container errors
    data object InvalidContainerFormat : VaultError("Invalid vault file format")
    data object UnsupportedVersion : VaultError("Unsupported vault version")
    data object CorruptedData : VaultError("Vault data is corrupted")
    
    // Storage errors
    data object DatabaseError : VaultError("Database operation failed")
    data object FileNotFound : VaultError("File not found")
    data object FileAccessDenied : VaultError("Cannot access file")
    data object InsufficientStorage : VaultError("Insufficient storage space")
    data object NotFound : VaultError("Requested item not found")
    
    // Vault state errors
    data object VaultLocked : VaultError("Vault is locked")
    data object VaultNotSetup : VaultError("Vault not set up")
    data object VaultAlreadyExists : VaultError("Vault already exists")
    
    // Validation errors
    data class ValidationError(override val message: String) : VaultError(message)
    
    // Generic error with custom message
    data class Unknown(override val message: String) : VaultError(message)
}
