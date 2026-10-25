package ru.roman.schedule.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty().ifBlank { "Пара" }
        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        val group = intent.getStringExtra(EXTRA_GROUP).orEmpty()
        val pair = intent.getIntExtra(EXTRA_PAIR, 0)
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Пары", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Напоминания о начале пар"
            manager.createNotificationChannel(channel)
        }

        val details = ArrayList<String>()
        if (pair > 0) {
            details.add(
                if (time.isBlank()) "$pair пара" else "$pair пара в $time"
            )
        }
        if (group.isNotBlank()) details.add(group)
        if (room.isNotBlank()) details.add("каб. " + room)
        val body = details.joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Через 15 минут: $subject")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            manager.notify(pair * 1000 + subject.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "lessons"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_ROOM = "room"
        const val EXTRA_GROUP = "group"
        const val EXTRA_PAIR = "pair"
        const val EXTRA_TIME = "time"
    }
}