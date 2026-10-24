package com.example.byokgpt.data

import android.content.Context
import android.content.SharedPreferences
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.AppSettings
import com.example.byokgpt.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("byokgpt_app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            customInstructions = prefs.getString("custom_instructions", "") ?: "",
            userName = prefs.getString("user_name", "User") ?: "User",
            themeMode = ThemeMode.fromString(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"),
            responseLanguage = com.example.byokgpt.model.ResponseLanguage.fromString(
                prefs.getString("response_language", com.example.byokgpt.model.ResponseLanguage.AUTOMATIC.name) ?: com.example.byokgpt.model.ResponseLanguage.AUTOMATIC.name
            ),
            fastAnswers = prefs.getBoolean("fast_answers", true),
            webSearch = prefs.getBoolean("web_search", false),
            visualAnswers = prefs.getBoolean("visual_answers", true),
            appLockEnabled = prefs.getBoolean("app_lock_enabled", false),
            pinCode = prefs.getString("pin_code", "") ?: ""
        )
    }

    fun updateResponseLanguage(language: com.example.byokgpt.model.ResponseLanguage) {
        prefs.edit().putString("response_language", language.name).apply()
        _settings.value = _settings.value.copy(responseLanguage = language)
    }

    fun updateCustomInstructions(instructions: String) {
        prefs.edit().putString("custom_instructions", instructions).apply()
        _settings.value = _settings.value.copy(customInstructions = instructions)
    }

    fun updateUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
        _settings.value = _settings.value.copy(userName = name)
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        prefs.edit().putString("theme_mode", themeMode.name).apply()
        _settings.value = _settings.value.copy(themeMode = themeMode)
    }

    fun updateFastAnswers(enabled: Boolean) {
        prefs.edit().putBoolean("fast_answers", enabled).apply()
        _settings.value = _settings.value.copy(fastAnswers = enabled)
    }

    fun updateWebSearch(enabled: Boolean) {
        prefs.edit().putBoolean("web_search", enabled).apply()
        _settings.value = _settings.value.copy(webSearch = enabled)
    }

    fun updateVisualAnswers(enabled: Boolean) {
        prefs.edit().putBoolean("visual_answers", enabled).apply()
        _settings.value = _settings.value.copy(visualAnswers = enabled)
    }

    fun updateAppLock(enabled: Boolean, pin: String = "") {
        prefs.edit()
            .putBoolean("app_lock_enabled", enabled)
            .putString("pin_code", pin)
            .apply()
        _settings.value = _settings.value.copy(appLockEnabled = enabled, pinCode = pin)
    }

    fun getMemorySummary(contextList: List<String>): String {
        if (contextList.isEmpty()) {
            return "No stored conversational memories yet. Memories are automatically collected across chats."
        }
        return contextList.joinToString("\n• ", prefix = "• ")
    }
}
