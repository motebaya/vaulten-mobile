package com.motebaya.vaulten.presentation.screens.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.domain.repository.VaultRepository
import com.motebaya.vaulten.security.crypto.Bip39Generator
import com.motebaya.vaulten.security.keystore.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SetupViewModel"

/**
 * ViewModel for the setup screen.
 * 
 * Manages the multi-step vault creation process:
 * 1. Generate BIP39 passphrase (first-time only)
 * 2. Confirm passphrase saved
 * 3. Set up PIN
 * 4. Optional biometric enrollment
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val authRepository: AuthRepository,
    private val bip39Generator: Bip39Generator,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        checkBiometricAvailability()
        generateInitialPassphrase()
    }
    
    /**
     * Expose KeystoreManager for BiometricAuthHelper in the UI layer.
     */
    fun getKeystoreManager(): KeystoreManager = keystoreManager

    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            val available = authRepository.isBiometricAvailable()
            _uiState.update { it.copy(biometricAvailable = available) }
        }
    }

    private fun generateInitialPassphrase() {
        viewModelScope.launch {
            try {
                val words = bip39Generator.generatePassphrase(Bip39Generator.WORD_COUNT_12)
                _uiState.update { 
                    it.copy(
                        generatedWords = words,
                        selectedWordCount = 12
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to generate passphrase: ${e.message}")
                }
            }
        }
    }

    // ========================================================================
    // PASSPHRASE GENERATION STEP
    // ========================================================================

    fun onWordCountChanged(count: Int) {
        viewModelScope.launch {
            val words = bip39Generator.generatePassphrase(count)
            _uiState.update { 
                it.copy(
                    generatedWords = words,
                    selectedWordCount = count
                )
            }
        }
    }

    fun onRegeneratePassphrase() {
        viewModelScope.launch {
            val count = _uiState.value.selectedWordCount
            val words = bip39Generator.generatePassphrase(count)
            _uiState.update { it.copy(generatedWords = words) }
        }
    }

    fun onPassphraseCopied() {
        _uiState.update { it.copy(hasUserCopiedPassphrase = true) }
    }

    fun onPassphraseSavedConfirmationChanged(confirmed: Boolean) {
        _uiState.update { it.copy(hasUserConfirmedSaved = confirmed) }
    }

    fun onGeneratorContinue() {
        val state = _uiState.value
        if (!state.hasUserConfirmedSaved) {
            _uiState.update { 
                it.copy(errorMessage = "Please confirm you have saved your passphrase")
            }
            return
        }
        
        // Set the passphrase for vault creation (only in memory)
        val passphraseString = state.generatedWords.joinToString(" ")
        _uiState.update { 
            it.copy(
                passphrase = passphraseString,
                confirmPassphrase = passphraseString,
                isPassphraseValid = true,
                step = SetupStep.PIN,
                errorMessage = null
            )
        }
    }

    // ========================================================================
    // MANUAL PASSPHRASE STEP (for import/recovery)
    // ========================================================================

    fun onPassphraseChange(passphrase: String) {
        _uiState.update {
            it.copy(
                passphrase = passphrase,
                errorMessage = null,
                isPassphraseValid = validatePassphrase(passphrase, it.confirmPassphrase)
            )
        }
    }

    fun onConfirmPassphraseChange(confirmPassphrase: String) {
        _uiState.update {
            it.copy(
                confirmPassphrase = confirmPassphrase,
                errorMessage = null,
                isPassphraseValid = validatePassphrase(it.passphrase, confirmPassphrase)
            )
        }
    }

    private fun validatePassphrase(passphrase: String, confirm: String): Boolean {
        return passphrase.length >= 8 && passphrase == confirm
    }

    fun onPassphraseConfirmed() {
        val state = _uiState.value
        
        if (state.passphrase != state.confirmPassphrase) {
            _uiState.update { it.copy(errorMessage = "Passphrases do not match") }
            return
        }
        
        if (state.passphrase.length < 8) {
            _uiState.update { it.copy(errorMessage = "Passphrase must be at least 8 characters") }
            return
        }
        
        _uiState.update { it.copy(step = SetupStep.PIN, errorMessage = null) }
    }

    // ========================================================================
    // PIN STEP
    // ========================================================================

    fun onPinChange(pin: String) {
        _uiState.update {
            it.copy(
                pin = pin,
                errorMessage = null,
                isPinValid = validatePin(pin, it.confirmPin)
            )
        }
    }

    fun onConfirmPinChange(confirmPin: String) {
        _uiState.update {
            it.copy(
                confirmPin = confirmPin,
                errorMessage = null,
                isPinValid = validatePin(it.pin, confirmPin)
            )
        }
    }

    private fun validatePin(pin: String, confirm: String): Boolean {
        return pin.length == 6 && pin == confirm && pin.all { it.isDigit() }
    }

    fun onPinConfirmed() {
        val state = _uiState.value
        
        if (state.pin != state.confirmPin) {
            _uiState.update { it.copy(errorMessage = "PINs do not match") }
            return
        }
        
        if (state.pin.length != 6) {
            _uiState.update { it.copy(errorMessage = "PIN must be 6 digits") }
            return
        }
        
        if (state.biometricAvailable) {
            _uiState.update { it.copy(step = SetupStep.BIOMETRIC, errorMessage = null) }
        } else {
            createVault(enableBiometric = false)
        }
    }

    fun onBackToGenerator() {
        _uiState.update { 
            it.copy(
                step = SetupStep.GENERATE_PASSPHRASE, 
                errorMessage = null,
                // Clear passphrase from memory when going back
                passphrase = "",
                confirmPassphrase = ""
            )
        }
    }

    fun onBackToPassphrase() {
        _uiState.update { it.copy(step = SetupStep.PASSPHRASE, errorMessage = null) }
    }

    // ========================================================================
    // BIOMETRIC STEP
    // ========================================================================
    
    /**
     * Called when user taps "Enable Fingerprint" button.
     * This triggers the BiometricPrompt in the UI.
     */
    fun onEnableBiometricRequested() {
        Log.d(TAG, "onEnableBiometricRequested called - setting showBiometricPrompt=true")
        _uiState.update { 
            it.copy(
                showBiometricPrompt = true,
                biometricError = null
            )
        }
        Log.d(TAG, "State updated, showBiometricPrompt=${_uiState.value.showBiometricPrompt}")
    }
    
    /**
     * Called after successful biometric verification.
     * Now we actually enable biometric and create the vault.
     */
    fun onBiometricVerified() {
        _uiState.update { it.copy(showBiometricPrompt = false) }
        createVault(enableBiometric = true)
    }
    
    /**
     * Called when biometric verification fails.
     */
    fun onBiometricSetupFailed(errorMessage: String) {
        _uiState.update { 
            it.copy(
                showBiometricPrompt = false,
                biometricError = "Fingerprint verification failed. Please try again."
            )
        }
    }
    
    /**
     * Called when user cancels biometric prompt.
     */
    fun onBiometricSetupCancelled() {
        _uiState.update { it.copy(showBiometricPrompt = false) }
    }

    fun onSkipBiometric() {
        createVault(enableBiometric = false)
    }

    // ========================================================================
    // VAULT CREATION
    // ========================================================================

    private fun createVault(enableBiometric: Boolean) {
        _uiState.update { it.copy(step = SetupStep.CREATING) }
        
        viewModelScope.launch {
            val state = _uiState.value
            
            // Step 1: Create the vault with passphrase
            when (val result = vaultRepository.createVault(state.passphrase)) {
                is VaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            step = SetupStep.GENERATE_PASSPHRASE,
                            errorMessage = "Failed to create vault: ${result.error.message}"
                        )
                    }
                    return@launch
                }
                is VaultResult.Success -> { /* continue */ }
            }
            
            // Step 2: Set up PIN
            when (val result = authRepository.setupPin(state.pin)) {
                is VaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            step = SetupStep.PIN,
                            errorMessage = "Failed to set up PIN: ${result.error.message}"
                        )
                    }
                    return@launch
                }
                is VaultResult.Success -> { /* continue */ }
            }
            
            // Step 3: Enable biometric if requested (user already verified via BiometricPrompt)
            if (enableBiometric) {
                authRepository.enableBiometric()
            }
            
            // Clear sensitive data from memory
            _uiState.update { 
                it.copy(
                    isComplete = true,
                    // Clear passphrase from memory after vault creation
                    passphrase = "",
                    confirmPassphrase = "",
                    generatedWords = emptyList()
                )
            }
        }
    }

    // ========================================================================
    // NAVIGATION
    // ========================================================================

    fun onSwitchToManualPassphrase() {
        _uiState.update { 
            it.copy(
                step = SetupStep.PASSPHRASE,
                passphrase = "",
                confirmPassphrase = "",
                errorMessage = null
            )
        }
    }
}

/**
 * Setup wizard steps.
 */
enum class SetupStep {
    GENERATE_PASSPHRASE,  // BIP39 generation (first step for new vault)
    PASSPHRASE,           // Manual passphrase entry (for import/recovery)
    PIN,
    BIOMETRIC,
    CREATING
}

/**
 * UI state for the setup screen.
 */
data class SetupUiState(
    val step: SetupStep = SetupStep.GENERATE_PASSPHRASE,
    
    // BIP39 generation
    val generatedWords: List<String> = emptyList(),
    val selectedWordCount: Int = 12,
    val hasUserCopiedPassphrase: Boolean = false,
    val hasUserConfirmedSaved: Boolean = false,
    
    // Passphrase
    val passphrase: String = "",
    val confirmPassphrase: String = "",
    val isPassphraseValid: Boolean = false,
    
    // PIN
    val pin: String = "",
    val confirmPin: String = "",
    val isPinValid: Boolean = false,
    
    // Biometric
    val biometricAvailable: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val biometricError: String? = null,
    
    // State
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)
