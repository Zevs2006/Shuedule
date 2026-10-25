package ru.roman.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.roman.schedule.data.BellSlot
import ru.roman.schedule.data.Bells
import ru.roman.schedule.data.BellsConfig

private val PROFILE_NAMES = listOf("Пн", "Вт–Пт", "Сб")

@Composable
fun BellDialog(
    config: BellsConfig,
    remindersEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (BellsConfig, Boolean) -> Unit
) {
    val count = 6
    var profile by remember { mutableStateOf(0) }
    var reminders by remember { mutableStateOf(remindersEnabled) }
    var error by remember { mutableStateOf<String?>(null) }

    val starts = listOf(
        remember { mutableStateListOf<String>() },
        remember { mutableStateListOf<String>() },
        remember { mutableStateListOf<String>() }
    )
    val ends = listOf(
        remember { mutableStateListOf<String>() },
        remember { mutableStateListOf<String>() },
        remember { mutableStateListOf<String>() }
    )

    if (starts[0].isEmpty()) {
        listOf(config.monday, config.weekdays, config.saturday).forEachIndexed { index, slots ->
            for (i in 0 until count) {
                val slot = slots.getOrNull(i)
                starts[index].add(slot?.start?.toString() ?: "")
                ends[index].add(slot?.end?.toString() ?: "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Расписание звонков") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PROFILE_NAMES.forEachIndexed { index, name ->
                        val selected = profile == index
                        Text(
                            text = name,
                            fontSize = 13.sp,
                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                            else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { profile = index }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Text(
                    "Время в формате 08:30. Пустое поле — пара без времени.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                for (index in 0 until count) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${index + 1} пара", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = starts[profile].getOrElse(index) { "" },
                            onValueChange = { starts[profile][index] = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ends[profile].getOrElse(index) { "" },
                            onValueChange = { ends[profile][index] = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Напоминать за 15 минут", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = reminders, onCheckedChange = { reminders = it })
                }

                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val profiles = ArrayList<List<BellSlot?>>()
                for (p in 0 until 3) {
                    val slots = ArrayList<BellSlot?>()
                    for (index in 0 until count) {
                        val startRaw = starts[p].getOrElse(index) { "" }.trim()
                        val endRaw = ends[p].getOrElse(index) { "" }.trim()
                        if (startRaw.isEmpty() && endRaw.isEmpty()) {
                            slots.add(null)
                            continue
                        }
                        val start = Bells.parseTime(startRaw)
                        val end = Bells.parseTime(endRaw)
                        if (start == null || end == null) {
                            error = "«${PROFILE_NAMES[p]}», ${index + 1} пара: время в формате 08:30"
                            return@TextButton
                        }
                        slots.add(BellSlot(start, end))
                    }
                    profiles.add(slots)
                }
                onSave(BellsConfig(profiles[0], profiles[1], profiles[2]), reminders)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}