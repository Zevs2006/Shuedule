package ru.roman.schedule.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import ru.roman.schedule.data.ParsedLesson
import java.io.InputStream
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class TeacherCard(
    val teacher: String,
    val lessons: List<ParsedLesson>,
    val isGroup: Boolean = false
)

/**
 * Разбор выгрузок aSc TimeTables:
 *  1) "Расписание преподавателей" — карточки на повёрнутой странице;
 *  2) "Расписание" студентов — таблица «дни x пары x группы»,
 *     ячейки определяются по линиям сетки, поэтому объединённые
 *     на несколько пар ячейки учитываются целиком.
 */
class PdfScheduleParser {

    private data class Run(val text: String, val x: Float, val y: Float, val size: Float)

    private data class LoadedPage(val runs: List<Run>, val segments: List<TableGrid.Segment>)

    private class Collector : PDFTextStripper() {
        val runs = ArrayList<Run>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val token = text.trim()
            if (token.isEmpty() || textPositions.isEmpty()) return
            val first = textPositions[0]
            runs.add(Run(token, first.x, first.y, first.fontSizeInPt))
        }
    }

    fun parse(input: InputStream): List<TeacherCard> {
        val document = PDDocument.load(input)
        try {
            val collector = Collector()
            collector.sortByPosition = false
            val pages = ArrayList<LoadedPage>()

            for (pageIndex in 0 until document.numberOfPages) {
                collector.runs.clear()
                collector.startPage = pageIndex + 1
                collector.endPage = pageIndex + 1
                collector.getText(document)
                pages.add(LoadedPage(collector.runs.toList(), TableGrid.segments(document, pageIndex)))
            }

            if (pages.isEmpty()) return emptyList()
            val hasTeacherCards = pages.any { page -> page.runs.any { it.text.startsWith(TEACHER_HEAD) } }
            return if (hasTeacherCards) {
                mergeByTeacher(pages.flatMap { buildTeacherCards(it.runs) })
            } else {
                buildStudentCards(pages)
            }
        } finally {
            document.close()
        }
    }

    // ---------- формат "Расписание преподавателей" ----------

    private fun buildTeacherCards(runs: List<Run>): List<TeacherCard> {
        if (runs.isEmpty()) return emptyList()
        val result = ArrayList<TeacherCard>()

        for (name in runs.filter { NAME_HEAD.matches(it.text) && it.size > 10f }) {
            val teacher = name.text.replace(NAME_PREFIX, "").trim()
            if (teacher.isEmpty()) continue

            val inside = runs.filter {
                it.x >= name.x - CARD_LEFT_OFFSET && it.x <= name.x + CARD_RIGHT_OFFSET &&
                    it.y >= name.y - 2f && it.y <= name.y + CARD_HEIGHT
            }
            val days = inside.filter { DAY_HEAD.matches(it.text) }.sortedBy { it.x }
            if (days.isEmpty()) continue

            val anchors = inside
                .filter { GROUP_ANCHOR.matches(it.text) && it.size >= 8.5f }
                .sortedWith(compareBy({ it.y }, { it.x }))
            if (anchors.isEmpty()) continue

            val firstDayX = days.first().x
            val rows = ArrayList<Float>()
            for (anchor in anchors) {
                if (rows.none { abs(it - anchor.y) < ROW_TOLERANCE }) rows.add(anchor.y)
            }
            rows.sort()

            val lessons = ArrayList<ParsedLesson>()
            for (anchor in anchors) {
                val day = ((anchor.x - firstDayX) / DAY_PITCH).roundToInt()
                if (day < 0 || day >= days.size) continue

                val pair = rows.indexOfFirst { abs(it - anchor.y) < ROW_TOLERANCE } + 1
                if (pair <= 0) continue

                val date = dayDate(days[day].text)?.toString() ?: continue

                val subject = inside
                    .filter {
                        it.size >= 5.5f && it.size < 7.5f &&
                            it.x - anchor.x > CELL_DX_MIN && it.x - anchor.x < CELL_DX_MAX &&
                            abs(it.y - (anchor.y + SUBJECT_DY)) < SUBJECT_DY_LIMIT
                    }
                    .sortedBy { it.x }
                    .joinToString(" ") { it.text }

                val room = inside
                    .filter {
                        it.size >= 4f && it.size < 5.5f &&
                            it.x - anchor.x > CELL_DX_MIN && it.x - anchor.x < CELL_DX_MAX &&
                            abs(it.y - (anchor.y + ROOM_DY)) < ROOM_DY_LIMIT
                    }
                    .sortedBy { it.x }
                    .joinToString(" ") { it.text }

                lessons.add(
                    ParsedLesson(
                        date = date,
                        pairNo = pair,
                        subject = subject,
                        group = anchor.text.trim(),
                        room = room
                    )
                )
            }

            if (lessons.isNotEmpty()) {
                result.add(
                    TeacherCard(
                        teacher = teacher,
                        lessons = lessons.distinct().sortedWith(compareBy({ it.date }, { it.pairNo }))
                    )
                )
            }
        }
        return result
    }

    // ---------- формат "Расписание" студентов ----------

    private fun buildStudentCards(pages: List<LoadedPage>): List<TeacherCard> {
        val byGroup = LinkedHashMap<String, MutableList<ParsedLesson>>()

        for (page in pages) {
            val weekStart = weekStart(page.runs) ?: continue
            val groups = page.runs.filter { GROUP_HEAD.matches(it.text) && it.size > 10f }.sortedBy { it.x }
            if (groups.isEmpty()) continue

            val pairRuns = page.runs
                .filter { PAIR_NUM.matches(it.text) && it.size in 6f..8f && it.x < 70f }
                .sortedBy { it.y }
            if (pairRuns.isEmpty()) continue

            val markDay = ArrayList<Int>()
            var day = 0
            for (mark in pairRuns) {
                if (mark.text == "1" && markDay.isNotEmpty()) day += 1
                markDay.add(day)
            }

            val vertical = page.segments
                .filter { abs(it.x0 - it.x1) < 0.6f }
                .map { (it.x0 + it.x1) / 2f }
            val xs = cluster(vertical)
            if (xs.size < 2) continue

            val horizontal = page.segments
                .filter { abs(it.y0 - it.y1) < 0.6f }
                .map { Line((it.y0 + it.y1) / 2f, minOf(it.x0, it.x1), maxOf(it.x0, it.x1)) }

            for (header in groups) {
                val columnIndex = xs.indexOfLast { it <= header.x + 1f }
                if (columnIndex < 0 || columnIndex >= xs.size - 1) continue
                val left = xs[columnIndex]
                val right = xs[columnIndex + 1]
                if (right - left < 20f) continue

                val ys = cluster(
                    horizontal
                        .filter { it.x0 <= left + 2f && it.x1 >= right - 2f }
                        .map { it.y }
                )
                if (ys.size < 2) continue

                val rowGaps = ys.zipWithNext { a, b -> b - a }.filter { it > 5f }
                val rowHeight = rowGaps.minOrNull() ?: 22.8f
                val dayHeight = rowHeight * 5f
                val gridOrigin = pairRuns.first().y - 17.5f
                var columnOrigin: Float? = null

                val groupName = header.text

                for (i in 0 until ys.size - 1) {
                    val top = ys[i]
                    val bottom = ys[i + 1]
                    if (bottom - top < 8f) continue

                    val cellRuns = page.runs.filter { run ->
                        val centerY = run.y - run.size / 2f
                        run.x >= left - 2f && run.x < right &&
                            centerY > top && centerY < bottom
                    }
                    if (cellRuns.isEmpty()) continue

                    val subjects = cellRuns
                        .filter { it.size >= 6.5f && it.size < 8f && !PAIR_NUM.matches(it.text) && !GROUP_HEAD.matches(it.text) }
                        .sortedBy { it.y }
                        .joinToString(" ") { it.text }
                    val teacher = cellRuns.firstOrNull { TEACHER_NAME.matches(it.text) }?.text ?: ""
                    val room = cellRuns
                        .filter {
                            it.size >= 4f && it.size < 6.5f &&
                                !TEACHER_NAME.matches(it.text) &&
                                !PAIR_NUM.matches(it.text) &&
                                !GROUP_HEAD.matches(it.text)
                        }
                        .sortedBy { it.x }
                        .joinToString(" ") { it.text }

                    if (subjects.isBlank() && teacher.isBlank()) continue

                    // День определяется блоками по 5 строк. У объединённых ячеек
                    // (например, практика на три пары) отсчёт идёт от первой
                    // ячейки колонки, у обычных — от сетки номеров пар.
                    if (columnOrigin == null) columnOrigin = top
                    val originValue = columnOrigin ?: top
                    val span = ((bottom - top) / rowHeight).roundToInt().coerceAtLeast(1)
                    val base = if (span >= 2) originValue else gridOrigin
                    val dayIndex = (((top - base) / dayHeight) + 0.02f).toInt().coerceAtLeast(0)
                    val dayStart = base + dayIndex * dayHeight
                    val pairStart = ((top - dayStart) / rowHeight).roundToInt() + 1

                    for (offsetIndex in 0 until span) {
                        val pairNo = pairStart + offsetIndex
                        if (pairNo > 5) break
                        val date = weekStart.plusDays(dayIndex.toLong()).toString()
                        byGroup.getOrPut(groupName) { ArrayList() }.add(
                            ParsedLesson(
                                date = date,
                                pairNo = pairNo,
                                subject = subjects,
                                group = groupName,
                                room = room,
                                teacher = teacher
                            )
                        )
                    }
                }
            }
        }

        return byGroup.map { (name, rawLessons) ->
            val sorted = rawLessons.distinct().sortedWith(compareBy({ it.date }, { it.pairNo }))
            val donors = sorted.filter { it.subject.isNotBlank() }
            val fixed = sorted.map { lesson ->
                if (lesson.subject.isNotBlank()) {
                    lesson
                } else {
                    val donor = donors.filter { it.date == lesson.date }.minByOrNull { abs(it.pairNo - lesson.pairNo) }
                    if (donor != null) lesson.copy(subject = donor.subject) else lesson
                }
            }
            TeacherCard(name, fixed, isGroup = true)
        }
    }

    private data class Line(val y: Float, val x0: Float, val x1: Float)

    private fun cluster(values: List<Float>): List<Float> {
        val sorted = values.sorted()
        val result = ArrayList<Float>()
        for (value in sorted) {
            val last = result.lastOrNull()
            if (last == null || value - last > 1.0f) result.add(value)
        }
        return result
    }

    private fun weekStart(runs: List<Run>): LocalDate? {
        val header = runs.firstOrNull { WEEK_RANGE.containsMatchIn(it.text) } ?: return null
        val match = WEEK_RANGE.find(header.text) ?: return null
        return dayDate(match.groupValues[1])
    }

    private fun dayDate(dotted: String): LocalDate? {
        val clean = dotted.trim().split(Regex("\\s+")).last()
        val parts = clean.split(".")
        val day = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        var year = LocalDate.now().year
        var date = LocalDate.of(year, month, day)
        if (date.isBefore(LocalDate.now().minusDays(200))) {
            year += 1
            date = LocalDate.of(year, month, day)
        }
        return date
    }

    private fun mergeByTeacher(cards: List<TeacherCard>): List<TeacherCard> {
        val byTeacher = LinkedHashMap<String, MutableList<ParsedLesson>>()
        for (card in cards) {
            byTeacher.getOrPut(card.teacher) { ArrayList() }.addAll(card.lessons)
        }
        return byTeacher.map { (teacher, lessons) ->
            TeacherCard(teacher, lessons.distinct().sortedWith(compareBy({ it.date }, { it.pairNo })))
        }
    }

    private companion object {
        private const val TEACHER_HEAD = "Преподаватель"

        private const val DAY_PITCH = 58.8f
        private const val SUBJECT_DY = 16.9f
        private const val ROOM_DY = -3.6f
        private const val SUBJECT_DY_LIMIT = 10f
        private const val ROOM_DY_LIMIT = 8f
        private const val CELL_DX_MIN = -10f
        private const val CELL_DX_MAX = 47f
        private const val ROW_TOLERANCE = 12f
        private const val CARD_LEFT_OFFSET = 135f
        private const val CARD_RIGHT_OFFSET = 275f
        private const val CARD_HEIGHT = 180f

        private val NAME_HEAD = Regex("^Преподаватель\\s+.+$")
        private val NAME_PREFIX = Regex("^Преподаватель\\s+")
        private val DAY_HEAD = Regex("^[А-Яа-яЁё]{2}\\s+\\d{2}\\.\\d{2}$")
        private val GROUP_ANCHOR = Regex("^[А-ЯЁ]{2,4}\\s+\\d{1,3}[а-яё]?$")

        private val GROUP_HEAD = Regex("^[А-ЯЁ]{2,5}\\s+\\d{1,3}$")
        private val PAIR_NUM = Regex("^[1-9]$")
        private val TEACHER_NAME = Regex("^[А-ЯЁ][а-яё-]+\\s+[А-ЯЁ]\\.\\s*[А-ЯЁ]\\.$")
        private val WEEK_RANGE = Regex("(\\d{2}\\.\\d{2})-\\d{2}\\.\\d{2}")
    }
}