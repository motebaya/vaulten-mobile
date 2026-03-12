package com.motebaya.vaulten.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.R

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
 * Menu items used by the floating bottom nav bar.
 * Kept from the old drawer system for data compatibility.
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
 * Lightweight page scaffold for screens hosted inside the MainScreen pager.
 *
 * Provides:
 * - CenterAlignedTopAppBar with app icon (left), title (center), lock icon (right)
 * - Content area with padding values from the top bar
 *
 * FAB is managed by each screen individually (with animated visibility).
 * Replaces the old AppScaffold (which used a ModalNavigationDrawer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onLock: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
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
                    // App icon on the left
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
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
                },
                actions = {
                    // Lock icon on the right
                    IconButton(onClick = onLock) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Lock vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        content(padding)
    }
}

/**
 * App header component with centered title.
 * Used when PageScaffold is not appropriate (e.g., detail screens).
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
