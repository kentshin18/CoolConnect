package com.example.coolconnect

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CoolConnectApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
