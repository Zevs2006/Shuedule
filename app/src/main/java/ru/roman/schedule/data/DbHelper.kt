package ru.roman.schedule.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE weeks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "start_date TEXT NOT NULL, " +
                "end_date TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "owner TEXT NOT NULL DEFAULT '', " +
                "kind TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE lessons (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "week_id INTEGER NOT NULL, " +
                "date TEXT NOT NULL, " +
                "pair_no INTEGER NOT NULL, " +
                "subject TEXT NOT NULL, " +
                "group_name TEXT NOT NULL DEFAULT '', " +
                "room TEXT NOT NULL DEFAULT '', " +
                "time TEXT NOT NULL DEFAULT '', " +
                "teacher TEXT NOT NULL DEFAULT '', " +
                "note TEXT NOT NULL DEFAULT '', " +
                "done INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_lessons_week_date ON lessons(week_id, date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE weeks ADD COLUMN owner TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE weeks ADD COLUMN kind TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN teacher TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE lessons ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
        }
    }

    companion object {
        private const val NAME = "schedule.db"
        private const val VERSION = 4
    }
}