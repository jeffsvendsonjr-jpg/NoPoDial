package com.nopodial.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nopodial.app.R
import com.nopodial.app.data.JournalDb
import com.nopodial.app.journal.NoteEditorActivity
import java.util.Date

/**
 * One person's journal: their notes newest-first, plus Call and Add-note.
 * This is the screen the whole product exists for — "what did we say last
 * time?" answered before the next call.
 */
class ContactDetailActivity : AppCompatActivity() {

    private lateinit var number: String
    private var contactName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_detail)

        number = intent.getStringExtra(EXTRA_NUMBER) ?: ""
        contactName = intent.getStringExtra(EXTRA_NAME)

        val who = contactName?.takeIf { it.isNotBlank() } ?: number
        title = who
        findViewById<TextView>(R.id.text_contact_number).text = number

        findViewById<Button>(R.id.button_call_contact).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
        findViewById<Button>(R.id.button_add_note).setOnClickListener {
            startActivity(NoteEditorActivity.intent(this, number, contactName, 0L))
        }
    }

    override fun onResume() {
        super.onResume()
        val notes = JournalDb.get(this).notesFor(number)
        val list = findViewById<ListView>(R.id.list_notes)
        val empty = findViewById<TextView>(R.id.text_empty)
        empty.setText(R.string.empty_notes)
        list.emptyView = empty

        val dateFmt = DateFormat.getMediumDateFormat(this)
        val timeFmt = DateFormat.getTimeFormat(this)
        list.adapter = TwoLineAdapter(this, notes) { note ->
            val ts = Date(note.createdTs)
            note.body to "${dateFmt.format(ts)} ${timeFmt.format(ts)}"
        }
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"

        fun intent(context: Context, number: String, name: String?): Intent =
            Intent(context, ContactDetailActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NAME, name)
    }
}
