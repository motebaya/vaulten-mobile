package com.motebaya.vaulten.domain.repository

import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.VaultResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for credential operations.
 * 
 * All credentials are encrypted before storage and decrypted on retrieval.
 * The repository implementation handles encryption/decryption transparently.
 * 
 * SECURITY NOTE: Implementations must ensure that:
 * - Credentials are encrypted with DEK before writing to database
 * - Credentials are decrypted with DEK when reading from database
 * - Operations fail gracefully if vault is locked (DEK unavailable)
 */
interface CredentialRepository {
    
    /**
     * Get all credentials, decrypted.
     * 
     * @return Flow of decrypted credentials, or error if vault is locked
     */
    fun getAllCredentials(): Flow<VaultResult<List<Credential>>>
    
    /**
     * Get credentials for a specific platform.
     * 
     * @param platformId The platform to filter by
     * @return Flow of decrypted credentials for the platform
     */
    fun getCredentialsByPlatform(platformId: String): Flow<VaultResult<List<Credential>>>
    
    /**
     * Get a single credential by ID.
     * 
     * @param id The credential ID
     * @return The decrypted credential, or null if not found
     */
    suspend fun getCredentialById(id: String): VaultResult<Credential?>
    
    /**
     * Search credentials by username or platform name.
     * 
     * @param query Search query
     * @return Flow of matching decrypted credentials
     */
    fun searchCredentials(query: String): Flow<VaultResult<List<Credential>>>
    
    /**
     * Save a credential (insert or update).
     * The credential will be encrypted before storage.
     * 
     * @param credential The credential to save
     * @return Success or error
     */
    suspend fun saveCredential(credential: Credential): VaultResult<Unit>
    
    /**
     * Delete a credential.
     * 
     * @param id The credential ID to delete
     * @return Success or error
     */
    suspend fun deleteCredential(id: String): VaultResult<Unit>
    
    /**
     * Delete all credentials for a platform.
     * 
     * @param platformId The platform ID
     * @return Success or error
     */
    suspend fun deleteCredentialsByPlatform(platformId: String): VaultResult<Unit>
    
    /**
     * Get the total count of credentials.
     * 
     * @return Number of stored credentials
     */
    suspend fun getCredentialCount(): Int
}
