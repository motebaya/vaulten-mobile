package com.motebaya.vaulten.presentation.screens.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.data.cache.FaviconCache
import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.CredentialType
import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.CredentialRepository
import com.motebaya.vaulten.domain.repository.PlatformRepository
import com.motebaya.vaulten.presentation.components.ToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for adding/editing credentials.
 */
@HiltViewModel
class AddCredentialViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val platformRepository: PlatformRepository,
    val faviconCache: FaviconCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCredentialUiState())
    val uiState: StateFlow<AddCredentialUiState> = _uiState.asStateFlow()

    init {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            platformRepository.observeAll()
                .collect { platforms ->
                    _uiState.update { 
                        it.copy(
                            platforms = platforms,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onPlatformSelected(platformId: String) {
        val platform = _uiState.value.platforms.find { it.id == platformId }
        val detectedType = platform?.let { 
            CredentialType.detectFromPlatform(it.name)
        } ?: CredentialType.STANDARD

        _uiState.update { 
            it.copy(
                selectedPlatformId = platformId,
                selectedPlatform = platform,
                credentialType = detectedType
            )
        }
    }

    fun onCredentialTypeSelected(type: CredentialType) {
        _uiState.update { it.copy(credentialType = type) }
    }

    fun onSaveCredential(
        username: String,
        password: String,
        email: String = "",
        notes: String = "",
        phoneNumber: String = "",
        recoveryEmail: String = "",
        twoFaEnabled: Boolean = false,
        backupCodes: String = "",
        accountName: String = "",
        privateKey: String = "",
        seedPhrase: String = "",
        birthdate: String = ""
    ) {
        val platformId = _uiState.value.selectedPlatformId
        if (platformId == null) {
            _uiState.update { it.copy(error = "Please select a platform") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val credential = Credential(
                platformId = platformId,
                username = username,
                password = password,
                notes = notes,
                email = email.takeIf { it.isNotBlank() },
                credentialType = _uiState.value.credentialType,
                phoneNumber = phoneNumber.takeIf { it.isNotBlank() },
                backupEmail = recoveryEmail.takeIf { it.isNotBlank() },
                recoveryCodes = backupCodes.takeIf { it.isNotBlank() },
                twoFaEnabled = twoFaEnabled,
                birthdate = birthdate.takeIf { it.isNotBlank() },
                privateKey = privateKey.takeIf { it.isNotBlank() },
                seedPhrase = seedPhrase.takeIf { it.isNotBlank() },
                accountName = accountName.takeIf { it.isNotBlank() }
            )

            when (val result = credentialRepository.saveCredential(credential)) {
                is VaultResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            saveSuccess = true
                        )
                    }
                    ToastManager.showSuccess("Credential saved successfully")
                }
                is VaultResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            error = result.error.message
                        )
                    }
                    ToastManager.showError("Failed to save credential")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetState() {
        _uiState.update { 
            AddCredentialUiState(
                platforms = it.platforms
            )
        }
    }
}

/**
 * UI state for add credential screen.
 */
data class AddCredentialUiState(
    val isLoading: Boolean = true,
    val platforms: List<Platform> = emptyList(),
    val selectedPlatformId: String? = null,
    val selectedPlatform: Platform? = null,
    val credentialType: CredentialType = CredentialType.STANDARD,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)
