package com.motebaya.vaulten.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.motebaya.vaulten.data.session.SessionManager
import com.motebaya.vaulten.data.session.SessionState
import com.motebaya.vaulten.presentation.screens.credential.AddCredentialScreen
import com.motebaya.vaulten.presentation.screens.changepin.ChangePinScreen
import com.motebaya.vaulten.presentation.screens.export.ExportVaultScreen
import com.motebaya.vaulten.presentation.screens.importing.ImportVaultScreen
import com.motebaya.vaulten.presentation.screens.importpinsetup.ImportPinSetupScreen
import com.motebaya.vaulten.presentation.screens.main.MainScreen
import com.motebaya.vaulten.presentation.screens.resetpin.ResetPinScreen
import com.motebaya.vaulten.presentation.screens.setup.SetupScreen
import com.motebaya.vaulten.presentation.screens.unlock.UnlockScreen

/**
 * Navigation routes for the Vault app.
 *
 * The 4 main authenticated screens (Credentials, Platforms, Settings, Support)
 * are now hosted inside a single [Main] route via HorizontalPager in MainScreen.
 * Legacy individual routes (Dashboard, Platforms, Settings, Support) are kept
 * as constants for any remaining internal references but are no longer registered
 * as separate composable destinations.
 */
sealed class VaultRoute(val route: String) {
    data object Unlock : VaultRoute("unlock")
    data object Setup : VaultRoute("setup")
    /** Single destination hosting all 4 main pages in a HorizontalPager. */
    data object Main : VaultRoute("main")
    @Deprecated("Use Main instead. Kept for internal reference only.")
    data object Dashboard : VaultRoute("dashboard")
    @Deprecated("Use Main instead. Kept for internal reference only.")
    data object Settings : VaultRoute("settings")
    @Deprecated("Use Main instead. Kept for internal reference only.")
    data object Platforms : VaultRoute("platforms")
    @Deprecated("Use Main instead. Kept for internal reference only.")
    data object Support : VaultRoute("support")
    data object ResetPin : VaultRoute("reset-pin")
    data object ChangePin : VaultRoute("change-pin")
    data object CredentialDetail : VaultRoute("credential/{id}") {
        fun createRoute(id: String) = "credential/$id"
    }
    data object CredentialAdd : VaultRoute("credential/add")
    data object ImportVault : VaultRoute("import?fromSetup={fromSetup}") {
        fun createRoute(fromSetup: Boolean = false) = "import?fromSetup=$fromSetup"
    }
    data object ExportVault : VaultRoute("export")
    data object ImportPinSetup : VaultRoute("import-pin-setup")
}

/**
 * Main navigation host for the Vault app.
 * 
 * Automatically navigates based on session state:
 * - Locked -> Unlock screen
 * - Unlocked -> Dashboard
 * - SetupRequired -> Setup screen
 */
