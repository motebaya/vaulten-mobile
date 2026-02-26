package com.motebaya.vaulten.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FaviconCache"

/**
 * Manages favicon caching for platform icons.
 * 
 * Features:
 * - Stores favicons in app's cache directory
 * - Uses Google's favicon service for fetching
 * - 30-day TTL before refresh
 * - 24-hour negative cache for failed fetches
 * - Follows HTTP redirects (Google redirects to gstatic.com)
 * - Non-blocking async loading
 * 
 * IMPORTANT: Google's favicon service requires a URL with protocol:
 * - Works: domain=https://phantom.com
 * - Fails: domain=phantom.com (returns 404)
 * 
 * NETWORK: This is the ONLY allowed network call in the app.
 * Used solely for favicon fetching from Google's service.
 */
@Singleton
class FaviconCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FAVICON_SIZE = 64
        private const val CACHE_TTL_DAYS = 30L
        private const val NEGATIVE_CACHE_HOURS = 24L
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 10000
        private const val FAVICON_PREFIX = "favicon_"
        private const val FAVICON_SUFFIX = ".png"
        private const val NEGATIVE_CACHE_FILE = "favicon_negative_cache.txt"
        
        /**
         * Convert domain input to fetch URL with protocol.
         * - If already has http/https, keep as-is
         * - Otherwise, prefix with https://
         */
        fun toFetchUrl(domain: String): String {
            val trimmed = domain.trim()
            if (trimmed.isBlank()) return ""
            
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "https://$trimmed"
            }
        }
        
        /**
         * Extract display hostname from domain input.
         * Used for UI display and duplicate detection.
         */
        fun toDisplayHost(domain: String): String {
            return domain
                .trim()
                .lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .removeSuffix("/")
                .takeWhile { it != '/' && it != '?' }
        }
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, "favicons").also { it.mkdirs() }
    }
    
    // In-memory negative cache: cacheKey -> timestamp of failure
    private val negativeCache = ConcurrentHashMap<String, Long>()
    
    init {
        // Load negative cache from disk on init
        loadNegativeCache()
    }
    
    /**
     * Get a cached favicon for the given domain.
     * 
     * @param cacheKey The normalized hostname to use as cache key (e.g., "google.com")
     * @return Bitmap if cached and not expired, null otherwise
     */
    suspend fun getCachedFavicon(cacheKey: String): Bitmap? = withContext(Dispatchers.IO) {
        val normalizedKey = normalizeCacheKey(cacheKey)
        val cacheFile = getCacheFile(normalizedKey)
        
        if (!cacheFile.exists()) {
            return@withContext null
        }
        
        // Check TTL
        val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
        val maxAgeMs = TimeUnit.DAYS.toMillis(CACHE_TTL_DAYS)
        
        if (ageMs > maxAgeMs) {
            // Expired, delete and return null
            cacheFile.delete()
            return@withContext null
        }
        
        try {
            BitmapFactory.decodeFile(cacheFile.absolutePath)
        } catch (e: Exception) {
            cacheFile.delete()
            null
        }
    }
    
    /**
     * Fetch and cache a favicon for the given domain.
     * 
     * Uses Google's favicon service with URL-with-protocol format:
     * https://www.google.com/s2/favicons?sz=64&domain=https://example.com
     * 
     * @param cacheKey The normalized hostname for cache key (e.g., "phantom.com")
     * @param fetchUrl The full URL with protocol for fetching (e.g., "https://phantom.com")
     * @return Bitmap if fetch succeeded, null otherwise
     */
    suspend fun fetchAndCacheFavicon(cacheKey: String, fetchUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val normalizedKey = normalizeCacheKey(cacheKey)
        
        if (normalizedKey.isBlank() || fetchUrl.isBlank()) {
            Log.d(TAG, "fetchAndCacheFavicon: cacheKey or fetchUrl is blank")
            return@withContext null
        }
        
        // Check negative cache
        if (isInNegativeCache(normalizedKey)) {
            Log.d(TAG, "fetchAndCacheFavicon: $normalizedKey is in negative cache, skipping")
            return@withContext null
        }
        
        // URL-encode the fetchUrl for the domain parameter
        val encodedUrl = URLEncoder.encode(fetchUrl, "UTF-8")
        val requestUrl = "https://www.google.com/s2/favicons?sz=$FAVICON_SIZE&domain=$encodedUrl"
        Log.d(TAG, "fetchAndCacheFavicon: fetching from $requestUrl")
        
        try {
            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true  // Follow redirects to gstatic.com
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Vaulten/1.0")
            connection.setRequestProperty("Accept", "image/*")
            
            try {
                connection.connect()
                
                val responseCode = connection.responseCode
                val finalUrl = connection.url.toString()
                Log.d(TAG, "fetchAndCacheFavicon: response code = $responseCode, final URL = $finalUrl")
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "fetchAndCacheFavicon: HTTP $responseCode for $normalizedKey")
                    addToNegativeCache(normalizedKey)
                    return@withContext null
                }
                
                // Read response bytes
                val inputStream = connection.inputStream
                val bytes = inputStream.readBytes()
                inputStream.close()
                
                Log.d(TAG, "fetchAndCacheFavicon: received ${bytes.size} bytes for $normalizedKey")
                
                // Check if we got a valid image (not empty or too small)
                if (bytes.size < 100) {
                    Log.w(TAG, "fetchAndCacheFavicon: response too small (${bytes.size} bytes), likely placeholder")
                    addToNegativeCache(normalizedKey)
                    return@withContext null
                }
                
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                if (bitmap != null) {
                    Log.d(TAG, "fetchAndCacheFavicon: decoded bitmap ${bitmap.width}x${bitmap.height} for $normalizedKey")
                    // Cache the bitmap
                    saveToCacheFile(normalizedKey, bitmap)
                    // Remove from negative cache if previously failed
                    negativeCache.remove(normalizedKey)
                } else {
                    Log.w(TAG, "fetchAndCacheFavicon: BitmapFactory.decodeByteArray returned null for $normalizedKey")
                    addToNegativeCache(normalizedKey)
                }
                
                bitmap
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndCacheFavicon: exception for $normalizedKey - ${e.javaClass.simpleName}: ${e.message}")
            addToNegativeCache(normalizedKey)
            null
        }
    }
    
    /**
     * Check if a favicon is cached (regardless of expiry).
     */
    fun hasCachedFavicon(cacheKey: String): Boolean {
        return getCacheFile(normalizeCacheKey(cacheKey)).exists()
    }
    
    /**
     * Clear all cached favicons.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        negativeCache.clear()
        saveNegativeCache()
    }
    
    /**
     * Get cache size in bytes.
     */
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    private fun getCacheFile(normalizedKey: String): File {
        val safeKey = normalizedKey.replace(".", "_").replace("/", "_")
        return File(cacheDir, "$FAVICON_PREFIX${safeKey}_$FAVICON_SIZE$FAVICON_SUFFIX")
    }
    
    private fun saveToCacheFile(normalizedKey: String, bitmap: Bitmap) {
        try {
            val cacheFile = getCacheFile(normalizedKey)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "saveToCacheFile: saved to ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "saveToCacheFile: failed - ${e.message}")
        }
    }
    
    /**
     * Normalize cache key to canonical hostname.
     * Both "https://www.example.com/path" and "example.com" should map to "example.com"
     */
    private fun normalizeCacheKey(input: String): String {
        return input
            .trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .removeSuffix("/")
            .takeWhile { it != '/' && it != '?' }  // Remove path and query
    }
    
    private fun isInNegativeCache(normalizedKey: String): Boolean {
        val failureTime = negativeCache[normalizedKey] ?: return false
        val ageMs = System.currentTimeMillis() - failureTime
        val maxAgeMs = TimeUnit.HOURS.toMillis(NEGATIVE_CACHE_HOURS)
        
        if (ageMs > maxAgeMs) {
            // Expired, remove from negative cache
            negativeCache.remove(normalizedKey)
            return false
        }
        return true
    }
    
    private fun addToNegativeCache(normalizedKey: String) {
        negativeCache[normalizedKey] = System.currentTimeMillis()
        saveNegativeCache()
    }
    
    private fun loadNegativeCache() {
        try {
            val file = File(cacheDir, NEGATIVE_CACHE_FILE)
            if (!file.exists()) return
            
            file.readLines().forEach { line ->
                val parts = line.split("=")
                if (parts.size == 2) {
                    val key = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: return@forEach
                    negativeCache[key] = timestamp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadNegativeCache: failed - ${e.message}")
        }
    }
    
    private fun saveNegativeCache() {
        try {
            val file = File(cacheDir, NEGATIVE_CACHE_FILE)
            val content = negativeCache.entries.joinToString("\n") { "${it.key}=${it.value}" }
            file.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "saveNegativeCache: failed - ${e.message}")
        }
    }
}
