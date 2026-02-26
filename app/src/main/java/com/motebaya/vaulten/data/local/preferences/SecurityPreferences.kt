package com.motebaya.vaulten.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_prefs")

/**
 * Manages security-related preferences including PIN lockout state.
 * 
 * SECURITY: Only stores counters and timestamps, never sensitive data.
 * Persists across app restarts, orientation changes, and process death.
 */
@Singleton
class SecurityPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val FAILED_ATTEMPT_COUNT = intPreferencesKey("failed_attempt_count")
        private val LOCKOUT_END_TIME = longPreferencesKey("lockout_end_time")
        private val LAST_ATTEMPT_TIME = longPreferencesKey("last_attempt_time")
        
        // Security constants
        const val MAX_ATTEMPTS = 5
        const val WARNING_THRESHOLD = 3
        const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }
    
    private val dataStore = context.securityDataStore
    
    /**
     * Flow of current failed attempt count.
     */
    val failedAttemptCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[FAILED_ATTEMPT_COUNT] ?: 0
    }
    
    /**
     * Flow of lockout end time (Unix timestamp in milliseconds).
     * Returns 0 if not locked out.
     */
    val lockoutEndTime: Flow<Long> = dataStore.data.map { prefs ->
        prefs[LOCKOUT_END_TIME] ?: 0L
    }
    
    /**
     * Flow of whether currently locked out.
     */
    val isLockedOut: Flow<Boolean> = lockoutEndTime.map { endTime ->
        endTime > System.currentTimeMillis()
    }
    
    /**
     * Flow of remaining lockout time in seconds.
     */
    val remainingLockoutSeconds: Flow<Int> = lockoutEndTime.map { endTime ->
        val remaining = endTime - System.currentTimeMillis()
        if (remaining > 0) (remaining / 1000).toInt() else 0
    }
    
    /**
     * Increment failed attempt counter.
     * Triggers lockout if max attempts reached.
     * 
     * @return true if lockout was triggered
     */
    suspend fun recordFailedAttempt(): Boolean {
        var triggeredLockout = false
        
        dataStore.edit { prefs ->
            val currentCount = (prefs[FAILED_ATTEMPT_COUNT] ?: 0) + 1
            prefs[FAILED_ATTEMPT_COUNT] = currentCount
            prefs[LAST_ATTEMPT_TIME] = System.currentTimeMillis()
            
            if (currentCount >= MAX_ATTEMPTS) {
                prefs[LOCKOUT_END_TIME] = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                triggeredLockout = true
            }
        }
        
        return triggeredLockout
    }
    
    /**
     * Reset all lockout state after successful authentication.
     */
    suspend fun resetLockout() {
        dataStore.edit { prefs ->
            prefs[FAILED_ATTEMPT_COUNT] = 0
            prefs[LOCKOUT_END_TIME] = 0L
            prefs[LAST_ATTEMPT_TIME] = 0L
        }
    }
    
    /**
     * Get current failed attempt count synchronously.
     */
    suspend fun getFailedAttemptCount(): Int {
        var count = 0
        dataStore.edit { prefs ->
            count = prefs[FAILED_ATTEMPT_COUNT] ?: 0
        }
        return count
    }
    
    /**
     * Get remaining lockout time in milliseconds.
     */
    suspend fun getRemainingLockoutMs(): Long {
        var remaining = 0L
        dataStore.edit { prefs ->
            val endTime = prefs[LOCKOUT_END_TIME] ?: 0L
            remaining = maxOf(0L, endTime - System.currentTimeMillis())
        }
        return remaining
    }
    
    /**
     * Check if currently locked out.
     */
    suspend fun checkLockedOut(): Boolean {
        return getRemainingLockoutMs() > 0
    }
}
