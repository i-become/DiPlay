package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.host.R

/** Keeps an explicitly started connection alive when another car app is in the foreground. */
class DiPlaySessionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var sessionStarted = false
    // Boot warm-up only: the process keeps foreground priority until CarPlay starts or this runs out.
    private val warmupTimeout = Runnable {
        if (!sessionStarted) {
            DiagnosticLogging.logcat { Log.i(TAG, "boot warm-up ended without a CarPlay session") }
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CarPlayBackgroundSession.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val warmup = intent?.action == ACTION_WARMUP
        if (warmup && sessionStarted) return START_NOT_STICKY
        if (!warmup) {
            sessionStarted = true
            handler.removeCallbacks(warmupTimeout)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "CarPlay connection", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, CHANNEL)
        } else {
            // Android 7.x has no notification channels; the priority stands in for the channel importance.
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.shilapi.xcertplay.newui.NewUiActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(this, 1, Intent(this, DiPlaySessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = builder
            .setSmallIcon(R.drawable.ic_diplay_notification)
            .setContentTitle("DiPlay")
            .setContentText(if (warmup) "Getting CarPlay ready" else "CarPlay connection running")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (!warmup && Build.VERSION.SDK_INT >= 30 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            // Without it Android stops location updates while another car app (the reversing camera,
            // the car's own map) covers CarPlay, and the iPhone gets no position until DiPlay is back.
            if (!warmup && AirPlayPersistence.loadLocationReportingEnabled(this) &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(1, notification, types)
        } else startForeground(1, notification)
        if (warmup) {
            handler.removeCallbacks(warmupTimeout)
            handler.postDelayed(warmupTimeout, WARMUP_MILLIS)
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(warmupTimeout)
        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!sessionStarted) return
        // BYD's recents force-stops the package ~10 ms after removing the task: end guidance first.
        com.shilapi.xcertplay.hud.BydNavigationOutputs.endNow()
        CarPlayBackgroundSession.stop()
        stopSelf()
    }
    companion object {
        const val ACTION_STOP = "com.shihab.diplay.DISCONNECT"
        private const val ACTION_WARMUP = "com.shihab.diplay.WARMUP"
        private const val CHANNEL = "diplay_connection"
        private const val TAG = "DiPlaySessionService"
        private const val WARMUP_MILLIS = 3 * 60_000L

        /** Holds foreground priority from boot so the wireless warm-up and first connection are not starved. */
        fun startWarmup(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DiPlaySessionService::class.java).setAction(ACTION_WARMUP),
                )
            }.onFailure { DiagnosticLogging.logcat { Log.w(TAG, "boot warm-up could not start: ${it.javaClass.simpleName}") } }
        }
    }
}
