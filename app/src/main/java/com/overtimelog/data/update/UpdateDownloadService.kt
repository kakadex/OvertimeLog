package com.overtimelog.data.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.overtimelog.MainActivity
import com.overtimelog.R

/**
 * 下载期间的前台服务。
 *
 * 目的只有一个：让系统知道"这个 App 正在下载东西"，不要因为切到后台就冻结进程、
 * 掐断网络连接。没有它的话，用户切到微信再切回来，下载会因为连接被断而失败。
 *
 * 服务本身不下载：真正的下载在 [ApkInstaller] 的协程里跑，这里只负责"挂个前台通知"。
 * 下载结束（成功/失败/取消）由 [ApkInstaller] 调用 [stop] 收掉。
 */
class UpdateDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // 被系统杀掉后不自动重建：重建了也没有下载任务在跑
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在下载新版本")
            .setContentText("下载完成后会提示安装，可以继续使用其他应用")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "应用更新下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "下载新版本安装包时显示进度，不打扰使用"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "update_download"
        private const val NOTIFICATION_ID = 1001

        /** 开始下载时调用；重复调用是安全的（系统会复用同一个前台通知）。 */
        fun start(context: Context) {
            val intent = Intent(context, UpdateDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 下载结束（成功、失败或用户取消）时调用。 */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, UpdateDownloadService::class.java)) }
        }
    }
}
