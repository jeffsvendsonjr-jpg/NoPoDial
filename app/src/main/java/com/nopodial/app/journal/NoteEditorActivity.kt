package com.nopodial.app.journal

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nopodial.app.R
import com.nopodial.app.data.JournalDb
import java.util.Calendar
import java.util.Date

/**
 * Capture screen for one call: free-text note plus optional callback
 * reminder. Reached from the post-call notification, a recents row, or a
 * contact's timeline. Friction budget: note saved in two taps.
 */
class NoteEditorActivity : AppCompatActivity() {

    private lateinit var number: String
    private var contactName: String? = null
    private var callTs: Long = 0L

    private lateinit var bodyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        number = intent.getStringExtra(EXTRA_NUMBER) ?: ""
        contactName = intent.getStringExtra(EXTRA_NAME)
        callTs = intent.getLongExtra(EXTRA_CALL_TS, 0L)

        // Coming from the prompt notification: clear it, the tap consumed it.
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notifId != -1) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notifId)
        }

        val who = contactName?.takeIf { it.isNotBlank() } ?: number
        title = getString(R.string.note_editor_title)
        findViewById<TextView>(R.id.text_note_header).text =
            if (callTs > 0L) {
                getString(
                    R.string.note_header_call, who,
                    DateFormat.getTimeFormat(this).format(Date(callTs))
                )
            } else {
                getString(R.string.note_header_plain, who)
            }

        bodyInput = findViewById(R.id.input_note_body)

        findViewById<Button>(R.id.button_save_note).setOnClickListener { saveNote() }
        findViewById<Button>(R.id.button_remind_1h).setOnClickListener {
            createReminder(System.currentTimeMillis() + 60 * 60 * 1000L)
        }
        findViewById<Button>(R.id.button_remind_tomorrow).setOnClickListener {
            createReminder(tomorrowAtNine())
        }
        findViewById<Button>(R.id.button_remind_pick).setOnClickListener { pickReminderTime() }
    }

    private fun saveNote() {
        val body = bodyInput.text.toString().trim()
        if (body.isEmpty()) {
            bodyInput.error = getString(R.string.error_empty_note)
            return
        }
        JournalDb.get(this).insertNote(number, contactName, callTs, body)
        Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun createReminder(dueTs: Long) {
        val body = bodyInput.text.toString().trim()
        val message = body.ifEmpty { getString(R.string.default_reminder_message) }
        Reminders.create(this, number, contactName, dueTs, message)
        Toast.makeText(
            this,
            getString(
                R.string.reminder_set,
                DateFormat.getMediumDateFormat(this).format(Date(dueTs)) + " " +
                    DateFormat.getTimeFormat(this).format(Date(dueTs))
            ),
            Toast.LENGTH_LONG
        ).show()
        // A reminder usually pairs with whatever was typed — keep it as a note too.
        if (body.isNotEmpty()) {
            JournalDb.get(this).insertNote(number, contactName, callTs, body)
        }
        finish()
    }

    private fun tomorrowAtNine(): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun pickReminderTime() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val due = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        createReminder(due)
                    },
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(this)
                ).show()
            },
            now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"
        const val EXTRA_CALL_TS = "call_ts"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun intent(context: Context, number: String, name: String?, callTs: Long): Intent =
            Intent(context, NoteEditorActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_CALL_TS, callTs)
    }
}
