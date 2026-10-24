package com.example.byokgpt.security

object LogSanitizer {
    private val KEY_PARAM_REGEX = Regex("([?&]key=)[^&]+")
    private val AUTH_HEADER_REGEX = Regex("(?i)(authorization:\\s*bearer\\s+)[^\\s,\"]+", RegexOption.IGNORE_CASE)
    private val API_KEY_HEADER_REGEX = Regex("(?i)(x-api-key:\\s*)[^\\s,\"]+", RegexOption.IGNORE_CASE)
    private val GOOG_KEY_HEADER_REGEX = Regex("(?i)(x-goog-api-key:\\s*)[^\\s,\"]+", RegexOption.IGNORE_CASE)

    fun sanitizeUrl(url: String): String {
        return url.replace(KEY_PARAM_REGEX, "$1[REDACTED]")
    }

    fun sanitizeHeader(name: String, value: String): String {
        if (name.equals("authorization", ignoreCase = true) ||
            name.equals("x-api-key", ignoreCase = true) ||
            name.equals("x-goog-api-key", ignoreCase = true) ||
            name.contains("key", ignoreCase = true)
        ) {
            return "[REDACTED]"
        }
        return value
    }

    fun sanitizeLog(message: String): String {
        return message
            .replace(KEY_PARAM_REGEX, "$1[REDACTED]")
            .replace(AUTH_HEADER_REGEX, "$1[REDACTED]")
            .replace(API_KEY_HEADER_REGEX, "$1[REDACTED]")
            .replace(GOOG_KEY_HEADER_REGEX, "$1[REDACTED]")
    }
}
