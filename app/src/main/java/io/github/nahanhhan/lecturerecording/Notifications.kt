package io.github.nahanhhan.lecturerecording

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {
    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("recording", "课堂录音", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("tasks", "笔记与模型任务", NotificationManager.IMPORTANCE_LOW))
    }
    fun build(context: Context, title: String, text: String, recording: Boolean = false): NotificationCompat.Builder {
        create(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, if (recording) "recording" else "tasks")
            .setSmallIcon(R.drawable.ic_notebook).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
    }
}
