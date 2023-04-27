package com.baxolino.apps.floats

import android.app.Application
import com.google.android.material.color.DynamicColors

class ThemeApp: Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}