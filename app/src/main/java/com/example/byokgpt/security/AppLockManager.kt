package com.example.byokgpt.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLockManager {

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var keyStorage: SecureKeyStorage? = null
    private var isInitialized = false

    fun init(context: Context, secureKeyStorage: SecureKeyStorage) {
        keyStorage = secureKeyStorage
        if (!isInitialized) {
            isInitialized = true
            // If lock is enabled and PIN is configured, start locked on cold launch
            if (secureKeyStorage.isAppLockEnabled() && secureKeyStorage.hasPin()) {
                _isLocked.value = true
            }
        }
    }

    fun onAppForegrounded() {
        val storage = keyStorage
        if (storage != null && storage.isAppLockEnabled() && storage.hasPin()) {
            _isLocked.value = true
        }
    }

    fun verifyAndUnlock(pin: String): Boolean {
        val storage = keyStorage ?: return false
        val matched = storage.verifyPin(pin)
        if (matched) {
            _isLocked.value = false
        }
        return matched
    }

    fun unlockBiometric() {
        _isLocked.value = false
    }

    fun lock() {
        val storage = keyStorage
        if (storage != null && storage.isAppLockEnabled() && storage.hasPin()) {
            _isLocked.value = true
        }
    }

    fun isBiometricsSupported(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val biometricManager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                biometricManager != null
            } else {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                km?.isKeyguardSecure == true
            }
        } catch (_: Exception) {
            false
        }
    }
}
