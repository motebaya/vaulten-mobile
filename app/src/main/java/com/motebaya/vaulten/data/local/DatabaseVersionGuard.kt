package com.motebaya.vaulten.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Guards against database version incompatibility.
 * 
 * Prevents the app from running if the database was created by a newer app version,
 * which would cause data loss or corruption if the older app tried to read it.
 * 
 * This is a common safety pattern used by apps like WhatsApp, Signal, and banking apps.
 */
object DatabaseVersionGuard {
    
    private const val TAG = "DatabaseVersionGuard"
    
    /**
     * The current database schema version supported by this app build.
     * 
     * CRITICAL: This MUST match VaultDatabase @Database(version = X)
     * 
     * When adding migrations:
     * 1. Add migration to DatabaseModule.kt
     * 2. Increment VaultDatabase.version
     * 3. Increment this constant to match
     * 
     * Current schema history:
     * - v1: Initial schema
     * - v2: Added 'type' column to platforms
     * - v3: Added 'lastNameEditAt' column to platforms
     * - v4: Added extended credential fields (email, credentialType, etc.)
     * - v5: Added 'lastEditedAt' column to credentials
     * - v6: Added wallet fields (accountName, encryptedPrivateKey, encryptedSeedPhrase)
     * - v7: Added 'createdAt' column to platforms
     */
    const val CURRENT_SCHEMA_VERSION = 7
    
    private const val DATABASE_NAME = "vault.db"
    
    /**
     * Check if the existing database (if any) is compatible with this app version.
     * 
     * @param context Application context
     * @return VersionCheckResult indicating compatibility status
     */
    fun checkCompatibility(context: Context): VersionCheckResult {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        
        // No database exists yet - fresh install, always compatible
        if (!dbFile.exists()) {
            Log.d(TAG, "No existing database found - fresh install compatible")
            return VersionCheckResult.Compatible
        }
        
        return try {
            val dbVersion = getDatabaseVersion(dbFile)
            
            // Log diagnostic info for debugging (non-sensitive)
            Log.i(TAG, "Database version check: dbVersion=$dbVersion, appSupports=$CURRENT_SCHEMA_VERSION")
            
            when {
                dbVersion > CURRENT_SCHEMA_VERSION -> {
                    // Database was created by a newer app version
                    Log.w(TAG, "Database incompatible: version $dbVersion > supported $CURRENT_SCHEMA_VERSION")
                    VersionCheckResult.IncompatibleNewer(
                        currentAppSupports = CURRENT_SCHEMA_VERSION,
                        databaseVersion = dbVersion
                    )
                }
                else -> {
                    // Database version is same or older - Room will handle migration
                    Log.d(TAG, "Database compatible: version $dbVersion <= supported $CURRENT_SCHEMA_VERSION")
                    VersionCheckResult.Compatible
                }
            }
        } catch (e: Exception) {
            // If we can't read the database version, assume compatible
            // Room will handle any actual issues during initialization
            Log.w(TAG, "Failed to read database version, assuming compatible", e)
            VersionCheckResult.Compatible
        }
    }
    
    /**
     * Read the user_version pragma from the SQLite database file.
     * This is how Room stores the schema version.
     */
    private fun getDatabaseVersion(dbFile: File): Int {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            db.rawQuery("PRAGMA user_version", null).use { cursor ->
                return if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    0
                }
            }
        } finally {
            db?.close()
        }
    }
}

/**
 * Result of database version compatibility check.
 */
sealed class VersionCheckResult {
    /**
     * Database is compatible with current app version.
     * Either no database exists, or the version is <= current.
     */
    object Compatible : VersionCheckResult()
    
    /**
     * Database was created by a newer app version.
     * Running this older app would cause data loss or corruption.
     * 
     * @param currentAppSupports The max schema version this app can handle
     * @param databaseVersion The actual version found in the database
     */
    data class IncompatibleNewer(
        val currentAppSupports: Int,
        val databaseVersion: Int
    ) : VersionCheckResult()
}
