package ru.roman.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.roman.schedule.data.BellSlot
import ru.roman.schedule.data.Bells
import ru.roman.schedule.data.Lesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

private val WEEKDAYS = listOf("", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private val ACCENTS = listOf(
    Color(0xFF3D5AFE),
    Color(0xFF00897B),
    Color(0xFFEF6C00),
    Color(0xFF8E24AA),
    Color(0xFFC62828),
    Color(0xFF3949AB),
    Color(0xFF6D4C41),
    Color(0xFF00838F)
)

private fun accentFor(subject: String): Color {
    if (subject.isBlank()) return Color(0xFF78909C)
    return ACCENTS[abs(subject.hashCode()) % ACCENTS.size]
}

@Composable
fun ThemeDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тема оформления") },
        text = {
            Column {
                ThemeRow("Как в системе", current == ThemeMode.SYSTEM) { onPick(ThemeMode.SYSTEM) }
                ThemeRow("Светлая", current == ThemeMode.LIGHT) { onPick(ThemeMode.LIGHT) }
                ThemeRow("Тёмная", current == ThemeMode.DARK) { onPick(ThemeMode.DARK) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun ThemeRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (selected) "● " else "○ ")
        Text(text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun Header(
    owner: String,
    title: String,
    onOpenWeeks: () -> Unit,
    onWeeks: () -> Unit,
    onImport: () -> Unit,
    onBells: () -> Unit,
    onSearch: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
    onTheme: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onChanges: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable { onOpenWeeks() }
        ) {
            Text(
                text = if (owner.isBlank()) "РАСПИСАНИЕ" else owner.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (title.isBlank()) "Нет недели" else title,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "▾",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text("•••", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Недели") },
                    onClick = { menuOpen = false; onWeeks() }
                )
                DropdownMenuItem(
                    text = { Text("Импорт PDF") },
                    onClick = { menuOpen = false; onImport() }
                )
                DropdownMenuItem(
                    text = { Text("Расписание звонков") },
                    onClick = { menuOpen = false; onBells() }
                )
                DropdownMenuItem(
                    text = { Text("Поиск") },
                    onClick = { menuOpen = false; onSearch() }
                )
                DropdownMenuItem(
                    text = { Text("Сегодня") },
                    onClick = { menuOpen = false; onToday() }
                )
                DropdownMenuItem(
                    text = { Text("В календарь") },
                    onClick = { menuOpen = false; onCalendar() }
                )
                DropdownMenuItem(
                    text = { Text("Тема оформления") },
                    onClick = { menuOpen = false; onTheme() }
                )
                DropdownMenuItem(
                    text = { Text("Резервная копия") },
                    onClick = { menuOpen = false; onBackup() }
                )
                DropdownMenuItem(
                    text = { Text("Восстановить из копии") },
                    onClick = { menuOpen = false; onRestore() }
                )
                DropdownMenuItem(
                    text = { Text("Изменения на день") },
                    onClick = { menuOpen = false; onChanges() }
                )
            }
        }
    }
}

@Composable
fun SegmentedMode(weekMode: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SegmentItem("День", !weekMode) { onToggle(false) }
        SegmentItem("Неделя", weekMode) { onToggle(true) }
    }
}

@Composable
private fun SegmentItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DayTabs(
    days: List<LocalDate>,
    lessons: List<Lesson>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { day ->
            val isSelected = day == selected
            val isToday = day == LocalDate.now()
            val count = lessons.count { it.date == day.toString() }

            Column(
                modifier = Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .clickable { onSelect(day) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = WEEKDAYS[day.dayOfWeek.value],
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = day.format(dayFmt),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        count > 0 -> count.toString()
                        isToday -> "•"
                        else -> " "
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LessonCardV2(
    lesson: Lesson,
    bell: BellSlot?,
    now: LocalTime,
    onToggleDone: () -> Unit,
    onClick: () -> Unit
) {
    val isNow = bell != null &&
        lesson.date == LocalDate.now().toString() &&
        !now.isBefore(bell.start) && now.isBefore(bell.end)

    val timeText = when {
        lesson.time.isNotBlank() -> lesson.time
        bell != null -> bell.start.toString() + "–" + bell.end.toString()
        else -> ""
    }

    val details = buildList {
        if (lesson.group.isNotBlank()) add(lesson.group)
        if (lesson.room.isNotBlank()) add("каб. " + lesson.room)
        if (lesson.teacher.isNotBlank()) add(lesson.teacher)
    }.joinToString(" · ")

    val isPast = bell != null && lesson.date == LocalDate.now().toString() && now.isAfter(bell.end)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.55f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (isNow) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accentFor(lesson.subject)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lesson.pairNo.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = lesson.subject.ifBlank { "Без названия" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (lesson.note.isNotBlank()) {
                Text(
                    text = lesson.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isNow) {
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ИДЁТ",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (lesson.note.isNotBlank()) {
                Checkbox(checked = lesson.done, onCheckedChange = { onToggleDone() })
            }
        }
    }
}

@Composable
fun EmptyStateV2(hasWeek: Boolean) {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗓", fontSize = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hasWeek) "На этот день пар нет" else "Расписания пока нет",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasWeek) {
                    "Нажмите «+», чтобы добавить занятие"
                } else {
                    "Меню «•••» → «Импорт PDF»"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
