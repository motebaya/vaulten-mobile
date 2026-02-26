package com.motebaya.vaulten.data.local.dao

import androidx.room.*
import com.motebaya.vaulten.data.local.entity.PlatformEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for platforms.
 */
@Dao
interface PlatformDao {
    
    @Query("SELECT * FROM platforms ORDER BY name ASC")
    fun getAllPlatforms(): Flow<List<PlatformEntity>>
    
    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun getPlatformById(id: String): PlatformEntity?
    
    @Query("SELECT * FROM platforms WHERE name LIKE '%' || :query || '%' OR domain LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchPlatforms(query: String): Flow<List<PlatformEntity>>
    
    @Query("SELECT * FROM platforms WHERE domain = :domain LIMIT 1")
    suspend fun findPlatformByDomain(domain: String): PlatformEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlatform(platform: PlatformEntity)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlatforms(platforms: List<PlatformEntity>)
    
    @Update
    suspend fun updatePlatform(platform: PlatformEntity)
    
    @Query("DELETE FROM platforms WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomPlatform(id: String): Int
    
    @Query("DELETE FROM platforms")
    suspend fun deleteAllPlatforms()
    
    @Query("SELECT * FROM platforms")
    suspend fun getAllPlatformsSync(): List<PlatformEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlatformsReplace(platforms: List<PlatformEntity>)

    /**
     * Get all platforms with credential statistics.
     */
    @Query("""
        SELECT p.id, p.name, p.domain, p.iconName, p.color, p.type, p.isCustom, p.lastNameEditAt, p.createdAt,
               COUNT(c.id) as credentialCount,
               MAX(c.createdAt) as lastCredentialAdded
        FROM platforms p
        LEFT JOIN credentials c ON p.id = c.platformId
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    fun getPlatformsWithStats(): Flow<List<PlatformWithStats>>
}

/**
 * Data class for platform with credential stats.
 */
data class PlatformWithStats(
    val id: String,
    val name: String,
    val domain: String,
    val iconName: String,
    val color: String,
    val type: String,
    val isCustom: Boolean,
    val lastNameEditAt: Long?,  // Epoch milliseconds or null if never edited
    val createdAt: Long,        // Epoch milliseconds when platform was created
    val credentialCount: Int,
    val lastCredentialAdded: Long?  // Epoch milliseconds or null if no credentials
)
