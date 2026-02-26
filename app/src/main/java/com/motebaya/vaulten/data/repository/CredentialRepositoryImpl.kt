package com.motebaya.vaulten.data.repository

import com.motebaya.vaulten.data.local.dao.CredentialDao
import com.motebaya.vaulten.data.mapper.CredentialMapper
import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.VaultError
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.CredentialRepository
import com.motebaya.vaulten.security.crypto.DekManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CredentialRepository.
 * 
 * Handles encryption/decryption transparently using CredentialMapper.
 */
@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val credentialDao: CredentialDao,
    private val dekManager: DekManager
) : CredentialRepository {

    private val mapper = CredentialMapper(dekManager)

    override fun getAllCredentials(): Flow<VaultResult<List<Credential>>> {
        return credentialDao.getAllCredentials().map { entities ->
            if (!dekManager.isUnlocked()) {
                VaultResult.Error(VaultError.VaultLocked)
            } else {
                try {
                    val credentials = mapper.toDomainList(entities)
                    VaultResult.Success(credentials)
                } catch (e: Exception) {
                    VaultResult.Error(VaultError.DecryptionFailed)
                }
            }
        }
    }

    override fun getCredentialsByPlatform(platformId: String): Flow<VaultResult<List<Credential>>> {
        return credentialDao.getCredentialsByPlatform(platformId).map { entities ->
            if (!dekManager.isUnlocked()) {
                VaultResult.Error(VaultError.VaultLocked)
            } else {
                try {
                    val credentials = mapper.toDomainList(entities)
                    VaultResult.Success(credentials)
                } catch (e: Exception) {
                    VaultResult.Error(VaultError.DecryptionFailed)
                }
            }
        }
    }

    override suspend fun getCredentialById(id: String): VaultResult<Credential?> {
        if (!dekManager.isUnlocked()) {
            return VaultResult.Error(VaultError.VaultLocked)
        }
        
        return try {
            val entity = credentialDao.getCredentialById(id)
            val credential = entity?.let { mapper.toDomain(it) }
            VaultResult.Success(credential)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DecryptionFailed)
        }
    }

    override fun searchCredentials(query: String): Flow<VaultResult<List<Credential>>> {
        return credentialDao.searchCredentials(query).map { entities ->
            if (!dekManager.isUnlocked()) {
                VaultResult.Error(VaultError.VaultLocked)
            } else {
                try {
                    val credentials = mapper.toDomainList(entities)
                    VaultResult.Success(credentials)
                } catch (e: Exception) {
                    VaultResult.Error(VaultError.DecryptionFailed)
                }
            }
        }
    }

    override suspend fun saveCredential(credential: Credential): VaultResult<Unit> {
        if (!dekManager.isUnlocked()) {
            return VaultResult.Error(VaultError.VaultLocked)
        }
        
        return try {
            val entity = mapper.toEntity(credential.withUpdatedTimestamp())
                ?: return VaultResult.Error(VaultError.EncryptionFailed)
            credentialDao.insertCredential(entity)
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun deleteCredential(id: String): VaultResult<Unit> {
        return try {
            credentialDao.deleteCredential(id)
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun deleteCredentialsByPlatform(platformId: String): VaultResult<Unit> {
        return try {
            credentialDao.deleteCredentialsByPlatform(platformId)
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun getCredentialCount(): Int {
        return try {
            credentialDao.getCredentialCount()
        } catch (e: Exception) {
            0
        }
    }
}
