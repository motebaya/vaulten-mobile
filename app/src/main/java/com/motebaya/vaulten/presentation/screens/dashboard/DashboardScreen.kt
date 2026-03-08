package com.motebaya.vaulten.presentation.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.data.cache.FaviconCache
import com.motebaya.vaulten.domain.entity.Credential
import com.motebaya.vaulten.domain.entity.CredentialType
import com.motebaya.vaulten.presentation.components.AppScaffold
import com.motebaya.vaulten.presentation.components.StatsInfoBar
import com.motebaya.vaulten.presentation.components.credential.ViewCredentialModal
import com.motebaya.vaulten.presentation.components.platform.PlatformIcon
import com.motebaya.vaulten.security.BiometricAuthHelper
import com.motebaya.vaulten.util.findActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Main dashboard screen showing all credentials.
 */
@Composable
fun DashboardScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onForgotPin: () -> Unit,
    onLock: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewCredentialState by viewModel.viewCredentialState.collectAsState()
    val deleteCredentialState by viewModel.deleteCredentialState.collectAsState()
    val editCredentialState by viewModel.editCredentialState.collectAsState()
    val biometricAvailable by viewModel.biometricAvailable.collectAsState()
    val showBiometricPrompt by viewModel.showBiometricPrompt.collectAsState()
    
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Use findActivity() extension to properly unwrap context to FragmentActivity
    val activity = context.findActivity()
    
    // Handle biometric prompt trigger
    LaunchedEffect(showBiometricPrompt) {
        val promptType = showBiometricPrompt
        if (promptType != null && activity != null) {
            val keystoreManager = viewModel.getKeystoreManager()
            val biometricHelper = BiometricAuthHelper(activity, keystoreManager)
            
            val (title, subtitle) = when (promptType) {
                BiometricPromptType.ViewCredential -> "View Credential" to "Use your fingerprint to view"
                BiometricPromptType.DeleteCredential -> "Delete Credential" to "Use your fingerprint to confirm deletion"
            }
            
            biometricHelper.authenticate(
                title = title,
                subtitle = subtitle,
                negativeButtonText = "Use PIN",
                onSuccess = {
                    when (promptType) {
                        BiometricPromptType.ViewCredential -> viewModel.onBiometricSuccessForView()
                        BiometricPromptType.DeleteCredential -> viewModel.onBiometricSuccessForDelete()
                    }
                },
                onError = { _ -> viewModel.onBiometricCancelled() },
                onCancel = { viewModel.onBiometricCancelled() }
            )
        }
    }
    
    // Show EditCredentialModal or CooldownModal based on edit state
    editCredentialState?.let { state ->
        when (state) {
            is EditCredentialState.Cooldown -> {
                EditCooldownModal(
                    remainingSeconds = state.remainingSeconds,
                    lastEditedAt = state.lastEditedAt,
                    onDismiss = { viewModel.dismissEditCredential() }
                )
            }
            is EditCredentialState.Editing,
            is EditCredentialState.Saving,
            is EditCredentialState.Error -> {
                EditCredentialModal(
                    state = state,
                    onSave = { credential -> viewModel.onSaveEditedCredential(credential) },
                    onDismiss = { viewModel.dismissEditCredential() }
                )
            }
        }
    }
    
    // Show ViewCredentialModal when state is not null
    viewCredentialState?.let { state ->
        ViewCredentialModal(
            state = state,
            biometricAvailable = biometricAvailable,
            onPinSubmit = { pin -> viewModel.onVerifyPinForView(pin) },
            onBiometricRequested = { viewModel.onBiometricRequestedForView() },
            onForgotPin = onForgotPin,
            onDismiss = { viewModel.dismissViewCredential() }
        )
    }
    
    // Show Delete Confirmation Dialog
    deleteCredentialState?.let { state ->
        DeleteCredentialDialog(
            state = state,
            biometricAvailable = biometricAvailable,
            onConfirm = { pin -> viewModel.onDeleteCredentialConfirm(pin) },
            onBiometricConfirm = { viewModel.onBiometricRequestedForDelete() },
            onForgotPin = onForgotPin,
            onDismiss = { viewModel.onDismissDeleteCredential() }
        )
    }
    
    // Platform filter bottom sheet
    if (uiState.showPlatformFilterSheet) {
        PlatformFilterBottomSheet(
            platforms = uiState.platformFilterItems,
            onToggle = viewModel::onPlatformFilterToggle,
            onClearAll = viewModel::onClearPlatformFilters,
            onDismiss = viewModel::onDismissPlatformFilterSheet
        )
    }
    
    // Date range picker dialog
    if (uiState.showDateRangePicker) {
        DateRangePickerDialog(
            currentRange = uiState.dateRange,
            onConfirm = { start, end ->
                viewModel.onDateRangeChange(start, end)
                viewModel.onDismissDateRangePicker()
            },
            onClear = {
                viewModel.onClearDateRange()
                viewModel.onDismissDateRangePicker()
            },
            onDismiss = viewModel::onDismissDateRangePicker
        )
    }
    
    AppScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        onLock = onLock,
        title = "Credentials",
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add credential")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusManager.clearFocus()
                }
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Search credentials") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )
            
            // Stats bar
            if (!uiState.isLoading) {
                StatsInfoBar(
                    totalCredentials = uiState.totalCredentials,
                    platformCount = uiState.platformCount
                )
                
                // Filter and sort row
                FilterSortRow(
                    selectedFilter = uiState.selectedFilter,
                    selectedSort = uiState.selectedSort,
                    platformFilterItems = uiState.platformFilterItems,
                    dateRange = uiState.dateRange,
                    onFilterChange = viewModel::onFilterChange,
                    onSortChange = viewModel::onSortChange,
                    onShowPlatformFilter = viewModel::onShowPlatformFilterSheet,
                    onShowDateRange = viewModel::onShowDateRangePicker,
                    onClearDateRange = viewModel::onClearDateRange
                )
            }
            
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                uiState.credentials.isEmpty() -> {
                    EmptyState(onAddFirst = onNavigateToAdd)
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.credentials,
                            key = { it.id }
                        ) { credential ->
                            CredentialCard(
                                credential = credential,
                                faviconCache = viewModel.faviconCache,
                                onDoubleTap = { viewModel.onViewCredential(credential.id) },
                                onEdit = { viewModel.onEditCredentialRequest(credential.id) },
                                onDelete = { viewModel.onDeleteCredentialRequest(credential.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAddFirst: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No credentials yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add your first credential to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onAddFirst) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Credential")
        }
    }
}

