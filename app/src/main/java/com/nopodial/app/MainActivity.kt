package com.nopodial.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var messageInput: EditText
    private lateinit var thresholdInput: EditText
    private lateinit var permissionStatus: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enabledSwitch = findViewById(R.id.switch_enabled)
        messageInput = findViewById(R.id.input_message)
        thresholdInput = findViewById(R.id.input_threshold)
        permissionStatus = findViewById(R.id.text_permission_status)

        enabledSwitch.isChecked = Prefs.isEnabled(this)
        messageInput.setText(Prefs.message(this))
        thresholdInput.setText(Prefs.maxDurationSec(this).toString())

        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !allPermissionsGranted()) {
                enabledSwitch.isChecked = false
                requestNeededPermissions()
                Toast.makeText(this, R.string.grant_permissions_first, Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            Prefs.get(this).edit().putBoolean(Prefs.KEY_ENABLED, checked).apply()
        }

        findViewById<Button>(R.id.button_save).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.button_permissions).setOnClickListener {
            requestNeededPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun saveSettings() {
        val message = messageInput.text.toString().trim()
        val threshold = thresholdInput.text.toString().toIntOrNull()

        if (message.isEmpty()) {
            messageInput.error = getString(R.string.error_empty_message)
            return
        }
        if (threshold == null || threshold !in 1..120) {
            thresholdInput.error = getString(R.string.error_bad_threshold)
            return
        }

        Prefs.get(this).edit()
            .putString(Prefs.KEY_MESSAGE, message)
            .putInt(Prefs.KEY_MAX_DURATION_SEC, threshold)
            .apply()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun neededPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms
    }

    private fun allPermissionsGranted(): Boolean =
        neededPermissions()
            .filter { it != Manifest.permission.POST_NOTIFICATIONS } // optional
            .all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }

    private fun requestNeededPermissions() {
        permissionLauncher.launch(neededPermissions().toTypedArray())
    }

    private fun refreshPermissionStatus() {
        permissionStatus.setText(
            if (allPermissionsGranted()) R.string.permissions_ok
            else R.string.permissions_missing
        )
        if (!allPermissionsGranted() && enabledSwitch.isChecked) {
            enabledSwitch.isChecked = false
            Prefs.get(this).edit().putBoolean(Prefs.KEY_ENABLED, false).apply()
        }
    }
}
