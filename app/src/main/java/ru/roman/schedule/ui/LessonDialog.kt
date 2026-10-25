package ru.roman.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.roman.schedule.data.Lesson

@Composable
fun LessonDialog(
    initial: Lesson,
    dayLabel: String,
    onDismiss: () -> Unit,
    onSave: (Lesson) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var pair by remember { mutableStateOf(if (initial.pairNo > 0) initial.pairNo.toString() else "") }
    var subject by remember { mutableStateOf(initial.subject) }
    var group by remember { mutableStateOf(initial.group) }
    var room by remember { mutableStateOf(initial.room) }
    var time by remember { mutableStateOf(initial.time) }
    var teacher by remember { mutableStateOf(initial.teacher) }
    var note by remember { mutableStateOf(initial.note) }
    var done by remember { mutableStateOf(initial.done) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Новое занятие" else "Занятие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dayLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Предмет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pair,
                        onValueChange = { value -> pair = value.filter { it.isDigit() } },
                        label = { Text("Пара") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Время") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Группа") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Преподаватель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Кабинет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка или задание") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Checkbox(checked = done, onCheckedChange = { done = it })
                    Text("Задание выполнено", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        pairNo = pair.toIntOrNull() ?: 0,
                        subject = subject.trim(),
                        group = group.trim(),
                        room = room.trim(),
                        time = time.trim(),
                        teacher = teacher.trim(),
                        note = note.trim(),
                        done = done
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить") }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}