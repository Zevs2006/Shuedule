package ru.roman.schedule.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.roman.schedule.data.BellSlot
import ru.roman.schedule.data.Backup
import ru.roman.schedule.data.Bells
import ru.roman.schedule.data.BellsConfig
import ru.roman.schedule.data.DbHelper
import ru.roman.schedule.data.Lesson
import ru.roman.schedule.data.ScheduleRepo
import ru.roman.schedule.data.Week
import ru.roman.schedule.notifications.Reminders
import ru.roman.schedule.pdf.ChangeRow
import ru.roman.schedule.pdf.ChangesPdfParser
import ru.roman.schedule.pdf.PdfScheduleParser
import ru.roman.schedule.widget.TodayWidgetProvider
import ru.roman.schedule.pdf.TeacherCard
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val subjectPalette = listOf(
    Color(0xFF4A6FA5),
    Color(0xFF8C5E4E),
    Color(0xFF4E7A63),
    Color(0xFF7A5C8C),
    Color(0xFF9A6B3F),
    Color(0xFF3F7A85)
)

private fun subjectColor(subject: String): Color {
    if (subject.isBlank()) return Color(0xFF7B8794)
    return subjectPalette[abs(subject.hashCode()) % subjectPalette.size]
}

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

private val weekdayShort = mapOf(
    DayOfWeek.MONDAY to "Пн",
    DayOfWeek.TUESDAY to "Вт",
    DayOfWeek.WEDNESDAY to "Ср",
    DayOfWeek.THURSDAY to "Чт",
    DayOfWeek.FRIDAY to "Пт",
    DayOfWeek.SATURDAY to "Сб",
    DayOfWeek.SUNDAY to "Вс"
)

