package com.motebaya.vaulten.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a stored credential.
 * 
 * This is the decrypted domain model used within the app.
 * When persisted, sensitive fields are encrypted with DEK.
 * 
 * PLAINTEXT fields (for search/filter): email, username, birthdate, credentialType, twoFaEnabled, accountName
 * ENCRYPTED fields (at rest): password, notes, backupEmail, phoneNumber, recoveryCodes, privateKey, seedPhrase
 * 
 * @property id Unique identifier
 * @property platformId Reference to the platform this credential belongs to
 * @property username The username for this credential
 * @property email The email address (plaintext for search)
 * @property password The password (plaintext in memory, encrypted at rest)
 * @property notes Optional notes (plaintext in memory, encrypted at rest)
 * @property backupEmail Recovery/backup email (plaintext in memory, encrypted at rest)
 * @property phoneNumber Phone number (plaintext in memory, encrypted at rest)
 * @property recoveryCodes Recovery codes (plaintext in memory, encrypted at rest)
 * @property birthdate Birthdate as ISO-8601 string (plaintext metadata)
 * @property twoFaEnabled Whether 2FA is enabled
 * @property credentialType Type of credential: standard, social, wallet, google
 * @property accountName Display name for wallet/google accounts (plaintext)
 * @property privateKey Crypto wallet private key (plaintext in memory, encrypted at rest)
 * @property seedPhrase Crypto wallet seed/recovery phrase (plaintext in memory, encrypted at rest)
 * @property createdAt Timestamp when credential was created
 * @property updatedAt Timestamp when credential was last modified
 * @property lastEditedAt Timestamp of last user edit (for 24h cooldown)
 */
data class Credential(
    val id: String = UUID.randomUUID().toString(),
    val platformId: String,
    val username: String,
    val password: String,
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    // Extended fields for Google/advanced credentials
    val email: String? = null,
    val credentialType: CredentialType = CredentialType.STANDARD,
    val backupEmail: String? = null,
    val phoneNumber: String? = null,
    val birthdate: String? = null,
    val twoFaEnabled: Boolean = false,
    val recoveryCodes: String? = null,
    // Wallet/Google specific fields
    val accountName: String? = null,
    val privateKey: String? = null,
    val seedPhrase: String? = null,
    // Edit restriction tracking (24h cooldown between edits)
    val lastEditedAt: Instant? = null
) {
    /**
     * Returns a copy with updated timestamp.
     */
    fun withUpdatedTimestamp(): Credential {
        return copy(updatedAt = Instant.now())
    }

    /**
     * Returns a masked version for logging (never log actual password).
     */
    fun toSafeString(): String {
        return "Credential(id=$id, platformId=$platformId, username=$username, email=$email, password=*****, notes=${if (notes.isNotEmpty()) "[REDACTED]" else "[]"}, type=${credentialType.name})"
    }
    
    /**
     * Get the display name (email or username).
     */
    val displayIdentifier: String
        get() = email?.takeIf { it.isNotBlank() } ?: username
}
