package com.nopodial.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nopodial.app.R

/**
 * Read-only view over the system contacts — NoPoDial deliberately has no
 * contact store of its own. Tapping a contact opens their journal.
 */
class ContactsFragment : Fragment() {

    private data class Entry(val name: String, val number: String)

    private lateinit var list: ListView
    private lateinit var empty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_plain_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.list_items)
        empty = view.findViewById(R.id.text_empty)
        list.emptyView = empty

        list.setOnItemClickListener { _, _, position, _ ->
            val entry = list.adapter.getItem(position) as Entry
            startActivity(
                ContactDetailActivity.intent(requireContext(), entry.number, entry.name)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            empty.setText(R.string.empty_contacts_no_permission)
            render(emptyList())
            return
        }
        empty.setText(R.string.empty_contacts)

        val entries = mutableListOf<Entry>()
        val seenContacts = mutableSetOf<Long>()
        requireContext().contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER),
            null, null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getLong(0)
                if (!seenContacts.add(contactId)) continue // first number per contact
                val name = c.getString(1) ?: continue
                val number = c.getString(2) ?: continue
                entries += Entry(name, number)
            }
        }
        render(entries)
    }

    private fun render(entries: List<Entry>) {
        list.adapter = TwoLineAdapter(requireContext(), entries) { it.name to it.number }
    }
}
