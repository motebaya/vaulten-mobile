package com.motebaya.vaulten.data.repository

import com.motebaya.vaulten.data.local.dao.PlatformDao
import com.motebaya.vaulten.data.mapper.PlatformMapper
import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.domain.entity.VaultError
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.PlatformRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlatformRepository.
 * 
 * Platforms don't require encryption as they're not sensitive.
 */
@Singleton
class PlatformRepositoryImpl @Inject constructor(
    private val platformDao: PlatformDao
) : PlatformRepository {

    override fun getAllPlatforms(): Flow<List<Platform>> {
        return platformDao.getAllPlatforms().map { entities ->
            PlatformMapper.toDomainList(entities)
        }
    }

    override fun observeAllWithStats(): Flow<List<Platform>> {
        return platformDao.getPlatformsWithStats().map { statsList ->
            PlatformMapper.toDomainListWithStats(statsList)
        }
    }

    override suspend fun getPlatformById(id: String): Platform? {
        return platformDao.getPlatformById(id)?.let { PlatformMapper.toDomain(it) }
    }

    override fun searchPlatforms(query: String): Flow<List<Platform>> {
        return platformDao.searchPlatforms(query).map { entities ->
            PlatformMapper.toDomainList(entities)
        }
    }

    override suspend fun findPlatformByDomain(domain: String): Platform? {
        return platformDao.findPlatformByDomain(domain)?.let { PlatformMapper.toDomain(it) }
    }

    override suspend fun create(platform: Platform): VaultResult<Unit> {
        return savePlatform(platform)
    }

    override suspend fun savePlatform(platform: Platform): VaultResult<Unit> {
        return try {
            platformDao.insertPlatform(PlatformMapper.toEntity(platform))
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun updateName(id: String, name: String) {
        try {
            val existing = platformDao.getPlatformById(id)
            if (existing != null && existing.isCustom) {
                platformDao.updatePlatform(existing.copy(
                    name = name,
                    lastNameEditAt = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            // Silently fail, could add logging
        }
    }
    
    override suspend fun updateNameWithRestriction(id: String, name: String): VaultResult<Unit> {
        return try {
            val existing = platformDao.getPlatformById(id)
                ?: return VaultResult.Error(VaultError.NotFound)
            
            if (!existing.isCustom) {
                return VaultResult.Error(VaultError.ValidationError("Cannot edit built-in platform"))
            }
            
            // Check 24h restriction
            val lastEditAt = existing.lastNameEditAt
            if (lastEditAt != null) {
                val hoursSinceEdit = (System.currentTimeMillis() - lastEditAt) / (1000 * 60 * 60)
                if (hoursSinceEdit < 24) {
                    val hoursRemaining = 24 - hoursSinceEdit
                    return VaultResult.Error(
                        VaultError.ValidationError("Name can only be edited once every 24 hours. Try again in $hoursRemaining hour(s).")
                    )
                }
            }
            
            platformDao.updatePlatform(existing.copy(
                name = name,
                lastNameEditAt = System.currentTimeMillis()
            ))
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun updateDomain(id: String, domain: String) {
        try {
            val existing = platformDao.getPlatformById(id)
            if (existing != null && existing.isCustom) {
                platformDao.updatePlatform(existing.copy(domain = domain))
            }
        } catch (e: Exception) {
            // Silently fail, could add logging
        }
    }
    
    override suspend fun updateType(id: String, type: String) {
        try {
            val existing = platformDao.getPlatformById(id)
            if (existing != null && existing.isCustom) {
                platformDao.updatePlatform(existing.copy(type = type))
            }
        } catch (e: Exception) {
            // Silently fail, could add logging
        }
    }
    
    override suspend fun updatePlatform(id: String, name: String?, domain: String?, type: String?): VaultResult<Unit> {
        return try {
            val existing = platformDao.getPlatformById(id)
                ?: return VaultResult.Error(VaultError.NotFound)
            
            if (!existing.isCustom) {
                return VaultResult.Error(VaultError.ValidationError("Cannot edit built-in platform"))
            }
            
            var updated = existing
            
            // Handle name update with 24h restriction
            if (name != null && name != existing.name) {
                val lastEditAt = existing.lastNameEditAt
                if (lastEditAt != null) {
                    val hoursSinceEdit = (System.currentTimeMillis() - lastEditAt) / (1000 * 60 * 60)
                    if (hoursSinceEdit < 24) {
                        val hoursRemaining = 24 - hoursSinceEdit
                        return VaultResult.Error(
                            VaultError.ValidationError("Name can only be edited once every 24 hours. Try again in $hoursRemaining hour(s).")
                        )
                    }
                }
                updated = updated.copy(name = name, lastNameEditAt = System.currentTimeMillis())
            }
            
            // Handle domain update (no restriction)
            if (domain != null) {
                updated = updated.copy(domain = domain)
            }
            
            // Handle type update (no restriction)
            if (type != null) {
                updated = updated.copy(type = type)
            }
            
            platformDao.updatePlatform(updated)
            VaultResult.Success(Unit)
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun delete(id: String): VaultResult<Unit> {
        return try {
            val deleted = platformDao.deleteCustomPlatform(id)
            if (deleted > 0) {
                VaultResult.Success(Unit)
            } else {
                VaultResult.Error(VaultError.ValidationError("Cannot delete built-in platform"))
            }
        } catch (e: Exception) {
            VaultResult.Error(VaultError.DatabaseError)
        }
    }

    override suspend fun deletePlatform(id: String): VaultResult<Unit> {
        return delete(id)
    }

    override suspend fun initializeBuiltInPlatforms() {
        val entities = PlatformMapper.toEntityList(Platform.BUILT_IN_PLATFORMS)
        platformDao.insertPlatforms(entities)
    }
}
