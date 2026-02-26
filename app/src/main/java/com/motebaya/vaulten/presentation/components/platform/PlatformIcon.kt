package com.motebaya.vaulten.presentation.components.platform

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motebaya.vaulten.data.cache.FaviconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform icon component that displays an appropriate icon based on platform name/domain.
 * 
 * Icon Resolution Order:
 * 1. Check built-in icons (social, gaming, etc.)
 * 2. Check cached favicon from domain
 * 3. Fetch favicon from Google's service (async, shows placeholder while loading)
 * 4. Fall back to first letter of platform name
 */
@Composable
fun PlatformIcon(
    platformName: String,
    domain: String = "",
    size: Dp = 40.dp,
    faviconCache: FaviconCache? = null,
    modifier: Modifier = Modifier
) {
    val iconData = getPlatformIconData(platformName, domain)
    
    // If we have a built-in icon, use it directly
    if (iconData.hasBuiltInIcon) {
        BuiltInPlatformIcon(
            iconData = iconData,
            platformName = platformName,
            size = size,
            modifier = modifier
        )
    } else if (domain.isNotBlank() && faviconCache != null) {
        // Try to load favicon from cache or network
        FaviconPlatformIcon(
            domain = domain,
            platformName = platformName,
            faviconCache = faviconCache,
            fallbackData = iconData,
            size = size,
            modifier = modifier
        )
    } else {
        // Use letter fallback
        BuiltInPlatformIcon(
            iconData = iconData,
            platformName = platformName,
            size = size,
            modifier = modifier
        )
    }
}

/**
 * Platform icon with favicon loading support.
 * 
 * Background behavior:
 * - If favicon loaded: NO background (transparent) - render favicon as-is
 * - If loading/failed: neutral surfaceVariant background with letter
 */
