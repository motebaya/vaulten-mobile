package com.motebaya.vaulten.domain.entity

/**
 * Types of credentials supported by the vault.
 * 
 * Each type has different required and optional fields.
 */
enum class CredentialType(val displayName: String) {
    /**
     * Standard credential with username and password.
     * Fields: username, password, email (optional), notes
     */
    STANDARD("Standard"),
    
    /**
     * Social media credential with additional recovery options.
     * Fields: username, email, password, phoneNumber, recoveryEmail, twoFaEnabled
     */
    SOCIAL("Social Media"),
    
    /**
     * Crypto wallet credential with seed phrase and private key.
     * Fields: accountName, privateKey, seedPhrase
     */
    WALLET("Crypto Wallet"),
    
    /**
     * Google account with full Google-specific fields.
     * Fields: name, email, password, backupEmail, phoneNumber, birthdate, twoFaEnabled, recoveryCodes
     */
    GOOGLE("Google Account");
    
    companion object {
        /**
         * Get credential type from string value.
         */
        fun fromString(value: String): CredentialType {
            return entries.find { 
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: STANDARD
        }
        
        /**
         * Detect credential type based on platform name.
         */
        fun detectFromPlatform(platformName: String): CredentialType {
            val lowerName = platformName.lowercase()
            
            return when {
                // Crypto wallets
                lowerName.contains("metamask") ||
                lowerName.contains("coinbase") ||
                lowerName.contains("binance") ||
                lowerName.contains("wallet") ||
                lowerName.contains("crypto") ||
                lowerName.contains("bitcoin") ||
                lowerName.contains("ethereum") -> WALLET
                
                // Google
                lowerName == "google" ||
                lowerName == "gmail" ||
                lowerName.contains("google") -> GOOGLE
                
                // Social media
                lowerName.contains("facebook") ||
                lowerName.contains("instagram") ||
                lowerName.contains("twitter") ||
                lowerName.contains("tiktok") ||
                lowerName.contains("snapchat") ||
                lowerName.contains("linkedin") ||
                lowerName.contains("discord") ||
                lowerName.contains("reddit") ||
                lowerName.contains("threads") -> SOCIAL
                
                // Default
                else -> STANDARD
            }
        }
    }
}

/**
 * Extended credential data for different credential types.
 * This is a sealed class hierarchy for type-safe credential data.
 */
sealed class CredentialData {
    /**
     * Standard credential data.
     */
    data class Standard(
        val username: String,
        val password: String,
        val email: String = "",
        val notes: String = ""
    ) : CredentialData()
    
    /**
     * Social media credential data.
     */
    data class Social(
        val username: String,
        val email: String,
        val password: String,
        val phoneNumber: String = "",
        val recoveryEmail: String = "",
        val twoFaEnabled: Boolean = false,
        val notes: String = ""
    ) : CredentialData()
    
    /**
     * Crypto wallet credential data.
     */
    data class Wallet(
        val accountName: String,
        val privateKey: String = "",
        val seedPhrase: String = "",
        val notes: String = ""
    ) : CredentialData()
    
    /**
     * Google account credential data.
     */
    data class Google(
        val name: String,
        val email: String,
        val password: String,
        val backupEmail: String = "",
        val phoneNumber: String = "",
        val birthdate: String = "",
        val twoFaEnabled: Boolean = false,
        val recoveryCodes: String = "",
        val notes: String = ""
    ) : CredentialData()
}
