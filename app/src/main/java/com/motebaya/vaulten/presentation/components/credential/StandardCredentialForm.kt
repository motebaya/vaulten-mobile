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
 * Form for standard credentials (username, password, email, notes).
 */
@Composable
fun StandardCredentialForm(
    isLoading: Boolean,
    error: String?,
    onSave: (username: String, password: String, email: String, notes: String) -> Unit,
    onCancel: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
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
    
    // Check if form is valid
    val isFormValid = username.isNotBlank() &&
        (usernameValidation == null || usernameValidation is ValidationResult.Success) &&
        password.isNotBlank() &&
        (emailValidation == null || emailValidation is ValidationResult.Success)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Standard Credential",
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
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (optional)") },
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
                    onClick = { onSave(username, password, email, notes) },
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