@Composable
private fun FaviconPlatformIcon(
    domain: String,
    platformName: String,
    faviconCache: FaviconCache,
    fallbackData: PlatformIconData,
    size: Dp,
    modifier: Modifier = Modifier
) {
    var favicon by remember(domain) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(domain) { mutableStateOf(true) }
    
    // Derive cache key and fetch URL from domain input
    val cacheKey = remember(domain) { FaviconCache.toDisplayHost(domain) }
    val fetchUrl = remember(domain) { FaviconCache.toFetchUrl(domain) }
    
    // Neutral background for loading/fallback states
    val neutralBackground = MaterialTheme.colorScheme.surfaceVariant
    
    // Load favicon on composition
    LaunchedEffect(domain) {
        if (cacheKey.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        
        isLoading = true
        
        // First check cache
        val cached = faviconCache.getCachedFavicon(cacheKey)
        if (cached != null) {
            favicon = cached
            isLoading = false
            return@LaunchedEffect
        }
        
        // Fetch from network on IO thread, then update state on Main
        val fetched = withContext(Dispatchers.IO) {
            faviconCache.fetchAndCacheFavicon(cacheKey, fetchUrl)
        }
        // State update happens on Main thread (LaunchedEffect context)
        favicon = fetched
        isLoading = false
    }
    
    // Determine background: transparent for favicon, neutral for loading/fallback
    val backgroundColor = if (favicon != null) Color.Transparent else neutralBackground
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            favicon != null -> {
                // Favicon loaded - render as-is without background
                Image(
                    bitmap = favicon!!.asImageBitmap(),
                    contentDescription = platformName,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            isLoading -> {
                // Show loading placeholder (letter) with neutral background
                Text(
                    text = platformName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (size.value * 0.4f).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                // Failed to load, show fallback letter with neutral background
                Text(
                    text = fallbackData.letter,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (size.value * 0.4f).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Built-in platform icon (no network loading).
 */
@Composable
private fun BuiltInPlatformIcon(
    iconData: PlatformIconData,
    platformName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(iconData.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (iconData.icon != null) {
            Icon(
                imageVector = iconData.icon,
                contentDescription = platformName,
                modifier = Modifier.size(size * 0.5f),
                tint = iconData.iconColor
            )
        } else {
            Text(
                text = iconData.letter,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold
                ),
                color = iconData.iconColor
            )
        }
    }
}

/**
 * Data class for platform icon styling.
 */
private data class PlatformIconData(
    val icon: ImageVector? = null,
    val letter: String = "",
    val backgroundColor: Color,
    val iconColor: Color = Color.White,
    val hasBuiltInIcon: Boolean = true
)

/**
 * Get icon data based on platform name and domain.
 */
private fun getPlatformIconData(platformName: String, domain: String): PlatformIconData {
    val lowerName = platformName.lowercase()
    val lowerDomain = domain.lowercase()
    
    return when {
        // Social Media
        lowerName.contains("facebook") || lowerDomain.contains("facebook") ->
            PlatformIconData(Icons.Default.Facebook, backgroundColor = Color(0xFF1877F2))
        
        lowerName.contains("twitter") || lowerName.contains("x.com") || lowerDomain.contains("twitter") ->
            PlatformIconData(letter = "X", backgroundColor = Color(0xFF000000))
        
        lowerName.contains("instagram") || lowerDomain.contains("instagram") ->
            PlatformIconData(Icons.Default.CameraAlt, backgroundColor = Color(0xFFE4405F))
        
        lowerName.contains("linkedin") || lowerDomain.contains("linkedin") ->
            PlatformIconData(letter = "in", backgroundColor = Color(0xFF0A66C2))
        
        lowerName.contains("tiktok") || lowerDomain.contains("tiktok") ->
            PlatformIconData(Icons.Default.MusicNote, backgroundColor = Color(0xFF000000))
        
        lowerName.contains("discord") || lowerDomain.contains("discord") ->
            PlatformIconData(Icons.AutoMirrored.Filled.Chat, backgroundColor = Color(0xFF5865F2))
        
        lowerName.contains("reddit") || lowerDomain.contains("reddit") ->
            PlatformIconData(letter = "R", backgroundColor = Color(0xFFFF4500))
        
        lowerName.contains("snapchat") || lowerDomain.contains("snapchat") ->
            PlatformIconData(Icons.Default.CameraAlt, backgroundColor = Color(0xFFFFFC00), iconColor = Color.Black)
        
        // Email
        lowerName.contains("gmail") || lowerName.contains("google") || lowerDomain.contains("google") ->
            PlatformIconData(Icons.Default.Mail, backgroundColor = Color(0xFF4285F4))
        
        lowerName.contains("outlook") || lowerName.contains("microsoft") || lowerDomain.contains("microsoft") ->
            PlatformIconData(Icons.Default.Mail, backgroundColor = Color(0xFF0078D4))
        
        // Gaming
        lowerName.contains("steam") || lowerDomain.contains("steam") ->
            PlatformIconData(Icons.Default.Games, backgroundColor = Color(0xFF171A21))
        
        lowerName.contains("epic") || lowerDomain.contains("epic") ->
            PlatformIconData(Icons.Default.Games, backgroundColor = Color(0xFF2F2F2F))
        
        lowerName.contains("playstation") || lowerDomain.contains("playstation") ->
            PlatformIconData(Icons.Default.Games, backgroundColor = Color(0xFF003791))
        
        lowerName.contains("xbox") || lowerDomain.contains("xbox") ->
            PlatformIconData(Icons.Default.Games, backgroundColor = Color(0xFF107C10))
        
        // Crypto
        lowerName.contains("metamask") ->
            PlatformIconData(Icons.Default.AccountBalanceWallet, backgroundColor = Color(0xFFF6851B))
        
        lowerName.contains("coinbase") || lowerDomain.contains("coinbase") ->
            PlatformIconData(Icons.Default.CurrencyBitcoin, backgroundColor = Color(0xFF0052FF))
        
        lowerName.contains("binance") || lowerDomain.contains("binance") ->
            PlatformIconData(Icons.Default.CurrencyBitcoin, backgroundColor = Color(0xFFF0B90B), iconColor = Color.Black)
        
        // Shopping
        lowerName.contains("amazon") || lowerDomain.contains("amazon") ->
            PlatformIconData(Icons.Default.ShoppingCart, backgroundColor = Color(0xFFFF9900), iconColor = Color.Black)
        
        lowerName.contains("ebay") || lowerDomain.contains("ebay") ->
            PlatformIconData(Icons.Default.ShoppingCart, backgroundColor = Color(0xFFE53238))
        
        // Streaming
        lowerName.contains("netflix") || lowerDomain.contains("netflix") ->
            PlatformIconData(letter = "N", backgroundColor = Color(0xFFE50914))
        
        lowerName.contains("spotify") || lowerDomain.contains("spotify") ->
            PlatformIconData(Icons.Default.MusicNote, backgroundColor = Color(0xFF1DB954))
        
        lowerName.contains("youtube") || lowerDomain.contains("youtube") ->
            PlatformIconData(Icons.Default.PlayArrow, backgroundColor = Color(0xFFFF0000))
        
        // Cloud
        lowerName.contains("dropbox") || lowerDomain.contains("dropbox") ->
            PlatformIconData(Icons.Default.Cloud, backgroundColor = Color(0xFF0061FF))
        
        lowerName.contains("github") || lowerDomain.contains("github") ->
            PlatformIconData(Icons.Default.Code, backgroundColor = Color(0xFF181717))
        
        // Finance
        lowerName.contains("paypal") || lowerDomain.contains("paypal") ->
            PlatformIconData(Icons.Default.Payment, backgroundColor = Color(0xFF003087))
        
        // Default - use first letter with generated color (allow favicon fetch)
        else -> {
            val letter = platformName.firstOrNull()?.uppercase() ?: "?"
            val color = generateColorFromName(platformName)
            PlatformIconData(
                letter = letter, 
                backgroundColor = color,
                hasBuiltInIcon = false  // Allow favicon fetch for unknown platforms
            )
        }
    }
}

/**
 * Generate a consistent color based on the platform name.
 */
private fun generateColorFromName(name: String): Color {
    val colors = listOf(
        Color(0xFFEF4444), // red
        Color(0xFFF97316), // orange
        Color(0xFFEAB308), // yellow
        Color(0xFF22C55E), // green
        Color(0xFF14B8A6), // teal
        Color(0xFF3B82F6), // blue
        Color(0xFF8B5CF6), // violet
        Color(0xFFEC4899)  // pink
    )
    val hash = name.lowercase().hashCode()
    return colors[kotlin.math.abs(hash) % colors.size]
}
