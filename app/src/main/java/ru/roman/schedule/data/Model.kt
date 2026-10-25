package ru.roman.schedule.data

data class Week(
    val id: Long = 0L,
    val startDate: String,
    val endDate: String,
    val title: String = "Неделя",
    val owner: String = "",
    val kind: String = ""
)

data class Lesson(
    val id: Long = 0L,
    val weekId: Long = 0L,
    val date: String,
    val pairNo: Int,
    val subject: String,
    val group: String = "",
    val room: String = "",
    val time: String = "",
    val teacher: String = "",
    val note: String = "",
    val done: Boolean = false
)

data class ParsedLesson(
    val date: String,
    val pairNo: Int,
    val subject: String,
    val group: String,
    val room: String,
    val teacher: String = ""
)