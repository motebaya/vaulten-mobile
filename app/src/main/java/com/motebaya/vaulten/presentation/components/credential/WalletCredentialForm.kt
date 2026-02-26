package com.motebaya.vaulten.presentation.components.credential

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.domain.validation.CredentialValidators
import com.motebaya.vaulten.domain.validation.ValidationResult
import com.motebaya.vaulten.presentation.components.SkeuoWarningBanner

/**
 * Form for crypto wallet credentials.
 */
@Composable
fun WalletCredentialForm(
    isLoading: Boolean,
    error: String?,
    onSave: (
        accountName: String,
        privateKey: String,
        seedPhrase: String,
        notes: String
    ) -> Unit,
    onCancel: () -> Unit
) {
    var accountName by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var seedPhrase by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showSeedPhrase by remember { mutableStateOf(false) }
    
    // Validation states
    val seedPhraseValidation = remember(seedPhrase) {
        if (seedPhrase.isBlank()) null
        else CredentialValidators.validateSeedPhrase(seedPhrase)
    }
    val privateKeyValidation = remember(privateKey) {
        if (privateKey.isBlank()) null
        else CredentialValidators.validatePrivateKey(privateKey)
    }
    
    // Check if form is valid
    val isFormValid = accountName.isNotBlank() &&
        (seedPhraseValidation == null || seedPhraseValidation is ValidationResult.Success) &&
        (privateKeyValidation == null || privateKeyValidation is ValidationResult.Success)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Crypto Wallet Credential",
                style = MaterialTheme.typography.titleMedium
            )

            SkeuoWarningBanner(
                message = "Store your seed phrase and private key securely. Never share them with anyone."
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Account Name / Wallet Name *") },
                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = privateKey,
                onValueChange = { privateKey = it },
                label = { Text("Private Key (optional)") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPrivateKey = !showPrivateKey }) {
                        Icon(
                            if (showPrivateKey) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showPrivateKey) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (showPrivateKey)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = privateKeyValidation is ValidationResult.Error,
                supportingText = if (privateKeyValidation is ValidationResult.Error) {
                    { Text(privateKeyValidation.message) }
                } else if (privateKey.isNotBlank()) {
                    { Text("32-200 characters required") }
                } else null
            )

            OutlinedTextField(
                value = seedPhrase,
                onValueChange = { seedPhrase = it },
                label = { Text("Seed Phrase / Recovery Phrase") },
                leadingIcon = { Icon(Icons.Default.Abc, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showSeedPhrase = !showSeedPhrase }) {
                        Icon(
                            if (showSeedPhrase) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showSeedPhrase) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (showSeedPhrase)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                placeholder = { Text("Enter 12 or 24 word seed phrase") },
                isError = seedPhraseValidation is ValidationResult.Error,
                supportingText = if (seedPhraseValidation is ValidationResult.Error) {
                    { Text(seedPhraseValidation.message) }
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
                    onClick = { onSave(accountName, privateKey, seedPhrase, notes) },
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
