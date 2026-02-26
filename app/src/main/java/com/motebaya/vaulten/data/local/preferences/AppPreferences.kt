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

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/**
 * Manages app-level preferences like sort order, filter settings, etc.
 * 
 * NOTE: Only non-sensitive preferences are stored here.
 * Sensitive data uses EncryptedSharedPreferences or the vault.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val PLATFORM_SORT_FIELD = stringPreferencesKey("platform_sort_field")
        private val PLATFORM_SORT_ASCENDING = booleanPreferencesKey("platform_sort_ascending")
        private val PLATFORM_TYPE_FILTER = stringPreferencesKey("platform_type_filter")
    }
    
    private val dataStore = context.appPrefsDataStore
    
    // Platform sort field
    val platformSortField: Flow<PlatformSortField> = dataStore.data.map { prefs ->
        val fieldName = prefs[PLATFORM_SORT_FIELD] ?: PlatformSortField.NAME.name
        try {
            PlatformSortField.valueOf(fieldName)
        } catch (e: Exception) {
            PlatformSortField.NAME
        }
    }
    
    // Platform sort direction
    val platformSortAscending: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PLATFORM_SORT_ASCENDING] ?: true
    }
    
    // Platform type filter (null = all types)
    val platformTypeFilter: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PLATFORM_TYPE_FILTER]?.takeIf { it.isNotEmpty() }
    }
    
    suspend fun setPlatformSortField(field: PlatformSortField) {
        dataStore.edit { prefs ->
            prefs[PLATFORM_SORT_FIELD] = field.name
        }
    }
    
    suspend fun setPlatformSortAscending(ascending: Boolean) {
        dataStore.edit { prefs ->
            prefs[PLATFORM_SORT_ASCENDING] = ascending
        }
    }
    
    suspend fun setPlatformTypeFilter(type: String?) {
        dataStore.edit { prefs ->
            if (type.isNullOrEmpty()) {
                prefs.remove(PLATFORM_TYPE_FILTER)
            } else {
                prefs[PLATFORM_TYPE_FILTER] = type
            }
        }
    }
    
    suspend fun clearPlatformFilters() {
        dataStore.edit { prefs ->
            prefs.remove(PLATFORM_SORT_FIELD)
            prefs.remove(PLATFORM_SORT_ASCENDING)
            prefs.remove(PLATFORM_TYPE_FILTER)
        }
    }
}

/**
 * Available sort fields for platforms.
 */
enum class PlatformSortField {
    NAME,
    CREDENTIAL_COUNT,
    LAST_CREDENTIAL_ADDED,
    CREATED_DATE
}
