package com.motebaya.vaulten.presentation.screens.importpinsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.security.keystore.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Import PIN Setup.
 */
data class ImportPinSetupUiState(
    val pin: String = "",
    val confirmPin: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val biometricAvailable: Boolean = false,
    val showBiometricOption: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val biometricError: String? = null
)

/**
 * ViewModel for PIN setup after importing a vault.
 * 
 * This is a simplified version of SetupViewModel that only handles:
 * 1. PIN creation
 * 2. Optional biometric enrollment
 */
@HiltViewModel
class ImportPinSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportPinSetupUiState())
    val uiState: StateFlow<ImportPinSetupUiState> = _uiState.asStateFlow()

    init {
        checkBiometricAvailability()
    }
    
    fun getKeystoreManager(): KeystoreManager = keystoreManager

    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            val available = authRepository.isBiometricAvailable()
            _uiState.update { it.copy(biometricAvailable = available) }
        }
    }

    fun onPinChange(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _uiState.update { 
                it.copy(
                    pin = pin,
                    errorMessage = null
                )
            }
        }
    }

    fun onConfirmPinChange(confirmPin: String) {
        if (confirmPin.length <= 6 && confirmPin.all { it.isDigit() }) {
            _uiState.update { 
                it.copy(
                    confirmPin = confirmPin,
                    errorMessage = null
                )
            }
        }
    }

    fun isPinValid(): Boolean {
        val state = _uiState.value
        return state.pin.length == 6 && 
               state.confirmPin.length == 6 && 
               state.pin == state.confirmPin
    }

    fun onPinConfirmed() {
        val state = _uiState.value
        
        if (state.pin.length != 6) {
            _uiState.update { it.copy(errorMessage = "PIN must be 6 digits") }
            return
        }
        
        if (state.pin != state.confirmPin) {
            _uiState.update { it.copy(errorMessage = "PINs do not match") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            when (val result = authRepository.setupPin(state.pin)) {
                is VaultResult.Success -> {
                    if (_uiState.value.biometricAvailable) {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                showBiometricOption = true
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isComplete = true
                            )
                        }
                    }
                }
                is VaultResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to set PIN: ${result.error.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * User requested to enable biometric - show the biometric prompt first.
     */
    fun onEnableBiometricRequested() {
        _uiState.update { 
            it.copy(
                showBiometricPrompt = true,
                biometricError = null
            )
        }
    }
    
    /**
     * Biometric verification succeeded - now enable biometric.
     */
    fun onBiometricVerified() {
        viewModelScope.launch {
            _uiState.update { it.copy(showBiometricPrompt = false) }
            authRepository.enableBiometric()
            _uiState.update { it.copy(isComplete = true) }
        }
    }
    
    /**
     * Biometric verification failed.
     */
    fun onBiometricFailed(errorMessage: String) {
        _uiState.update { 
            it.copy(
                showBiometricPrompt = false,
                biometricError = errorMessage
            )
        }
    }
    
    /**
     * Biometric verification cancelled by user.
     */
    fun onBiometricCancelled() {
        _uiState.update { 
            it.copy(
                showBiometricPrompt = false,
                biometricError = null
            )
        }
    }

    fun onSkipBiometric() {
        _uiState.update { it.copy(isComplete = true) }
    }
}
