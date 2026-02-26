package com.motebaya.vaulten.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.BuildConfig
import com.motebaya.vaulten.R
import com.motebaya.vaulten.presentation.theme.VaultThemeExtras
import kotlinx.coroutines.launch

/**
 * Navigation menu item data class.
 */
data class DrawerMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

/**
 * Drawer menu items for the app.
 */
object DrawerMenuItems {
    val Credentials = DrawerMenuItem(
        id = "credentials",
        label = "Credentials",
        icon = Icons.Outlined.Key,
        selectedIcon = Icons.Filled.Key
    )
    val Platforms = DrawerMenuItem(
        id = "platforms",
        label = "Platforms",
        icon = Icons.Outlined.GridView,
        selectedIcon = Icons.Filled.GridView
    )
    val Settings = DrawerMenuItem(
        id = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings
    )
    val Support = DrawerMenuItem(
        id = "support",
        label = "Support",
        icon = Icons.AutoMirrored.Outlined.HelpOutline,
        selectedIcon = Icons.AutoMirrored.Filled.Help
    )
    val Lock = DrawerMenuItem(
        id = "lock",
        label = "Lock Vault",
        icon = Icons.Outlined.Lock,
        selectedIcon = Icons.Filled.Lock
    )
    
    val navigationItems = listOf(Credentials, Platforms, Settings, Support)
}

/**
 * Main app layout with hamburger drawer navigation.
 * 
 * Features:
 * - Hidden drawer that opens on hamburger tap
 * - Horizontal menu items with [ICON] [TEXT] layout
 * - Centered page title
 * - App icon at top of drawer
 * - Lock button at bottom of drawer
 * 
 * Used for all authenticated screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLock: () -> Unit,
    title: String = "",
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scope = rememberCoroutineScope()
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = skeuoColors.surfaceCard
            ) {
                DrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        onNavigate(route)
                    },
                    onLock = {
                        scope.launch { drawerState.close() }
                        onLock()
                    }
                )
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hamburger menu button
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open menu"
                                )
                            }
                            
                            // App icon next to hamburger
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_icon),
                                    contentDescription = "Vaulten",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = floatingActionButton,
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            content(padding)
        }
    }
}

/**
 * Drawer content with menu items.
 */
@Composable
private fun DrawerContent(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLock: () -> Unit
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        // Header with app icon and name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "Vaulten",
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Column {
                Text(
                    text = "Vaulten",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Secure Password Manager",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Navigation Items
        DrawerMenuItems.navigationItems.forEach { item ->
            val isSelected = when (item.id) {
                "credentials" -> currentRoute == "dashboard"
                "platforms" -> currentRoute == "platforms"
                "settings" -> currentRoute == "settings"
                "support" -> currentRoute == "support"
                else -> false
            }
            
            DrawerNavItem(
                item = item,
                isSelected = isSelected,
                onClick = {
                    val route = when (item.id) {
                        "credentials" -> "dashboard"
                        "platforms" -> "platforms"
                        "settings" -> "settings"
                        "support" -> "support"
                        else -> "dashboard"
                    }
                    onNavigate(route)
                }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Version info
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // Lock button at bottom
        DrawerNavItem(
            item = DrawerMenuItems.Lock,
            isSelected = false,
            onClick = onLock,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Individual drawer navigation item with horizontal [ICON] [TEXT] layout.
 */
@Composable
private fun DrawerNavItem(
    item: DrawerMenuItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    // Use secondaryContainer for selected state - provides better contrast than primaryContainer
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = tint ?: if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(24.dp),
            tint = contentColor
        )
        
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}

/**
 * App header component with centered title.
 * Used when AppScaffold is not appropriate (e.g., detail screens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
