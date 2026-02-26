package com.motebaya.vaulten.presentation.components.credential

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.domain.validation.CredentialValidators
import com.motebaya.vaulten.domain.validation.ValidationResult

/**
 * Data class for Google credential form state.
 */
data class GoogleCredentialFormData(
    val accountName: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val backupCodes: String = "",
    val recoveryEmail: String = "",
    val birthdate: String = "",
    val twoFaEnabled: Boolean = false,
    val notes: String = ""
)

/**
 * Form for Google/email account credentials.
 * 
 * Includes validation for:
 * - Email (required, RFC 5322 format)
 * - Password (required)
 * - Phone (optional, digits + spaces + plus only)
 * - Backup codes (optional, digits + spaces only)
 * - Recovery email (optional, RFC 5322 format)
 * - Birthdate (optional, ISO-8601 or common date format)
 * - 2FA enabled (toggle)
 */
@Composable
fun GoogleCredentialForm(
    isLoading: Boolean,
    error: String?,
    onSave: (data: GoogleCredentialFormData) -> Unit,
    onCancel: () -> Unit,
    initialData: GoogleCredentialFormData = GoogleCredentialFormData()
) {
    var accountName by remember { mutableStateOf(initialData.accountName) }
    var email by remember { mutableStateOf(initialData.email) }
    var password by remember { mutableStateOf(initialData.password) }
    var phone by remember { mutableStateOf(initialData.phone) }
    var backupCodes by remember { mutableStateOf(initialData.backupCodes) }
    var recoveryEmail by remember { mutableStateOf(initialData.recoveryEmail) }
    var birthdate by remember { mutableStateOf(initialData.birthdate) }
    var twoFaEnabled by remember { mutableStateOf(initialData.twoFaEnabled) }
    var notes by remember { mutableStateOf(initialData.notes) }
    var showPassword by remember { mutableStateOf(false) }
    var showBackupCodes by remember { mutableStateOf(false) }
    
    // Validation states
    val emailValidation = remember(email) { 
        if (email.isBlank()) null else CredentialValidators.validateEmail(email) 
    }
    val phoneValidation = remember(phone) { 
        if (phone.isBlank()) null else CredentialValidators.validatePhone(phone) 
    }
    val backupCodesValidation = remember(backupCodes) { 
        if (backupCodes.isBlank()) null else CredentialValidators.validateBackupCodes(backupCodes) 
    }
    val recoveryEmailValidation = remember(recoveryEmail) { 
        if (recoveryEmail.isBlank()) null else CredentialValidators.validateEmail(recoveryEmail) 
    }
    val birthdateValidation = remember(birthdate) {
        if (birthdate.isBlank()) null else CredentialValidators.validateBirthdate(birthdate)
    }
    
    val isValid = email.isNotBlank() && 
        password.isNotBlank() && 
        emailValidation?.isSuccess != false &&
        phoneValidation?.isSuccess != false &&
        backupCodesValidation?.isSuccess != false &&
        recoveryEmailValidation?.isSuccess != false &&
        birthdateValidation?.isSuccess != false

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Google / Email Account",
                style = MaterialTheme.typography.titleMedium
            )

            // Account Name (optional)
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Account Name (optional)") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                placeholder = { Text("e.g., Personal, Work") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Email (required)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address *") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = emailValidation?.isError == true,
                supportingText = emailValidation?.errorMessageOrNull()?.let { { Text(it) } },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Password (required)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password *") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (showPassword)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Phone (optional)
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number (optional)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneValidation?.isError == true,
                supportingText = phoneValidation?.errorMessageOrNull()?.let { { Text(it) } },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Birthdate (optional)
            OutlinedTextField(
                value = birthdate,
                onValueChange = { birthdate = it },
                label = { Text("Birthdate (optional)") },
                leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                placeholder = { Text("YYYY-MM-DD") },
                isError = birthdateValidation?.isError == true,
                supportingText = birthdateValidation?.errorMessageOrNull()?.let { { Text(it) } }
                    ?: { Text("Format: YYYY-MM-DD or MM/DD/YYYY") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 2FA Enabled toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2FA Enabled",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = twoFaEnabled,
                    onCheckedChange = { twoFaEnabled = it },
                    enabled = !isLoading
                )
            }

            // Backup Codes (optional)
            OutlinedTextField(
                value = backupCodes,
                onValueChange = { backupCodes = it },
                label = { Text("Backup Codes (optional)") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showBackupCodes = !showBackupCodes }) {
                        Icon(
                            if (showBackupCodes) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showBackupCodes) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (showBackupCodes)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                isError = backupCodesValidation?.isError == true,
                supportingText = backupCodesValidation?.errorMessageOrNull()?.let { { Text(it) } }
                    ?: { Text("Enter backup/recovery codes separated by spaces") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Recovery Email (optional)
            OutlinedTextField(
                value = recoveryEmail,
                onValueChange = { recoveryEmail = it },
                label = { Text("Recovery Email (optional)") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = recoveryEmailValidation?.isError == true,
                supportingText = recoveryEmailValidation?.errorMessageOrNull()?.let { { Text(it) } },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Notes (optional)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { 
                        onSave(GoogleCredentialFormData(
                            accountName = accountName,
                            email = email,
                            password = password,
                            phone = phone,
                            backupCodes = backupCodes,
                            recoveryEmail = recoveryEmail,
                            birthdate = birthdate,
                            twoFaEnabled = twoFaEnabled,
                            notes = notes
                        ))
                    },
                    enabled = isValid && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Legacy signature for backward compatibility.
 * DEPRECATED: Use the new GoogleCredentialFormData signature instead.
 */
@Composable
fun GoogleCredentialForm(
    isLoading: Boolean,
    error: String?,
    onSave: (accountName: String, email: String, password: String, phone: String, backupCodes: String, recoveryEmail: String, birthdate: String, twoFaEnabled: Boolean, notes: String) -> Unit,
    onCancel: () -> Unit
) {
    GoogleCredentialForm(
        isLoading = isLoading,
        error = error,
        onSave = { data ->
            onSave(data.accountName, data.email, data.password, data.phone, data.backupCodes, data.recoveryEmail, data.birthdate, data.twoFaEnabled, data.notes)
        },
        onCancel = onCancel
    )
}

/**
 * Checks if a platform is a Google or email platform based on domain/name.
 */
fun isGoogleOrEmailPlatform(platformName: String, platformDomain: String): Boolean {
    val lowerName = platformName.lowercase()
    val lowerDomain = platformDomain.lowercase()
    
    val emailKeywords = listOf(
        "google", "gmail", "outlook", "hotmail", "yahoo", "icloud", 
        "protonmail", "proton", "mail", "email"
    )
    
    val emailDomains = listOf(
        "google.com", "gmail.com", "outlook.com", "hotmail.com", 
        "yahoo.com", "icloud.com", "protonmail.com", "proton.me"
    )
    
    return emailKeywords.any { lowerName.contains(it) } ||
           emailDomains.any { lowerDomain.contains(it) }
}
