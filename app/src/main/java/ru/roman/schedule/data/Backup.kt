package ru.roman.schedule.data

import org.json.JSONArray
import org.json.JSONObject

object Backup {

    fun toJson(repo: ScheduleRepo): String {
        val root = JSONObject()
        root.put("version", 1)
        val weeks = JSONArray()

        for (week in repo.weeks()) {
            val item = JSONObject()
            item.put("start", week.startDate)
            item.put("end", week.endDate)
            item.put("title", week.title)
            item.put("owner", week.owner)
            item.put("kind", week.kind)

            val lessons = JSONArray()
            for (lesson in repo.lessons(week.id)) {
                val row = JSONObject()
                row.put("date", lesson.date)
                row.put("pair", lesson.pairNo)
                row.put("subject", lesson.subject)
                row.put("group", lesson.group)
                row.put("room", lesson.room)
                row.put("time", lesson.time)
                row.put("teacher", lesson.teacher)
                row.put("note", lesson.note)
                row.put("done", lesson.done)
                lessons.put(row)
            }
            item.put("lessons", lessons)
            weeks.put(item)
        }

        root.put("weeks", weeks)
        return root.toString()
    }

    fun fromJson(repo: ScheduleRepo, text: String): Int {
        val root = JSONObject(text)
        val weeks = root.optJSONArray("weeks") ?: return 0
        var restored = 0

        for (index in 0 until weeks.length()) {
            val item = weeks.getJSONObject(index)
            val week = Week(
                startDate = item.getString("start"),
                endDate = item.getString("end"),
                title = item.optString("title"),
                owner = item.optString("owner"),
                kind = item.optString("kind")
            )
            val rows = item.optJSONArray("lessons") ?: JSONArray()
            val lessons = ArrayList<Lesson>()
            for (rowIndex in 0 until rows.length()) {
                val row = rows.getJSONObject(rowIndex)
                lessons.add(
                    Lesson(
                        date = row.getString("date"),
                        pairNo = row.optInt("pair"),
                        subject = row.optString("subject"),
                        group = row.optString("group"),
                        room = row.optString("room"),
                        time = row.optString("time"),
                        teacher = row.optString("teacher"),
                        note = row.optString("note"),
                        done = row.optBoolean("done")
                    )
                )
            }
            repo.insertWeek(week, lessons)
            restored++
        }
        return restored
    }
}