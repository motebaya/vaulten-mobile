package com.motebaya.vaulten.presentation.screens.resetpin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.data.session.SessionManager
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.domain.repository.VaultRepository
import com.motebaya.vaulten.security.crypto.DekManager
import com.motebaya.vaulten.security.crypto.KdfService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for PIN reset flow.
 * 
 * SECURITY: 
 * - Passphrase verification now properly derives KEK and unwraps DEK
 * - DEK is held temporarily in memory during reset process
 * - DEK is re-wrapped with device KEK after new PIN is set
 * - All sensitive data is cleared after completion or cancellation
 */
@HiltViewModel
class ResetPinViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val vaultRepository: VaultRepository,
    private val dekManager: DekManager,
    private val kdfService: KdfService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPinUiState())
    val uiState: StateFlow<ResetPinUiState> = _uiState.asStateFlow()

    // SECURITY: DEK stored temporarily as ByteArray for secure clearing
    private var tempDek: ByteArray? = null

    /**
     * Verify the passphrase can derive KEK and unwrap DEK.
     * 
     * SECURITY: This properly verifies the passphrase by attempting
     * to unwrap the DEK. The unwrapped DEK is stored temporarily
     * for re-wrapping after the new PIN is set.
     */
    fun verifyPassphrase(passphrase: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, passphraseError = null) }

            try {
                // Validate passphrase format
                val words = passphrase.trim().split("\\s+".toRegex())
                if (words.size != 12 && words.size != 24) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            passphraseError = "Passphrase must be 12 or 24 words"
                        )
                    }
                    return@launch
                }

                // Verify passphrase and get unwrapped DEK
                val dekResult = vaultRepository.verifyPassphraseAndGetDek(passphrase)
                
                when (dekResult) {
                    is VaultResult.Success -> {
                        // Store DEK temporarily for re-wrapping after PIN reset
                        tempDek = dekResult.data
                        
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentStep = ResetPinStep.NEW_PIN,
                                passphraseVerified = true
                            )
                        }
                    }
                    is VaultResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                passphraseError = "Invalid passphrase. Please check and try again."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        passphraseError = e.message ?: "Failed to verify passphrase"
                    )
                }
            }
        }
    }

    /**
     * Set the new PIN after passphrase verification.
     * 
     * SECURITY: This re-wraps the DEK with the device KEK after
     * setting the new PIN hash, ensuring the DEK can be unwrapped
     * on next unlock.
     */
    fun setNewPin(newPin: String, confirmPin: String) {
        val dek = tempDek
        if (dek == null) {
            _uiState.update {
                it.copy(pinError = "Session expired. Please start over.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pinError = null) }

            try {
                // Validate PIN
                if (newPin.length != 6) {
                    _uiState.update {
                        it.copy(isLoading = false, pinError = "PIN must be 6 digits")
                    }
                    return@launch
                }

                if (newPin != confirmPin) {
                    _uiState.update {
                        it.copy(isLoading = false, pinError = "PINs do not match")
                    }
                    return@launch
                }

                if (!newPin.all { it.isDigit() }) {
                    _uiState.update {
                        it.copy(isLoading = false, pinError = "PIN must contain only digits")
                    }
                    return@launch
                }

                // Reset the PIN (stores new hash and regenerates device KEK if needed)
                when (val result = authRepository.setupPin(newPin)) {
                    is VaultResult.Success -> {
                        // Re-wrap DEK with the (possibly new) device KEK
                        val wrappedDekDevice = dekManager.wrapDekForDevice(dek)
                        
                        if (wrappedDekDevice == null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    pinError = "Failed to secure vault with new PIN"
                                )
                            }
                            return@launch
                        }
                        
                        // Store the new device-wrapped DEK
                        val storeResult = vaultRepository.storeWrappedDekDevice(wrappedDekDevice)
                        
                        if (storeResult is VaultResult.Error) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    pinError = "Failed to save vault configuration"
                                )
                            }
                            return@launch
                        }
                        
                        // Clear sensitive data
                        clearSensitiveData()
                        
                        // BUG FIX #2: Lock the vault after PIN reset
                        // This ensures the session state transitions from Locked -> Unlocked
                        // when the user enters their new PIN, triggering the navigation
                        // LaunchedEffect in VaultNavHost correctly.
                        sessionManager.lockVault()
                        
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentStep = ResetPinStep.SUCCESS
                            )
                        }
                    }
                    is VaultResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pinError = result.error.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pinError = e.message ?: "Failed to reset PIN"
                    )
                }
            }
        }
    }

    /**
     * Go back to passphrase step.
     */
    fun goBack() {
        when (_uiState.value.currentStep) {
            ResetPinStep.NEW_PIN -> {
                clearSensitiveData()
                _uiState.update {
                    it.copy(
                        currentStep = ResetPinStep.PASSPHRASE,
                        passphraseVerified = false
                    )
                }
            }
            else -> { /* No action */ }
        }
    }

    /**
     * Clear all sensitive data from memory.
     */
    private fun clearSensitiveData() {
        tempDek?.fill(0)
        tempDek = null
    }

    override fun onCleared() {
        clearSensitiveData()
        super.onCleared()
    }
}

/**
 * Steps in the PIN reset flow.
 */
enum class ResetPinStep {
    PASSPHRASE,
    NEW_PIN,
    SUCCESS
}

/**
 * UI state for PIN reset screen.
 */
data class ResetPinUiState(
    val currentStep: ResetPinStep = ResetPinStep.PASSPHRASE,
    val isLoading: Boolean = false,
    val passphraseVerified: Boolean = false,
    val passphraseError: String? = null,
    val pinError: String? = null
)
