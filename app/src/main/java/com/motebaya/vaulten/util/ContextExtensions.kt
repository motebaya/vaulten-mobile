package com.motebaya.vaulten.util

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/**
 * Extension function to find the FragmentActivity from a Context.
 * 
 * In Jetpack Compose, LocalContext.current often returns a ContextWrapper
 * (like ContextThemeWrapper) rather than the actual Activity.
 * This function unwraps the context hierarchy to find the FragmentActivity.
 * 
 * This is required for APIs that need a FragmentActivity, such as BiometricPrompt.
 */
fun Context.findActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}
