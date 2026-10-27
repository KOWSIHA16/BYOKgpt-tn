package com.example.byokgpt.model

import com.example.R

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val defaultEndpoint: String,
    val iconResId: Int,
    val apiKeyPlaceholder: String,
    val docsUrl: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-3.8-flash",
        defaultEndpoint = "https://generativelanguage.googleapis.com",
        iconResId = R.drawable.gemini,
        apiKeyPlaceholder = "AQ... or AIzaSy...",
        docsUrl = "https://aistudio.google.com/app/apikey"
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-sonnet-20241022",
        defaultEndpoint = "https://api.anthropic.com/v1",
        iconResId = R.drawable.anthropic,
        apiKeyPlaceholder = "sk-ant-api03-...",
        docsUrl = "https://console.anthropic.com/settings/keys"
    ),
    OPENAI(
        displayName = "OpenAI ChatGPT",
        defaultModel = "gpt-4o-mini",
        defaultEndpoint = "https://api.openai.com/v1",
        iconResId = R.drawable.openai,
        apiKeyPlaceholder = "sk-proj-...",
        docsUrl = "https://platform.openai.com/api-keys"
    );

    companion object {
        fun fromString(value: String): AiProvider = entries.find {
            it.name.equals(value, ignoreCase = true)
        } ?: GEMINI
    }
}

data class AiModel(
    val id: String,
    val displayName: String,
    val provider: AiProvider,
    val description: String = ""
)

object ModelCatalog {
    val models: List<AiModel> = listOf(
        // Google Gemini (Exclusive 2026 Gemini model)
        AiModel("gemini-3.8-flash", "Gemini 3.8 Flash", AiProvider.GEMINI, "Frontier high-speed thinking multimodal model"),

        // Anthropic Claude (Second)
        AiModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", AiProvider.CLAUDE, "Most intelligent Claude model"),
        AiModel("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", AiProvider.CLAUDE, "Fastest, compact Claude model"),
        AiModel("claude-3-opus-20240229", "Claude 3 Opus", AiProvider.CLAUDE, "Complex reasoning and analysis"),

        // OpenAI ChatGPT (Third)
        AiModel("gpt-4o-mini", "GPT-4o Mini", AiProvider.OPENAI, "Affordable, fast flagship mini"),
        AiModel("gpt-4o", "GPT-4o", AiProvider.OPENAI, "Flagship high-intelligence model"),
        AiModel("gpt-4-turbo", "GPT-4 Turbo", AiProvider.OPENAI, "High capability reasoning model")
    )

    fun getModelsFor(provider: AiProvider): List<AiModel> =
        models.filter { it.provider == provider }
}

enum class MessageRole {
    USER, ASSISTANT, SYSTEM;

    companion object {
        fun fromString(value: String): MessageRole = entries.find {
            it.name.equals(value, ignoreCase = true)
        } ?: USER
    }
}

enum class MessageStatus {
    SENT, STREAMING, SUCCESS, ERROR, CANCELLED;

    companion object {
        fun fromString(value: String): MessageStatus = entries.find {
            it.name.equals(value, ignoreCase = true)
        } ?: SUCCESS
    }
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(value: String): ThemeMode = entries.find {
            it.name.equals(value, ignoreCase = true)
        } ?: SYSTEM
    }
}

data class ProviderConfig(
    val provider: AiProvider,
    val apiKey: String = "",
    val customEndpoint: String = "",
    val selectedModel: String = provider.defaultModel,
    val isEnabled: Boolean = true
)

enum class ResponseLanguage(val displayName: String, val promptInstruction: String) {
    AUTOMATIC("Automatic", "Respond naturally in the same language as the user's message."),
    ENGLISH("English", "Respond strictly in English."),
    TAMIL("Tamil (தமிழ்)", "Respond strictly in Tamil script (தமிழ்)."),
    TANGLISH("Tanglish (Tamil in English Script)", "Respond in conversational Tamil written phonetically using the English/Latin alphabet (Tanglish). E.g., 'Vanakkam buddy, eppadi irukinga?' Do not use Tamil script."),
    MALAYALAM("Malayalam (മലയാളം)", "Respond strictly in Malayalam script (മലയാളം)."),
    HINDI("Hindi (हिन्दी)", "Respond strictly in Hindi (Devanagari script हिन्दी)."),
    KANNADA("Kannada (ಕನ್ನಡ)", "Respond strictly in Kannada script (ಕನ್ನಡ)."),
    TELUGU("Telugu (తెలుగు)", "Respond strictly in Telugu script (తెలుగు)."),
    BENGALI("Bengali (বাংলা)", "Respond strictly in Bengali script (বাংলা)."),
    MARATHI("Marathi (मराठी)", "Respond strictly in Marathi (Devanagari script मराठी).");

