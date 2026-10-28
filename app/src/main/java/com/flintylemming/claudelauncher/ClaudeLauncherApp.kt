package com.flintylemming.claudelauncher

import android.app.Application
import com.google.android.material.color.DynamicColors

class ClaudeLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply Material You dynamic theming on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
