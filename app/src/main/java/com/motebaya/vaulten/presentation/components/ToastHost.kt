package com.motebaya.vaulten.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Toast type determines the color and icon of the toast.
 */
enum class ToastType {
    SUCCESS,
    WARNING,
    ERROR,
    INFO
}

/**
 * Data class representing a single toast message.
 */
data class ToastData(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val type: ToastType = ToastType.INFO,
    val durationMs: Long = when (type) {
        ToastType.SUCCESS -> 3000L
        ToastType.WARNING -> 4000L
        ToastType.ERROR -> 5000L
        ToastType.INFO -> 3000L
    }
)

/**
 * Global toast state manager.
 * Use this singleton to show toasts from anywhere in the app.
 */
object ToastManager {
    private val _toastFlow = MutableSharedFlow<ToastData>(extraBufferCapacity = 10)
    val toastFlow: SharedFlow<ToastData> = _toastFlow.asSharedFlow()
    
    fun showSuccess(message: String) {
        _toastFlow.tryEmit(ToastData(message = message, type = ToastType.SUCCESS))
    }
    
    fun showWarning(message: String) {
        _toastFlow.tryEmit(ToastData(message = message, type = ToastType.WARNING))
    }
    
    fun showError(message: String) {
        _toastFlow.tryEmit(ToastData(message = message, type = ToastType.ERROR))
    }
    
    fun showInfo(message: String) {
        _toastFlow.tryEmit(ToastData(message = message, type = ToastType.INFO))
    }
    
    fun show(toast: ToastData) {
        _toastFlow.tryEmit(toast)
    }
}

/**
 * Toast host composable that displays toasts in the top-right corner.
 * Place this at the root of your app's composable hierarchy.
 * 
 * Features:
 * - Position: Top-right
 * - Max visible: 3 toasts, queue the rest
 * - Auto-dismiss based on type (success=3s, warning=4s, error=5s)
 * - Animated enter/exit
 */
@Composable
fun ToastHost(
    modifier: Modifier = Modifier
) {
    val toasts = remember { mutableStateListOf<ToastData>() }
    val scope = rememberCoroutineScope()
    
    // Collect toasts from the global manager
    LaunchedEffect(Unit) {
        ToastManager.toastFlow.collect { toast ->
            // Limit to 3 visible toasts
            if (toasts.size >= 3) {
                toasts.removeAt(0) // Remove oldest
            }
            toasts.add(toast)
            
            // Schedule removal after duration
            scope.launch {
                delay(toast.durationMs)
                toasts.removeAll { it.id == toast.id }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, end = 16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            toasts.forEach { toast ->
                key(toast.id) {
                    AnimatedToast(
                        toast = toast,
                        onDismiss = { toasts.removeAll { it.id == toast.id } }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedToast(
    toast: ToastData,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(toast) {
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        ToastCard(toast = toast, onDismiss = onDismiss)
    }
}

@Composable
private fun ToastCard(
    toast: ToastData,
    onDismiss: () -> Unit
) {
    val (backgroundColor, contentColor, icon) = when (toast.type) {
        ToastType.SUCCESS -> Triple(
            Color(0xFF22C55E), // Green
            Color.White,
            Icons.Default.CheckCircle
        )
        ToastType.WARNING -> Triple(
            Color(0xFFF59E0B), // Yellow/Amber
            Color.Black,
            Icons.Default.Warning
        )
        ToastType.ERROR -> Triple(
            Color(0xFFEF4444), // Red
            Color.White,
            Icons.Default.Error
        )
        ToastType.INFO -> Triple(
            Color(0xFF3B82F6), // Blue
            Color.White,
            Icons.Default.Info
        )
    }
    
    Surface(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = toast.message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
