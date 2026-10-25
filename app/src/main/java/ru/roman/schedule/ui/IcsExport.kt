package ru.roman.schedule.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ru.roman.schedule.data.BellsConfig
import ru.roman.schedule.data.Bells
import ru.roman.schedule.data.Lesson
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object IcsExport {

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun share(context: Context, weekTitle: String, lessons: List<Lesson>, bells: BellsConfig) {
        val content = build(lessons, bells)
        if (content.isBlank()) return

        val dir = File(context.cacheDir, "shared")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "schedule.ics")
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Расписание " + weekTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Поделиться расписанием"))
    }

    fun build(lessons: List<Lesson>, bells: BellsConfig): String {
        val head = StringBuilder()
        head.append("BEGIN:VCALENDAR\r\n")
        head.append("VERSION:2.0\r\n")
        head.append("PRODID:-//Schedule//RU\r\n")
        head.append("CALSCALE:GREGORIAN\r\n")

        val stamp = STAMP.format(LocalDateTime.now())
        var events = 0

        for (lesson in lessons) {
            val slot = Bells.slotFor(bells, lesson.date, lesson.pairNo) ?: continue
            val date = try {
                LocalDate.parse(lesson.date)
            } catch (error: Exception) {
                continue
            }
            val day = date.toString().replace("-", "")
            val start = day + "T" + slot.start.toString().replace(":", "") + "00"
            val end = day + "T" + slot.end.toString().replace(":", "") + "00"

            head.append("BEGIN:VEVENT\r\n")
            head.append("UID:").append((lesson.date + lesson.pairNo + lesson.subject).hashCode()).append("@schedule\r\n")
            head.append("DTSTAMP:").append(stamp).append("\r\n")
            head.append("DTSTART:").append(start).append("\r\n")
            head.append("DTEND:").append(end).append("\r\n")
            head.append("SUMMARY:").append(escape(summary(lesson))).append("\r\n")
            if (lesson.room.isNotBlank()) {
                head.append("LOCATION:").append(escape("каб. " + lesson.room)).append("\r\n")
            }
            val description = buildString {
                append(lesson.pairNo).append(" пара")
                if (lesson.group.isNotBlank()) append(", ").append(lesson.group)
                if (lesson.teacher.isNotBlank()) append(", ").append(lesson.teacher)
                if (lesson.note.isNotBlank()) append("\n").append(lesson.note)
            }
            head.append("DESCRIPTION:").append(escape(description)).append("\r\n")
            head.append("END:VEVENT\r\n")
            events++
        }

        head.append("END:VCALENDAR\r\n")
        return if (events == 0) "" else head.toString()
    }

    private fun summary(lesson: Lesson): String {
        return if (lesson.group.isBlank()) lesson.subject else lesson.subject + " (" + lesson.group + ")"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}