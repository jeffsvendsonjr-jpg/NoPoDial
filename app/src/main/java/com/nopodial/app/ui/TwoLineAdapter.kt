package com.nopodial.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/**
 * ListView adapter over the platform two-line row. Every Phase 0 list is
 * "title + detail line", so one adapter covers recents, contacts, notes,
 * and follow-ups.
 */
class TwoLineAdapter<T>(
    context: Context,
    items: List<T>,
    private val bind: (T) -> Pair<CharSequence, CharSequence>
) : ArrayAdapter<T>(context, android.R.layout.simple_list_item_2, android.R.id.text1, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val (title, detail) = bind(getItem(position)!!)
        view.findViewById<TextView>(android.R.id.text1).text = title
        view.findViewById<TextView>(android.R.id.text2).text = detail
        return view
    }
}
