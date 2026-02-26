package com.motebaya.vaulten.presentation.screens.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.data.local.preferences.SecurityPreferences
import com.motebaya.vaulten.domain.entity.VaultError
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.security.keystore.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the unlock screen.
 * 
 * Handles PIN entry, validation, biometric authentication,
 * and lockout countdown timer.
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val securityPreferences: SecurityPreferences,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val pinBuilder = StringBuilder()
    private var countdownJob: Job? = null

    init {
        checkInitialState()
        observeLockoutState()
    }
    
    /**
     * Expose KeystoreManager for BiometricAuthHelper in the UI layer.
     */
    fun getKeystoreManager(): KeystoreManager = keystoreManager

    private fun checkInitialState() {
        viewModelScope.launch {
            // Check if vault/PIN is set up
            if (!authRepository.isPinSetup()) {
                _uiState.update { it.copy(needsSetup = true) }
                return@launch
            }

            // Check biometric availability
            val biometricAvailable = authRepository.isBiometricEnabled() &&
                    authRepository.isBiometricAvailable()
            _uiState.update { it.copy(biometricAvailable = biometricAvailable) }

            // Check if locked out
            checkLockoutStatus()
        }
    }

    private fun observeLockoutState() {
        viewModelScope.launch {
            securityPreferences.failedAttemptCount.collect { count ->
                val showWarning = count >= SecurityPreferences.WARNING_THRESHOLD && 
                                  count < SecurityPreferences.MAX_ATTEMPTS
                val remainingAttempts = SecurityPreferences.MAX_ATTEMPTS - count
                
                _uiState.update { 
                    it.copy(
                        failedAttempts = count,
                        showWarning = showWarning,
                        remainingAttempts = if (showWarning) remainingAttempts else null
                    )
                }
            }
        }
    }

    private suspend fun checkLockoutStatus() {
        val isLockedOut = securityPreferences.checkLockedOut()
        if (isLockedOut) {
            val remainingMs = securityPreferences.getRemainingLockoutMs()
            startCountdownTimer(remainingMs)
        }
    }

    private fun startCountdownTimer(remainingMs: Long) {
        countdownJob?.cancel()
        
        _uiState.update { 
            it.copy(
                isLockedOut = true,
                cooldownSeconds = (remainingMs / 1000).toInt()
            )
        }
        
        countdownJob = viewModelScope.launch {
            var remaining = remainingMs
            
            while (remaining > 0) {
                _uiState.update { 
                    it.copy(cooldownSeconds = (remaining / 1000).toInt())
                }
                delay(1000)
                remaining -= 1000
            }
            
            // Lockout expired
            securityPreferences.resetLockout()
            _uiState.update { 
                it.copy(
                    isLockedOut = false,
                    cooldownSeconds = 0,
                    errorMessage = null,
                    showWarning = false,
                    remainingAttempts = null
                )
            }
        }
    }

    fun onDigitEntered(digit: Int) {
        if (_uiState.value.isLockedOut || _uiState.value.isLoading) return
        if (pinBuilder.length >= 6) return

        pinBuilder.append(digit)
        _uiState.update { it.copy(pinLength = pinBuilder.length, errorMessage = null) }

        // Auto-submit when 6 digits entered
        if (pinBuilder.length == 6) {
            submitPin()
        }
    }

    fun onBackspace() {
        if (pinBuilder.isNotEmpty()) {
            pinBuilder.deleteCharAt(pinBuilder.length - 1)
            _uiState.update { it.copy(pinLength = pinBuilder.length, errorMessage = null) }
        }
    }

    private fun submitPin() {
        val pin = pinBuilder.toString()
        pinBuilder.clear()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pinLength = 0) }

            when (val result = authRepository.verifyPin(pin)) {
                is VaultResult.Success -> {
                    // Reset lockout on success
                    securityPreferences.resetLockout()
                    _uiState.update { it.copy(isLoading = false, isUnlocked = true) }
                }
                is VaultResult.Error -> {
                    // Record failed attempt
                    val triggeredLockout = securityPreferences.recordFailedAttempt()
                    
                    if (triggeredLockout) {
                        val remainingMs = securityPreferences.getRemainingLockoutMs()
                        startCountdownTimer(remainingMs)
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                errorMessage = "Too many attempts. Please wait."
                            )
                        }
                    } else {
                        val failedCount = securityPreferences.getFailedAttemptCount()
                        val remaining = SecurityPreferences.MAX_ATTEMPTS - failedCount
                        
                        val errorMessage = when {
                            failedCount >= SecurityPreferences.WARNING_THRESHOLD ->
                                "Incorrect PIN. $remaining attempts remaining."
                            else -> "Incorrect PIN"
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                errorMessage = errorMessage
                            )
                        }
                    }
                }
            }
        }
    }

    fun onBiometricRequested() {
        _uiState.update { it.copy(showBiometricPrompt = true) }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showBiometricPrompt = false) }
            
            // CRITICAL: Unwrap DEK after biometric success (same as PIN verification)
            when (authRepository.unlockWithBiometric()) {
                is VaultResult.Success -> {
                    securityPreferences.resetLockout()
                    _uiState.update { it.copy(isLoading = false, isUnlocked = true) }
                }
                is VaultResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to unlock vault. Please use PIN."
                        )
                    }
                }
            }
        }
    }

    fun onBiometricFailed() {
        _uiState.update {
            it.copy(
                showBiometricPrompt = false,
                errorMessage = "Biometric authentication failed"
            )
        }
    }

    fun onBiometricCancelled() {
        _uiState.update { it.copy(showBiometricPrompt = false) }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}

/**
 * UI state for the unlock screen.
 */
data class UnlockUiState(
    val pinLength: Int = 0,
    val isLoading: Boolean = false,
    val isUnlocked: Boolean = false,
    val needsSetup: Boolean = false,
    val errorMessage: String? = null,
    val biometricAvailable: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val isLockedOut: Boolean = false,
    val cooldownSeconds: Int = 0,
    val failedAttempts: Int = 0,
    val showWarning: Boolean = false,
    val remainingAttempts: Int? = null
) {
    /**
     * Format cooldown seconds as MM:SS.
     */
    val formattedCooldown: String
        get() {
            val minutes = cooldownSeconds / 60
            val seconds = cooldownSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
