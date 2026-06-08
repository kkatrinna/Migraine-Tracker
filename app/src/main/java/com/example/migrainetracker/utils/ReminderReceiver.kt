package com.example.migrainetracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.migrainetracker.R
import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicine_name") ?: "Лекарство"
        val reminderId = intent.getIntExtra("reminder_id", 0)
        val repeatInterval = intent.getIntExtra("repeat_interval", 0)

        showNotification(context, medicineName, reminderId)

        // Если это повторяющееся напоминание, планируем следующее
        if (repeatInterval > 0) {
            scheduleNextReminder(context, reminderId, medicineName, repeatInterval)
        }
    }

    private fun scheduleNextReminder(context: Context, reminderId: Int, medicineName: String, repeatInterval: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("medicine_name", medicineName)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_interval", repeatInterval)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            // Добавляем интервал к текущему времени
            add(Calendar.HOUR_OF_DAY, repeatInterval)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotification(context: Context, medicineName: String, reminderId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reminder_channel",
                "Напоминания о лекарствах",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о времени приема лекарств"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(R.drawable.ic_reminder_bell)
            .setContentTitle("💊 Напоминание о лекарстве")
            .setContentText("Время принять: $medicineName")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Не забудьте принять лекарство: $medicineName"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_delete,
                    "Отложить на 15 мин",
                    getSnoozePendingIntent(context, reminderId, medicineName)
                ).build()
            )
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(reminderId, notification)
    }

    private fun getSnoozePendingIntent(context: Context, reminderId: Int, medicineName: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("medicine_name", medicineName)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_interval", 0) // Отложить на 15 минут
        }

        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 15)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        return pendingIntent
    }
}