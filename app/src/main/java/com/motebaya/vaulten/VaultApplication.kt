package com.motebaya.vaulten

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.motebaya.vaulten.data.session.SessionManager
import com.motebaya.vaulten.data.session.SessionState
import com.motebaya.vaulten.security.crypto.DekManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class for the Vault app.
 * 
 * Security responsibilities:
 * - Initialize Hilt dependency injection
 * - Monitor app lifecycle for auto-lock functionality
 * - Clear sensitive data when app goes to background
 * - Observe session state changes to clear DEK on lock
 */
@HiltAndroidApp
class VaultApplication : Application() {

    companion object {
        private const val TAG = "VaultApplication"
        
        fun get(context: Context): VaultApplication {
            return context.applicationContext as VaultApplication
        }
    }

    @Inject
    lateinit var sessionManager: SessionManager
    
    @Inject
    lateinit var dekManager: DekManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        
        override fun onStart(owner: LifecycleOwner) {
            // App came to foreground
            sessionManager.onAppForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            // App went to background - CRITICAL: Lock immediately
            // EXCEPTION: Skip locking during export/import flows (file picker open)
            if (sessionManager.shouldSkipAutoLock()) {
                Log.d(TAG, "onStop: NOT locking - export/import in progress")
            } else {
                Log.d(TAG, "onStop: locking vault")
                sessionManager.onAppBackground()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Register lifecycle observer for auto-lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        
        // CRITICAL: Observe session state to clear DEK when vault locks
        observeSessionStateForDekClearing()
        
        // Security: Disable WebView data directory (we don't use WebView, but be safe)
        disableWebViewDataCollection()
    }

    /**
     * CRITICAL: Observe session state changes and clear DEK when vault is locking.
     * 
     * This fixes Bug #13 where the vault becomes impossible to unlock after idle timeout.
     * The root cause was that DEK was not being cleared when SessionState transitioned
     * to Locking, leaving stale encrypted data that couldn't be decrypted.
     */
    private fun observeSessionStateForDekClearing() {
        applicationScope.launch {
            sessionManager.sessionState.collect { state ->
                when (state) {
                    is SessionState.Locking -> {
                        // CRITICAL: Clear DEK immediately when vault is locking
                        dekManager.clearDek()
                    }
                    is SessionState.Locked -> {
                        // Double-check DEK is cleared when locked state is reached
                        if (dekManager.isUnlocked()) {
                            dekManager.clearDek()
                        }
                    }
                    else -> {
                        // No action needed for other states
                    }
                }
            }
        }
    }

    override fun onTerminate() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        
        // Clear any sensitive data
        sessionManager.clearSession()
        
        // Cancel the application scope
        applicationScope.cancel()
        
        super.onTerminate()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        // If system is under memory pressure and we're not visible, lock the vault
        // EXCEPTION: Skip locking during export/import flows (file picker open)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            if (sessionManager.shouldSkipAutoLock()) {
                Log.d(TAG, "onTrimMemory: NOT locking - export/import in progress")
            } else {
                Log.d(TAG, "onTrimMemory: locking vault (level=$level)")
                sessionManager.lockVault()
            }
        }
    }

    private fun disableWebViewDataCollection() {
        // We don't use WebView, but ensure no data collection if any library does
        try {
            // Disable WebView metrics collection
            android.webkit.WebView.setDataDirectorySuffix("disabled")
        } catch (e: Exception) {
            // Ignore - WebView might not be available
        }
    }
}
