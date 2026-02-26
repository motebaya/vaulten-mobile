package com.motebaya.vaulten.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a platform/service where credentials are stored.
 * 
 * Platforms are used to group credentials and provide visual identification
 * (icons, colors) in the UI.
 * 
 * @property id Unique identifier
 * @property name Display name (e.g., "Google", "GitHub")
 * @property domain Optional domain for auto-matching (e.g., "google.com")
 * @property iconName Name of the icon resource to display
 * @property color Hex color code for the platform (e.g., "#4285F4" for Google)
 * @property type Platform type (social, gaming, email, work, shopping, finance, or custom)
 * @property isCustom Whether this is a user-created platform vs. built-in
 * @property credentialCount Number of credentials for this platform (computed)
 * @property lastCredentialAdded When the last credential was added (computed)
 * @property lastNameEditAt When the platform name was last edited (for 24h restriction)
 * @property createdAt When the platform was created
 */
data class Platform(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val domain: String = "",
    val iconName: String = "default",
    val color: String = "#6B7280",
    val type: String = "social",
    val isCustom: Boolean = true,
    val credentialCount: Int = 0,
    val lastCredentialAdded: Instant? = null,
    val lastNameEditAt: Instant? = null,
    val createdAt: Instant = Instant.now()
) {
    companion object {
        /**
         * Creates a custom platform with default styling.
         */
        fun custom(name: String, domain: String = "", type: String = "social"): Platform {
            return Platform(
                name = name,
                domain = domain,
                iconName = "default",
                color = generateColorFromName(name),
                type = type,
                isCustom = true
            )
        }

        /**
         * Generate a consistent color based on the platform name.
         */
        private fun generateColorFromName(name: String): String {
            val colors = listOf(
                "#EF4444", // red
                "#F97316", // orange
                "#EAB308", // yellow
                "#22C55E", // green
                "#14B8A6", // teal
                "#3B82F6", // blue
                "#8B5CF6", // violet
                "#EC4899"  // pink
            )
            val hash = name.lowercase().hashCode()
            return colors[Math.abs(hash) % colors.size]
        }

        /**
         * Default platform types available.
         */
        val DEFAULT_TYPES = listOf(
            "social" to "Social Media",
            "gaming" to "Gaming",
            "email" to "Email",
            "work" to "Work",
            "shopping" to "Shopping",
            "finance" to "Finance"
        )

        /**
         * Built-in platforms for common services.
         */
        val BUILT_IN_PLATFORMS = listOf(
            Platform("google", "Google", "google.com", "google", "#4285F4", "email", false),
            Platform("github", "GitHub", "github.com", "github", "#181717", "work", false),
            Platform("facebook", "Facebook", "facebook.com", "facebook", "#1877F2", "social", false),
            Platform("twitter", "Twitter/X", "twitter.com", "twitter", "#1DA1F2", "social", false),
            Platform("amazon", "Amazon", "amazon.com", "amazon", "#FF9900", "shopping", false),
            Platform("apple", "Apple", "apple.com", "apple", "#000000", "work", false),
            Platform("microsoft", "Microsoft", "microsoft.com", "microsoft", "#00A4EF", "work", false),
            Platform("linkedin", "LinkedIn", "linkedin.com", "linkedin", "#0A66C2", "social", false),
            Platform("netflix", "Netflix", "netflix.com", "netflix", "#E50914", "shopping", false),
            Platform("spotify", "Spotify", "spotify.com", "spotify", "#1DB954", "shopping", false),
            Platform("discord", "Discord", "discord.com", "discord", "#5865F2", "social", false),
            Platform("slack", "Slack", "slack.com", "slack", "#4A154B", "work", false),
            Platform("dropbox", "Dropbox", "dropbox.com", "dropbox", "#0061FF", "work", false),
            Platform("paypal", "PayPal", "paypal.com", "paypal", "#003087", "finance", false),
        )
    }
}
