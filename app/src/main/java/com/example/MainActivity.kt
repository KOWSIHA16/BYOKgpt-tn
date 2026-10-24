package com.example

import android.os.Bundle
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.byokgpt.ui.ByokGptApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ByokGptApp()
        }
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        try {
            super.onActionModeFinished(mode)
        } catch (_: Exception) {}
    }
}
