package com.motebaya.vaulten.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.CredentialType
import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.domain.repository.CredentialRepository
import com.motebaya.vaulten.domain.repository.PlatformRepository
import com.motebaya.vaulten.data.cache.FaviconCache
import com.motebaya.vaulten.presentation.components.ToastManager
import com.motebaya.vaulten.presentation.components.credential.ViewCredentialState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Sort options for credentials list.
 */
enum class CredentialSortOption(val displayName: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    NEWEST("Newest First"),
    OLDEST("Oldest First")
}

/**
 * Filter options for credentials list.
 */
enum class CredentialFilterOption(val displayName: String) {
    ALL("All"),
    STANDARD("Standard"),
    SOCIAL("Social"),
    WALLET("Wallet"),
    GOOGLE("Google")
}

/**
 * Platform filter item for multi-select.
 */
data class PlatformFilterItem(
    val id: String,
    val name: String,
    val credentialCount: Int,
    val isSelected: Boolean = false
)

/**
 * Date range for filtering.
 */
data class DateRange(
    val startMillis: Long? = null,
    val endMillis: Long? = null
) {
    val hasRange: Boolean get() = startMillis != null || endMillis != null
    
    fun contains(instant: Instant): Boolean {
        val millis = instant.toEpochMilli()
        val afterStart = startMillis?.let { millis >= it } ?: true
        val beforeEnd = endMillis?.let { millis <= it } ?: true
        return afterStart && beforeEnd
    }
}

