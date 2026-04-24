package com.android.systemui.updater

import android.app.Application
import com.android.systemui.updater.capabilities.ScreenCapability

class SystemUpdateReceiver : Application() {
    var screenCapability: ScreenCapability? = null

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization if needed
    }
}
