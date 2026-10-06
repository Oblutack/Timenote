package com.oblutack.timenote

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    // Ask for the microphone (voice memos) and notifications (timer notification)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Features that need a denied permission explain themselves where they are used
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestMissingPermissions()

        val container = (application as TimenoteApplication).container
        setContent {
            App(container)
        }
    }

    /**
     * Asks only for permissions that are still missing, and only when asking can still achieve something:
     * the first time, or after a single denial (when Android still shows the dialog). After that Android
     * blocks the dialog anyway, so asking again at every launch would just be noise.
     */
    private fun requestMissingPermissions() {
        val wanted = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return

        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(KEY_PERMISSIONS_ASKED, false)
        val canStillAsk = missing.any { shouldShowRequestPermissionRationale(it) }

        if (!askedBefore || canStillAsk) {
            prefs.edit().putBoolean(KEY_PERMISSIONS_ASKED, true).apply()
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private companion object {
        const val KEY_PERMISSIONS_ASKED = "permissions_asked"
    }
}
