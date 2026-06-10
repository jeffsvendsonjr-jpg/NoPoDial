package com.nopodial.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.nopodial.app.R
import com.nopodial.app.data.JournalDb
import com.nopodial.app.journal.NoteEditorActivity

/**
 * The journal home: recent calls merged with the latest note per number,
 * filterable by name, number, or note text. Tapping a call opens the note
 * editor for it.
 */
class RecentsFragment : Fragment() {

    private data class Row(
        val number: String,
        val name: String?,
        val dateMs: Long,
        val type: Int,
        val noteSnippet: String?
    )

    private var allRows: List<Row> = emptyList()
    private lateinit var list: ListView
    private lateinit var empty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_recents, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.list_recents)
        empty = view.findViewById(R.id.text_empty)
        list.emptyView = empty

        view.findViewById<EditText>(R.id.input_search).doAfterTextChanged { text ->
            render(filtered(text?.toString().orEmpty()))
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val row = list.adapter.getItem(position) as Row
            startActivity(
                NoteEditorActivity.intent(requireContext(), row.number, row.name, row.dateMs)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            allRows = emptyList()
            empty.setText(R.string.empty_recents_no_permission)
            render(allRows)
            return
        }
        empty.setText(R.string.empty_recents)

        val snippets = JournalDb.get(requireContext()).latestNotePerNumber()
        val rows = mutableListOf<Row>()
        requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && rows.size < MAX_ROWS) {
                val number = c.getString(0) ?: continue
                rows += Row(
                    number = number,
                    name = c.getString(1),
                    dateMs = c.getLong(2),
                    type = c.getInt(3),
                    noteSnippet = snippets[JournalDb.normalize(number)]
                )
            }
        }
        allRows = rows
        render(filtered(view?.findViewById<EditText>(R.id.input_search)?.text?.toString().orEmpty()))
    }

    private fun filtered(query: String): List<Row> {
        if (query.isBlank()) return allRows
        val q = query.trim()
        return allRows.filter { row ->
            row.number.contains(q, ignoreCase = true) ||
                row.name?.contains(q, ignoreCase = true) == true ||
                row.noteSnippet?.contains(q, ignoreCase = true) == true
        }
    }

    private fun render(rows: List<Row>) {
        list.adapter = TwoLineAdapter(requireContext(), rows) { row ->
            val title = row.name?.takeIf { it.isNotBlank() } ?: row.number
            val parts = mutableListOf(
                DateUtils.getRelativeTimeSpanString(row.dateMs).toString(),
                typeLabel(row.type)
            )
            row.noteSnippet?.let { parts += "✎ ${it.take(60)}" }
            title to parts.joinToString(" · ")
        }
    }

    private fun typeLabel(type: Int): String = getString(
        when (type) {
            CallLog.Calls.INCOMING_TYPE -> R.string.call_incoming
            CallLog.Calls.OUTGOING_TYPE -> R.string.call_outgoing
            CallLog.Calls.MISSED_TYPE -> R.string.call_missed
            CallLog.Calls.REJECTED_TYPE -> R.string.call_rejected
            else -> R.string.call_other
        }
    )

    companion object {
        private const val MAX_ROWS = 200
    }
}
