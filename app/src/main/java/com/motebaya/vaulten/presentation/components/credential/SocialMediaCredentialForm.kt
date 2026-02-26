package com.motebaya.vaulten.presentation.components.credential

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.domain.validation.CredentialValidators
import com.motebaya.vaulten.domain.validation.ValidationResult
import com.motebaya.vaulten.presentation.components.PasswordStrengthMeter

/**
 * Form for social media credentials.
 */
@Composable
fun SocialMediaCredentialForm(
    isLoading: Boolean,
    error: String?,
    onSave: (
        username: String,
        email: String,
        password: String,
        phoneNumber: String,
        recoveryEmail: String,
        twoFaEnabled: Boolean,
        notes: String
    ) -> Unit,
    onCancel: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf("") }
    var twoFaEnabled by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    // Validation states
    val usernameValidation = remember(username) {
        if (username.isBlank()) null
        else CredentialValidators.validateUsername(username, required = true)
    }
    val emailValidation = remember(email) {
        if (email.isBlank()) null
        else CredentialValidators.validateEmail(email)
    }
    val phoneValidation = remember(phoneNumber) {
        if (phoneNumber.isBlank()) null
        else CredentialValidators.validatePhone(phoneNumber)
    }
    val recoveryEmailValidation = remember(recoveryEmail) {
        if (recoveryEmail.isBlank()) null
        else CredentialValidators.validateEmail(recoveryEmail)
    }
    
    // Check if form is valid
    val isFormValid = username.isNotBlank() &&
        (usernameValidation == null || usernameValidation is ValidationResult.Success) &&
        email.isNotBlank() && (emailValidation == null || emailValidation is ValidationResult.Success) &&
        password.isNotBlank() &&
        (phoneValidation == null || phoneValidation is ValidationResult.Success) &&
        (recoveryEmailValidation == null || recoveryEmailValidation is ValidationResult.Success)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Social Media Credential",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = usernameValidation is ValidationResult.Error,
                supportingText = if (usernameValidation is ValidationResult.Error) {
                    { Text(usernameValidation.message) }
                } else null
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email *") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = emailValidation is ValidationResult.Error,
                supportingText = if (emailValidation is ValidationResult.Error) {
                    { Text(emailValidation.message) }
                } else null
            )

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
            
            // Password strength meter
            if (password.isNotEmpty()) {
                PasswordStrengthMeter(
                    password = password,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number (optional)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = phoneValidation is ValidationResult.Error,
                supportingText = if (phoneValidation is ValidationResult.Error) {
                    { Text(phoneValidation.message) }
                } else null
            )

            OutlinedTextField(
                value = recoveryEmail,
                onValueChange = { recoveryEmail = it },
                label = { Text("Recovery Email (optional)") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = recoveryEmailValidation is ValidationResult.Error,
                supportingText = if (recoveryEmailValidation is ValidationResult.Error) {
                    { Text(recoveryEmailValidation.message) }
                } else null
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Two-Factor Authentication",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = twoFaEnabled,
                    onCheckedChange = { twoFaEnabled = it },
                    enabled = !isLoading
                )
            }

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
                        onSave(
                            username, email, password, phoneNumber,
                            recoveryEmail, twoFaEnabled, notes
                        )
                    },
                    enabled = isFormValid && !isLoading,
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
