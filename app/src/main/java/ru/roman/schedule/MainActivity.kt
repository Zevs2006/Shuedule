package ru.roman.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import ru.roman.schedule.ui.ScheduleApp
import ru.roman.schedule.ui.ThemeMode
import ru.roman.schedule.ui.ThemeSettings
import ru.roman.schedule.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeSettings.load(this)
        setContent {
            val mode = ThemeSettings.mode
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ScheduleTheme(darkTheme = dark) {
                ScheduleApp()
            }
        }
    }
}