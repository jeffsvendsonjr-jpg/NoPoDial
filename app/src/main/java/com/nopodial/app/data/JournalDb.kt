package com.nopodial.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.telephony.PhoneNumberUtils

/**
 * Local store for the call journal: notes attached to calls/contacts and
 * callback reminders. Plain SQLite on purpose — no ORM, no processors, and
 * the whole Phase 0 schema is two tables.
 *
 * Notes and reminders key on the normalized phone number (not the contact),
 * so they survive contact renames and work for numbers not in contacts.
 */
class JournalDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "journal.db", null, 1) {

    data class Note(
        val id: Long,
        val number: String,
        val name: String?,
        val callTs: Long,     // timestamp of the call this note is about, 0 = standalone
        val createdTs: Long,
        val body: String
    )

    data class Reminder(
        val id: Long,
        val number: String,
        val name: String?,
        val dueTs: Long,
        val message: String,
        val done: Boolean
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE notes(
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL,
                name TEXT,
                call_ts INTEGER NOT NULL DEFAULT 0,
                created_ts INTEGER NOT NULL,
                body TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_notes_number ON notes(number)")
        db.execSQL(
            """CREATE TABLE reminders(
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL,
                name TEXT,
                due_ts INTEGER NOT NULL,
                message TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                created_ts INTEGER NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---- notes ----

    fun insertNote(number: String, name: String?, callTs: Long, body: String): Long =
        writableDatabase.insert("notes", null, ContentValues().apply {
            put("number", normalize(number))
            put("name", name)
            put("call_ts", callTs)
            put("created_ts", System.currentTimeMillis())
            put("body", body)
        })

    fun notesFor(number: String): List<Note> =
        readableDatabase.query(
            "notes", null, "number = ?", arrayOf(normalize(number)),
            null, null, "created_ts DESC"
        ).use { readNotes(it) }

    /** Latest note body per number — used for snippets in the recents list. */
    fun latestNotePerNumber(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        readableDatabase.rawQuery(
            "SELECT number, body FROM notes ORDER BY created_ts ASC", null
        ).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    fun searchNotes(query: String): List<Note> =
        readableDatabase.query(
            "notes", null,
            "body LIKE ? OR name LIKE ? OR number LIKE ?",
            Array(3) { "%$query%" },
            null, null, "created_ts DESC", "100"
        ).use { readNotes(it) }

    private fun readNotes(c: Cursor): List<Note> {
        val out = mutableListOf<Note>()
        while (c.moveToNext()) {
            out += Note(
                id = c.getLong(c.getColumnIndexOrThrow("_id")),
                number = c.getString(c.getColumnIndexOrThrow("number")),
                name = c.getString(c.getColumnIndexOrThrow("name")),
                callTs = c.getLong(c.getColumnIndexOrThrow("call_ts")),
                createdTs = c.getLong(c.getColumnIndexOrThrow("created_ts")),
                body = c.getString(c.getColumnIndexOrThrow("body"))
            )
        }
        return out
    }

    // ---- reminders ----

    fun insertReminder(number: String, name: String?, dueTs: Long, message: String): Long =
        writableDatabase.insert("reminders", null, ContentValues().apply {
            put("number", normalize(number))
            put("name", name)
            put("due_ts", dueTs)
            put("message", message)
            put("done", 0)
            put("created_ts", System.currentTimeMillis())
        })

    fun reminder(id: Long): Reminder? =
        readableDatabase.query(
            "reminders", null, "_id = ?", arrayOf(id.toString()), null, null, null
        ).use { c -> if (c.moveToFirst()) readReminder(c) else null }

    fun pendingReminders(): List<Reminder> =
        readableDatabase.query(
            "reminders", null, "done = 0", null, null, null, "due_ts ASC"
        ).use { c ->
            val out = mutableListOf<Reminder>()
            while (c.moveToNext()) out += readReminder(c)
            out
        }

    fun markReminderDone(id: Long) {
        writableDatabase.update(
            "reminders", ContentValues().apply { put("done", 1) },
            "_id = ?", arrayOf(id.toString())
        )
    }

    fun updateReminderDue(id: Long, dueTs: Long) {
        writableDatabase.update(
            "reminders", ContentValues().apply { put("due_ts", dueTs) },
            "_id = ?", arrayOf(id.toString())
        )
    }

    fun deleteReminder(id: Long) {
        writableDatabase.delete("reminders", "_id = ?", arrayOf(id.toString()))
    }

    private fun readReminder(c: Cursor): Reminder = Reminder(
        id = c.getLong(c.getColumnIndexOrThrow("_id")),
        number = c.getString(c.getColumnIndexOrThrow("number")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        dueTs = c.getLong(c.getColumnIndexOrThrow("due_ts")),
        message = c.getString(c.getColumnIndexOrThrow("message")),
        done = c.getInt(c.getColumnIndexOrThrow("done")) != 0
    )

    companion object {
        @Volatile private var instance: JournalDb? = null

        fun get(context: Context): JournalDb =
            instance ?: synchronized(this) {
                instance ?: JournalDb(context.applicationContext).also { instance = it }
            }

        fun normalize(raw: String): String {
            val normalized = PhoneNumberUtils.normalizeNumber(raw)
            return if (normalized.isNullOrEmpty()) raw.trim() else normalized
        }
    }
}
