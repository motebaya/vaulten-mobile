package com.motebaya.vaulten.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing platforms.
 * 
 * Platforms are NOT encrypted as they don't contain sensitive data.
 * 
 * @property id Unique identifier
 * @property name Display name (e.g., "Google")
 * @property domain Optional domain for auto-matching
 * @property iconName Name of the icon resource
 * @property color Hex color code
 * @property type Platform type (social, gaming, email, work, shopping, finance, or custom)
 * @property isCustom Whether this is user-created vs. built-in
 * @property lastNameEditAt Timestamp of last name edit (null if never edited, for 24h restriction)
 * @property createdAt Timestamp when the platform was created
 */
@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val domain: String,
    val iconName: String,
    val color: String,
    val type: String = "social",
    val isCustom: Boolean,
    val lastNameEditAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
