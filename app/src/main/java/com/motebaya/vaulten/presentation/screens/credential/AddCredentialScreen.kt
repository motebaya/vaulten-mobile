package com.motebaya.vaulten.presentation.screens.credential

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.data.cache.FaviconCache
import com.motebaya.vaulten.domain.entity.CredentialType
import com.motebaya.vaulten.presentation.components.credential.StandardCredentialForm
import com.motebaya.vaulten.presentation.components.credential.SocialMediaCredentialForm
import com.motebaya.vaulten.presentation.components.credential.WalletCredentialForm
import com.motebaya.vaulten.presentation.components.credential.GoogleCredentialForm
import com.motebaya.vaulten.presentation.components.platform.PlatformIcon

/**
 * Screen for adding a new credential.
 * 
 * Keyboard-aware: adjusts for keyboard and dismisses on tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCredentialScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: AddCredentialViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onSaveSuccess()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(), // Adjust for keyboard
        topBar = {
            TopAppBar(
                title = { Text("Add Credential") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Platform Selection
            PlatformSelector(
                platforms = uiState.platforms,
                selectedPlatformId = uiState.selectedPlatformId,
                faviconCache = viewModel.faviconCache,
                onPlatformSelected = { viewModel.onPlatformSelected(it) }
            )

            if (uiState.selectedPlatformId != null) {
                Spacer(modifier = Modifier.height(24.dp))

                // Credential Type Selector
                CredentialTypeSelector(
                    selectedType = uiState.credentialType,
                    onTypeSelected = { viewModel.onCredentialTypeSelected(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Dynamic Form Based on Type
                when (uiState.credentialType) {
                    CredentialType.STANDARD -> StandardCredentialForm(
                        isLoading = uiState.isSaving,
                        error = uiState.error,
                        onSave = { username, password, email, notes ->
                            viewModel.onSaveCredential(
                                username = username,
                                password = password,
                                email = email,
                                notes = notes
                            )
                        },
                        onCancel = onNavigateBack
                    )

                    CredentialType.SOCIAL -> SocialMediaCredentialForm(
                        isLoading = uiState.isSaving,
                        error = uiState.error,
                        onSave = { username, email, password, phone, recoveryEmail, twoFa, notes ->
                            viewModel.onSaveCredential(
                                username = username,
                                password = password,
                                email = email,
                                phoneNumber = phone,
                                recoveryEmail = recoveryEmail,
                                twoFaEnabled = twoFa,
                                notes = notes
                            )
                        },
                        onCancel = onNavigateBack
                    )

                    CredentialType.WALLET -> WalletCredentialForm(
                        isLoading = uiState.isSaving,
                        error = uiState.error,
                        onSave = { accountName, privateKey, seedPhrase, notes ->
                            viewModel.onSaveCredential(
                                username = accountName,
                                password = privateKey.ifEmpty { seedPhrase },
                                accountName = accountName,
                                privateKey = privateKey,
                                seedPhrase = seedPhrase,
                                notes = notes
                            )
                        },
                        onCancel = onNavigateBack
                    )

                    CredentialType.GOOGLE -> GoogleCredentialForm(
                        isLoading = uiState.isSaving,
                        error = uiState.error,
                        onSave = { accountName, email, password, phone, backupCodes, recoveryEmail, birthdate, twoFaEnabled, notes ->
                            viewModel.onSaveCredential(
                                username = email,
                                password = password,
                                email = email,
                                phoneNumber = phone,
                                backupCodes = backupCodes,
                                recoveryEmail = recoveryEmail,
                                birthdate = birthdate,
                                twoFaEnabled = twoFaEnabled,
                                accountName = accountName,
                                notes = notes
                            )
                        },
                        onCancel = onNavigateBack
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a platform to continue",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformSelector(
    platforms: List<com.motebaya.vaulten.domain.entity.Platform>,
    selectedPlatformId: String?,
    faviconCache: FaviconCache,
    onPlatformSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlatform = platforms.find { it.id == selectedPlatformId }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Platform",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPlatform?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select a platform") },
                    leadingIcon = {
                        if (selectedPlatform != null) {
                            PlatformIcon(
                                platformName = selectedPlatform.name,
                                domain = selectedPlatform.domain,
                                size = 24.dp,
                                faviconCache = faviconCache
                            )
                        } else {
                            Icon(Icons.Default.GridView, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    platforms.forEach { platform ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PlatformIcon(
                                        platformName = platform.name,
                                        domain = platform.domain,
                                        size = 24.dp,
                                        faviconCache = faviconCache
                                    )
                                    Text(platform.name)
                                }
                            },
                            onClick = {
                                onPlatformSelected(platform.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialTypeSelector(
    selectedType: CredentialType,
    onTypeSelected: (CredentialType) -> Unit
) {
    val types = listOf(
        CredentialType.STANDARD to Icons.Default.Person,
        CredentialType.SOCIAL to Icons.Default.Share,
        CredentialType.WALLET to Icons.Default.AccountBalanceWallet
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Credential Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { (type, icon) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        label = { Text(type.displayName) },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
