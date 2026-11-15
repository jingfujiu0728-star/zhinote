package com.example.zhinote.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

@Composable
fun rememberThermalThrottle(context: Context): Boolean {
    var throttle by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= 29) {
            throttle = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            val listener = PowerManager.OnThermalStatusChangedListener { status -> throttle = status >= PowerManager.THERMAL_STATUS_MODERATE }
            power.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
            onDispose { power.removeThermalStatusListener(listener) }
        } else onDispose { }
    }
    return throttle
}
