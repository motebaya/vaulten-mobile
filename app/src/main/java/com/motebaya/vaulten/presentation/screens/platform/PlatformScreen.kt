package com.motebaya.vaulten.presentation.screens.platform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motebaya.vaulten.data.local.preferences.PlatformSortField
import com.motebaya.vaulten.domain.entity.Platform
import com.motebaya.vaulten.presentation.components.PageScaffold
import com.motebaya.vaulten.presentation.components.StatsInfoBar
import com.motebaya.vaulten.presentation.components.platform.PlatformIcon
import com.motebaya.vaulten.data.cache.FaviconCache
import com.motebaya.vaulten.security.BiometricAuthHelper
import com.motebaya.vaulten.util.findActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Platform management screen.
 * Hosted inside MainScreen's HorizontalPager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformScreen(
    onLock: () -> Unit,
    onForgotPin: () -> Unit = {},
    isFabVisible: Boolean = true,
    viewModel: PlatformViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val biometricAvailable by viewModel.biometricAvailable.collectAsState()
    val showBiometricPrompt by viewModel.showBiometricPrompt.collectAsState()
    
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Use findActivity() extension to properly unwrap context to FragmentActivity
    val activity = context.findActivity()
    
    // Handle biometric prompt trigger
    LaunchedEffect(showBiometricPrompt) {
        if (showBiometricPrompt && activity != null) {
            val keystoreManager = viewModel.getKeystoreManager()
            val biometricHelper = BiometricAuthHelper(activity, keystoreManager)
            
            biometricHelper.authenticate(
                title = "Delete Platform",
                subtitle = "Use your fingerprint to confirm deletion",
                negativeButtonText = "Use PIN",
                onSuccess = { viewModel.onBiometricSuccessForDelete() },
                onError = { _ -> viewModel.onBiometricCancelled() },
                onCancel = { viewModel.onBiometricCancelled() }
            )
        }
    }

    PageScaffold(
        title = "Platforms",
        onLock = onLock
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                // Search bar with filter button - semi-rounded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Search platforms") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Filter button with badge if filter is active
                    // selectedTypes empty means all are selected (no filter)
                    val hasActiveFilter = uiState.selectedTypes.isNotEmpty() || 
                        uiState.sortField != PlatformSortField.NAME || 
                        !uiState.sortAscending
                    
                    FilledTonalIconButton(
                        onClick = { viewModel.onShowFilterSheet() }
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasActiveFilter) {
                                    Badge()
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter and sort")
                        }
                    }
                }
                
                // Stats bar (same as Dashboard)
                if (!uiState.isLoading) {
                    StatsInfoBar(
                        totalCredentials = uiState.totalCredentials,
                        platformCount = uiState.totalPlatforms
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

                    uiState.platforms.isEmpty() -> {
                        EmptyPlatformState(
                            onAddFirst = { viewModel.onShowAddDialog() }
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp // Extra padding for floating nav bar
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.platforms, key = { it.id }) { platform ->
                                PlatformCard(
                                    platform = platform,
                                    faviconCache = viewModel.faviconCache,
                                    onEdit = { viewModel.onEditPlatformRequest(platform) },
                                    onDelete = { viewModel.onDeletePlatformRequest(platform) }
                                )
                            }
                        }
                    }
                }
            }
            
            // Animated FAB - positioned above floating nav bar
            AnimatedVisibility(
                visible = isFabVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 88.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.onShowAddDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add platform")
                }
            }
        }

        // Filter/Sort Bottom Sheet
        if (uiState.showFilterSheet) {
            FilterSortBottomSheet(
                sheetState = sheetState,
                sortField = uiState.sortField,
                sortAscending = uiState.sortAscending,
                allTypes = uiState.allTypes,
                selectedTypes = uiState.selectedTypes,
                onSortFieldChange = viewModel::onSortFieldChange,
                onSortDirectionChange = viewModel::onSortDirectionChange,
                onTypeToggle = viewModel::onTypeToggle,
                onSelectAllTypes = viewModel::onSelectAllTypes,
                onDismiss = { viewModel.onDismissFilterSheet() }
            )
        }

        // Add Platform Dialog
        if (uiState.showAddDialog) {
            AddPlatformDialog(
                customTypes = uiState.customTypes,
                onDismiss = { viewModel.onDismissAddDialog() },
                onConfirm = { name, domain, type ->
                    viewModel.onAddPlatform(name, domain, type)
                }
            )
        }

        // Delete Confirmation Dialog
        if (uiState.showDeleteDialog && uiState.platformToDelete != null) {
            DeletePlatformDialog(
                platform = uiState.platformToDelete!!,
                isDeleting = uiState.isDeleting,
                error = uiState.deleteError,
                biometricAvailable = biometricAvailable,
                onDismiss = { viewModel.onDismissDeleteDialog() },
                onConfirm = { pin -> viewModel.onDeletePlatformConfirm(pin) },
                onBiometricConfirm = { viewModel.onBiometricRequestedForDelete() },
                onForgotPin = {
                    viewModel.onDismissDeleteDialog()
                    onForgotPin()
                }
            )
        }
        
        // Edit Platform Dialog
        if (uiState.showEditDialog && uiState.platformToEdit != null) {
            EditPlatformDialog(
                platform = uiState.platformToEdit!!,
                isEditing = uiState.isEditing,
                error = uiState.editError,
                onDismiss = { viewModel.onDismissEditDialog() },
                onConfirm = { name, domain, type ->
                    viewModel.onEditPlatformConfirm(name, domain, type)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSortBottomSheet(
    sheetState: SheetState,
    sortField: PlatformSortField,
    sortAscending: Boolean,
    allTypes: List<String>,
    selectedTypes: Set<String>,
    onSortFieldChange: (PlatformSortField) -> Unit,
    onSortDirectionChange: (Boolean) -> Unit,
    onTypeToggle: (String) -> Unit,
    onSelectAllTypes: () -> Unit,
    onDismiss: () -> Unit
) {
    // Empty selectedTypes means "all types selected"
    val isAllSelected = selectedTypes.isEmpty()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sort & Filter",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Sort section
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            PlatformSortField.entries.forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = sortField == field,
                            onClick = { onSortFieldChange(field) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortField == field,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (field) {
                            PlatformSortField.NAME -> "Name"
                            PlatformSortField.CREDENTIAL_COUNT -> "Credential count"
                            PlatformSortField.LAST_CREDENTIAL_ADDED -> "Last credential added"
                            PlatformSortField.CREATED_DATE -> "Created date"
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sort direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Direction:", modifier = Modifier.padding(end = 8.dp))
                FilterChip(
                    selected = sortAscending,
                    onClick = { onSortDirectionChange(true) },
                    label = { Text("Ascending") },
                    leadingIcon = if (sortAscending) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !sortAscending,
                    onClick = { onSortDirectionChange(false) },
                    label = { Text("Descending") },
                    leadingIcon = if (!sortAscending) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Type filter section - Checkbox UI
            Text(
                text = "Filter by type",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // "All" checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAllTypes() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onSelectAllTypes() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "All",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Type checkboxes in a FlowRow for compact display
            if (allTypes.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    allTypes.forEach { type ->
                        // When allSelected (empty set), all types are implicitly selected
                        // When not allSelected, check if type is in selectedTypes
                        val isChecked = isAllSelected || type in selectedTypes
                        
                        // Get display name for default types
                        val displayName = Platform.DEFAULT_TYPES
                            .find { it.first == type }?.second ?: type.replaceFirstChar { it.uppercase() }
                        
                        FilterChip(
                            selected = isChecked,
                            onClick = {
                                if (isAllSelected) {
                                    // Switching from "all" to individual selection
                                    // Select all types except this one (deselect this one)
                                    allTypes.forEach { t ->
                                        if (t != type) onTypeToggle(t)
                                    }
                                } else {
                                    onTypeToggle(type)
                                }
                            },
                            label = { Text(displayName) },
                            leadingIcon = if (isChecked) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            } else {
                Text(
                    text = "No platforms yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reset button
            OutlinedButton(
                onClick = {
                    onSortFieldChange(PlatformSortField.NAME)
                    onSortDirectionChange(true)
                    onSelectAllTypes()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to defaults")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlatformCard(
    platform: PlatformUiModel,
    faviconCache: FaviconCache,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Use the same date formatter as CredentialCard for consistency
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }
    var showContextMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = { showContextMenu = true }
            )
    ) {
        // Use Column layout matching CredentialCard structure
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform icon (matching credential card's 44.dp icon size)
                PlatformIcon(
                    platformName = platform.name,
                    domain = platform.domain,
                    size = 44.dp,
                    faviconCache = faviconCache
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (platform.sanitizedDomain.isNotEmpty()) {
                        Text(
                            text = platform.sanitizedDomain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${platform.credentialCount} credential${if (platform.credentialCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Context menu dropdown
                Box {
                    IconButton(
                        onClick = { showContextMenu = true },
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
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showContextMenu = false
                                onEdit()
                            }
                        )
                        if (platform.isCustom) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { 
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    ) 
                                },
                                onClick = {
                                    showContextMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
            
            // Creation date at bottom - matching CredentialCard exactly
            // Uses same formatter, style, and color as credential cards
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFormatter.format(platform.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmptyPlatformState(onAddFirst: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.GridView,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No platforms yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add your first platform to organize credentials",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onAddFirst) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Platform")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPlatformDialog(
    customTypes: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, domain: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("social") }
    var customType by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showCustomTypeField by remember { mutableStateOf(false) }
    var selectedExistingCustomType by remember { mutableStateOf<String?>(null) }
    
    // Validation for custom type
    val customTypeError = remember(customType) {
        when {
            customType.isBlank() -> null
            customType.length < 2 -> "Type must be at least 2 characters"
            customType.length > 30 -> "Type must be 30 characters or less"
            !customType.matches(Regex("^[a-zA-Z0-9 ]+$")) -> "Only letters, numbers, and spaces allowed"
            else -> null
        }
    }
    
    // Determine final type: existing custom > new custom > selected default
    val finalType = when {
        selectedExistingCustomType != null -> selectedExistingCustomType!!
        showCustomTypeField && customType.isNotBlank() -> customType.trim()
        else -> selectedType
    }
    
    val isValid = name.isNotBlank() && 
        (!showCustomTypeField || selectedExistingCustomType != null || (customType.isNotBlank() && customTypeError == null))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Platform") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Platform Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain (optional)") },
                    placeholder = { Text("e.g., facebook.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Type dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    val displayType = when {
                        selectedExistingCustomType != null -> selectedExistingCustomType!!
                        showCustomTypeField -> "Other"
                        else -> Platform.DEFAULT_TYPES.find { it.first == selectedType }?.second ?: selectedType
                    }
                    OutlinedTextField(
                        value = displayType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Platform.DEFAULT_TYPES.forEach { (typeKey, typeDisplay) ->
                            DropdownMenuItem(
                                text = { Text(typeDisplay) },
                                onClick = {
                                    selectedType = typeKey
                                    showCustomTypeField = false
                                    selectedExistingCustomType = null
                                    expanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Other (custom)") },
                            onClick = {
                                showCustomTypeField = true
                                selectedExistingCustomType = null
                                expanded = false
                            }
                        )
                    }
                }
                
                // Custom type section (when "Other" is selected)
                if (showCustomTypeField) {
                    var existingTypeExpanded by remember { mutableStateOf(false) }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Section: Pick existing custom type (if any exist)
                            if (customTypes.isNotEmpty()) {
                                Text(
                                    text = "Pick existing (custom)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                // Dropdown for existing custom types
                                ExposedDropdownMenuBox(
                                    expanded = existingTypeExpanded,
                                    onExpandedChange = { existingTypeExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedExistingCustomType ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        placeholder = { Text("Select existing type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = existingTypeExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        singleLine = true
                                    )
                                    
                                    ExposedDropdownMenu(
                                        expanded = existingTypeExpanded,
                                        onDismissRequest = { existingTypeExpanded = false }
                                    ) {
                                        // Option to clear selection
                                        if (selectedExistingCustomType != null) {
                                            DropdownMenuItem(
                                                text = { Text("Clear selection", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    selectedExistingCustomType = null
                                                    existingTypeExpanded = false
                                                }
                                            )
                                            HorizontalDivider()
                                        }
                                        
                                        customTypes.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type) },
                                                onClick = {
                                                    selectedExistingCustomType = type
                                                    customType = "" // Clear new custom type input
                                                    existingTypeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                // Divider with "or"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "  or  ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                }
                            }
                            
                            // Section: Create new custom type
                            Text(
                                text = "Create new",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = customType,
                                onValueChange = { 
                                    customType = it
                                    if (it.isNotBlank()) {
                                        selectedExistingCustomType = null // Clear existing selection
                                    }
                                },
                                label = { Text("Custom Type") },
                                placeholder = { Text("e.g., Streaming, Utilities") },
                                isError = customTypeError != null,
                                supportingText = customTypeError?.let { { Text(it) } },
                                singleLine = true,
                                enabled = selectedExistingCustomType == null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, domain, finalType) },
                enabled = isValid
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeletePlatformDialog(
    platform: PlatformUiModel,
    isDeleting: Boolean,
    error: String?,
    biometricAvailable: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit,
    onBiometricConfirm: () -> Unit = {},
    onForgotPin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    
    // Clear PIN on error
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = !isDeleting,
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
                    text = "Delete Platform",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Are you sure you want to delete \"${platform.name}\"?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (platform.credentialCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Warning: This will also delete ${platform.credentialCount} credential${if (platform.credentialCount != 1) "s" else ""} associated with this platform.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
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
                    onSubmit = { onConfirm(pin) },
                    enabled = !isDeleting,
                    showBiometric = biometricAvailable,
                    onBiometric = onBiometricConfirm
                )
                
                if (isDeleting) {
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
                        enabled = !isDeleting
                    ) {
                        Text(
                            text = "Forgot PIN?",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isDeleting
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlatformDialog(
    platform: PlatformUiModel,
    isEditing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String?, domain: String?, type: String?) -> Unit
) {
    var name by remember { mutableStateOf(platform.name) }
    var domain by remember { mutableStateOf(platform.domain) }
    var selectedType by remember { mutableStateOf(platform.type) }
    var customType by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    // Check if current type is a default type
    val isDefaultType = Platform.DEFAULT_TYPES.any { it.first == platform.type }
    var showCustomTypeField by remember { mutableStateOf(!isDefaultType) }
    
    // Initialize custom type if needed
    LaunchedEffect(platform.type) {
        if (!isDefaultType) {
            customType = platform.type
            showCustomTypeField = true
        }
    }
    
    // Validation for custom type
    val customTypeError = remember(customType) {
        when {
            !showCustomTypeField -> null
            customType.isBlank() -> null
            customType.length < 2 -> "Type must be at least 2 characters"
            customType.length > 30 -> "Type must be 30 characters or less"
            !customType.matches(Regex("^[a-zA-Z0-9 ]+$")) -> "Only letters, numbers, and spaces allowed"
            else -> null
        }
    }
    
    val finalType = if (showCustomTypeField) customType.trim() else selectedType
    val nameChanged = name != platform.name
    val canEditName = platform.canEditName
    
    val isValid = name.isNotBlank() && 
        (!showCustomTypeField || (customType.isNotBlank() && customTypeError == null)) &&
        (!nameChanged || canEditName)

    AlertDialog(
        onDismissRequest = { if (!isEditing) onDismiss() },
        title = { Text("Edit Platform") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Info text with credential count
                Text(
                    text = "You are editing \"${platform.name}\" with ${platform.credentialCount} credential${if (platform.credentialCount != 1) "s" else ""}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Name field with 24h restriction
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Platform Name *") },
                    singleLine = true,
                    enabled = !isEditing && canEditName,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (!canEditName && nameChanged) {
                        { Text("Name can be edited again in ${platform.hoursUntilNameEditable} hour(s)", color = MaterialTheme.colorScheme.error) }
                    } else if (!canEditName) {
                        { Text("Name can be edited again in ${platform.hoursUntilNameEditable} hour(s)") }
                    } else null
                )

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain (optional)") },
                    placeholder = { Text("e.g., facebook.com") },
                    singleLine = true,
                    enabled = !isEditing,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Type dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isEditing) expanded = it }
                ) {
                    val displayType = if (showCustomTypeField) "Other" else {
                        Platform.DEFAULT_TYPES.find { it.first == selectedType }?.second ?: selectedType
                    }
                    OutlinedTextField(
                        value = displayType,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isEditing,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Platform.DEFAULT_TYPES.forEach { (typeKey, typeDisplay) ->
                            DropdownMenuItem(
                                text = { Text(typeDisplay) },
                                onClick = {
                                    selectedType = typeKey
                                    showCustomTypeField = false
                                    expanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Other (custom)") },
                            onClick = {
                                showCustomTypeField = true
                                expanded = false
                            }
                        )
                    }
                }
                
                // Custom type input field
                if (showCustomTypeField) {
                    OutlinedTextField(
                        value = customType,
                        onValueChange = { customType = it },
                        label = { Text("Custom Type *") },
                        placeholder = { Text("e.g., Streaming, Utilities") },
                        isError = customTypeError != null,
                        supportingText = customTypeError?.let { { Text(it) } },
                        singleLine = true,
                        enabled = !isEditing,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onConfirm(
                        if (name != platform.name) name else null,
                        if (domain != platform.domain) domain else null,
                        if (finalType != platform.type) finalType else null
                    )
                },
                enabled = isValid && !isEditing
            ) {
                if (isEditing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
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
                enabled = !isEditing
            ) {
                Text("Cancel")
            }
        }
    )
}
