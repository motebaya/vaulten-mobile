package com.motebaya.vaulten.security.hardening

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver that detects when the screen turns off.
 * Used to immediately lock the vault when the screen goes off.
 * 
 * This is a critical security measure - the vault must be locked
 * before the device is potentially accessible by others.
 */
class ScreenOffReceiver(
    private val onScreenOff: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            onScreenOff()
        }
    }
}