@Composable
fun VaultNavHost(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController()
) {
    val sessionState by sessionManager.sessionState.collectAsState()
    
    // React to session state changes
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.Locked, is SessionState.Locking -> {
                // Check for pending import flow from setup - this can happen if the app
                // was at SetupRequired but somehow ended up in Locked state
                val pending = sessionManager.pendingExportImport.value
                if (pending?.route == "import" && pending.isFromSetup) {
                    // For import from setup, we should go to setup first, which will
                    // detect the pending flow and redirect to import
                    // But we also need to restore SetupRequired state
                    sessionManager.setSetupRequired()
                    return@LaunchedEffect
                }
                
                navController.navigate(VaultRoute.Unlock.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is SessionState.Unlocked -> {
                // Check for pending export/import flow to restore
                val pending = sessionManager.pendingExportImport.value
                if (pending != null) {
                    // Clear the skip auto-lock flag since we're restoring
                    sessionManager.setSkipAutoLock(false)
                    
                    when (pending.route) {
                        "export" -> {
                            navController.navigate(VaultRoute.ExportVault.route) {
                                popUpTo(VaultRoute.Unlock.route) { inclusive = true }
                            }
                            return@LaunchedEffect
                        }
                        "import" -> {
                            navController.navigate(VaultRoute.ImportVault.createRoute(pending.isFromSetup)) {
                                popUpTo(VaultRoute.Unlock.route) { inclusive = true }
                            }
                            return@LaunchedEffect
                        }
                    }
                }
                
                // Only navigate to main screen if currently on unlock/setup/import-pin-setup screen
                // Don't navigate away from Export or Import screens - let them show their success/error states
                val currentRoute = navController.currentDestination?.route
                if (currentRoute == VaultRoute.Unlock.route || 
                    currentRoute == VaultRoute.Setup.route ||
                    currentRoute == VaultRoute.ResetPin.route ||
                    currentRoute == VaultRoute.ImportPinSetup.route) {
                    navController.navigate(VaultRoute.Main.route) {
                        popUpTo(VaultRoute.Unlock.route) { inclusive = true }
                    }
                }
            }
            is SessionState.SetupRequired -> {
                // Check current route first
                val currentRoute = navController.currentDestination?.route
                val isOnImportScreen = currentRoute?.startsWith("import") == true
                
                // If we're already on import screen, don't navigate anywhere
                // This prevents interrupting the import flow when returning from file picker
                if (isOnImportScreen) {
                    return@LaunchedEffect
                }
                
                // Check for pending import flow to restore (from setup)
                val pending = sessionManager.pendingExportImport.value
                if (pending?.route == "import" && pending.isFromSetup) {
                    // Navigate to import screen to restore the pending flow
                    navController.navigate(VaultRoute.ImportVault.createRoute(fromSetup = true)) {
                        popUpTo(0) { inclusive = true }
                    }
                    return@LaunchedEffect
                }
                
                // Navigate to setup screen
                navController.navigate(VaultRoute.Setup.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = VaultRoute.Unlock.route
    ) {
        // Unlock Screen (no sidebar)
        composable(VaultRoute.Unlock.route) {
            UnlockScreen(
                onUnlockSuccess = {
                    sessionManager.unlockVault()
                },
                onForgotPin = {
                    navController.navigate(VaultRoute.ResetPin.route)
                },
                onNeedSetup = {
                    sessionManager.setSetupRequired()
                }
            )
        }
        
        // Setup Screen (no sidebar)
        composable(VaultRoute.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    sessionManager.unlockVault()
                },
                onImportVault = {
                    navController.navigate(VaultRoute.ImportVault.createRoute(fromSetup = true))
                }
            )
        }
        
        // Reset PIN Screen (no sidebar)
        composable(VaultRoute.ResetPin.route) {
            ResetPinScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onResetComplete = {
                    navController.navigate(VaultRoute.Unlock.route) {
                        popUpTo(VaultRoute.ResetPin.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Main Screen - hosts Credentials, Platforms, Settings, Support in a HorizontalPager
        composable(VaultRoute.Main.route) {
            MainScreen(
                onNavigateToAdd = {
                    navController.navigate(VaultRoute.CredentialAdd.route)
                },
                onExportVault = {
                    navController.navigate(VaultRoute.ExportVault.route)
                },
                onImportVault = {
                    navController.navigate(VaultRoute.ImportVault.createRoute(fromSetup = false))
                },
                onChangePin = {
                    navController.navigate(VaultRoute.ChangePin.route)
                },
                onForgotPin = {
                    navController.navigate(VaultRoute.ResetPin.route)
                },
                onLock = {
                    sessionManager.lockVault()
                }
            )
        }
        
        // Add Credential Screen
        composable(VaultRoute.CredentialAdd.route) {
            AddCredentialScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = {
                    navController.popBackStack()
                }
            )
        }
        
        // Change PIN Screen
        composable(VaultRoute.ChangePin.route) {
            ChangePinScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPinChanged = {
                    // Lock vault and return to unlock screen
                    sessionManager.lockVault()
                }
            )
        }
        
        // Export Vault Screen
        composable(VaultRoute.ExportVault.route) {
            ExportVaultScreen(
                onNavigateBack = {
                    // Navigate to Main instead of popBackStack
                    // because the back stack may be cleared if export restored from pending flow
                    if (!navController.popBackStack()) {
                        navController.navigate(VaultRoute.Main.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
        
        // Import Vault Screen
        composable(
            route = VaultRoute.ImportVault.route,
            arguments = listOf(
                navArgument("fromSetup") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val isFromSetup = backStackEntry.arguments?.getBoolean("fromSetup") ?: false
            ImportVaultScreen(
                isFromSetup = isFromSetup,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onImportComplete = {
                    // Lock vault to force re-authentication with new credentials
                    sessionManager.lockVault()
                },
                onImportCompleteFromSetup = {
                    // Navigate to PIN setup after import from setup
                    navController.navigate(VaultRoute.ImportPinSetup.route) {
                        popUpTo(VaultRoute.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Import PIN Setup Screen (after importing vault from setup)
        composable(VaultRoute.ImportPinSetup.route) {
            ImportPinSetupScreen(
                onSetupComplete = {
                    // Unlock vault after PIN setup and navigate to dashboard
                    sessionManager.unlockVault()
                }
            )
        }
    }
}
