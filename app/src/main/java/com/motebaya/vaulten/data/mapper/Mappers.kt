package com.motebaya.vaulten.data.mapper

import com.motebaya.vaulten.data.local.dao.PlatformWithStats
import com.motebaya.vaulten.data.local.entity.CredentialEntity
import com.motebaya.vaulten.data.local.entity.PlatformEntity
import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.CredentialType
import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.security.crypto.DekManager
import java.time.Instant

/**
 * Maps between domain entities and database entities.
 * 
 * SECURITY: Handles encryption/decryption of sensitive fields.
 * - To DB: Encrypts password, notes, backupEmail, phoneNumber, recoveryCodes, privateKey, seedPhrase with DEK
 * - From DB: Decrypts the above fields with DEK
 */
class CredentialMapper(
    private val dekManager: DekManager
) {
    /**
     * Map domain credential to database entity.
     * Encrypts sensitive fields.
     */
    suspend fun toEntity(credential: Credential): CredentialEntity? {
        val encryptedPassword = dekManager.encrypt(credential.password.toByteArray(Charsets.UTF_8))
            ?: return null
        val encryptedNotes = dekManager.encrypt(credential.notes.toByteArray(Charsets.UTF_8))
            ?: return null
        
        // Encrypt optional sensitive fields
        val encryptedBackupEmail = credential.backupEmail?.let { 
            dekManager.encrypt(it.toByteArray(Charsets.UTF_8))
        }
        val encryptedPhoneNumber = credential.phoneNumber?.let {
            dekManager.encrypt(it.toByteArray(Charsets.UTF_8))
        }
        val encryptedRecoveryCodes = credential.recoveryCodes?.let {
            dekManager.encrypt(it.toByteArray(Charsets.UTF_8))
        }
        // Encrypt wallet-specific fields
        val encryptedPrivateKey = credential.privateKey?.let {
            dekManager.encrypt(it.toByteArray(Charsets.UTF_8))
        }
        val encryptedSeedPhrase = credential.seedPhrase?.let {
            dekManager.encrypt(it.toByteArray(Charsets.UTF_8))
        }
        
        return CredentialEntity(
            id = credential.id,
            platformId = credential.platformId,
            username = credential.username,
            encryptedPassword = encryptedPassword,
            encryptedNotes = encryptedNotes,
            createdAt = credential.createdAt,
            updatedAt = credential.updatedAt,
            email = credential.email,
            credentialType = credential.credentialType.name.lowercase(),
            encryptedBackupEmail = encryptedBackupEmail,
            encryptedPhoneNumber = encryptedPhoneNumber,
            birthdate = credential.birthdate,
            twoFaEnabled = credential.twoFaEnabled,
            encryptedRecoveryCodes = encryptedRecoveryCodes,
            accountName = credential.accountName,
            encryptedPrivateKey = encryptedPrivateKey,
            encryptedSeedPhrase = encryptedSeedPhrase,
            lastEditedAt = credential.lastEditedAt?.toEpochMilli()
        )
    }

    /**
     * Map database entity to domain credential.
     * Decrypts sensitive fields.
     */
    suspend fun toDomain(entity: CredentialEntity): Credential? {
        val password = dekManager.decrypt(entity.encryptedPassword)
            ?.toString(Charsets.UTF_8)
            ?: return null
        val notes = dekManager.decrypt(entity.encryptedNotes)
            ?.toString(Charsets.UTF_8)
            ?: return null
        
        // Decrypt optional sensitive fields
        val backupEmail = entity.encryptedBackupEmail?.let {
            dekManager.decrypt(it)?.toString(Charsets.UTF_8)
        }
        val phoneNumber = entity.encryptedPhoneNumber?.let {
            dekManager.decrypt(it)?.toString(Charsets.UTF_8)
        }
        val recoveryCodes = entity.encryptedRecoveryCodes?.let {
            dekManager.decrypt(it)?.toString(Charsets.UTF_8)
        }
        // Decrypt wallet-specific fields
        val privateKey = entity.encryptedPrivateKey?.let {
            dekManager.decrypt(it)?.toString(Charsets.UTF_8)
        }
        val seedPhrase = entity.encryptedSeedPhrase?.let {
            dekManager.decrypt(it)?.toString(Charsets.UTF_8)
        }
        
        return Credential(
            id = entity.id,
            platformId = entity.platformId,
            username = entity.username,
            password = password,
            notes = notes,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            email = entity.email,
            credentialType = CredentialType.fromString(entity.credentialType),
            backupEmail = backupEmail,
            phoneNumber = phoneNumber,
            birthdate = entity.birthdate,
            twoFaEnabled = entity.twoFaEnabled,
            recoveryCodes = recoveryCodes,
            accountName = entity.accountName,
            privateKey = privateKey,
            seedPhrase = seedPhrase,
            lastEditedAt = entity.lastEditedAt?.let { Instant.ofEpochMilli(it) }
        )
    }

    /**
     * Map a list of database entities to domain credentials.
     * Returns only successfully decrypted credentials.
     */
    suspend fun toDomainList(entities: List<CredentialEntity>): List<Credential> {
        return entities.mapNotNull { toDomain(it) }
    }
}

/**
 * Maps between domain Platform and database PlatformEntity.
 * No encryption needed for platforms.
 */
object PlatformMapper {
    
    fun toEntity(platform: Platform): PlatformEntity {
        return PlatformEntity(
            id = platform.id,
            name = platform.name,
            domain = platform.domain,
            iconName = platform.iconName,
            color = platform.color,
            type = platform.type,
            isCustom = platform.isCustom,
            lastNameEditAt = platform.lastNameEditAt?.toEpochMilli(),
            createdAt = platform.createdAt.toEpochMilli()
        )
    }

    fun toDomain(entity: PlatformEntity): Platform {
        return Platform(
            id = entity.id,
            name = entity.name,
            domain = entity.domain,
            iconName = entity.iconName,
            color = entity.color,
            type = entity.type,
            isCustom = entity.isCustom,
            lastNameEditAt = entity.lastNameEditAt?.let { Instant.ofEpochMilli(it) },
            createdAt = Instant.ofEpochMilli(entity.createdAt)
        )
    }

    /**
     * Map PlatformWithStats (from DAO join query) to domain Platform.
     * Includes credentialCount and lastCredentialAdded.
     */
    fun toDomain(stats: PlatformWithStats): Platform {
        return Platform(
            id = stats.id,
            name = stats.name,
            domain = stats.domain,
            iconName = stats.iconName,
            color = stats.color,
            type = stats.type,
            isCustom = stats.isCustom,
            credentialCount = stats.credentialCount,
            lastCredentialAdded = stats.lastCredentialAdded?.let { Instant.ofEpochMilli(it) },
            lastNameEditAt = stats.lastNameEditAt?.let { Instant.ofEpochMilli(it) },
            createdAt = Instant.ofEpochMilli(stats.createdAt)
        )
    }

    fun toDomainList(entities: List<PlatformEntity>): List<Platform> {
        return entities.map { toDomain(it) }
    }

    fun toDomainListWithStats(stats: List<PlatformWithStats>): List<Platform> {
        return stats.map { toDomain(it) }
    }
    
    fun toEntityList(platforms: List<Platform>): List<PlatformEntity> {
        return platforms.map { toEntity(it) }
    }
}
