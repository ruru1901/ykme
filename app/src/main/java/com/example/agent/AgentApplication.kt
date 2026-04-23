package com.example.agent

import android.app.Application
import android.content.Context
import com.example.agent.capabilities.ScreenCapability

class AgentApplication : Application() {
    var screenCapability: ScreenCapability? = null

    override fun onCreate() {
        super.onCreate()
    }
}
