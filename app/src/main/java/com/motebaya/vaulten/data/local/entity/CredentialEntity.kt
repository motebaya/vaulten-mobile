package com.motebaya.vaulten.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for storing credentials.
 * 
 * SECURITY: Encrypted fields store nonce + ciphertext encrypted with DEK.
 * PLAINTEXT fields (for search/filter): email, username, birthdate, credentialType, twoFaEnabled, accountName
 * ENCRYPTED fields: password, notes, backupEmail, phoneNumber, recoveryCodes, privateKey, seedPhrase
 * 
 * @property id Unique identifier
 * @property platformId Foreign key to the platform
 * @property username Username (stored in plaintext - for search)
 * @property email Email address (stored in plaintext - for search)
 * @property encryptedPassword Password encrypted with DEK (nonce + ciphertext)
 * @property encryptedNotes Notes encrypted with DEK (nonce + ciphertext)
 * @property encryptedBackupEmail Backup/recovery email encrypted with DEK
 * @property encryptedPhoneNumber Phone number encrypted with DEK
 * @property encryptedRecoveryCodes Recovery codes encrypted with DEK
 * @property birthdate Birthdate as ISO-8601 string (plaintext metadata)
 * @property twoFaEnabled Whether 2FA is enabled (plaintext flag)
 * @property credentialType Type of credential: standard, social, wallet, google
 * @property accountName Display name for wallet/google accounts (plaintext)
 * @property encryptedPrivateKey Crypto wallet private key encrypted with DEK
 * @property encryptedSeedPhrase Crypto wallet seed phrase encrypted with DEK
 * @property createdAt Creation timestamp
 * @property updatedAt Last modification timestamp
 * @property lastEditedAt Last user edit timestamp for 24h cooldown
 */
@Entity(
    tableName = "credentials",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["platformId"]),
        Index(value = ["email"]),
        Index(value = ["username"]),
        Index(value = ["credentialType"])
    ]
)
data class CredentialEntity(
    @PrimaryKey
    val id: String,
    val platformId: String,
    val username: String,
    val encryptedPassword: ByteArray,  // Encrypted with DEK
    val encryptedNotes: ByteArray,      // Encrypted with DEK
    val createdAt: Instant,
    val updatedAt: Instant,
    // Extended fields for Google/advanced credentials
    val email: String? = null,                      // Plaintext (for search)
    val credentialType: String = "standard",        // standard, social, wallet, google
    val encryptedBackupEmail: ByteArray? = null,    // Encrypted
    val encryptedPhoneNumber: ByteArray? = null,    // Encrypted
    val birthdate: String? = null,                  // ISO-8601 string (plaintext metadata)
    val twoFaEnabled: Boolean = false,              // Plaintext flag
    val encryptedRecoveryCodes: ByteArray? = null,  // Encrypted
    // Wallet/Google specific fields
    val accountName: String? = null,                // Plaintext display name
    val encryptedPrivateKey: ByteArray? = null,     // Encrypted wallet private key
    val encryptedSeedPhrase: ByteArray? = null,     // Encrypted wallet seed phrase
    // Edit restriction tracking (24h cooldown between edits)
    val lastEditedAt: Long? = null                  // Epoch millis when credential was last edited
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
