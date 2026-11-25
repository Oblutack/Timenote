package com.oblutack.timenote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import com.oblutack.timenote.core.LocalPermissionAsker
import com.oblutack.timenote.core.PermissionAsker

class MainActivity : ComponentActivity() {

    // What to do once the permission question that is open right now has been answered
    private var afterPermissionQuestion: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Yes or no: whoever asked carries on, and explains itself if the answer was no
        val next = afterPermissionQuestion
        afterPermissionQuestion = null
        next?.invoke()
    }

    /**
     * Permissions are asked for where they are needed: the microphone at the first voice memo, notifications at the
     * first session. Android shows its own question once; after a permanent "no" it answers at once without a dialog.
     */
    private val permissionAsker = object : PermissionAsker {
        override fun microphone(then: () -> Unit) = ask(Manifest.permission.RECORD_AUDIO, then)

        override fun notifications(then: () -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask(Manifest.permission.POST_NOTIFICATIONS, then) else then()
        }
    }

    private fun ask(permission: String, then: () -> Unit) {
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            then()
            return
        }
        afterPermissionQuestion = then
        permissionLauncher.launch(permission)
    }

    override fun onStart() {
        super.onStart()
        // Coming to the foreground: sync, unless sync is off or it just happened
        (application as? TimenoteApplication)?.container?.syncManager?.onAppForeground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as TimenoteApplication).container
        setContent {
            CompositionLocalProvider(LocalPermissionAsker provides permissionAsker) {
                App(container)
            }
        }
    }
}
