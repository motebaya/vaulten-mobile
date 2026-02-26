package com.motebaya.vaulten.security.crypto

import android.content.Context
import com.motebaya.vaulten.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BIP-39 Passphrase Generator.
 * 
 * Generates cryptographically secure passphrases using the BIP-39 standard wordlist.
 * The wordlist contains 2048 words, and passphrases are 12 or 24 words.
 * 
 * SECURITY:
 * - Uses SecureRandom for cryptographically secure randomness
 * - Wordlist is loaded from resources, not hardcoded
 * - Generated passphrase is NOT stored - only exists in memory
 */
@Singleton
class Bip39Generator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val WORD_COUNT_12 = 12
        const val WORD_COUNT_24 = 24
        private const val WORDLIST_SIZE = 2048
    }

    private val wordlist: List<String> by lazy {
        loadWordlist()
    }

    private val secureRandom = SecureRandom()

    /**
     * Load the BIP-39 wordlist from raw resources.
     */
    private fun loadWordlist(): List<String> {
        val words = mutableListOf<String>()
        context.resources.openRawResource(R.raw.bip39_wordlist).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val word = line?.trim()?.lowercase()
                    if (!word.isNullOrBlank()) {
                        words.add(word)
                    }
                }
            }
        }
        
        require(words.size == WORDLIST_SIZE) {
            "BIP-39 wordlist must contain exactly $WORDLIST_SIZE words, found ${words.size}"
        }
        
        return words.toList()
    }

    /**
     * Generate a random passphrase with the specified word count.
     * 
     * @param wordCount Number of words (12 or 24)
     * @return List of randomly selected words
     */
    fun generatePassphrase(wordCount: Int = WORD_COUNT_12): List<String> {
        require(wordCount == WORD_COUNT_12 || wordCount == WORD_COUNT_24) {
            "Word count must be $WORD_COUNT_12 or $WORD_COUNT_24"
        }
        
        val selectedWords = mutableListOf<String>()
        
        repeat(wordCount) {
            val index = secureRandom.nextInt(WORDLIST_SIZE)
            selectedWords.add(wordlist[index])
        }
        
        return selectedWords.toList()
    }

    /**
     * Generate a passphrase and return as a single space-separated string.
     * 
     * @param wordCount Number of words (12 or 24)
     * @return Space-separated passphrase string
     */
    fun generatePassphraseString(wordCount: Int = WORD_COUNT_12): String {
        return generatePassphrase(wordCount).joinToString(" ")
    }

    /**
     * Validate that a passphrase contains only valid BIP-39 words.
     * 
     * @param words List of words to validate
     * @return true if all words are valid BIP-39 words
     */
    fun validateWords(words: List<String>): Boolean {
        return words.all { word ->
            wordlist.contains(word.lowercase().trim())
        }
    }

    /**
     * Check if the wordlist is loaded and valid.
     */
    fun isWordlistLoaded(): Boolean {
        return try {
            wordlist.size == WORDLIST_SIZE
        } catch (e: Exception) {
            false
        }
    }
}
