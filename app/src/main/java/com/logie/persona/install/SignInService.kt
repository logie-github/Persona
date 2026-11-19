package com.logie.persona.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Keeps the app running while a sign-in waits for the person to approve it in the Steam app. Without it, Android freezes the app as soon as it leaves the
 * screen (to open the Steam app on the same phone): the sign-in's connection goes quiet, Steam drops the session, and the Steam app says it failed to load the
 * QR info. The sign-in itself runs in [InstallCenter]'s own scope; this only holds the process in the foreground with a quiet notification.
 */
class SignInService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Signing in", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Signing in").setOngoing(true).build())
        // a stop comes as a command after the start (never as stopService, which could arrive before startForeground and make Android end the app)
        if (intent?.action == ACTION_STOP) { stopForeground(true); stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "signin"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            val i = Intent(context, SignInService::class.java)
            runCatching { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i) }
        }

        fun stop(context: Context) {
            val i = Intent(context, SignInService::class.java).setAction(ACTION_STOP)
            runCatching { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i) }
        }
    }
}
