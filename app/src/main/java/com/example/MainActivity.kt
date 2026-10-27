package com.example

import android.os.Bundle
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.byokgpt.security.AppLockManager
import com.example.byokgpt.security.SecureKeyStorage
import com.example.byokgpt.ui.ByokGptApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = SecureKeyStorage(this)
        AppLockManager.init(this, storage)
        setContent {
            ByokGptApp()
        }
    }

    override fun onStart() {
        super.onStart()
        AppLockManager.onAppForegrounded()
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        try {
            super.onActionModeFinished(mode)
        } catch (_: Exception) {}
    }
}
