package com.motebaya.vaulten

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.motebaya.vaulten.data.local.DatabaseVersionGuard
import com.motebaya.vaulten.data.local.VersionCheckResult
import com.motebaya.vaulten.presentation.components.ToastHost
import androidx.core.content.ContextCompat
import com.motebaya.vaulten.data.session.SessionManager
import com.motebaya.vaulten.presentation.navigation.VaultNavHost
import com.motebaya.vaulten.presentation.screens.incompatible.IncompatibleVersionScreen
import com.motebaya.vaulten.presentation.theme.VaultTheme
import com.motebaya.vaulten.security.hardening.ScreenOffReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity for the Vault app using Jetpack Compose.
 * 
 * Security measures implemented:
 * - FLAG_SECURE: Prevents screenshots and screen recording
 * - Screen off detection: Locks vault when screen turns off
 * - Task recents exclusion: Hides content in recent apps
 * - Auto-lock on pause: Locks when activity loses focus
 * - Database version guard: Prevents data loss from app downgrades
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var sessionManager: SessionManager

    private var screenOffReceiver: ScreenOffReceiver? = null
    
    // Version check result - mutable state for Compose
    private var versionCheckResult: VersionCheckResult by mutableStateOf(VersionCheckResult.Compatible)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SECURITY: Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // SECURITY: Exclude from recent apps thumbnails
        // The content won't be visible when swiping through recent apps
        setRecentsScreenshotEnabled(false)
        
        // SECURITY: Check database version BEFORE any database access
        // This prevents data loss if user downgrades the app
        versionCheckResult = DatabaseVersionGuard.checkCompatibility(this)

        // Register screen off receiver for auto-lock
        registerScreenOffReceiver()

        enableEdgeToEdge()

        setContent {
            VaultTheme {
                // Check version compatibility first
                when (val result = versionCheckResult) {
                    is VersionCheckResult.Compatible -> {
                        // Normal app flow
                        Box(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                VaultNavHost(sessionManager = sessionManager)
                            }
                            // Toast overlay - always on top
                            ToastHost()
                        }
                    }
                    is VersionCheckResult.IncompatibleNewer -> {
                        // Blocking screen - user must update app
                        IncompatibleVersionScreen(
                            currentAppVersion = result.currentAppSupports,
                            databaseVersion = result.databaseVersion
                        )
                    }
                }
            }
        }

        // Handle intent if opened via file share (vault.enc import)
        // Only if version is compatible
        if (versionCheckResult is VersionCheckResult.Compatible) {
            handleIncomingIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // SECURITY: Start lock timer when activity loses focus
        sessionManager.onActivityPaused()
    }

    override fun onResume() {
        super.onResume()
        // Check if session is still valid
        sessionManager.onActivityResumed()
    }

    /**
     * SECURITY: Reset idle timer on any user touch event.
     * This ensures the 5-minute auto-lock countdown restarts on user activity.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        sessionManager.resetActivityTimer()
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        // SECURITY: Lock immediately when activity is no longer visible
        // EXCEPTION: If export/import flow is in progress (file picker open),
        // we allow the session to remain unlocked so the operation can complete.
        // After returning from file picker, the flow will either complete or
        // be resumed after unlock if the app was killed.
        val shouldSkip = sessionManager.shouldSkipAutoLock()
        Log.d(TAG, "onStop: shouldSkipAutoLock=$shouldSkip")
        if (!shouldSkip) {
            Log.d(TAG, "onStop: locking vault")
            sessionManager.lockVault()
        } else {
            Log.d(TAG, "onStop: NOT locking - export/import in progress")
        }
    }

    override fun onDestroy() {
        unregisterScreenOffReceiver()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User pressed home or switched apps - lock immediately
        // EXCEPTION: If export/import flow is in progress (file picker open),
        // we allow the session to remain unlocked so the operation can complete.
        val shouldSkip = sessionManager.shouldSkipAutoLock()
        Log.d(TAG, "onUserLeaveHint: shouldSkipAutoLock=$shouldSkip")
        if (!shouldSkip) {
            Log.d(TAG, "onUserLeaveHint: locking vault")
            sessionManager.lockVault()
        } else {
            Log.d(TAG, "onUserLeaveHint: NOT locking - export/import in progress")
        }
    }

    private fun registerScreenOffReceiver() {
        screenOffReceiver = ScreenOffReceiver { sessionManager.lockVault() }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered
            }
        }
        screenOffReceiver = null
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            // Check if it's a vault.enc file
            val path = uri.path ?: ""
            if (path.endsWith(".enc")) {
                sessionManager.setPendingImportUri(uri)
            }
        }
    }
}
