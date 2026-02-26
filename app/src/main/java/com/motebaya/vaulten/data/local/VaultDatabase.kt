package com.motebaya.vaulten.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.motebaya.vaulten.data.local.dao.CredentialDao
import com.motebaya.vaulten.data.local.dao.PlatformDao
import com.motebaya.vaulten.data.local.entity.CredentialEntity
import com.motebaya.vaulten.data.local.entity.PlatformEntity

/**
 * Room database for the vault.
 * 
 * IMPORTANT: The database stores ENCRYPTED data.
 * Sensitive fields (password, notes, privateKey, seedPhrase, etc.) are encrypted with DEK before storage.
 * The database file itself is NOT encrypted.
 * 
 * CURRENT SCHEMA VERSION: 7
 * 
 * CRITICAL: When changing the version number here, you MUST also update:
 * - DatabaseVersionGuard.CURRENT_SCHEMA_VERSION (to match this version)
 * - Add the corresponding migration in DatabaseModule.kt
 * 
 * This version is checked by DatabaseVersionGuard to prevent data loss from app downgrades.
 * The DatabaseVersionGuard constant must ALWAYS match this version number.
 */
@Database(
    entities = [
        CredentialEntity::class,
        PlatformEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    
    abstract fun credentialDao(): CredentialDao
    
    abstract fun platformDao(): PlatformDao
    
    companion object {
        /**
         * Current schema version for reference.
         * 
         * MUST match the @Database(version = X) annotation above
         * and DatabaseVersionGuard.CURRENT_SCHEMA_VERSION.
         */
        const val SCHEMA_VERSION = 7
    }
}
