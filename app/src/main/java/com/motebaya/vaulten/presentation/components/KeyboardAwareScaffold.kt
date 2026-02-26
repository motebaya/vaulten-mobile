package com.motebaya.vaulten.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * A scaffold wrapper that provides keyboard-aware behavior:
 * 1. Page content adjusts when keyboard appears (via imePadding)
 * 2. Tapping outside any input field dismisses the keyboard globally
 * 
 * Use this for any page with input fields to ensure:
 * - Keyboard doesn't cover input fields
 * - Keyboard dismisses when tapping outside inputs
 */
@Composable
fun KeyboardAwareScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Scaffold(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    // Clear focus and hide keyboard when tapping outside inputs
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .imePadding(), // Adjust for keyboard
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content
    )
}

/**
 * A scrollable column that is keyboard-aware.
 * 
 * Features:
 * - Scrollable content
 * - Adjusts when keyboard appears (via imePadding on parent)
 * - Tapping outside inputs dismisses keyboard
 * 
 * Use inside a regular Scaffold or KeyboardAwareScaffold.
 */
@Composable
fun KeyboardAwareColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: androidx.compose.ui.Alignment.Horizontal = androidx.compose.ui.Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .verticalScroll(scrollState)
            .imePadding(),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

/**
 * Modifier extension to make any composable dismiss keyboard on tap outside.
 */
@Composable
fun Modifier.dismissKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus()
            keyboardController?.hide()
        })
    }
}
