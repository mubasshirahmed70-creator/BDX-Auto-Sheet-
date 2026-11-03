package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.repository.SheetRepository
import com.example.ui.theme.AppThemeManager

class BdxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppThemeManager.init(this)
        // Initialize SheetRepository early
        SheetRepository.getInstance(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "bdx_floating_bubble_channel"
        const val NOTIFICATION_ID = 20261
    }
}