    companion object {
        fun fromString(value: String): ResponseLanguage = entries.find {
            it.name.equals(value, ignoreCase = true)
        } ?: AUTOMATIC
    }
}

enum class ProviderConnectionStatus(val label: String) {
    NOT_CONFIGURED("Not configured"),
    CHECKING("Checking credentials..."),
    CONNECTED("Connected and verified"),
    FAILED("Connection failed"),
    UNKNOWN("Status unknown")
}

data class ProviderStatusInfo(
    val status: ProviderConnectionStatus = ProviderConnectionStatus.NOT_CONFIGURED,
    val lastVerifiedAtMs: Long = 0L,
    val message: String = ""
)

data class AppSettings(
    val customInstructions: String = "",
    val userName: String = "User",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val responseLanguage: ResponseLanguage = ResponseLanguage.AUTOMATIC,
    val fastAnswers: Boolean = true,
    val webSearch: Boolean = false,
    val visualAnswers: Boolean = true,
    val appLockEnabled: Boolean = false,
    val pinCode: String = "",
    val memoryEnabled: Boolean = true
)

sealed class StreamChunk {
    data class FirstBytes(val timestampMs: Long = System.currentTimeMillis()) : StreamChunk()
    data class Delta(val text: String) : StreamChunk()
    data class Done(val fullText: String) : StreamChunk()
    data class Error(
        val message: String,
        val isRateLimit: Boolean = false,
        val isAuthError: Boolean = false
    ) : StreamChunk()
}

/**
 * Latency metrics per chat turn:
 * T0: Send button tapped by user
 * T1: User message appended and visible in UI
 * T2: Network request dispatched
 * T3: First response bytes (HTTP headers) arrive
 * T4: First AI text token parsed and visible
 * T5: Full stream completed
 */
data class TurnTimings(
    val turnId: String,
    val provider: String,
    val model: String,
    val t0SendTappedMs: Long = System.currentTimeMillis(),
    var t1UserVisibleMs: Long = 0L,
    var t2NetworkStartMs: Long = 0L,
    var t3FirstBytesMs: Long = 0L,
    var t4FirstTextMs: Long = 0L,
    var t5CompletedMs: Long = 0L,
    var errorMessage: String? = null
) {
    val t0ToT1Ms: Long get() = if (t1UserVisibleMs > 0) (t1UserVisibleMs - t0SendTappedMs).coerceAtLeast(0L) else 0L
    val t1ToT2Ms: Long get() = if (t2NetworkStartMs > 0 && t1UserVisibleMs > 0) (t2NetworkStartMs - t1UserVisibleMs).coerceAtLeast(0L) else 0L
    val t2ToT3TtfbMs: Long get() = if (t3FirstBytesMs > 0 && t2NetworkStartMs > 0) (t3FirstBytesMs - t2NetworkStartMs).coerceAtLeast(0L) else 0L
    val t3ToT4Ms: Long get() = if (t4FirstTextMs > 0 && t3FirstBytesMs > 0) (t4FirstTextMs - t3FirstBytesMs).coerceAtLeast(0L) else 0L
    val t4ToT5Ms: Long get() = if (t5CompletedMs > 0 && t4FirstTextMs > 0) (t5CompletedMs - t4FirstTextMs).coerceAtLeast(0L) else 0L
    val totalLatencyMs: Long get() = if (t5CompletedMs > 0) (t5CompletedMs - t0SendTappedMs).coerceAtLeast(0L) else 0L

    fun toLogString(): String =
        "[BYOKGPT Latency] turn=$turnId provider=$provider model=$model | " +
        "T0->T1 (User Visible): ${t0ToT1Ms}ms | " +
        "T1->T2 (Net Start): ${t1ToT2Ms}ms | " +
        "T2->T3 (TTFB): ${t2ToT3TtfbMs}ms | " +
        "T3->T4 (First Text): ${t3ToT4Ms}ms | " +
        "T4->T5 (Stream): ${t4ToT5Ms}ms | " +
        "Total: ${totalLatencyMs}ms" +
        (if (errorMessage != null) " | Error: $errorMessage" else "")
}

enum class AppMode {
    AI,
    BROWSER
}
