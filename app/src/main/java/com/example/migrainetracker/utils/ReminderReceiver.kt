package com.example.migrainetracker.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.migrainetracker.R
import com.example.migrainetracker.ui.MainActivity
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val ACTION_COMPLETE = "ACTION_COMPLETE_REMINDER"
        private const val ACTION_SNOOZE = "ACTION_SNOOZE_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive вызван с action: ${intent.action}")

        val action = intent.action

        when (action) {
            ACTION_COMPLETE -> {
                val medicineName = intent.getStringExtra("medicine_name") ?: "Лекарство"
                val reminderId = intent.getIntExtra("reminder_id", 0)

                Log.d(TAG, "Завершить: $medicineName, id=$reminderId")
                Toast.makeText(context, "✅ Приём $medicineName отмечен", Toast.LENGTH_SHORT).show()

                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.cancel(reminderId)
            }

            ACTION_SNOOZE -> {
                val medicineName = intent.getStringExtra("medicine_name") ?: "Лекарство"
                val reminderId = intent.getIntExtra("reminder_id", 0)
                val repeatInterval = intent.getIntExtra("repeat_interval", 0)

                Log.d(TAG, "Отложить: $medicineName, id=$reminderId")
                Toast.makeText(context, "⏰ Напоминание отложено на 10 минут", Toast.LENGTH_SHORT).show()

                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.cancel(reminderId)

                scheduleReminder(context, reminderId, medicineName, repeatInterval, 10)
            }

            else -> {
                val medicineName = intent.getStringExtra("medicine_name") ?: "Лекарство"
                val reminderId = intent.getIntExtra("reminder_id", 0)
                val repeatInterval = intent.getIntExtra("repeat_interval", 0)

                Log.d(TAG, "Обычное напоминание: $medicineName, id=$reminderId, repeat=$repeatInterval")

                showNotification(context, medicineName, reminderId, repeatInterval)

                // Планируем следующее напоминание только для повторяющихся
                if (repeatInterval > 0 && repeatInterval != 24) {
                    scheduleNextReminder(context, reminderId, medicineName, repeatInterval)
                }
            }
        }
    }

    private fun scheduleNextReminder(context: Context, reminderId: Int, medicineName: String, repeatInterval: Int) {
        scheduleReminder(context, reminderId, medicineName, repeatInterval, repeatInterval)
    }

    private fun scheduleReminder(context: Context, reminderId: Int, medicineName: String, repeatInterval: Int, minutesLater: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MINUTE, minutesLater)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        try {
            Log.d(TAG, "Планируем напоминание через $minutesLater минут в ${calendar.time}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка планирования", e)
        }
    }

    private fun showNotification(context: Context, medicineName: String, reminderId: Int, repeatInterval: Int) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra("medicine_name", medicineName)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_interval", repeatInterval)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId + 10000,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("medicine_name", medicineName)
            putExtra("reminder_id", reminderId)
            putExtra("repeat_interval", repeatInterval)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId + 20000,
            snoozeIntent,
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
                    android.R.drawable.ic_menu_save,
                    "✅ Завершить",
                    completePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_recent_history,
                    "⏰ Отложить (10 мин)",
                    snoozePendingIntent
                ).build()
            )
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(reminderId, notification)
        Log.d(TAG, "Уведомление отправлено: $medicineName")
    }

    private fun createNotificationChannel(context: Context) {
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
            Log.d(TAG, "Notification channel created")
        }
    }
}