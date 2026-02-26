package com.motebaya.vaulten.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Centralized date/time formatting utility for the Vault app.
 * 
 * Ensures consistent formatting across all screens and components.
 */
object VaultDateFormatter {
    
    private val dateTimeFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    
    private val dateOnlyFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
    
    private val shortDateTimeFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    
    private val backupFileFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(ZoneId.systemDefault())
    
    /**
     * Format an instant as a human-readable date and time.
     * Example: "January 15, 2025, 3:45 PM"
     * 
     * @param instant The instant to format, or null
     * @return Formatted string, or "Unknown" if null
     */
    fun formatDateTime(instant: Instant?): String {
        return instant?.let { dateTimeFormatter.format(it) } ?: "Unknown"
    }
    
    /**
     * Format an instant as a human-readable date only.
     * Example: "January 15, 2025"
     * 
     * @param instant The instant to format, or null
     * @return Formatted string, or "Unknown" if null
     */
    fun formatDate(instant: Instant?): String {
        return instant?.let { dateOnlyFormatter.format(it) } ?: "Unknown"
    }
    
    /**
     * Format an instant as a short date/time for compact display.
     * Example: "1/15/25, 3:45 PM"
     * 
     * @param instant The instant to format, or null
     * @return Formatted string, or "Unknown" if null
     */
    fun formatShortDateTime(instant: Instant?): String {
        return instant?.let { shortDateTimeFormatter.format(it) } ?: "Unknown"
    }
    
    /**
     * Format an instant for use in backup filenames.
     * Format: "vault-backup_yyyy-MM-dd_HH-mm-ss.zip"
     * Example: "vault-backup_2025-01-15_15-45-30.zip"
     * 
     * @param instant The instant to format
     * @return Formatted string suitable for backup filenames
     */
    fun formatForBackupFilename(instant: Instant): String {
        return "vault-backup_${backupFileFormatter.format(instant)}.zip"
    }
    
    /**
     * Format seconds as HH:MM:SS countdown string.
     * 
     * @param totalSeconds Total seconds remaining
     * @return Formatted string like "23:45:30"
     */
    fun formatCountdown(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Format seconds as MM:SS countdown string (for shorter durations).
     * 
     * @param totalSeconds Total seconds remaining
     * @return Formatted string like "05:30"
     */
    fun formatShortCountdown(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
