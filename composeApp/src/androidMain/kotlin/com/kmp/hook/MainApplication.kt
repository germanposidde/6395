package com.kmp.hook

import android.app.Application
import com.kmp.hook.elite.AbTracker
import com.kmp.hook.elite.PushNotifier

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AbTracker.init(this)
        PushNotifier.createChannel(this)
    }
}