@Composable
fun ScheduleApp() {
    val context = LocalContext.current
    val repo = remember { ScheduleRepo(DbHelper(context.applicationContext)) }
    val scope = rememberCoroutineScope()

    var weeks by remember { mutableStateOf<List<Week>>(emptyList()) }
    var currentId by remember { mutableStateOf(0L) }
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Lesson?>(null) }

    var parsing by remember { mutableStateOf(false) }
    var parsedCards by remember { mutableStateOf<List<TeacherCard>>(emptyList()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var selectedTeacher by remember { mutableStateOf("") }
    var weeksOpen by remember { mutableStateOf(false) }
    var weekToDelete by remember { mutableStateOf<Week?>(null) }
    var weekMode by remember { mutableStateOf(false) }
    var bells by remember { mutableStateOf(Bells.defaults()) }
    var bellsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var themeOpen by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var changesDate by remember { mutableStateOf("") }
    var changesMatches by remember { mutableStateOf<List<ChangeMatch>?>(null) }
    val nowState = produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(30_000)
        }
    }
    val currentTime = nowState.value

    fun toggleDone(lesson: Lesson) {
        scope.launch {
            withContext(Dispatchers.IO) { repo.saveLesson(lesson.copy(done = !lesson.done)) }
            revision++
        }
    }

    fun scheduleReminders() {
        scope.launch {
            val allLessons = withContext(Dispatchers.IO) {
                val collected = ArrayList<Lesson>()
                for (weekItem in repo.weeks()) {
                    collected.addAll(repo.lessons(weekItem.id))
                }
                collected
            }
            withContext(Dispatchers.IO) { Reminders.reschedule(context, allLessons, bells) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scheduleReminders()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                parsing = true
                importError = null
                try {
                    val cards = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            PdfScheduleParser().parse(stream)
                        } ?: emptyList()
                    }
                    if (cards.isEmpty()) {
                        importError = "В файле не найдено расписание.\n" +
                            "Поддерживаются выгрузки aSc: «Расписание преподавателей» и «Расписание» студентов."
                    } else {
                        parsedCards = cards
                        selectedTeacher = cards.first().teacher
                    }
                } catch (error: Exception) {
                    importError = "Не удалось прочитать PDF: " + (error.message ?: "неизвестная ошибка")
                } finally {
                    parsing = false
                }
            }
        }
    }

    val changesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                parsing = true
                importError = null
                try {
                    val doc = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            ChangesPdfParser().parse(stream)
                        }
                    }
                    if (doc == null) {
                        importError = "Не похоже на файл изменений.\nОжидается таблица «группа / пара / дисциплина / замена / каб.»"
                    } else {
                        val target = LocalDate.parse(doc.date)
                        val candidates = withContext(Dispatchers.IO) {
                            val collected = ArrayList<Lesson>()
                            for (weekItem in repo.weeks()) {
                                val start = runCatching { LocalDate.parse(weekItem.startDate) }.getOrNull() ?: continue
                                val end = runCatching { LocalDate.parse(weekItem.endDate) }.getOrNull() ?: continue
                                if (!target.isBefore(start) && !target.isAfter(end)) {
                                    collected.addAll(repo.lessons(weekItem.id))
                                }
                            }
                            collected
                        }
                        changesDate = doc.date
                        changesMatches = doc.rows.map { row ->
                            val lesson = candidates.firstOrNull {
                                it.date == doc.date &&
                                    it.pairNo == row.pairNo &&
                                    normalizeGroup(it.group) == normalizeGroup(row.group)
                            }
                            ChangeMatch(row, lesson)
                        }
                    }
                } catch (error: Exception) {
                    importError = "Не удалось разобрать изменения: " + (error.message ?: "ошибка")
                } finally {
                    parsing = false
                }
            }
        }
    }

    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (text.isNullOrBlank()) {
                    importError = "Файл копии пуст или не читается"
                } else {
                    pendingRestore = text
                }
            }
        }
    }

    fun exportBackup() {
        scope.launch {
            val json = withContext(Dispatchers.IO) { Backup.toJson(repo) }
            val dir = File(context.cacheDir, "shared")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "schedule-backup.json")
            withContext(Dispatchers.IO) { file.writeText(json, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Резервная копия расписания")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(send, "Сохранить копию"))
        }
    }

    LaunchedEffect(Unit) {
        bells = Bells.load(context)
        withContext(Dispatchers.IO) { repo.bootstrap() }
        val all = withContext(Dispatchers.IO) { repo.weeks() }
        weeks = all
        val latest = all.firstOrNull()
        if (latest != null) {
            currentId = latest.id
            selectedDay = LocalDate.parse(latest.startDate)
        }
        if (Reminders.isEnabled(context)) scheduleReminders()
    }

    LaunchedEffect(currentId, revision) {
        if (currentId != 0L) {
            lessons = withContext(Dispatchers.IO) { repo.lessons(currentId) }
        }
        TodayWidgetProvider.refresh(context)
    }

    val week = weeks.firstOrNull { it.id == currentId }
    val weekStart = week?.let { LocalDate.parse(it.startDate) }
    val weekEnd = week?.let { LocalDate.parse(it.endDate) }

    val days: List<LocalDate> = week?.let { daysOf(it) } ?: emptyList()

    val dayLessons = remember(lessons, selectedDay) {
        lessons.filter { it.date == selectedDay.toString() }.sortedBy { it.pairNo }
    }

    val weekIndex = weeks.indexOfFirst { it.id == currentId }

    Scaffold(
        floatingActionButton = {
            if (currentId != 0L) {
                FloatingActionButton(onClick = {
                    editing = Lesson(
                        weekId = currentId,
                        date = selectedDay.toString(),
                        pairNo = (dayLessons.maxOfOrNull { it.pairNo } ?: 0) + 1,
                        subject = ""
                    )
                }) {
                    Text("+", fontSize = 28.sp)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(
                owner = week?.owner ?: "",
                title = week?.title ?: "",
                onOpenWeeks = { weeksOpen = true },
                onWeeks = { weeksOpen = true },
                onImport = { picker.launch(arrayOf("application/pdf")) },
                onBells = { bellsOpen = true },
                onSearch = { searchOpen = true },
                onCalendar = {
                    val currentWeek = weeks.firstOrNull { it.id == currentId }
                    if (currentWeek != null) {
                        IcsExport.share(context, currentWeek.title, lessons, bells)
                    }
                },
                onTheme = { themeOpen = true },
                onBackup = { exportBackup() },
                onRestore = { backupPicker.launch(arrayOf("*/*")) },
                onChanges = { changesPicker.launch(arrayOf("application/pdf")) },
                onToday = {
                    val today = LocalDate.now()
                    val current = week
                    if (current != null) {
                        val start = LocalDate.parse(current.startDate)
                        val end = LocalDate.parse(current.endDate)
                        selectedDay = when {
                            today < start -> start
                            today > end -> end
                            else -> today
                        }
                    }
                }
            )

            SegmentedMode(weekMode = weekMode, onToggle = { weekMode = it })

            if (!weekMode) {
                DayTabs(days = days, lessons = lessons, selected = selectedDay, onSelect = { selectedDay = it })
            }

            Spacer(Modifier.height(8.dp))

            if (weekMode) {
                WeekList(days = days, lessons = lessons, bells = bells, now = currentTime, onToggleDone = { toggleDone(it) }, onLessonClick = { editing = it })
            } else if (dayLessons.isEmpty()) {
                EmptyStateV2(hasWeek = currentId != 0L)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayLessons, key = { it.id }) { lesson ->
                        LessonCardV2(lesson, Bells.slotFor(bells, lesson.date, lesson.pairNo), currentTime, onToggleDone = { toggleDone(lesson) }) { editing = lesson }
                    }
                }
            }
        }
    }

    val matches = changesMatches
    if (matches != null) {
        ChangeDialog(
            date = changesDate,
            matches = matches,
            onDismiss = { changesMatches = null },
            onApply = {
                changesMatches = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        for (match in matches) {
                            val lesson = match.lesson ?: continue
                            val subject = if (meaningful(match.row.newSubject)) match.row.newSubject else lesson.subject
                            val teacher = if (meaningful(match.row.newTeacher)) match.row.newTeacher else lesson.teacher
                            val room = if (match.row.room.isNotBlank()) match.row.room else lesson.room
                            repo.saveLesson(lesson.copy(subject = subject, teacher = teacher, room = room))
                        }
                    }
                    revision++
                }
            }
        )
    }

    val restoreText = pendingRestore
    if (restoreText != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Восстановить из копии?") },
            text = { Text("Текущее расписание будет заменено данными из файла.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) {
                                repo.clearAll()
                                Backup.fromJson(repo, restoreText)
                            }
                            val all = withContext(Dispatchers.IO) { repo.weeks() }
                            weeks = all
                            val first = all.firstOrNull()
                            currentId = first?.id ?: 0L
                            if (first != null) selectedDay = LocalDate.parse(first.startDate)
                            revision++
                            if (count == 0) importError = "В копии не найдено недель"
                        } catch (error: Exception) {
                            importError = "Не удалось прочитать копию: " + (error.message ?: "ошибка")
                        }
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Отмена") }
            }
        )
    }

    if (themeOpen) {
        ThemeDialog(
            current = ThemeSettings.mode,
            onPick = { picked ->
                ThemeSettings.set(context, picked)
                themeOpen = false
            },
            onDismiss = { themeOpen = false }
        )
    }

    if (searchOpen) {
        SearchDialog(
            lessons = lessons,
            onDismiss = { searchOpen = false },
            onPick = { picked ->
                searchOpen = false
                weekMode = false
                selectedDay = LocalDate.parse(picked.date)
            }
        )
    }

    if (bellsOpen) {
        BellDialog(
            config = bells,
            remindersEnabled = Reminders.isEnabled(context),
            onDismiss = { bellsOpen = false },
            onSave = { updated, remindersOn ->
                Bells.save(context, updated)
                bells = updated
                bellsOpen = false
                Reminders.setEnabled(context, remindersOn)
                if (remindersOn) {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scheduleReminders()
                    }
                } else {
                    scope.launch {
                        withContext(Dispatchers.IO) { Reminders.reschedule(context, emptyList(), updated) }
                    }
                }
            }
        )
    }

    if (weeksOpen) {
        AlertDialog(
            onDismissRequest = { weeksOpen = false },
            title = { Text("Недели") },
            text = {
                if (weeks.isEmpty()) {
                    Text("Пока нет ни одной недели. Импортируйте PDF.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(weeks) { w ->
                            val isCurrent = w.id == currentId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentId = w.id
                                        selectedDay = LocalDate.parse(w.startDate)
                                        weeksOpen = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isCurrent) "● " else "○ ")
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (w.owner.isBlank()) "Без названия" else w.owner,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = w.title,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "✕",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { weekToDelete = w }.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { weeksOpen = false }) { Text("Закрыть") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val currentWeek = weeks.firstOrNull { it.id == currentId }
                    if (currentWeek != null) {
                        IcsExport.share(context, currentWeek.title, lessons, bells)
                    }
                }) { Text("В календарь") }
            }
        )
    }

    val pendingDelete = weekToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { weekToDelete = null },
            title = { Text("Удалить неделю?") },
            text = {
                Text(
                    (if (pendingDelete.owner.isBlank()) "Без названия" else pendingDelete.owner) +
                        " · " + pendingDelete.title +
                        "\nВсе занятия этой недели будут удалены."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    weekToDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteWeek(pendingDelete.id) }
                        val all = withContext(Dispatchers.IO) { repo.weeks() }
                        weeks = all
                        if (currentId == pendingDelete.id) {
                            val next = all.firstOrNull()
                            currentId = next?.id ?: 0L
                            if (next != null) selectedDay = LocalDate.parse(next.startDate)
                        }
                        revision++
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { weekToDelete = null }) { Text("Отмена") }
            }
        )
    }

    if (parsing) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Читаю расписание") },
            text = { Text("Разбираю PDF и ищу карточки преподавателей…") },
            confirmButton = { }
        )
    }

    val error = importError
    if (error != null) {
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Импорт не удался") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text("Понятно") }
            }
        )
    }

    if (parsedCards.isNotEmpty()) {
        ImportPreviewDialog(
            cards = parsedCards,
            selected = selectedTeacher,
            onSelect = { selectedTeacher = it },
            onDismiss = { parsedCards = emptyList() },
            onSave = {
                val card = parsedCards.firstOrNull { it.teacher == selectedTeacher } ?: parsedCards.first()
                parsedCards = emptyList()
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { saveWeek(repo, card) }
                    val all = withContext(Dispatchers.IO) { repo.weeks() }
                    weeks = all
                    currentId = saved.id
                    selectedDay = LocalDate.parse(saved.startDate)
                    revision++
                    scheduleReminders()
                }
            }
        )
    }

    val editable = editing
    if (editable != null) {
        LessonDialog(
            initial = editable,
            dayLabel = dayLabel(editable.date),
            onDismiss = { editing = null },
            onDelete = if (editable.id != 0L) {
                {
                    editing = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteLesson(editable.id) }
                        revision++
                    }
                }
            } else null,
            onSave = { updated ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { repo.saveLesson(updated) }
                    revision++
                }
            }
        )
    }
}

