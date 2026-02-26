package com.motebaya.vaulten.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.presentation.components.ToastManager
import com.motebaya.vaulten.security.keystore.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    fun getKeystoreManager(): KeystoreManager = keystoreManager

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val biometricAvailable = authRepository.isBiometricAvailable()
            val biometricEnabled = authRepository.isBiometricEnabled()
            
            _uiState.update {
                it.copy(
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = biometricEnabled
                )
            }
        }
    }

    /**
     * Called when user wants to enable biometric - show PIN verification first.
     */
    fun onRequestBiometricEnable() {
        _uiState.update { 
            it.copy(
                showPinVerificationForBiometric = true,
                pinVerificationError = null
            )
        }
    }

    /**
     * Verify PIN before enabling biometric.
     * After PIN verification succeeds, we need to show biometric prompt.
     */
    fun onVerifyPinForBiometric(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPinVerifying = true, pinVerificationError = null) }
            
            when (val result = authRepository.verifyPin(pin)) {
                is VaultResult.Success -> {
                    // PIN verified, now request biometric verification
                    _uiState.update { 
                        it.copy(
                            isPinVerifying = false,
                            showPinVerificationForBiometric = false,
                            showBiometricPromptForEnable = true
                        )
                    }
                }
                is VaultResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isPinVerifying = false,
                            pinVerificationError = "Incorrect PIN"
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Biometric verification succeeded - now actually enable biometric.
     */
    fun onBiometricVerifiedForEnable() {
        viewModelScope.launch {
            _uiState.update { it.copy(showBiometricPromptForEnable = false) }
            
            when (val biometricResult = authRepository.enableBiometric()) {
                is VaultResult.Success -> {
                    _uiState.update { 
                        it.copy(biometricEnabled = true)
                    }
                    ToastManager.showSuccess("Biometric unlock enabled")
                }
                is VaultResult.Error -> {
                    ToastManager.showError(biometricResult.error.message)
                }
            }
        }
    }
    
    /**
     * Biometric verification failed for enable.
     */
    fun onBiometricFailedForEnable(errorMessage: String) {
        _uiState.update { 
            it.copy(
                showBiometricPromptForEnable = false,
                biometricEnableError = errorMessage
            )
        }
        ToastManager.showError("Biometric verification failed: $errorMessage")
    }
    
    /**
     * Biometric verification cancelled for enable.
     */
    fun onBiometricCancelledForEnable() {
        _uiState.update { 
            it.copy(
                showBiometricPromptForEnable = false,
                biometricEnableError = null
            )
        }
    }

    /**
     * Dismiss PIN verification dialog.
     */
    fun onDismissPinVerification() {
        _uiState.update { 
            it.copy(
                showPinVerificationForBiometric = false,
                pinVerificationError = null,
                isPinVerifying = false
            )
        }
    }

    fun onBiometricToggle(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // This path should not be called directly - use onRequestBiometricEnable
                onRequestBiometricEnable()
            } else {
                authRepository.disableBiometric()
                _uiState.update { it.copy(biometricEnabled = false) }
                ToastManager.showSuccess("Biometric unlock disabled")
            }
        }
    }
}

/**
 * UI state for the settings screen.
 */
data class SettingsUiState(
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutMinutes: Int = 5,
    // PIN verification for biometric
    val showPinVerificationForBiometric: Boolean = false,
    val isPinVerifying: Boolean = false,
    val pinVerificationError: String? = null,
    // Biometric prompt for enable
    val showBiometricPromptForEnable: Boolean = false,
    val biometricEnableError: String? = null
)
