package com.motebaya.vaulten.domain.entity

import java.time.Instant

/**
 * Domain entity containing vault metadata.
 * 
 * This information is stored in the vault and describes the vault itself.
 * Used for display purposes and compatibility checking.
 * 
 * @property version Vault format version (for migration compatibility)
 * @property createdAt When the vault was first created
 * @property lastModifiedAt When any credential was last modified
 * @property credentialCount Number of credentials stored
 * @property kdfType KDF algorithm used (1 = PBKDF2, 2 = Argon2id)
 * @property kdfIterations Number of KDF iterations (for PBKDF2)
 */
data class VaultInfo(
    val version: Int = CURRENT_VERSION,
    val createdAt: Instant = Instant.now(),
    val lastModifiedAt: Instant = Instant.now(),
    val credentialCount: Int = 0,
    val kdfType: Int = KDF_TYPE_PBKDF2,
    val kdfIterations: Int = DEFAULT_PBKDF2_ITERATIONS
) {
    companion object {
        const val CURRENT_VERSION = 1
        
        // KDF Types (must match desktop format)
        const val KDF_TYPE_PBKDF2 = 1
        const val KDF_TYPE_ARGON2ID = 2
        
        // PBKDF2 parameters (must match desktop for compatibility)
        const val DEFAULT_PBKDF2_ITERATIONS = 310_000
        const val KEY_LENGTH_BYTES = 32  // 256 bits
    }

    /**
     * Check if this vault format is compatible with the current app version.
     */
    fun isCompatible(): Boolean {
        return version <= CURRENT_VERSION
    }
}
