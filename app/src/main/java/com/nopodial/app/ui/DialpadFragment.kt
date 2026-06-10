package com.nopodial.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nopodial.app.R

/**
 * Minimal dialpad. Places the call directly when CALL_PHONE is granted,
 * otherwise hands off to the system dialer (ACTION_DIAL) — one extra tap,
 * zero failure modes.
 */
class DialpadFragment : Fragment() {

    private lateinit var display: TextView
    private val digits = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dialpad, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        display = view.findViewById(R.id.text_dial_display)

        val keys = mapOf(
            R.id.btn_1 to '1', R.id.btn_2 to '2', R.id.btn_3 to '3',
            R.id.btn_4 to '4', R.id.btn_5 to '5', R.id.btn_6 to '6',
            R.id.btn_7 to '7', R.id.btn_8 to '8', R.id.btn_9 to '9',
            R.id.btn_star to '*', R.id.btn_0 to '0', R.id.btn_hash to '#'
        )
        for ((id, char) in keys) {
            view.findViewById<Button>(id).setOnClickListener {
                digits.append(char)
                display.text = digits
            }
        }
        // Long-press 0 for the + prefix, like every dialer.
        view.findViewById<Button>(R.id.btn_0).setOnLongClickListener {
            digits.append('+')
            display.text = digits
            true
        }

        view.findViewById<Button>(R.id.button_backspace).apply {
            setOnClickListener {
                if (digits.isNotEmpty()) {
                    digits.deleteCharAt(digits.length - 1)
                    display.text = digits
                }
            }
            setOnLongClickListener {
                digits.clear()
                display.text = ""
                true
            }
        }

        view.findViewById<Button>(R.id.button_call).setOnClickListener { placeCall() }
    }

    private fun placeCall() {
        val number = digits.toString()
        if (number.isBlank()) return
        val uri = Uri.parse("tel:${Uri.encode(number)}")
        val canCallDirectly = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        startActivity(
            Intent(if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
        )
    }
}
