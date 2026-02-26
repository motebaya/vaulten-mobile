package com.motebaya.vaulten.domain.validation

/**
 * Validation utilities for credential fields.
 * 
 * Provides validators for various credential field types including
 * seed phrases, backup codes, phone numbers, and email addresses.
 */
object CredentialValidators {

    /**
     * Validates a seed phrase (crypto wallet recovery phrase).
     * 
     * Requirements:
     * - Must contain 12-24 words
     * - Words must contain only letters (no numbers or special characters)
     * - Words are separated by spaces
     * 
     * @param seedPhrase The seed phrase to validate
     * @return ValidationResult with success or error message
     */
    fun validateSeedPhrase(seedPhrase: String): ValidationResult {
        val trimmed = seedPhrase.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("Seed phrase is required")
        }
        
        // Split by whitespace and filter empty strings
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        // Check word count (12, 15, 18, 21, or 24 words are valid BIP39 lengths)
        if (words.size < 12 || words.size > 24) {
            return ValidationResult.Error("Seed phrase must contain 12-24 words")
        }
        
        // Check each word contains only letters
        val invalidWords = words.filter { word -> !word.all { it.isLetter() } }
        if (invalidWords.isNotEmpty()) {
            return ValidationResult.Error("Seed phrase words can only contain letters")
        }
        
