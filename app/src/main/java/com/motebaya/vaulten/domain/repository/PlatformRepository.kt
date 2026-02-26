package com.motebaya.vaulten.domain.repository

import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.domain.entity.VaultResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for platform operations.
 * 
 * Platforms are not encrypted as they don't contain sensitive data.
 * They provide organizational structure and visual identity for credentials.
 */
interface PlatformRepository {
    
    /**
     * Get all platforms (built-in and custom).
     * 
     * @return Flow of all platforms sorted by name
     */
    fun getAllPlatforms(): Flow<List<Platform>>
    
    /**
     * Observe all platforms (alias for getAllPlatforms).
     */
    fun observeAll(): Flow<List<Platform>> = getAllPlatforms()
    
    /**
     * Observe all platforms with credential statistics.
     * 
     * @return Flow of platforms with credential counts
     */
    fun observeAllWithStats(): Flow<List<Platform>>
    
    /**
     * Get a platform by ID.
     * 
     * @param id The platform ID
     * @return The platform, or null if not found
     */
    suspend fun getPlatformById(id: String): Platform?
    
    /**
     * Search platforms by name or domain.
     * 
     * @param query Search query
     * @return Flow of matching platforms
     */
    fun searchPlatforms(query: String): Flow<List<Platform>>
    
    /**
     * Find a platform by domain.
     * Useful for auto-matching credentials to platforms.
     * 
     * @param domain The domain to search for
     * @return The matching platform, or null
     */
    suspend fun findPlatformByDomain(domain: String): Platform?
    
    /**
     * Create a new platform.
     * 
     * @param platform The platform to create
     * @return Success or error
     */
    suspend fun create(platform: Platform): VaultResult<Unit>
    
    /**
     * Save a custom platform.
     * 
     * @param platform The platform to save
     * @return Success or error
     */
    suspend fun savePlatform(platform: Platform): VaultResult<Unit>
    
    /**
     * Update platform name.
     * 
     * @param id The platform ID
     * @param name The new name
     */
    suspend fun updateName(id: String, name: String)
    
    /**
     * Update platform name with 24h restriction check.
     * Returns error if last name edit was within 24 hours.
     * 
     * @param id The platform ID
     * @param name The new name
     * @return Success or error (if within 24h restriction)
     */
    suspend fun updateNameWithRestriction(id: String, name: String): VaultResult<Unit>
    
    /**
     * Update platform domain.
     * 
     * @param id The platform ID
     * @param domain The new domain
     */
    suspend fun updateDomain(id: String, domain: String)
    
    /**
     * Update platform type.
     * 
     * @param id The platform ID
     * @param type The new type
     */
    suspend fun updateType(id: String, type: String)
    
    /**
     * Update platform (full update - name, domain, type).
     * Enforces 24h restriction on name changes.
     * 
     * @param id Platform ID
     * @param name New name (or null to skip)
     * @param domain New domain (or null to skip)
     * @param type New type (or null to skip)
     * @return Success or error
     */
    suspend fun updatePlatform(id: String, name: String?, domain: String?, type: String?): VaultResult<Unit>
    
    /**
     * Delete a custom platform.
     * Built-in platforms cannot be deleted.
     * 
     * @param id The platform ID to delete
     * @return Success or error (fails if platform is built-in)
     */
    suspend fun delete(id: String): VaultResult<Unit>
    
    /**
     * Delete a custom platform (alias for delete).
     */
    suspend fun deletePlatform(id: String): VaultResult<Unit> = delete(id)
    
    /**
     * Initialize built-in platforms if not already present.
     * Called on first app launch.
     */
    suspend fun initializeBuiltInPlatforms()
}