/**
 * ViewModel for the dashboard screen.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val platformRepository: PlatformRepository,
    private val authRepository: AuthRepository,
    val faviconCache: FaviconCache,
    private val keystoreManager: com.motebaya.vaulten.security.keystore.KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val filterOption = MutableStateFlow(CredentialFilterOption.ALL)
    private val sortOption = MutableStateFlow(CredentialSortOption.NAME_ASC)
    private val selectedPlatformIds = MutableStateFlow<Set<String>>(emptySet())
    private val dateRange = MutableStateFlow(DateRange())
    
    // ViewCredentialModal state
    private val _viewCredentialState = MutableStateFlow<ViewCredentialState?>(null)
    val viewCredentialState: StateFlow<ViewCredentialState?> = _viewCredentialState.asStateFlow()
    
    // Delete credential state
    private val _deleteCredentialState = MutableStateFlow<DeleteCredentialState?>(null)
    val deleteCredentialState: StateFlow<DeleteCredentialState?> = _deleteCredentialState.asStateFlow()
    
    // Biometric availability
    private val _biometricAvailable = MutableStateFlow(false)
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable.asStateFlow()
    
    // Biometric prompt trigger
    private val _showBiometricPrompt = MutableStateFlow<BiometricPromptType?>(null)
    val showBiometricPrompt: StateFlow<BiometricPromptType?> = _showBiometricPrompt.asStateFlow()
    
    /**
     * Expose KeystoreManager for BiometricAuthHelper in the UI layer.
     */
    fun getKeystoreManager() = keystoreManager
    
    // Edit credential state
    private val _editCredentialState = MutableStateFlow<EditCredentialState?>(null)
    val editCredentialState: StateFlow<EditCredentialState?> = _editCredentialState.asStateFlow()
    
    private var selectedCredentialId: String? = null
    private var countdownJob: Job? = null
    private var editCooldownJob: Job? = null

    init {
        loadCredentials()
        checkBiometricAvailability()
    }
    
    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            _biometricAvailable.value = authRepository.isBiometricEnabled() &&
                    authRepository.isBiometricAvailable()
        }
    }

    private fun loadCredentials() {
        viewModelScope.launch {
            combine(
                credentialRepository.getAllCredentials(),
                platformRepository.getAllPlatforms(),
                searchQuery,
                filterOption,
                sortOption,
                selectedPlatformIds,
                dateRange
            ) { params ->
                val credentialsResult = params[0] as VaultResult<List<Credential>>
                @Suppress("UNCHECKED_CAST")
                val platforms = params[1] as List<Platform>
                val query = params[2] as String
                val filter = params[3] as CredentialFilterOption
                val sort = params[4] as CredentialSortOption
                @Suppress("UNCHECKED_CAST")
                val platformIds = params[5] as Set<String>
                val range = params[6] as DateRange
                
                when (credentialsResult) {
                    is VaultResult.Success -> {
                        val platformMap = platforms.associateBy { it.id }
                        val allCredentials = credentialsResult.data
                        
                        // Build platform filter items with credential counts
                        val platformCounts = allCredentials.groupingBy { it.platformId }.eachCount()
                        val platformFilterItems = platforms.map { platform ->
                            PlatformFilterItem(
                                id = platform.id,
                                name = platform.name,
                                credentialCount = platformCounts[platform.id] ?: 0,
                                isSelected = platformIds.contains(platform.id)
                            )
                        }.filter { it.credentialCount > 0 }
                            .sortedByDescending { it.credentialCount }
                        
                        // Apply search filter
                        var filtered = if (query.isBlank()) {
                            allCredentials
                        } else {
                            allCredentials.filter { cred ->
                                val platform = platformMap[cred.platformId]
                                cred.username.contains(query, ignoreCase = true) ||
                                        cred.email?.contains(query, ignoreCase = true) == true ||
                                        platform?.name?.contains(query, ignoreCase = true) == true
                            }
                        }
                        
                        // Apply type filter
                        filtered = when (filter) {
                            CredentialFilterOption.ALL -> filtered
                            CredentialFilterOption.STANDARD -> filtered.filter { it.credentialType == CredentialType.STANDARD }
                            CredentialFilterOption.SOCIAL -> filtered.filter { it.credentialType == CredentialType.SOCIAL }
                            CredentialFilterOption.WALLET -> filtered.filter { it.credentialType == CredentialType.WALLET }
                            CredentialFilterOption.GOOGLE -> filtered.filter { it.credentialType == CredentialType.GOOGLE }
                        }
                        
                        // Apply platform filter (if any platforms selected)
                        if (platformIds.isNotEmpty()) {
                            filtered = filtered.filter { platformIds.contains(it.platformId) }
                        }
                        
                        // Apply date range filter
                        if (range.hasRange) {
                            filtered = filtered.filter { range.contains(it.createdAt) }
                        }
                        
                        // Apply sort
                        val sorted = when (sort) {
                            CredentialSortOption.NAME_ASC -> filtered.sortedBy { 
                                platformMap[it.platformId]?.name?.lowercase() ?: "" 
                            }
                            CredentialSortOption.NAME_DESC -> filtered.sortedByDescending { 
                                platformMap[it.platformId]?.name?.lowercase() ?: "" 
                            }
                            CredentialSortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
                            CredentialSortOption.OLDEST -> filtered.sortedBy { it.createdAt }
                        }
                        
                        val uiModels = sorted.map { cred ->
                            val platform = platformMap[cred.platformId]
                            
                            // Fix Google credential display: use accountName as primary, email as secondary
                            val displayUsername = when (cred.credentialType) {
                                CredentialType.GOOGLE -> cred.accountName ?: cred.username
                                CredentialType.WALLET -> cred.accountName ?: cred.username
                                else -> cred.username
                            }
                            
                            CredentialUiModel(
                                id = cred.id,
                                platformName = platform?.name ?: "Unknown",
                                platformColor = platform?.color ?: "#6B7280",
                                platformDomain = platform?.domain ?: "",
                                username = displayUsername,
                                email = cred.email,
                                notes = cred.notes,
                                credentialType = cred.credentialType,
                                createdAt = cred.createdAt,
                                lastEditedAt = cred.lastEditedAt
                            )
                        }
                        FilteredResult(
                            credentials = uiModels,
                            totalCredentials = allCredentials.size,
                            platformCount = platforms.size,
                            filter = filter,
                            sort = sort,
                            platformFilterItems = platformFilterItems,
                            dateRange = range
                        )
                    }
                    is VaultResult.Error -> FilteredResult(
                        credentials = emptyList(),
                        totalCredentials = 0,
                        platformCount = 0,
                        filter = filter,
                        sort = sort,
                        platformFilterItems = emptyList(),
                        dateRange = range
                    )
                }
            }.collect { result ->
                _uiState.update {
                    it.copy(
                        credentials = result.credentials,
                        isLoading = false,
                        totalCredentials = result.totalCredentials,
                        platformCount = result.platformCount,
                        selectedFilter = result.filter,
                        selectedSort = result.sort,
                        platformFilterItems = result.platformFilterItems,
                        dateRange = result.dateRange
                    )
                }
            }
        }
    }
    
    private data class FilteredResult(
        val credentials: List<CredentialUiModel>,
        val totalCredentials: Int,
        val platformCount: Int,
        val filter: CredentialFilterOption,
        val sort: CredentialSortOption,
        val platformFilterItems: List<PlatformFilterItem>,
        val dateRange: DateRange
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun onFilterChange(filter: CredentialFilterOption) {
        filterOption.value = filter
    }
    
    fun onSortChange(sort: CredentialSortOption) {
        sortOption.value = sort
    }
    
    fun onPlatformFilterToggle(platformId: String) {
        selectedPlatformIds.update { current ->
            if (current.contains(platformId)) {
                current - platformId
            } else {
                current + platformId
            }
        }
    }
    
    fun onClearPlatformFilters() {
        selectedPlatformIds.value = emptySet()
    }
    
    fun onDateRangeChange(startMillis: Long?, endMillis: Long?) {
        dateRange.value = DateRange(startMillis, endMillis)
    }
    
    fun onClearDateRange() {
        dateRange.value = DateRange()
    }
    
    fun onShowPlatformFilterSheet() {
        _uiState.update { it.copy(showPlatformFilterSheet = true) }
    }
    
    fun onDismissPlatformFilterSheet() {
        _uiState.update { it.copy(showPlatformFilterSheet = false) }
    }
    
    fun onShowDateRangePicker() {
        _uiState.update { it.copy(showDateRangePicker = true) }
    }
    
    fun onDismissDateRangePicker() {
        _uiState.update { it.copy(showDateRangePicker = false) }
    }
    
    // Credential deletion with PIN
    
    fun onDeleteCredentialRequest(credentialId: String) {
        _deleteCredentialState.value = DeleteCredentialState.PinRequired(credentialId)
    }
    
    fun onDeleteCredentialConfirm(pin: String) {
        val state = _deleteCredentialState.value
        // Allow retry from Error state as well as initial PinRequired state
        val credentialId = when (state) {
            is DeleteCredentialState.PinRequired -> state.credentialId
            is DeleteCredentialState.Error -> state.credentialId
            else -> return
        }
        
        viewModelScope.launch {
            _deleteCredentialState.value = DeleteCredentialState.Deleting(credentialId)
            
            when (val pinResult = authRepository.verifyPin(pin)) {
                is VaultResult.Success -> {
                    when (val deleteResult = credentialRepository.deleteCredential(credentialId)) {
                        is VaultResult.Success -> {
                            _deleteCredentialState.value = null
                            ToastManager.showSuccess("Credential deleted successfully")
                        }
                        is VaultResult.Error -> {
                            _deleteCredentialState.value = DeleteCredentialState.Error(
                                credentialId,
                                deleteResult.error.message ?: "Failed to delete credential"
                            )
                        }
                    }
                }
                is VaultResult.Error -> {
                    _deleteCredentialState.value = DeleteCredentialState.Error(
                        credentialId,
                        "Invalid PIN"
                    )
                }
            }
        }
    }
    
    /**
     * Called when user requests biometric verification for deleting credential.
     */
    fun onBiometricRequestedForDelete() {
        _showBiometricPrompt.value = BiometricPromptType.DeleteCredential
    }
    
    /**
     * Called after successful biometric authentication for deleting credential.
     */
    fun onBiometricSuccessForDelete() {
        _showBiometricPrompt.value = null
        val state = _deleteCredentialState.value
        val credentialId = when (state) {
            is DeleteCredentialState.PinRequired -> state.credentialId
            is DeleteCredentialState.Error -> state.credentialId
            else -> return
        }
        
        viewModelScope.launch {
            _deleteCredentialState.value = DeleteCredentialState.Deleting(credentialId)
            
            when (val deleteResult = credentialRepository.deleteCredential(credentialId)) {
                is VaultResult.Success -> {
                    _deleteCredentialState.value = null
                    ToastManager.showSuccess("Credential deleted successfully")
                }
                is VaultResult.Error -> {
                    _deleteCredentialState.value = DeleteCredentialState.Error(
                        credentialId,
                        deleteResult.error.message ?: "Failed to delete credential"
                    )
                }
            }
        }
    }
    
    fun onDismissDeleteCredential() {
        _deleteCredentialState.value = null
    }
    
    // ViewCredentialModal methods
    
    /**
     * Called when user double-taps a credential to view it securely.
     */
    fun onViewCredential(credentialId: String) {
        selectedCredentialId = credentialId
        _viewCredentialState.value = ViewCredentialState.PinRequired
    }
    
    /**
     * Called when user submits PIN in the ViewCredentialModal.
     */
    fun onVerifyPinForView(pin: String) {
        val credentialId = selectedCredentialId ?: return
        
        viewModelScope.launch {
            _viewCredentialState.value = ViewCredentialState.Verifying
            
            when (val pinResult = authRepository.verifyPin(pin)) {
                is VaultResult.Success -> {
                    // PIN verified, fetch and decrypt credential
                    when (val credResult = credentialRepository.getCredentialById(credentialId)) {
                        is VaultResult.Success -> {
                            val credential = credResult.data
                            if (credential != null) {
                                startViewingCredential(credential)
                            } else {
                                _viewCredentialState.value = ViewCredentialState.Error("Credential not found")
                            }
                        }
                        is VaultResult.Error -> {
                            _viewCredentialState.value = ViewCredentialState.Error(
                                credResult.error.message ?: "Failed to load credential"
                            )
                        }
                    }
                }
                is VaultResult.Error -> {
                    _viewCredentialState.value = ViewCredentialState.Error(
                        pinResult.error.message ?: "Invalid PIN"
                    )
                }
            }
        }
    }
    
    /**
     * Called when user requests biometric verification for viewing credential.
     */
    fun onBiometricRequestedForView() {
        _showBiometricPrompt.value = BiometricPromptType.ViewCredential
    }
    
    /**
     * Called after successful biometric authentication for viewing credential.
     */
    fun onBiometricSuccessForView() {
        _showBiometricPrompt.value = null
        val credentialId = selectedCredentialId ?: return
        
        viewModelScope.launch {
            _viewCredentialState.value = ViewCredentialState.Verifying
            
            // Biometric already verified, just fetch the credential
            when (val credResult = credentialRepository.getCredentialById(credentialId)) {
                is VaultResult.Success -> {
                    val credential = credResult.data
                    if (credential != null) {
                        startViewingCredential(credential)
                    } else {
                        _viewCredentialState.value = ViewCredentialState.Error("Credential not found")
                    }
                }
                is VaultResult.Error -> {
                    _viewCredentialState.value = ViewCredentialState.Error(
                        credResult.error.message ?: "Failed to load credential"
                    )
                }
            }
        }
    }
    
    /**
     * Called when biometric authentication fails or is cancelled.
     */
    fun onBiometricCancelled() {
        _showBiometricPrompt.value = null
    }
    
    private fun startViewingCredential(credential: Credential) {
        // Start with 5 minutes (300 seconds)
        _viewCredentialState.value = ViewCredentialState.Viewing(
            credential = credential,
            remainingSeconds = 300
        )
        
        // Start countdown timer
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = 300
            while (remaining > 0) {
                delay(1000)
                remaining--
                val currentState = _viewCredentialState.value
                if (currentState is ViewCredentialState.Viewing) {
                    _viewCredentialState.value = currentState.copy(remainingSeconds = remaining)
                } else {
                    break // Modal was closed
                }
            }
            // Auto-close after timeout
            dismissViewCredential()
        }
    }
    
    /**
     * Called when the ViewCredentialModal is dismissed.
     */
    fun dismissViewCredential() {
        countdownJob?.cancel()
        countdownJob = null
        selectedCredentialId = null
        _viewCredentialState.value = null
    }
    
    // Edit credential methods
    
    /**
     * Called when user requests to edit a credential via 3-dot menu.
     * Checks 24h cooldown before allowing edit.
     */
    fun onEditCredentialRequest(credentialId: String) {
        viewModelScope.launch {
            when (val result = credentialRepository.getCredentialById(credentialId)) {
                is VaultResult.Success -> {
                    val credential = result.data
                    if (credential == null) {
                        ToastManager.showError("Credential not found")
                        return@launch
                    }
                    
                    // Check 24h cooldown
                    val lastEdited = credential.lastEditedAt
                    if (lastEdited != null) {
                        val now = Instant.now()
                        val hoursSinceEdit = java.time.Duration.between(lastEdited, now).toHours()
                        if (hoursSinceEdit < 24) {
                            // Still in cooldown - show countdown modal
                            val remainingMillis = java.time.Duration.ofHours(24).toMillis() - 
                                java.time.Duration.between(lastEdited, now).toMillis()
                            startEditCooldownCountdown(credentialId, remainingMillis, lastEdited)
                            return@launch
                        }
                    }
                    
                    // No cooldown - show edit modal
                    _editCredentialState.value = EditCredentialState.Editing(credential)
                }
                is VaultResult.Error -> {
                    ToastManager.showError("Failed to load credential")
                }
            }
        }
    }
    
    private fun startEditCooldownCountdown(credentialId: String, remainingMillis: Long, lastEditedAt: Instant?) {
        _editCredentialState.value = EditCredentialState.Cooldown(
            credentialId = credentialId,
            remainingSeconds = (remainingMillis / 1000).toInt(),
            lastEditedAt = lastEditedAt
        )
        
        editCooldownJob?.cancel()
        editCooldownJob = viewModelScope.launch {
            var remaining = (remainingMillis / 1000).toInt()
            while (remaining > 0) {
                delay(1000)
                remaining--
                val currentState = _editCredentialState.value
                if (currentState is EditCredentialState.Cooldown) {
                    _editCredentialState.value = currentState.copy(remainingSeconds = remaining)
                } else {
                    break
                }
            }
            // Cooldown finished - auto dismiss
            _editCredentialState.value = null
            ToastManager.showSuccess("Cooldown finished. You can now edit the credential.")
        }
    }
    
    /**
     * Called when user saves edited credential.
     */
    fun onSaveEditedCredential(credential: Credential) {
        viewModelScope.launch {
            _editCredentialState.value = EditCredentialState.Saving(credential)
            
            // Update lastEditedAt to now
            val updatedCredential = credential.copy(
                lastEditedAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            when (val result = credentialRepository.saveCredential(updatedCredential)) {
                is VaultResult.Success -> {
                    _editCredentialState.value = null
                    ToastManager.showSuccess("Credential updated successfully")
                }
                is VaultResult.Error -> {
                    _editCredentialState.value = EditCredentialState.Error(
                        credential = credential,
                        message = result.error.message ?: "Failed to save credential"
                    )
                }
            }
        }
    }
    
    /**
     * Called when edit modal is dismissed.
     */
    fun dismissEditCredential() {
        editCooldownJob?.cancel()
        editCooldownJob = null
        _editCredentialState.value = null
    }
}

