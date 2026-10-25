package ru.roman.schedule.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BellSlot(val start: LocalTime, val end: LocalTime)

data class BellsConfig(
    val monday: List<BellSlot?>,
    val weekdays: List<BellSlot?>,
    val saturday: List<BellSlot?>
)

object Bells {

    private const val PREFS = "bells"
    private const val KEY_MONDAY = "monday"
    private const val KEY_WEEKDAYS = "weekdays"
    private const val KEY_SATURDAY = "saturday"
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun defaults(): BellsConfig {
        val regular = listOf(
            BellSlot(LocalTime.of(8, 30), LocalTime.of(10, 5)),
            BellSlot(LocalTime.of(10, 15), LocalTime.of(11, 50)),
            BellSlot(LocalTime.of(12, 30), LocalTime.of(14, 5)),
            BellSlot(LocalTime.of(14, 15), LocalTime.of(15, 50)),
            BellSlot(LocalTime.of(16, 0), LocalTime.of(17, 35)),
            null
        )
        val saturday = listOf(
            BellSlot(LocalTime.of(8, 30), LocalTime.of(9, 50)),
            BellSlot(LocalTime.of(10, 0), LocalTime.of(11, 20)),
            BellSlot(LocalTime.of(11, 40), LocalTime.of(13, 0)),
            BellSlot(LocalTime.of(13, 10), LocalTime.of(14, 30)),
            null,
            null
        )
        return BellsConfig(monday = regular, weekdays = regular, saturday = saturday)
    }

    fun load(context: Context): BellsConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fallback = defaults()

        fun read(key: String, def: List<BellSlot?>): List<BellSlot?> {
            val raw = prefs.getString(key, null) ?: return def
            val parsed = raw.split(";").map { parseSlot(it) }
            return if (parsed.isEmpty()) def else parsed
        }

        return BellsConfig(
            monday = read(KEY_MONDAY, fallback.monday),
            weekdays = read(KEY_WEEKDAYS, fallback.weekdays),
            saturday = read(KEY_SATURDAY, fallback.saturday)
        )
    }

    fun save(context: Context, config: BellsConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MONDAY, encode(config.monday))
            .putString(KEY_WEEKDAYS, encode(config.weekdays))
            .putString(KEY_SATURDAY, encode(config.saturday))
            .apply()
    }

    fun slotsFor(config: BellsConfig, date: LocalDate): List<BellSlot?> = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> config.monday
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> config.saturday
        else -> config.weekdays
    }

    fun slotFor(config: BellsConfig, isoDate: String, pairNo: Int): BellSlot? {
        if (pairNo <= 0) return null
        val date = try {
            LocalDate.parse(isoDate)
        } catch (error: Exception) {
            return null
        }
        return slotsFor(config, date).getOrNull(pairNo - 1)
    }

    fun format(slot: BellSlot): String = slot.start.format(FORMAT) + "–" + slot.end.format(FORMAT)

    fun parseSlot(raw: String): BellSlot? {
        val parts = raw.trim().split("-")
        if (parts.size != 2) return null
        val start = parseTime(parts[0]) ?: return null
        val end = parseTime(parts[1]) ?: return null
        return BellSlot(start, end)
    }

    fun parseTime(raw: String): LocalTime? {
        return try {
            LocalTime.parse(raw.trim(), FORMAT)
        } catch (error: Exception) {
            null
        }
    }

    private fun encode(slots: List<BellSlot?>): String = slots.joinToString(";") { slot ->
        if (slot == null) "" else slot.start.format(FORMAT) + "-" + slot.end.format(FORMAT)
    }
}