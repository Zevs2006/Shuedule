package ru.roman.schedule.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import java.time.LocalDate
import kotlin.math.abs

data class ChangeRow(
    val group: String,
    val pairNo: Int,
    val oldSubject: String,
    val oldTeacher: String,
    val newSubject: String,
    val newTeacher: String,
    val room: String
)

data class ChangesDoc(val date: String, val rows: List<ChangeRow>)

/**
 * Разбор файла "Изменения в расписании" на конкретный день.
 *
 * Таблица: группа | пара | дисциплина по расписанию / преподаватель |
 * замена / преподаватель | кабинет. Группа может быть объединена
 * на несколько строк, поэтому наследуется сверху.
 */
class ChangesPdfParser {

    private data class Run(val text: String, val x: Float, val y: Float, val size: Float)

    private class Collector : PDFTextStripper() {
        val runs = ArrayList<Run>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val token = text.trim()
            if (token.isEmpty() || textPositions.isEmpty()) return
            val first = textPositions[0]
            runs.add(Run(token, first.x, first.y, first.fontSizeInPt))
        }
    }

    fun parse(input: InputStream): ChangesDoc? {
        val document = PDDocument.load(input)
        try {
            val collector = Collector()
            collector.sortByPosition = false
            collector.getText(document)
            val runs = collector.runs
            if (runs.isEmpty()) return null

            val subtitle = runs.firstOrNull { it.text.contains("неделя", ignoreCase = true) } ?: return null
            val date = DATE.find(subtitle.text)?.value?.let { parseDate(it) } ?: return null

            val pairRuns = runs
                .filter { it.x > 130f && it.x < 165f && it.size >= 10f && PAIR.matches(it.text) && it.y > 180f }
                .sortedBy { it.y }
            if (pairRuns.isEmpty()) return null

            val rows = pairRuns.map { Row(it.y, it.text.toInt(), ArrayList()) }

            for (run in runs) {
                if (run === subtitle) continue
                if (run.x > 130f && run.x < 165f && run.size >= 10f && PAIR.matches(run.text)) continue
                if (run.y < 180f) continue

                var best = -1
                var distance = Float.MAX_VALUE
                rows.forEachIndexed { index, row ->
                    val current = abs(run.y - row.y)
                    if (current < distance) {
                        distance = current
                        best = index
                    }
                }
                if (best >= 0 && distance < 16f) rows[best].items.add(run)
            }

            val groups = runs
                .filter { GROUP.matches(it.text) && it.x < 132f && it.size >= 10f }
                .sortedBy { it.y }

            val result = ArrayList<ChangeRow>()
            var lastGroup = ""
            for (row in rows) {
                val group = groups.lastOrNull { it.y <= row.y + 8f }?.text ?: lastGroup
                if (group.isBlank()) continue
                lastGroup = group

                result.add(
                    ChangeRow(
                        group = group,
                        pairNo = row.pair,
                        oldSubject = pick(row.items, 165f, 240f, 10f, 14f),
                        oldTeacher = pick(row.items, 240f, 340f, 8f, 10f),
                        newSubject = pick(row.items, 340f, 440f, 10f, 14f),
                        newTeacher = pick(row.items, 440f, 500f, 8f, 10f),
                        room = pick(row.items, 500f, 580f, 8f, 12f)
                    )
                )
            }

            if (result.isEmpty()) return null
            return ChangesDoc(date.toString(), result)
        } finally {
            document.close()
        }
    }

    private fun pick(items: List<Run>, xMin: Float, xMax: Float, sizeMin: Float, sizeMax: Float): String =
        items
            .filter { it.x >= xMin && it.x < xMax && it.size >= sizeMin && it.size <= sizeMax }
            .sortedWith(compareBy({ it.y }, { it.x }))
            .joinToString(" ") { it.text }
            .trim()

    private fun parseDate(dotted: String): LocalDate? {
        val parts = dotted.split(".")
        val day = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val year = parts.getOrNull(2)?.toIntOrNull() ?: return null
        return try {
            LocalDate.of(year, month, day)
        } catch (error: Exception) {
            null
        }
    }

    private data class Row(val y: Float, val pair: Int, val items: MutableList<Run>)

    private companion object {
        private val DATE = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")
        private val PAIR = Regex("^[1-9]$")
        private val GROUP = Regex("^[А-ЯЁ]{2,5}\\s+\\d{1,3}[а-яё]?$")
    }
}