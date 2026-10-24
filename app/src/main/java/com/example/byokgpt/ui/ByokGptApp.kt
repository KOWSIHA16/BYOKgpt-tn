package com.example.byokgpt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.byokgpt.model.ThemeMode
import com.example.byokgpt.ui.chat.ChatScreen
import com.example.byokgpt.ui.chat.ChatViewModel
import com.example.byokgpt.ui.settings.SettingsScreen
import com.example.ui.theme.ByokGptTheme

const val ROUTE_CHAT = "chat"
const val ROUTE_SETTINGS = "settings"

@Composable
fun ByokGptApp(
    chatViewModel: ChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val settings by chatViewModel.settingsManager.settings.collectAsState()

    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ByokGptTheme(darkTheme = isDark) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_CHAT
        ) {
            composable(ROUTE_CHAT) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateToSettings = {
                        navController.navigate(ROUTE_SETTINGS)
                    }
                )
            }

            composable(ROUTE_SETTINGS) {
                BackHandler {
                    navController.popBackStack()
                }
                SettingsScreen(
                    viewModel = chatViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
