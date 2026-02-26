package com.motebaya.vaulten.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.motebaya.vaulten.data.local.VaultDatabase
import com.motebaya.vaulten.data.local.dao.CredentialDao
import com.motebaya.vaulten.data.local.dao.PlatformDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database dependencies.
 * 
 * IMPORTANT: The Room database stores ENCRYPTED data only.
 * All sensitive fields are encrypted with DEK before storage.
 * The database file itself is NOT encrypted - the data within is.
 * 
 * This allows us to:
 * - Use Room's standard query capabilities
 * - Maintain cross-platform vault.enc compatibility
 * - Keep encryption logic separate from persistence
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Migration from version 1 to 2.
     * Adds the 'type' column to the platforms table.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE platforms ADD COLUMN type TEXT NOT NULL DEFAULT 'social'"
            )
        }
    }

    /**
     * Migration from version 2 to 3.
     * Adds the 'lastNameEditAt' column to the platforms table for 24h edit restriction.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE platforms ADD COLUMN lastNameEditAt INTEGER"
            )
        }
    }

    /**
     * Migration from version 3 to 4.
     * Adds extended credential fields for Google/advanced credentials.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add email column (plaintext for search)
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN email TEXT"
            )
            // Add credential type column
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN credentialType TEXT NOT NULL DEFAULT 'standard'"
            )
            // Add encrypted backup email
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN encryptedBackupEmail BLOB"
            )
            // Add encrypted phone number
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN encryptedPhoneNumber BLOB"
            )
            // Add birthdate (plaintext metadata)
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN birthdate TEXT"
            )
            // Add 2FA enabled flag
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN twoFaEnabled INTEGER NOT NULL DEFAULT 0"
            )
            // Add encrypted recovery codes
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN encryptedRecoveryCodes BLOB"
            )
            // Create indexes for search
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_credentials_email ON credentials(email)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_credentials_username ON credentials(username)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_credentials_credentialType ON credentials(credentialType)"
            )
        }
    }

    /**
     * Migration from version 4 to 5.
     * Adds lastEditedAt column to credentials for 24h edit restriction.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN lastEditedAt INTEGER"
            )
        }
    }

    /**
     * Migration from version 5 to 6.
     * Adds wallet/google-specific fields: accountName, encryptedPrivateKey, encryptedSeedPhrase.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add accountName (plaintext display name for wallet/google)
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN accountName TEXT"
            )
            // Add encrypted private key for crypto wallets
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN encryptedPrivateKey BLOB"
            )
            // Add encrypted seed phrase for crypto wallets
            database.execSQL(
                "ALTER TABLE credentials ADD COLUMN encryptedSeedPhrase BLOB"
            )
        }
    }

    /**
     * Migration from version 6 to 7.
     * Adds createdAt column to platforms table for tracking when platforms were created.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add createdAt column with current timestamp as default for existing platforms
            database.execSQL(
                "ALTER TABLE platforms ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}"
            )
        }
    }

    @Provides
    @Singleton
    fun provideVaultDatabase(
        @ApplicationContext context: Context
    ): VaultDatabase {
        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
    }

    @Provides
    fun provideCredentialDao(database: VaultDatabase): CredentialDao {
        return database.credentialDao()
    }

    @Provides
    fun providePlatformDao(database: VaultDatabase): PlatformDao {
        return database.platformDao()
    }
}