        return ValidationResult.Success
    }

    /**
     * Validates backup codes (Google/2FA recovery codes).
     * 
     * Requirements:
     * - Can only contain digits and spaces
     * - At least one code must be present
     * 
     * @param backupCodes The backup codes to validate
     * @return ValidationResult with success or error message
     */
    fun validateBackupCodes(backupCodes: String): ValidationResult {
        val trimmed = backupCodes.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("Backup codes are required")
        }
        
        // Check for valid characters (digits and spaces only)
        if (!trimmed.all { it.isDigit() || it.isWhitespace() }) {
            return ValidationResult.Error("Backup codes can only contain numbers and spaces")
        }
        
        // Ensure there's at least one digit
        if (!trimmed.any { it.isDigit() }) {
            return ValidationResult.Error("At least one backup code is required")
        }
        
        return ValidationResult.Success
    }

    /**
     * Validates a phone number.
     * 
     * Requirements:
     * - Can only contain digits, spaces, and plus sign
     * - Must have at least 5 digits
     * 
     * @param phone The phone number to validate
     * @return ValidationResult with success or error message
     */
    fun validatePhone(phone: String): ValidationResult {
        val trimmed = phone.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Success // Phone is often optional
        }
        
        // Check for valid characters (digits, spaces, plus sign only)
        if (!trimmed.all { it.isDigit() || it.isWhitespace() || it == '+' }) {
            return ValidationResult.Error("Phone number can only contain digits, spaces, and +")
        }
        
        // Count digits
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount < 5) {
            return ValidationResult.Error("Phone number must have at least 5 digits")
        }
        
        // Plus sign can only appear at the beginning
        val plusIndex = trimmed.indexOf('+')
        if (plusIndex > 0) {
            return ValidationResult.Error("Plus sign (+) can only appear at the start")
        }
        
        return ValidationResult.Success
    }

    /**
     * Validates an email address.
     * 
     * Uses a simplified RFC 5322 pattern that covers most real-world email addresses.
     * 
     * @param email The email address to validate
     * @return ValidationResult with success or error message
     */
    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("Email address is required")
        }
        
        // Simplified email regex pattern (covers most real-world cases)
        val emailPattern = Regex(
            "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*\$"
        )
        
        if (!emailPattern.matches(trimmed)) {
            return ValidationResult.Error("Please enter a valid email address")
        }
        
        return ValidationResult.Success
    }

    /**
     * Validates a platform type.
     * 
     * Requirements:
     * - Must be 2-30 characters
     * - Can only contain letters, numbers, and spaces
     * 
     * @param type The platform type to validate
     * @return ValidationResult with success or error message
     */
    fun validatePlatformType(type: String): ValidationResult {
        val trimmed = type.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("Type is required")
        }
        
        if (trimmed.length < 2) {
            return ValidationResult.Error("Type must be at least 2 characters")
        }
        
        if (trimmed.length > 30) {
            return ValidationResult.Error("Type must be 30 characters or less")
        }
        
        if (!trimmed.all { it.isLetterOrDigit() || it.isWhitespace() }) {
            return ValidationResult.Error("Type can only contain letters, numbers, and spaces")
        }
        
        return ValidationResult.Success
    }

    /**
     * Validates a username/identifier.
     * 
     * Requirements:
     * - Only allows letters, numbers, underscores, hyphens, dots, and @ symbol
     * - Rejects other special characters like *&^%$#*?/ etc.
     * 
     * @param username The username to validate
     * @param required Whether the field is required
     * @return ValidationResult with success or error message
     */
    fun validateUsername(username: String, required: Boolean = true): ValidationResult {
        val trimmed = username.trim()
        
        if (trimmed.isEmpty()) {
            return if (required) {
                ValidationResult.Error("Username is required")
            } else {
                ValidationResult.Success
            }
        }
        
        if (trimmed.length > 256) {
            return ValidationResult.Error("Username is too long")
        }
        
        // Only allow letters, numbers, underscores, hyphens, dots, and @ symbol
        // Reject punctuation like *&^%$#*?/ etc.
        val allowedCharsPattern = Regex("^[a-zA-Z0-9._@-]+$")
        if (!allowedCharsPattern.matches(trimmed)) {
            return ValidationResult.Error("Username can only contain letters, numbers, @ . _ -")
        }
        
        return ValidationResult.Success
    }

    /**
     * Validates a password.
     * 
     * @param password The password to validate
     * @return ValidationResult with success or error message
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.isEmpty()) {
            return ValidationResult.Error("Password is required")
        }
        
        // No length limit for passwords - user may have legitimate long passwords
        
        return ValidationResult.Success
    }
    
    /**
     * Validates a crypto private key.
     * 
     * Requirements:
     * - Must be 32-200 characters
     * - Can contain alphanumeric and common private key characters
     * 
     * @param privateKey The private key to validate
     * @return ValidationResult with success or error message
     */
    fun validatePrivateKey(privateKey: String): ValidationResult {
        val trimmed = privateKey.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Success // Private key is often optional
        }
        
        if (trimmed.length < 32) {
            return ValidationResult.Error("Private key must be at least 32 characters")
        }
        
        if (trimmed.length > 200) {
            return ValidationResult.Error("Private key must be 200 characters or less")
        }
        
        return ValidationResult.Success
    }
    
    /**
     * Calculates password strength with a score and label.
     * Matches web app algorithm for parity.
     * 
     * Scoring:
     * - Length 8+ → +20, 12+ → +10, 16+ → +10
     * - Has lowercase → +15
     * - Has uppercase → +15
     * - Has digits → +15
     * - Has special chars → +15
     * - 50%+ unique chars → +10
     * 
     * Strength labels:
     * - 0-39: Weak
     * - 40-69: Medium
     * - 70+: Strong
     * 
     * @param password The password to analyze
     * @return PasswordStrengthResult with score and strength label
     */
    fun calculatePasswordStrength(password: String): PasswordStrengthResult {
        if (password.isEmpty()) {
            return PasswordStrengthResult(0, PasswordStrength.WEAK)
        }
        
        var score = 0
        
        // Length scoring
        if (password.length >= 8) score += 20
        if (password.length >= 12) score += 10
        if (password.length >= 16) score += 10
        
        // Character types
        if (password.any { it.isLowerCase() }) score += 15
        if (password.any { it.isUpperCase() }) score += 15
        if (password.any { it.isDigit() }) score += 15
        if (password.any { !it.isLetterOrDigit() }) score += 15
        
        // Variety (50%+ unique characters)
        val uniqueChars = password.toSet().size
        if (uniqueChars >= password.length * 0.5) score += 10
        
        val strength = when {
            score >= 70 -> PasswordStrength.STRONG
            score >= 40 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
        
        return PasswordStrengthResult(minOf(score, 100), strength)
    }
    
    /**
     * Validates a birthdate.
     * 
     * Accepts common date formats:
     * - YYYY-MM-DD (ISO 8601)
     * - MM/DD/YYYY (US format)
     * - DD/MM/YYYY (European format)
     * 
     * @param birthdate The birthdate string to validate
     * @return ValidationResult with success or error message
     */
    fun validateBirthdate(birthdate: String): ValidationResult {
        val trimmed = birthdate.trim()
        
        if (trimmed.isEmpty()) {
            return ValidationResult.Success // Birthdate is optional
        }
        
        // ISO 8601 format: YYYY-MM-DD
        val isoPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        // US format: MM/DD/YYYY
        val usPattern = Regex("^\\d{2}/\\d{2}/\\d{4}$")
        // European format: DD/MM/YYYY
        val euPattern = Regex("^\\d{2}/\\d{2}/\\d{4}$")
        // With dashes: DD-MM-YYYY or MM-DD-YYYY
        val dashPattern = Regex("^\\d{2}-\\d{2}-\\d{4}$")
        
        if (!isoPattern.matches(trimmed) && 
            !usPattern.matches(trimmed) && 
            !euPattern.matches(trimmed) &&
            !dashPattern.matches(trimmed)) {
            return ValidationResult.Error("Invalid date format. Use YYYY-MM-DD or MM/DD/YYYY")
        }
        
        // Try to parse and validate the date components
        try {
            val parts = if (trimmed.contains("-")) {
                trimmed.split("-")
            } else {
                trimmed.split("/")
            }
            
            val (year, month, day) = if (parts[0].length == 4) {
                // YYYY-MM-DD format
                Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else {
                // MM/DD/YYYY or DD/MM/YYYY - assume first is month for US format
                Triple(parts[2].toInt(), parts[0].toInt(), parts[1].toInt())
            }
            
            // Basic range validation
            if (year < 1900 || year > 2100) {
                return ValidationResult.Error("Year must be between 1900 and 2100")
            }
            if (month < 1 || month > 12) {
                return ValidationResult.Error("Month must be between 1 and 12")
            }
            if (day < 1 || day > 31) {
                return ValidationResult.Error("Day must be between 1 and 31")
            }
            
            return ValidationResult.Success
        } catch (e: Exception) {
            return ValidationResult.Error("Invalid date format")
        }
    }
}

/**
 * Result of a validation check.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun errorMessageOrNull(): String? = (this as? Error)?.message
}

/**
 * Password strength levels.
 */
enum class PasswordStrength {
    WEAK,
    MEDIUM,
    STRONG
}

/**
 * Result of password strength calculation.
 */
data class PasswordStrengthResult(
    val score: Int,          // 0-100
    val strength: PasswordStrength
)
