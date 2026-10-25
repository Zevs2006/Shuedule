package ru.roman.schedule

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ScheduleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}