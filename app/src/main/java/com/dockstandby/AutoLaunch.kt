package com.dockstandby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dockstandby.data.Prefs

/**
 * Keeps a power-connected listener alive so StandBy opens the moment charging starts.
 * Android only delivers ACTION_POWER_CONNECTED to runtime-registered receivers, and only lets a
 * background app open an activity when it holds "Display over other apps" — hence this
 * foreground service plus the overlay permission.
 */
class AutoLaunchService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                DockFeedback.play(context)
                launchStandBy(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Auto-open on charging", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps StandBy ready to open when you plug in"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("StandBy is ready")
            .setContentText("Opens automatically when you start charging")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL = "autolaunch"
        private const val NOTIFICATION_ID = 1

        fun canAutoLaunch(context: Context) = Settings.canDrawOverlays(context)

        /** Starts or stops the service to match the saved setting. */
        fun sync(context: Context) {
            val intent = Intent(context, AutoLaunchService::class.java)
            if (Prefs.get(context).settings.value.autoLaunch && canAutoLaunch(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }

        fun launchStandBy(context: Context) {
            if (!canAutoLaunch(context)) return
            context.startActivity(
                Intent(context, StandByActivity::class.java)
                    .putExtra(StandByActivity.EXTRA_AUTO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}

/** Brings the service back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AutoLaunchService.sync(context)
        }
    }
}