private fun saveWeek(repo: ScheduleRepo, card: TeacherCard): Week {
    val dates = card.lessons.map { LocalDate.parse(it.date) }.sorted()
    val start = dates.first()
    val end = dates.last()
    val title = start.format(dayFormat) + " – " + end.format(dayFormat)
    val existing = repo.findWeek(start.toString(), end.toString(), card.teacher)
    if (existing != null) repo.deleteWeek(existing.id)

    val week = Week(
        startDate = start.toString(),
        endDate = end.toString(),
        title = title,
        owner = card.teacher,
        kind = if (card.isGroup) "group" else "teacher"
    )
    val id = repo.insertWeek(
        week,
        card.lessons.map {
            Lesson(
                weekId = 0L,
                date = it.date,
                pairNo = it.pairNo,
                subject = it.subject,
                group = it.group,
                room = it.room,
                teacher = it.teacher
            )
        }
    )
    return week.copy(id = id)
}

@Composable
private fun SearchDialog(lessons: List<Lesson>, onDismiss: () -> Unit, onPick: (Lesson) -> Unit) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val results = remember(lessons, trimmed) {
        if (trimmed.length < 2) {
            emptyList()
        } else {
            lessons.filter {
                it.subject.contains(trimmed, true) ||
                    it.group.contains(trimmed, true) ||
                    it.room.contains(trimmed, true) ||
                    it.teacher.contains(trimmed, true) ||
                    it.note.contains(trimmed, true)
            }.sortedWith(compareBy({ it.date }, { it.pairNo })).take(60)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск по неделе") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Предмет, группа, кабинет, преподаватель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                when {
                    trimmed.length < 2 -> Text(
                        "Введите хотя бы два символа",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    results.isEmpty() -> Text("Ничего не найдено", fontSize = 13.sp)
                    else -> LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
                        items(results, key = { it.id }) { lesson ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(lesson) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = dayLabel(lesson.date) + " · " + lesson.pairNo + " пара · " + lesson.subject,
                                    fontSize = 13.sp
                                )
                                val extra = buildList {
                                    if (lesson.group.isNotBlank()) add(lesson.group)
                                    if (lesson.room.isNotBlank()) add(lesson.room)
                                    if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                                }.joinToString(" · ")
                                if (extra.isNotBlank()) {
                                    Text(
                                        text = extra,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ModeSwitch(weekMode: Boolean, onToggle: (Boolean) -> Unit, onBells: () -> Unit, onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            TextButton(onClick = onBells) { Text("Звонки", fontSize = 13.sp) }
            TextButton(onClick = onSearch) { Text("Поиск", fontSize = 13.sp) }
        }
        Row {
        TextButton(onClick = { onToggle(false) }) {
            Text(
                text = "День",
                fontSize = 13.sp,
                fontWeight = if (!weekMode) FontWeight.Bold else FontWeight.Normal
            )
        }
        TextButton(onClick = { onToggle(true) }) {
            Text(
                text = "Неделя",
                fontSize = 13.sp,
                fontWeight = if (weekMode) FontWeight.Bold else FontWeight.Normal
            )
        }
        }
    }
}

@Composable
private fun WeekList(days: List<LocalDate>, lessons: List<Lesson>, bells: BellsConfig, now: LocalTime, onToggleDone: (Lesson) -> Unit, onLessonClick: (Lesson) -> Unit) {
    val grouped = days.map { day ->
        day to lessons.filter { it.date == day.toString() }.sortedBy { it.pairNo }
    }.filter { it.second.isNotEmpty() }

    if (grouped.isEmpty()) {
        EmptyStateV2(hasWeek = true)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { pair ->
            val day = pair.first
            val list = pair.second
            item(key = "day-" + day.toString()) {
                Text(
                    text = dayLabel(day.toString()) + "  ·  " + list.size + " " + pairsWord(list.size),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            items(list, key = { it.id }) { lesson ->
                LessonCardV2(lesson, Bells.slotFor(bells, lesson.date, lesson.pairNo), now, onToggleDone = { onToggleDone(lesson) }) { onLessonClick(lesson) }
            }
        }
    }
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

private data class ChangeMatch(val row: ChangeRow, val lesson: Lesson?)

private fun normalizeGroup(value: String): String = value.replace(" ", "").replace("-", "").uppercase()

private fun meaningful(value: String): Boolean =
    value.isNotBlank() && value != "-" && value != "—"

@Composable
private fun ChangeDialog(
    date: String,
    matches: List<ChangeMatch>,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    val applicable = matches.count { it.lesson != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменения на " + shortDate(date)) },
        text = {
            Column {
                Text(
                    "Строк в файле: " + matches.size + ", совпало с расписанием: " + applicable,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                    items(matches) { match ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            val target = if (meaningful(match.row.newSubject)) match.row.newSubject else match.row.oldSubject
                            Text(
                                text = match.row.group + " · " + match.row.pairNo + " пара · " + target,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val details = buildList {
                                if (match.row.oldTeacher.isNotBlank()) add("было: " + match.row.oldTeacher)
                                if (meaningful(match.row.newTeacher)) add("замена: " + match.row.newTeacher)
                                if (match.row.room.isNotBlank()) add("каб. " + match.row.room)
                                if (match.lesson == null) add("нет в приложении")
                            }.joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(
                                    text = details,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = applicable > 0) { Text("Применить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun WeekBar(
    title: String,
    owner: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onToday: () -> Unit,
    onTitleClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onImport) { Text("Импорт") }
        TextButton(onClick = onPrev, enabled = canPrev) { Text("<") }
        Column(
            modifier = Modifier.weight(1f).clickable { onTitleClick() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (owner.isNotBlank()) {
                Text(
                    text = owner,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (title.isBlank()) "Нет недели" else title + "  ▾",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
        TextButton(onClick = onNext, enabled = canNext) { Text(">") }
        TextButton(onClick = onToday) { Text("Сегодня", fontSize = 12.sp) }
    }
}

@Composable
private fun ImportPreviewDialog(
    cards: List<TeacherCard>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val card = cards.firstOrNull { it.teacher == selected } ?: cards.first()
    val preview = card.lessons.sortedWith(compareBy({ it.date }, { it.pairNo }))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт расписания") },
        text = {
            Column {
                Text(
                    "Выберите из списка (${cards.size}):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.fillMaxWidth().height(120.dp)) {
                    items(cards) { c ->
                        val isSelected = c.teacher == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(c.teacher) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isSelected) "● " else "○ ")
                            Text(
                                text = c.teacher,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${c.lessons.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Найдено занятий: ${preview.size}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
                    items(preview) { lesson ->
                        Text(
                            text = shortDate(lesson.date) + " · " + lesson.pairNo + " пара · " + lesson.subject,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        val details = buildList {
                            if (lesson.group.isNotBlank()) add(lesson.group)
                            if (lesson.room.isNotBlank()) add(lesson.room)
                        }
                        if (details.isNotEmpty()) {
                            Text(
                                text = details.joinToString(" · "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun DayStrip(days: List<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { d ->
            val isSelected = d == selected
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekdayShort[d.dayOfWeek] ?: "",
                    fontSize = 12.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = d.format(dayFormat),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, bell: BellSlot?, now: LocalTime, onToggleDone: () -> Unit, onClick: () -> Unit) {
    val isNow = bell != null &&
        lesson.date == LocalDate.now().toString() &&
        !now.isBefore(bell.start) && now.isBefore(bell.end)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(subjectColor(lesson.subject), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lesson.pairNo.toString(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.subject, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val details = buildList {
                    if (lesson.group.isNotBlank()) add(lesson.group)
                    if (lesson.room.isNotBlank()) add(lesson.room)
                    if (lesson.time.isNotBlank()) add(lesson.time)
                    else if (bell != null) add(Bells.format(bell))
                    if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isNow) {
                    Text(
                        text = "Идёт сейчас · до " + bell!!.end.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (lesson.note.isNotBlank()) {
                    Text(
                        text = lesson.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun EmptyDay(hasWeek: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasWeek) "Пока пусто" else "Расписания пока нет",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasWeek) {
                    "Нажмите «+», чтобы добавить занятие,\nили «Импорт» — чтобы загрузить PDF с расписанием."
                } else {
                    "Нажмите «Импорт» и выберите PDF\nс расписанием преподавателей или студентов."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun daysOf(week: Week): List<LocalDate> {
    val result = ArrayList<LocalDate>()
    var d = LocalDate.parse(week.startDate)
    val end = LocalDate.parse(week.endDate)
    while (d <= end) {
        result.add(d)
        d = d.plusDays(1)
    }
    return result
}

private fun dayLabel(isoDate: String): String {
    return try {
        val d = LocalDate.parse(isoDate)
        (weekdayShort[d.dayOfWeek] ?: "") + " " + d.format(dayFormat)
    } catch (e: Exception) {
        isoDate
    }
}

private fun shortDate(isoDate: String): String {
    return try {
        LocalDate.parse(isoDate).format(dayFormat)
    } catch (e: Exception) {
        isoDate
    }
}