/**
 * UI state for the dashboard screen.
 */
data class DashboardUiState(
    val credentials: List<CredentialUiModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val totalCredentials: Int = 0,
    val platformCount: Int = 0,
    val selectedFilter: CredentialFilterOption = CredentialFilterOption.ALL,
    val selectedSort: CredentialSortOption = CredentialSortOption.NAME_ASC,
    val platformFilterItems: List<PlatformFilterItem> = emptyList(),
    val dateRange: DateRange = DateRange(),
    val showPlatformFilterSheet: Boolean = false,
    val showDateRangePicker: Boolean = false
)

/**
 * State for credential deletion with PIN verification.
 */
sealed class DeleteCredentialState {
    data class PinRequired(val credentialId: String) : DeleteCredentialState()
    data class Deleting(val credentialId: String) : DeleteCredentialState()
    data class Error(val credentialId: String, val message: String) : DeleteCredentialState()
}

/**
 * Types of biometric prompt actions.
 */
enum class BiometricPromptType {
    ViewCredential,
    DeleteCredential
}

/**
 * State for credential editing with 24h cooldown.
 */
sealed class EditCredentialState {
    /** Showing edit form with credential data */
    data class Editing(val credential: Credential) : EditCredentialState()
    /** Saving the edited credential */
    data class Saving(val credential: Credential) : EditCredentialState()
    /** Error saving credential */
    data class Error(val credential: Credential, val message: String) : EditCredentialState()
    /** Cooldown active - cannot edit yet */
    data class Cooldown(
        val credentialId: String, 
        val remainingSeconds: Int,
        val lastEditedAt: Instant? = null
    ) : EditCredentialState()
}
