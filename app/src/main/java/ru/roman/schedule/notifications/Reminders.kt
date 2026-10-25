package ru.roman.schedule.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import ru.roman.schedule.data.BellsConfig
import ru.roman.schedule.data.Bells
import ru.roman.schedule.data.Lesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {

    const val LEAD_MINUTES = 15L

    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CODES = "codes"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Пересобирает все будильники напоминаний: старые отменяются,
     * новые ставятся только на будущие пары в пределах трёх недель.
     */
    fun reschedule(context: Context, lessons: List<Lesson>, bells: BellsConfig) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, manager)
        if (!isEnabled(context)) return

        val now = LocalDateTime.now()
        val horizon = now.plusDays(21)
        val codes = ArrayList<Int>()

        for (lesson in lessons) {
            val slot = Bells.slotFor(bells, lesson.date, lesson.pairNo) ?: continue
            val date = try {
                LocalDate.parse(lesson.date)
            } catch (error: Exception) {
                continue
            }
            val trigger = LocalDateTime.of(date, slot.start).minusMinutes(LEAD_MINUTES)
            if (trigger.isBefore(now) || trigger.isAfter(horizon)) continue

            val code = codeFor(lesson)
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_SUBJECT, lesson.subject)
                putExtra(ReminderReceiver.EXTRA_ROOM, lesson.room)
                putExtra(ReminderReceiver.EXTRA_GROUP, lesson.group)
                putExtra(ReminderReceiver.EXTRA_PAIR, lesson.pairNo)
                putExtra(ReminderReceiver.EXTRA_TIME, slot.start.toString())
            }
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, millis, pending)
            }
            codes.add(code)
        }

        prefs(context).edit().putString(KEY_CODES, codes.joinToString(",")).apply()
    }

    private fun cancelAll(context: Context, manager: AlarmManager) {
        val stored = prefs(context).getString(KEY_CODES, "").orEmpty()
        for (part in stored.split(",")) {
            val code = part.toIntOrNull() ?: continue
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pending)
        }
        prefs(context).edit().putString(KEY_CODES, "").apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun codeFor(lesson: Lesson): Int =
        (lesson.date + "|" + lesson.pairNo + "|" + lesson.subject + "|" + lesson.group).hashCode()
}