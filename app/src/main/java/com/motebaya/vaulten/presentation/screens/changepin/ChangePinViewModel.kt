package com.motebaya.vaulten.presentation.screens.changepin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motebaya.vaulten.domain.entity.VaultResult
import com.motebaya.vaulten.domain.repository.AuthRepository
import com.motebaya.vaulten.domain.repository.VaultRepository
import com.motebaya.vaulten.presentation.components.ToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps for the Change PIN flow.
 */
enum class ChangePinStep {
    CURRENT_PIN,
    NEW_PIN,
    CONFIRM_PIN,
    PASSPHRASE,
    SUCCESS
}

/**
 * ViewModel for the Change PIN screen.
 */
@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChangePinUiState())
    val uiState: StateFlow<ChangePinUiState> = _uiState.asStateFlow()
    
    // Store validated values between steps
    private var verifiedCurrentPin: String? = null
    private var newPin: String? = null
    
    /**
     * Called when user submits current PIN.
     */
    fun onCurrentPinSubmit(pin: String) {
        if (pin.length != 6) {
            _uiState.update { it.copy(error = "PIN must be 6 digits") }
            ToastManager.showError("PIN must be 6 digits")
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            when (val result = authRepository.verifyPin(pin)) {
                is VaultResult.Success -> {
                    verifiedCurrentPin = pin
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            step = ChangePinStep.NEW_PIN,
                            error = null
                        )
                    }
                }
                is VaultResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Incorrect current PIN"
                        )
                    }
                    ToastManager.showError("Incorrect current PIN")
                }
            }
        }
    }
    
    /**
     * Called when user submits new PIN.
     */
    fun onNewPinSubmit(pin: String) {
        if (pin.length != 6) {
            _uiState.update { it.copy(error = "PIN must be 6 digits") }
            ToastManager.showError("PIN must be 6 digits")
            return
        }
        
        if (pin == verifiedCurrentPin) {
            _uiState.update { it.copy(error = "New PIN must be different from current PIN") }
            ToastManager.showError("New PIN must be different from current PIN")
            return
        }
        
        newPin = pin
        _uiState.update { 
            it.copy(
                step = ChangePinStep.CONFIRM_PIN,
                error = null
            )
        }
    }
    
    /**
     * Called when user confirms new PIN.
     */
    fun onConfirmPinSubmit(confirmPin: String) {
        if (confirmPin != newPin) {
            _uiState.update { it.copy(error = "PINs do not match") }
            ToastManager.showError("PINs do not match")
            return
        }
        
        _uiState.update { 
            it.copy(
                step = ChangePinStep.PASSPHRASE,
                error = null
            )
        }
    }
    
    /**
     * Called when user submits passphrase for final verification.
     */
    fun onPassphraseSubmit(passphrase: String) {
        if (passphrase.isBlank()) {
            _uiState.update { it.copy(error = "Passphrase is required") }
            return
        }
        
        val pinToSet = newPin ?: run {
            _uiState.update { it.copy(error = "Invalid state - please start over") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Verify passphrase and change PIN
            when (val result = vaultRepository.changePinWithPassphrase(pinToSet, passphrase)) {
                is VaultResult.Success -> {
                    ToastManager.showSuccess("PIN changed successfully")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            step = ChangePinStep.SUCCESS,
                            error = null
                        )
                    }
                    
                    // Brief delay before signaling completion (for animation)
                    delay(1500)
                    _uiState.update { it.copy(isComplete = true) }
                }
                is VaultResult.Error -> {
                    val errorMessage = when (result.error) {
                        is com.motebaya.vaulten.domain.entity.VaultError.WrongPassphrase -> "Incorrect passphrase"
                        else -> result.error.message
                    }
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }
                    ToastManager.showError(errorMessage)
                }
            }
        }
    }
    
    /**
     * Go back to previous step.
     */
    fun goBack() {
        val newStep = when (_uiState.value.step) {
            ChangePinStep.NEW_PIN -> ChangePinStep.CURRENT_PIN
            ChangePinStep.CONFIRM_PIN -> ChangePinStep.NEW_PIN
            ChangePinStep.PASSPHRASE -> ChangePinStep.CONFIRM_PIN
            else -> return
        }
        _uiState.update { it.copy(step = newStep, error = null) }
    }
    
    override fun onCleared() {
        // Clear sensitive data
        verifiedCurrentPin = null
        newPin = null
        super.onCleared()
    }
}

/**
 * UI state for Change PIN screen.
 */
data class ChangePinUiState(
    val step: ChangePinStep = ChangePinStep.CURRENT_PIN,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false
)