/**
 * Credential card with tap behavior:
 * - Single tap: no action
 * - Double tap: trigger PIN verification and view modal
 * - 3-dot menu: Edit and Delete options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialCard(
    credential: CredentialUiModel,
    faviconCache: FaviconCache,
    onDoubleTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(credential.id) {
                detectTapGestures(
                    onTap = { /* Single tap: no action per spec */ },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform icon
                PlatformIcon(
                    platformName = credential.platformName,
                    domain = credential.platformDomain,
                    size = 44.dp,
                    faviconCache = faviconCache
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    // Platform name
                    Text(
                        text = credential.platformName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Email (if available)
                    credential.email?.takeIf { it.isNotBlank() }?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Username
                    Text(
                        text = credential.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 3-dot overflow menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "Delete",
                                    color = MaterialTheme.colorScheme.error
                                ) 
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
            
            // Notes snippet (if not empty) - placed above created date
            if (credential.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = credential.notes.replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Creation date at bottom
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFormatter.format(credential.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * UI model for credential display in the list.
 */
data class CredentialUiModel(
    val id: String,
    val platformId: String,
    val platformName: String,
    val platformColor: String,
    val platformDomain: String = "",
    val username: String,
    val email: String? = null,
    val notes: String = "",
    val credentialType: CredentialType = CredentialType.STANDARD,
    val createdAt: Instant = Instant.now(),
    val lastEditedAt: Instant? = null
)

/**
 * Filter and sort row with chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortRow(
    selectedFilter: CredentialFilterOption,
    selectedSort: CredentialSortOption,
    platformFilterItems: List<PlatformFilterItem>,
    dateRange: DateRange,
    onFilterChange: (CredentialFilterOption) -> Unit,
    onSortChange: (CredentialSortOption) -> Unit,
    onShowPlatformFilter: () -> Unit,
    onShowDateRange: () -> Unit,
    onClearDateRange: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val selectedPlatformCount = platformFilterItems.count { it.isSelected }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Filter chips (horizontal scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Platform filter chip
            FilterChip(
                selected = selectedPlatformCount > 0,
                onClick = onShowPlatformFilter,
                label = { 
                    Text(
                        if (selectedPlatformCount > 0) 
                            "Platforms ($selectedPlatformCount)" 
                        else 
                            "Platforms"
                    ) 
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            
            // Date range chip
            FilterChip(
                selected = dateRange.hasRange,
                onClick = onShowDateRange,
                label = { Text(if (dateRange.hasRange) "Date Range" else "Date") },
                leadingIcon = {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (dateRange.hasRange) {
                    {
                        IconButton(
                            onClick = onClearDateRange,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else null
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // Sort dropdown
            Box {
                AssistChip(
                    onClick = { showSortMenu = true },
                    label = { Text(selectedSort.displayName) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    CredentialSortOption.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.displayName) },
                            onClick = {
                                onSortChange(sort)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (selectedSort == sort) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Bottom sheet for platform multi-select filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformFilterBottomSheet(
    platforms: List<PlatformFilterItem>,
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter by Platform",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onClearAll) {
                    Text("Clear All")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (platforms.isEmpty()) {
                Text(
                    text = "No platforms with credentials",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                platforms.forEach { platform ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = platform.isSelected,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(platform.id) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = platform.isSelected,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = platform.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${platform.credentialCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Date range picker dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    currentRange: DateRange,
    onConfirm: (Long?, Long?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = currentRange.startMillis,
        initialSelectedEndDateMillis = currentRange.endMillis
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        dateRangePickerState.selectedStartDateMillis,
                        dateRangePickerState.selectedEndDateMillis
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(16.dp)
                )
            },
            showModeToggle = false,
            modifier = Modifier.height(500.dp)
        )
    }
}

/**
 * Delete credential confirmation dialog with PIN using numeric keypad.
 * 
 * State machine:
 * - Idle: waiting for input
 * - Verifying: PIN submitted, awaiting result
 * - Error: wrong PIN -> clear input, show error, allow retry
 * - Success: deletion complete -> close modal
 */
@Composable
private fun DeleteCredentialDialog(
    state: DeleteCredentialState,
    biometricAvailable: Boolean = false,
    onConfirm: (String) -> Unit,
    onBiometricConfirm: () -> Unit = {},
    onForgotPin: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var lastErrorTimestamp by remember { mutableStateOf(0L) }
    
    val isLoading = state is DeleteCredentialState.Deleting
    
    // Handle error state: show error message and clear PIN for retry
    LaunchedEffect(state) {
        if (state is DeleteCredentialState.Error) {
            val now = System.currentTimeMillis()
            // Only process if this is a new error (prevent re-processing same error)
            if (now != lastErrorTimestamp) {
                lastErrorTimestamp = now
                localError = state.message
                pin = "" // Clear PIN to allow fresh retry
            }
        }
    }
    
    // Clear error when user starts typing new PIN (but not when we just cleared it)
    LaunchedEffect(pin) {
        if (pin.isNotEmpty()) {
            localError = null
        }
    }
    
    val error = localError
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Delete Credential",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Enter your PIN to confirm deletion.\nThis action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Numeric keypad for PIN entry
                com.motebaya.vaulten.presentation.components.NumericKeypad(
                    pin = pin,
                    onPinChange = { pin = it },
                    onSubmit = {
                        localError = null
                        onConfirm(pin)
                    },
                    enabled = !isLoading,
                    showBiometric = biometricAvailable,
                    onBiometric = onBiometricConfirm
                )
                
                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Forgot PIN and Cancel buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onForgotPin,
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Forgot PIN?",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modal showing cooldown timer when user tries to edit a credential
 * that was edited within the last 24 hours.
 */
@Composable
private fun EditCooldownModal(
    remainingSeconds: Int,
    lastEditedAt: Instant?,
    onDismiss: () -> Unit
) {
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    
    // Format lastEditedAt using centralized formatter
    val lastEditedText = remember(lastEditedAt) {
        if (lastEditedAt != null) {
            val formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
            "This credential was last edited on: ${formatter.format(lastEditedAt)}"
        } else {
            "This credential has a cooldown period active."
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { 
            Text(
                "Edit Cooldown Active",
                style = MaterialTheme.typography.headlineSmall
            ) 
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show when the credential was last edited
                Text(
                    text = lastEditedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "You must wait until the cooldown period ends before you can edit this credential again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

/**
 * Modal for editing a credential with field-level enable/disable.
 * Each field starts disabled with a pencil icon; tapping enables editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCredentialModal(
    state: EditCredentialState,
    onSave: (Credential) -> Unit,
    onDismiss: () -> Unit
) {
    val credential = when (state) {
        is EditCredentialState.Editing -> state.credential
        is EditCredentialState.Saving -> state.credential
        is EditCredentialState.Error -> state.credential
        else -> return
    }
    
    val isLoading = state is EditCredentialState.Saving
    val errorMessage = (state as? EditCredentialState.Error)?.message
    
    // Field states - track which fields are enabled for editing
    var usernameEnabled by remember { mutableStateOf(false) }
    var emailEnabled by remember { mutableStateOf(false) }
    var passwordEnabled by remember { mutableStateOf(false) }
    var notesEnabled by remember { mutableStateOf(false) }
    var backupEmailEnabled by remember { mutableStateOf(false) }
    var phoneNumberEnabled by remember { mutableStateOf(false) }
    var birthdateEnabled by remember { mutableStateOf(false) }
    var recoveryCodesEnabled by remember { mutableStateOf(false) }
    var twoFaEnabled by remember { mutableStateOf(false) }
    var accountNameEnabled by remember { mutableStateOf(false) }
    var privateKeyEnabled by remember { mutableStateOf(false) }
    var seedPhraseEnabled by remember { mutableStateOf(false) }
    
    // Field values
    var username by remember { mutableStateOf(credential.username) }
    var email by remember { mutableStateOf(credential.email ?: "") }
    var password by remember { mutableStateOf(credential.password) }
    var notes by remember { mutableStateOf(credential.notes) }
    var backupEmail by remember { mutableStateOf(credential.backupEmail ?: "") }
    var phoneNumber by remember { mutableStateOf(credential.phoneNumber ?: "") }
    var birthdate by remember { mutableStateOf(credential.birthdate ?: "") }
    var recoveryCodes by remember { mutableStateOf(credential.recoveryCodes ?: "") }
    var twoFaEnabledValue by remember { mutableStateOf(credential.twoFaEnabled) }
    var showPassword by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf(credential.accountName ?: "") }
    var privateKey by remember { mutableStateOf(credential.privateKey ?: "") }
    var seedPhrase by remember { mutableStateOf(credential.seedPhrase ?: "") }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showSeedPhrase by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val displayIdentifier = credential.email?.takeIf { it.isNotBlank() } ?: credential.username
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Header text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "You are currently editing credentials for $displayIdentifier.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Warning text with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "You can only update credentials once per day. Please ensure the information is correct before saving, or try again tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error message
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Username field (all types)
                EditableField(
                    label = if (credential.credentialType == CredentialType.WALLET) "Account Name" else "Username",
                    value = username,
                    onValueChange = { username = it },
                    enabled = usernameEnabled,
                    onEnableClick = { usernameEnabled = true },
                    isLoading = isLoading
                )
                
                // Email field (for non-wallet types)
                if (credential.credentialType != CredentialType.WALLET) {
                    EditableField(
                        label = "Email",
                        value = email,
                        onValueChange = { email = it },
                        enabled = emailEnabled,
                        onEnableClick = { emailEnabled = true },
                        isLoading = isLoading
                    )
                }
                
                // Password field (for non-wallet types only)
                if (credential.credentialType != CredentialType.WALLET) {
                    EditableField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it },
                        enabled = passwordEnabled,
                        onEnableClick = { passwordEnabled = true },
                        isLoading = isLoading,
                        isPassword = true,
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword }
                    )
                }
                
                // Notes field (for non-wallet types only - wallet has separate notes field)
                if (credential.credentialType != CredentialType.WALLET) {
                    EditableField(
                        label = "Notes",
                        value = notes,
                        onValueChange = { notes = it },
                        enabled = notesEnabled,
                        onEnableClick = { notesEnabled = true },
                        isLoading = isLoading,
                        multiline = true
                    )
                }
                
                // Type-specific fields
                when (credential.credentialType) {
                    CredentialType.GOOGLE -> {
                        // Account Name (optional)
                        EditableField(
                            label = "Account Name",
                            value = accountName,
                            onValueChange = { accountName = it },
                            enabled = accountNameEnabled,
                            onEnableClick = { accountNameEnabled = true },
                            isLoading = isLoading
                        )
                        // Backup Email
                        EditableField(
                            label = "Backup Email",
                            value = backupEmail,
                            onValueChange = { backupEmail = it },
                            enabled = backupEmailEnabled,
                            onEnableClick = { backupEmailEnabled = true },
                            isLoading = isLoading
                        )
                        // Phone Number
                        EditableField(
                            label = "Phone Number",
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            enabled = phoneNumberEnabled,
                            onEnableClick = { phoneNumberEnabled = true },
                            isLoading = isLoading
                        )
                        // Birthdate
                        EditableField(
                            label = "Birthdate",
                            value = birthdate,
                            onValueChange = { birthdate = it },
                            enabled = birthdateEnabled,
                            onEnableClick = { birthdateEnabled = true },
                            isLoading = isLoading
                        )
                        // 2FA Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2FA Enabled",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = twoFaEnabledValue,
                                onCheckedChange = { twoFaEnabledValue = it },
                                enabled = !isLoading
                            )
                        }
                        // Recovery Codes
                        EditableField(
                            label = "Recovery Codes",
                            value = recoveryCodes,
                            onValueChange = { recoveryCodes = it },
                            enabled = recoveryCodesEnabled,
                            onEnableClick = { recoveryCodesEnabled = true },
                            isLoading = isLoading,
                            multiline = true
                        )
                    }
                    CredentialType.SOCIAL -> {
                        // Phone Number
                        EditableField(
                            label = "Phone Number",
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            enabled = phoneNumberEnabled,
                            onEnableClick = { phoneNumberEnabled = true },
                            isLoading = isLoading
                        )
                        // Backup Email
                        EditableField(
                            label = "Recovery Email",
                            value = backupEmail,
                            onValueChange = { backupEmail = it },
                            enabled = backupEmailEnabled,
                            onEnableClick = { backupEmailEnabled = true },
                            isLoading = isLoading
                        )
                        // 2FA Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2FA Enabled",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = twoFaEnabledValue,
                                onCheckedChange = { twoFaEnabledValue = it },
                                enabled = !isLoading
                            )
                        }
                    }
                    CredentialType.WALLET -> {
                        // Account Name
                        EditableField(
                            label = "Account Name",
                            value = accountName,
                            onValueChange = { accountName = it },
                            enabled = accountNameEnabled,
                            onEnableClick = { accountNameEnabled = true },
                            isLoading = isLoading
                        )
                        // Private Key (dedicated field)
                        EditableField(
                            label = "Private Key",
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            enabled = privateKeyEnabled,
                            onEnableClick = { privateKeyEnabled = true },
                            isLoading = isLoading,
                            isPassword = true,
                            showPassword = showPrivateKey,
                            onTogglePassword = { showPrivateKey = !showPrivateKey }
                        )
                        // Seed Phrase (dedicated field)
                        EditableField(
                            label = "Seed Phrase / Mnemonic",
                            value = seedPhrase,
                            onValueChange = { seedPhrase = it },
                            enabled = seedPhraseEnabled,
                            onEnableClick = { seedPhraseEnabled = true },
                            isLoading = isLoading,
                            isPassword = true,
                            showPassword = showSeedPhrase,
                            onTogglePassword = { showSeedPhrase = !showSeedPhrase },
                            multiline = true
                        )
                        // Notes (separate from seed phrase)
                        EditableField(
                            label = "Notes",
                            value = notes,
                            onValueChange = { notes = it },
                            enabled = notesEnabled,
                            onEnableClick = { notesEnabled = true },
                            isLoading = isLoading,
                            multiline = true
                        )
                    }
                    CredentialType.STANDARD -> {
                        // No additional fields
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedCredential = credential.copy(
                        username = username,
                        email = email.takeIf { it.isNotBlank() },
                        password = password,
                        notes = notes,
                        backupEmail = backupEmail.takeIf { it.isNotBlank() },
                        phoneNumber = phoneNumber.takeIf { it.isNotBlank() },
                        birthdate = birthdate.takeIf { it.isNotBlank() },
                        recoveryCodes = recoveryCodes.takeIf { it.isNotBlank() },
                        twoFaEnabled = twoFaEnabledValue,
                        accountName = accountName.takeIf { it.isNotBlank() },
                        privateKey = privateKey.takeIf { it.isNotBlank() },
                        seedPhrase = seedPhrase.takeIf { it.isNotBlank() }
                    )
                    onSave(updatedCredential)
                },
                enabled = !isLoading && username.isNotBlank() && (
                    credential.credentialType == CredentialType.WALLET || password.isNotBlank()
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Editable field with pencil icon to enable editing.
 */
@Composable
private fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onEnableClick: () -> Unit,
    isLoading: Boolean,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    multiline: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled && !isLoading,
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 4 else 1,
        visualTransformation = if (isPassword && !showPassword) 
            androidx.compose.ui.text.input.PasswordVisualTransformation() 
        else 
            androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = {
            Row {
                if (isPassword && onTogglePassword != null) {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                }
                if (!enabled) {
                    IconButton(onClick = onEnableClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Enable editing",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
