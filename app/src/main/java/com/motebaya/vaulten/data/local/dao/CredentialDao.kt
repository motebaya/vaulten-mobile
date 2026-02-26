package com.motebaya.vaulten.data.local.dao

import androidx.room.*
import com.motebaya.vaulten.data.local.entity.CredentialEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for credentials.
 */
@Dao
interface CredentialDao {
    
    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    fun getAllCredentials(): Flow<List<CredentialEntity>>
    
    @Query("SELECT * FROM credentials WHERE platformId = :platformId ORDER BY username ASC")
    fun getCredentialsByPlatform(platformId: String): Flow<List<CredentialEntity>>
    
    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getCredentialById(id: String): CredentialEntity?
    
    @Query("SELECT * FROM credentials WHERE username LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchCredentials(query: String): Flow<List<CredentialEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: CredentialEntity)
    
    @Update
    suspend fun updateCredential(credential: CredentialEntity)
    
    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteCredential(id: String)
    
    @Query("DELETE FROM credentials WHERE platformId = :platformId")
    suspend fun deleteCredentialsByPlatform(platformId: String)
    
    @Query("SELECT COUNT(*) FROM credentials")
    suspend fun getCredentialCount(): Int
    
    @Query("SELECT * FROM credentials")
    suspend fun getAllCredentialsSync(): List<CredentialEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredentials(credentials: List<CredentialEntity>)
    
    @Query("DELETE FROM credentials")
    suspend fun deleteAllCredentials()
}
