package ru.roman.schedule.data

import android.content.ContentValues
import java.time.format.DateTimeFormatter

class ScheduleRepo(private val helper: DbHelper) {

    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

    fun weeks(): List<Week> {
        val out = ArrayList<Week>()
        helper.readableDatabase.use { db ->
            db.rawQuery(
                "SELECT id, start_date, end_date, title, owner, kind FROM weeks ORDER BY start_date DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        Week(
                            id = c.getLong(0),
                            startDate = c.getString(1),
                            endDate = c.getString(2),
                            title = c.getString(3),
                            owner = c.getString(4),
                            kind = c.getString(5)
                        )
                    )
                }
            }
        }
        return out
    }

    fun lessons(weekId: Long): List<Lesson> {
        val out = ArrayList<Lesson>()
        helper.readableDatabase.use { db ->
            db.rawQuery(
                "SELECT id, week_id, date, pair_no, subject, group_name, room, time, teacher, note, done " +
                    "FROM lessons WHERE week_id = ? ORDER BY date, pair_no",
                arrayOf(weekId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        Lesson(
                            id = c.getLong(0),
                            weekId = c.getLong(1),
                            date = c.getString(2),
                            pairNo = c.getInt(3),
                            subject = c.getString(4),
                            group = c.getString(5),
                            room = c.getString(6),
                            time = c.getString(7),
                            teacher = c.getString(8),
                            note = c.getString(9),
                            done = c.getInt(10) != 0
                        )
                    )
                }
            }
        }
        return out
    }

    fun insertWeek(week: Week, lessons: List<Lesson>): Long {
        var id = 0L
        helper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                id = db.insert("weeks", null, ContentValues().apply {
                    put("start_date", week.startDate)
                    put("end_date", week.endDate)
                    put("title", week.title)
                    put("created_at", System.currentTimeMillis())
                    put("owner", week.owner)
                    put("kind", week.kind)
                })
                for (l in lessons) {
                    db.insert("lessons", null, lessonValues(l, id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return id
    }

    fun saveLesson(lesson: Lesson) {
        helper.writableDatabase.use { db ->
            if (lesson.id == 0L) {
                db.insert("lessons", null, lessonValues(lesson, lesson.weekId))
            } else {
                db.update("lessons", lessonValues(lesson, lesson.weekId), "id = ?", arrayOf(lesson.id.toString()))
            }
        }
    }

    fun deleteLesson(id: Long) {
        helper.writableDatabase.use { db ->
            db.delete("lessons", "id = ?", arrayOf(id.toString()))
        }
    }

    fun deleteWeek(id: Long) {
        helper.writableDatabase.use { db ->
            db.delete("lessons", "week_id = ?", arrayOf(id.toString()))
            db.delete("weeks", "id = ?", arrayOf(id.toString()))
        }
    }

    fun findWeek(startDate: String, endDate: String, owner: String): Week? {
        return weeks().firstOrNull {
            it.startDate == startDate && it.endDate == endDate && it.owner == owner
        }
    }

    fun clearAll() {
        helper.writableDatabase.use { db ->
            db.delete("lessons", null, null)
            db.delete("weeks", null, null)
        }
    }

    fun bootstrap(): Week? {
        helper.writableDatabase.use { db ->
            db.delete("weeks", "kind = 'manual' AND id NOT IN (SELECT week_id FROM lessons)", null)
        }
        return weeks().firstOrNull()
    }

    private fun lessonValues(l: Lesson, weekId: Long): ContentValues = ContentValues().apply {
        put("week_id", weekId)
        put("date", l.date)
        put("pair_no", l.pairNo)
        put("subject", l.subject)
        put("group_name", l.group)
        put("room", l.room)
        put("time", l.time)
        put("teacher", l.teacher)
        put("note", l.note)
        put("done", if (l.done) 1 else 0)
    }
}