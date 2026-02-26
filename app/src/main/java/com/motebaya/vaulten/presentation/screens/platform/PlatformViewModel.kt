package com.motebaya.vaulten.presentation.screens.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.data.cache.FaviconCache
import com.motebaya.vaulten.data.local.preferences.AppPreferences
import com.motebaya.vaulten.data.local.preferences.PlatformSortField
import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.domain.repository.PlatformRepository
import com.motebaya.vaulten.presentation.components.ToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel for platform management screen.
 */
@HiltViewModel
class PlatformViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val appPreferences: AppPreferences,
    private val authRepository: com.motebaya.vaulten.domain.repository.AuthRepository,
    val faviconCache: FaviconCache,
    private val keystoreManager: com.motebaya.vaulten.security.keystore.KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlatformUiState())
    val uiState: StateFlow<PlatformUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _sortField = MutableStateFlow(PlatformSortField.NAME)
    private val _sortAscending = MutableStateFlow(true)
    private val _typeFilter = MutableStateFlow<String?>(null)
    // Multi-select type filter (for checkbox UI). Empty set = all types selected
    private val _selectedTypes = MutableStateFlow<Set<String>>(emptySet())
    
    // Biometric availability
    private val _biometricAvailable = MutableStateFlow(false)
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable.asStateFlow()
    
    // Biometric prompt trigger
    private val _showBiometricPrompt = MutableStateFlow(false)
    val showBiometricPrompt: StateFlow<Boolean> = _showBiometricPrompt.asStateFlow()
    
    /**
     * Expose KeystoreManager for BiometricAuthHelper in the UI layer.
     */
    fun getKeystoreManager() = keystoreManager

    init {
        loadPreferences()
        loadPlatforms()
        checkBiometricAvailability()
    }
    
    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            _biometricAvailable.value = authRepository.isBiometricEnabled() &&
                    authRepository.isBiometricAvailable()
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            appPreferences.platformSortField.collect { field ->
                _sortField.value = field
                _uiState.update { it.copy(sortField = field) }
            }
        }
        viewModelScope.launch {
            appPreferences.platformSortAscending.collect { ascending ->
                _sortAscending.value = ascending
                _uiState.update { it.copy(sortAscending = ascending) }
            }
        }
        viewModelScope.launch {
            appPreferences.platformTypeFilter.collect { type ->
                _typeFilter.value = type
                _uiState.update { it.copy(typeFilter = type) }
            }
        }
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                combine(
                    platformRepository.observeAllWithStats(),
                    _searchQuery,
                    _sortField,
                    _sortAscending,
                    _selectedTypes
                ) { platforms, query, sortField, ascending, selectedTypes ->
                    var result = platforms
                    
                    // Apply text search filter
                    if (query.isNotBlank()) {
                        result = result.filter { platform ->
                            platform.name.contains(query, ignoreCase = true) ||
                            platform.domain.contains(query, ignoreCase = true)
                        }
                    }
                    
                    // Apply type filter (empty set = show all types)
                    if (selectedTypes.isNotEmpty()) {
                        result = result.filter { it.type in selectedTypes }
                    }
                    
                    // Apply sorting
                    result = when (sortField) {
                        PlatformSortField.NAME -> {
                            if (ascending) result.sortedBy { it.name.lowercase() }
                            else result.sortedByDescending { it.name.lowercase() }
                        }
                        PlatformSortField.CREDENTIAL_COUNT -> {
                            if (ascending) result.sortedBy { it.credentialCount }
                            else result.sortedByDescending { it.credentialCount }
                        }
                        PlatformSortField.LAST_CREDENTIAL_ADDED -> {
                            if (ascending) result.sortedBy { it.lastCredentialAdded ?: Instant.MIN }
                            else result.sortedByDescending { it.lastCredentialAdded ?: Instant.MIN }
                        }
                        PlatformSortField.CREATED_DATE -> {
                            if (ascending) result.sortedBy { it.createdAt }
                            else result.sortedByDescending { it.createdAt }
                        }
                    }
                    
                    result
                }.collect { filteredPlatforms ->
                    // Calculate total credentials from all platforms (not just filtered)
                    val allPlatformsStats = platformRepository.observeAllWithStats().first()
                    val totalCreds = allPlatformsStats.sumOf { it.credentialCount }
                    
                    // Extract all unique types
                    val allUniqueTypes = allPlatformsStats.map { it.type }.distinct()
                    
                    // Default type keys
                    val defaultTypeKeys = Platform.DEFAULT_TYPES.map { it.first }
                    
                    // Custom types = types not in default list
                    val customTypesList = allUniqueTypes
                        .filter { it !in defaultTypeKeys }
                        .distinct()
                        .sorted()
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            platforms = filteredPlatforms.map { p -> p.toPlatformUiModel() },
                            totalCredentials = totalCreds,
                            totalPlatforms = allPlatformsStats.size,
                            customTypes = customTypesList,
                            allTypes = allUniqueTypes.sorted(),
                            selectedTypes = _selectedTypes.value
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = e.message ?: "Failed to load platforms"
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortFieldChange(field: PlatformSortField) {
        viewModelScope.launch {
            appPreferences.setPlatformSortField(field)
            _sortField.value = field
            _uiState.update { it.copy(sortField = field) }
        }
    }

    fun onSortDirectionChange(ascending: Boolean) {
        viewModelScope.launch {
            appPreferences.setPlatformSortAscending(ascending)
            _sortAscending.value = ascending
            _uiState.update { it.copy(sortAscending = ascending) }
        }
    }

    fun onTypeFilterChange(type: String?) {
        viewModelScope.launch {
            appPreferences.setPlatformTypeFilter(type)
            _typeFilter.value = type
            _uiState.update { it.copy(typeFilter = type) }
        }
    }
    
    /**
     * Toggle a type in/out of the selectedTypes set.
     * Empty set = show all types (no filter).
     */
    fun onTypeToggle(type: String) {
        val current = _selectedTypes.value
        val newSet = if (type in current) {
            current - type
        } else {
            current + type
        }
        _selectedTypes.value = newSet
        _uiState.update { it.copy(selectedTypes = newSet) }
    }
    
    /**
     * Select all types (clear the filter - empty set means all).
     */
    fun onSelectAllTypes() {
        _selectedTypes.value = emptySet()
        _uiState.update { it.copy(selectedTypes = emptySet()) }
    }
    
    /**
     * Deselect all types (set to empty selection which shows nothing).
     * Actually sets selectedTypes to all available types to maintain "all selected" state,
     * then user can deselect individual types.
     */
    fun onDeselectAllTypes() {
        val allTypes = _uiState.value.allTypes.toSet()
        _selectedTypes.value = allTypes
        _uiState.update { it.copy(selectedTypes = allTypes) }
    }

    fun onShowFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = true) }
    }

    fun onDismissFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = false) }
    }

    fun onAddPlatform(name: String, domain: String, type: String) {
        viewModelScope.launch {
            // Normalize inputs for duplicate checking
            val normalizedName = normalizeName(name)
            val normalizedDomain = FaviconCache.toDisplayHost(domain)
            
            // Check for duplicate name
            val existingPlatforms = _uiState.value.platforms
            val duplicateName = existingPlatforms.any { 
                normalizeName(it.name) == normalizedName 
            }
            if (duplicateName) {
                ToastManager.showError("Platform name already exists")
                return@launch
            }
            
            // Check for duplicate domain (only if domain is provided)
            if (normalizedDomain.isNotBlank()) {
                val duplicateDomain = existingPlatforms.any { 
                    FaviconCache.toDisplayHost(it.domain) == normalizedDomain 
                }
                if (duplicateDomain) {
                    ToastManager.showError("Platform domain already exists")
                    return@launch
                }
            }
            
            _uiState.update { it.copy(isLoading = true, showAddDialog = false) }
            
            try {
                val platform = Platform.custom(name = name, domain = domain, type = type)
                platformRepository.create(platform)
                _uiState.update { it.copy(isLoading = false) }
                ToastManager.showSuccess("Platform added successfully")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = e.message ?: "Failed to add platform"
                    )
                }
                ToastManager.showError("Failed to add platform")
            }
        }
    }
    
    /**
     * Normalize platform name for duplicate comparison.
     * - Trim whitespace
     * - Lowercase
     * - Collapse multiple spaces to single space
     */
    private fun normalizeName(name: String): String {
        return name.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    fun onUpdatePlatformName(platformId: String, newName: String) {
        viewModelScope.launch {
            try {
                platformRepository.updateName(platformId, newName)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = e.message ?: "Failed to update platform")
                }
            }
        }
    }

    fun onUpdatePlatformDomain(platformId: String, newDomain: String) {
        viewModelScope.launch {
            try {
                platformRepository.updateDomain(platformId, newDomain)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = e.message ?: "Failed to update platform")
                }
            }
        }
    }

    fun onDeletePlatformRequest(platform: PlatformUiModel) {
        _uiState.update { 
            it.copy(
                showDeleteDialog = true,
                platformToDelete = platform
            )
        }
    }

    fun onDeletePlatformConfirm(pin: String) {
        val platform = _uiState.value.platformToDelete ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deleteError = null) }
            
            try {
                // Verify PIN before deletion (BUG FIX #1: Was previously TODO)
                val pinResult = authRepository.verifyPin(pin)
                if (pinResult is com.motebaya.vaulten.domain.entity.VaultResult.Error) {
                    val errorMessage = when (pinResult.error) {
                        is com.motebaya.vaulten.domain.entity.VaultError.WrongPin -> "Incorrect PIN"
                        is com.motebaya.vaulten.domain.entity.VaultError.TooManyAttempts -> "Too many attempts. Please wait."
                        else -> "PIN verification failed"
                    }
                    _uiState.update { 
                        it.copy(
                            isDeleting = false,
                            deleteError = errorMessage
                        )
                    }
                    return@launch
                }
                
                // PIN verified, proceed with deletion
                platformRepository.delete(platform.id)
                _uiState.update { 
                    it.copy(
                        isDeleting = false,
                        showDeleteDialog = false,
                        platformToDelete = null
                    )
                }
                ToastManager.showSuccess("Platform deleted successfully")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isDeleting = false,
                        deleteError = e.message ?: "Failed to delete platform"
                    )
                }
            }
        }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { 
            it.copy(
                showDeleteDialog = false,
                platformToDelete = null,
                deleteError = null
            )
        }
    }
    
    /**
     * Called when user requests biometric verification for deleting platform.
     */
    fun onBiometricRequestedForDelete() {
        _showBiometricPrompt.value = true
    }
    
    /**
     * Called after successful biometric authentication for deleting platform.
     */
    fun onBiometricSuccessForDelete() {
        _showBiometricPrompt.value = false
        val platform = _uiState.value.platformToDelete ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deleteError = null) }
            
            try {
                // Biometric already verified, proceed with deletion
                platformRepository.delete(platform.id)
                _uiState.update { 
                    it.copy(
                        isDeleting = false,
                        showDeleteDialog = false,
                        platformToDelete = null
                    )
                }
                ToastManager.showSuccess("Platform deleted successfully")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isDeleting = false,
                        deleteError = e.message ?: "Failed to delete platform"
                    )
                }
            }
        }
    }
    
    /**
     * Called when biometric authentication is cancelled.
     */
    fun onBiometricCancelled() {
        _showBiometricPrompt.value = false
    }

    fun onShowAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    // Edit platform handlers
    fun onEditPlatformRequest(platform: PlatformUiModel) {
        _uiState.update { 
            it.copy(
                showEditDialog = true,
                platformToEdit = platform,
                editError = null
            )
        }
    }
    
    fun onDismissEditDialog() {
        _uiState.update { 
            it.copy(
                showEditDialog = false,
                platformToEdit = null,
                editError = null
            )
        }
    }
    
    fun onEditPlatformConfirm(name: String?, domain: String?, type: String?) {
        val platform = _uiState.value.platformToEdit ?: return
        
        viewModelScope.launch {
            // Check for duplicate name (exclude current platform)
            if (name != null && name != platform.name) {
                val normalizedNewName = normalizeName(name)
                val duplicateName = _uiState.value.platforms.any { 
                    it.id != platform.id && normalizeName(it.name) == normalizedNewName 
                }
                if (duplicateName) {
                    _uiState.update { it.copy(editError = "Platform name already exists") }
                    ToastManager.showError("Platform name already exists")
                    return@launch
                }
            }
            
            // Check for duplicate domain (exclude current platform)
            if (domain != null && domain != platform.domain) {
                val normalizedNewDomain = FaviconCache.toDisplayHost(domain)
                if (normalizedNewDomain.isNotBlank()) {
                    val duplicateDomain = _uiState.value.platforms.any { 
                        it.id != platform.id && FaviconCache.toDisplayHost(it.domain) == normalizedNewDomain 
                    }
                    if (duplicateDomain) {
                        _uiState.update { it.copy(editError = "Platform domain already exists") }
                        ToastManager.showError("Platform domain already exists")
                        return@launch
                    }
                }
            }
            
            _uiState.update { it.copy(isEditing = true, editError = null) }
            
            try {
                val result = platformRepository.updatePlatform(
                    id = platform.id,
                    name = if (name != platform.name) name else null,
                    domain = if (domain != platform.domain) domain else null,
                    type = if (type != platform.type) type else null
                )
                
                when (result) {
                    is com.motebaya.vaulten.domain.entity.VaultResult.Success -> {
                        _uiState.update { 
                            it.copy(
                                isEditing = false,
                                showEditDialog = false,
                                platformToEdit = null
                            )
                        }
                        ToastManager.showSuccess("Platform updated successfully")
                    }
                    is com.motebaya.vaulten.domain.entity.VaultResult.Error -> {
                        val errorMessage = when (val error = result.error) {
                            is com.motebaya.vaulten.domain.entity.VaultError.ValidationError -> error.message
                            else -> "Failed to update platform"
                        }
                        _uiState.update { 
                            it.copy(
                                isEditing = false,
                                editError = errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isEditing = false,
                        editError = e.message ?: "Failed to update platform"
                    )
                }
            }
        }
    }
}

/**
 * UI state for the platform screen.
 */
data class PlatformUiState(
    val isLoading: Boolean = false,
    val platforms: List<PlatformUiModel> = emptyList(),
    val searchQuery: String = "",
    val sortField: PlatformSortField = PlatformSortField.NAME,
    val sortAscending: Boolean = true,
    val typeFilter: String? = null,
    // Multi-select type filter. Empty set = all types selected (no filter)
    val selectedTypes: Set<String> = emptySet(),
    val showFilterSheet: Boolean = false,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val platformToDelete: PlatformUiModel? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    // Edit dialog state
    val showEditDialog: Boolean = false,
    val platformToEdit: PlatformUiModel? = null,
    val isEditing: Boolean = false,
    val editError: String? = null,
    // Stats for StatsInfoBar
    val totalCredentials: Int = 0,
    val totalPlatforms: Int = 0,
    // Custom types created by user (for reuse in Add Platform dialog)
    val customTypes: List<String> = emptyList(),
    // All unique types (default + custom) for filter checklist
    val allTypes: List<String> = emptyList()
)

/**
 * UI model for platform display.
 */
data class PlatformUiModel(
    val id: String,
    val name: String,
    val domain: String,
    val color: String,
    val type: String,
    val credentialCount: Int,
    val lastCredentialAdded: Instant?,
    val lastNameEditAt: Instant?,
    val isCustom: Boolean,
    val createdAt: Instant
) {
    /**
     * Sanitized domain for display (removes protocol, www, trailing slash, paths).
     * e.g., "https://www.ilovepdf.com/" becomes "ilovepdf.com"
     */
    val sanitizedDomain: String
        get() = domain
            .trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .removeSuffix("/")
            .takeWhile { it != '/' }  // Remove path
    
    /**
     * Check if name can be edited (24h since last edit, or never edited).
     */
    val canEditName: Boolean
        get() {
            val lastEdit = lastNameEditAt ?: return true
            val hoursSinceEdit = java.time.Duration.between(lastEdit, Instant.now()).toHours()
            return hoursSinceEdit >= 24
        }
    
    /**
     * Hours remaining until name can be edited again.
     */
    val hoursUntilNameEditable: Long
        get() {
            val lastEdit = lastNameEditAt ?: return 0
            val hoursSinceEdit = java.time.Duration.between(lastEdit, Instant.now()).toHours()
            return maxOf(0, 24 - hoursSinceEdit)
        }
}

/**
 * Extension to convert domain Platform to UI model.
 */
private fun Platform.toPlatformUiModel(): PlatformUiModel {
    return PlatformUiModel(
        id = id,
        name = name,
        domain = domain,
        color = color,
        type = type,
        credentialCount = credentialCount,
        lastCredentialAdded = lastCredentialAdded,
        lastNameEditAt = lastNameEditAt,
        isCustom = isCustom,
        createdAt = createdAt
    )
}
