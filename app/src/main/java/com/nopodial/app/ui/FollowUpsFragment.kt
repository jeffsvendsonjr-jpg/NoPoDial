package com.nopodial.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.nopodial.app.R
import com.nopodial.app.data.JournalDb
import com.nopodial.app.journal.Reminders

/** Pending callback reminders, soonest first. */
class FollowUpsFragment : Fragment() {

    private lateinit var list: ListView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_plain_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.list_items)
        val empty = view.findViewById<TextView>(R.id.text_empty)
        empty.setText(R.string.empty_followups)
        list.emptyView = empty

        list.setOnItemClickListener { _, _, position, _ ->
            val reminder = list.adapter.getItem(position) as JournalDb.Reminder
            showActions(reminder)
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val reminders = JournalDb.get(requireContext()).pendingReminders()
        list.adapter = TwoLineAdapter(requireContext(), reminders) { r ->
            val who = r.name?.takeIf { it.isNotBlank() } ?: r.number
            val due = DateUtils.getRelativeTimeSpanString(r.dueTs)
            who to getString(R.string.followup_detail, due, r.message)
        }
    }

    private fun showActions(reminder: JournalDb.Reminder) {
        val who = reminder.name?.takeIf { it.isNotBlank() } ?: reminder.number
        AlertDialog.Builder(requireContext())
            .setTitle(who)
            .setItems(
                arrayOf(
                    getString(R.string.action_call_now),
                    getString(R.string.mark_done),
                    getString(R.string.delete)
                )
            ) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${reminder.number}"))
                    )
                    1 -> {
                        JournalDb.get(requireContext()).markReminderDone(reminder.id)
                        Reminders.cancel(requireContext(), reminder.id)
                        load()
                    }
                    2 -> {
                        JournalDb.get(requireContext()).deleteReminder(reminder.id)
                        Reminders.cancel(requireContext(), reminder.id)
                        load()
                    }
                }
            }
            .show()
    }
}
