package com.logie.persona.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.logie.persona.backend.catalog.Catalog
import com.logie.persona.backend.steam.InstallProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Runs installs in the background with a plain progress notification (the game's name, a step, a bar). */
class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val version = Catalog.findVersion(intent?.getStringExtra(EXTRA_VERSION))
        if (version == null) { if (active.get() == 0) stopSelf(); return START_NOT_STICKY }
        val title = Catalog.gameOf(version).title + " " + version.label
        startForeground(NOTIFICATION_ID, notification(title, InstallProgress(InstallProgress.Step.CONNECTING, 0f)))
        if (InstallCenter.isRunning(version.id)) return START_NOT_STICKY
        active.incrementAndGet()
        scope.launch {
            try {
                InstallCenter.install(applicationContext, version) { p ->
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, p))
                }
            } finally {
                if (active.decrementAndGet() == 0) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(title: String, p: InstallProgress): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Installs", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(Wording.step(p.step))
            .setProgress(100, (p.fraction * 100).toInt(), p.step != InstallProgress.Step.DOWNLOADING)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "installs"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_VERSION = "version"

        fun start(context: Context, versionId: String) {
            val i = Intent(context, InstallService::class.java).putExtra(EXTRA_VERSION, versionId)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
