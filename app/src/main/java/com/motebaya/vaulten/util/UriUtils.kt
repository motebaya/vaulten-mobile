package com.motebaya.vaulten.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Utility functions for working with Android URIs.
 * 
 * Handles SAF (Storage Access Framework) URIs which don't expose
 * real file paths or names directly through the Uri object.
 */
object UriUtils {
    
    /**
     * Get the display name of a file from its content URI.
     * 
     * This properly handles SAF URIs which return document IDs (like "28")
     * instead of actual filenames when using Uri.lastPathSegment.
     * 
     * @param context Application context for ContentResolver
     * @param uri The content URI to get the filename from
     * @return The display name of the file, or a fallback if unavailable
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName: String? = null
        
        // Try to get the display name from ContentResolver
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fall through to fallback
            }
        }
        
        // Fallback: try to extract from path
        if (fileName == null) {
            fileName = uri.path?.let { path ->
                // Handle document URIs like /document/primary:Download/filename.zip
                if (path.contains(":")) {
                    path.substringAfterLast(":")
                        .substringAfterLast("/")
                } else {
                    path.substringAfterLast("/")
                }
            }?.takeIf { it.isNotEmpty() && !it.all { c -> c.isDigit() } }
        }
        
        return fileName ?: "backup.zip"
    }
    
    /**
     * Get a human-readable location string from a content URI.
     * 
     * This extracts the folder path in a format like "Download" or 
     * "Documents/Backups" from SAF URIs.
     * 
     * @param context Application context for ContentResolver
     * @param uri The content URI to get the location from
     * @return A human-readable location string
     */
    fun getLocationPath(context: Context, uri: Uri): String {
        // First try to get the full path from the URI
        val path = uri.path ?: return "Selected location"
        
        // Handle document URIs like /document/primary:Download/filename.zip
        // or /tree/primary:Download/document/primary:Download/filename.zip
        return when {
            path.contains("/document/") -> {
                val docPath = path.substringAfter("/document/")
                if (docPath.contains(":")) {
                    // Format: primary:Download/filename.zip -> Download
                    val afterColon = docPath.substringAfter(":")
                    val folder = if (afterColon.contains("/")) {
                        afterColon.substringBeforeLast("/")
                    } else {
                        afterColon
                    }
                    folder.replace("%2F", "/").ifEmpty { "Internal Storage" }
                } else {
                    "Selected location"
                }
            }
            else -> "Selected location"
        }
    }
}
