package ru.roman.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.roman.schedule.MainActivity
import ru.roman.schedule.R
import ru.roman.schedule.data.BellSlot
import ru.roman.schedule.data.Bells
import ru.roman.schedule.data.DbHelper
import ru.roman.schedule.data.Lesson
import ru.roman.schedule.data.ScheduleRepo
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val views = buildViews(appContext)
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (error: Exception) {
                // виджет не должен падать из-за данных
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private val WEEKDAYS = listOf("", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodayWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(context, TodayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val today = LocalDate.now()
            val now = LocalTime.now()

            val repo = ScheduleRepo(DbHelper(context))
            val allWeeks = repo.weeks()
            val week = allWeeks.firstOrNull { week ->
                val start = week.startDate.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val end = week.endDate.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
            } ?: allWeeks.firstOrNull()

            val lessons = if (week == null) {
                emptyList<Lesson>()
            } else {
                repo.lessons(week.id).filter { it.date == today.toString() }.sortedBy { it.pairNo }
            }
            val bells = Bells.load(context)

            views.setTextViewText(
                R.id.widget_title,
                WEEKDAYS[today.dayOfWeek.value] + " " + today.format(DAY)
            )

            val current = lessons.firstOrNull { lesson ->
                val slot = Bells.slotFor(bells, lesson.date, lesson.pairNo)
                slot != null && !now.isBefore(slot.start) && now.isBefore(slot.end)
            }
            val upcoming = current ?: lessons.firstOrNull { lesson ->
                val slot = Bells.slotFor(bells, lesson.date, lesson.pairNo)
                slot != null && now.isBefore(slot.start)
            }

            if (upcoming == null) {
                views.setTextViewText(R.id.widget_subject, if (lessons.isEmpty()) "Нет занятий" else "Пары на сегодня")
                views.setTextViewText(
                    R.id.widget_details,
                    if (lessons.isEmpty()) "Хорошего дня" else lessons.size.toString() + " " + pairsWord(lessons.size)
                )
            } else {
                val slot: BellSlot? = Bells.slotFor(bells, upcoming.date, upcoming.pairNo)
                views.setTextViewText(
                    R.id.widget_subject,
                    if (current != null) "Сейчас: " + upcoming.subject else upcoming.subject
                )
                val details = ArrayList<String>()
                details.add(upcoming.pairNo.toString() + " пара")
                if (slot != null) {
                    details.add(slot.start.format(TIME) + "–" + slot.end.format(TIME))
                }
                if (upcoming.room.isNotBlank()) details.add("каб. " + upcoming.room)
                if (upcoming.group.isNotBlank()) details.add(upcoming.group)
                views.setTextViewText(R.id.widget_details, details.joinToString(" · "))
            }

            val openIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            return views
        }

        private fun pairsWord(count: Int): String {
            val mod10 = count % 10
            val mod100 = count % 100
            return when {
                mod10 == 1 && mod100 != 11 -> "пара"
                mod10 in 2..4 && mod100 !in 12..14 -> "пары"
                else -> "пар"
            }
        }
    }
}