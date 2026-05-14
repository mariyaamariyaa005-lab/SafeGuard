package com.safeguard.app.receiver

import android.content.*
import androidx.core.content.ContextCompat
import com.safeguard.app.data.PrefsManager
import com.safeguard.app.service.VoiceMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PrefsManager(context).autoStartOnBoot) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, VoiceMonitorService::class.java)
                )
            }
        }
    }
}