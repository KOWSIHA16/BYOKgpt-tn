# BYOKGPT — Complete Implementation & Reliability Plan

Complete overhaul and enhancement of BYOKGPT Android AI client: replacing the app icon with the user-uploaded neon badge, adding real-time provider connection verification, multi-language system prompt injection, Google-powered web search with in-app browser, Claude-inspired multi-attachment carousel, and rich Markdown code blocks while preserving instant message responsiveness.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during interactive clarification:
> - **Response Language Selector**: Accessible both via the **Settings screen** (default selection) and via a **quick-toggle language chip in the Chat header** for instant switching between Automatic, English, Tamil, Tanglish, Malayalam, Hindi, Kannada, Telugu, Bengali, and Marathi.
> - **Google Web Search & Browser**: Integrated as a **Web Search toggle in the composer** that instructs models to cite search-grounded real-time facts, coupled with a lightweight **in-app secure WebView browser sheet** for clicking search results, inspecting citations, or browsing queries directly within the app.
> - **Claude-Inspired Attachments**: Enabled as a **multi-file horizontal carousel** positioned directly above the input composer, featuring image thumbnails, PDF/document type badges, file sizes, and quick-remove buttons, backed by a strict **10MB cumulative limit**.
> - **App Logo & Icon**: The uploaded glossy neon 'B' badge (`file_00000000a4fc8208b423e83efa890d18_e7339cd5.png`) will be converted and installed as the app's adaptive launcher icon, round icon, and displayed in the drawer header and empty state.

---

## 1. Overview & Core Concept

- **What It Does**: BYOKGPT ("Bring Your Own Key GPT") is a native Android AI companion that empowers users to communicate securely with Google Gemini, Anthropic Claude, and OpenAI using their own API keys. It delivers instant local UI responsiveness, live token streaming, rich Markdown formatting with one-tap code copying, multimodal file processing, in-app Google web browsing, and multilingual responses across South Asian and global languages.
- **Target Audience**: Power users, developers, and multilingual individuals wanting a high-performance, private, zero-subscription AI client with complete control over their credentials.
- **Key Value**: Eliminates artificial response delays, provides transparent provider health checks, supports native script & phonetic (Tanglish) communication, and combines multimodal attachments with in-app search browsing.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **App Icon & Branding**: The user sees the high-contrast neon cyan & magenta glowing 'B' badge with BYOKGPT branding on their Android home screen and in the navigation drawer.
2. **Provider Verification Flow**: In Settings → AI Providers & API Keys, each provider displays an informative status badge (`Not configured`, `Checking credentials…`, `Connected & verified`, or `Connection failed`). Tapping "Test Connection" executes a lightweight ping and updates the state in real time without exposing keys.
3. **Chatting & Language Switching**:
   - The user taps the **Language Chip** in the chat header (e.g. `🌐 Tanglish`) to switch target output language on the fly.
   - User types a query or toggles **Google Web Search** (`🔍 Search`).
   - Tapping Send immediately renders the user bubble (< 10ms) and initiates async streaming with a live cursor.
4. **Claude-Style Multi-Attachment Workflow**:
   - Tapping the paperclip button opens the picker to select images or PDF documents.
   - Selected files populate a horizontal scrollable carousel above the input bar with thumbnail previews, filename, size, and individual delete buttons.
   - Files exceeding 10MB trigger an immediate warning snackbar.
5. **In-App Google Web Search & Browser**:
   - If Web Search is enabled, queries request grounded facts with URLs.
   - Tapping a citation or search action slides up an **embedded WebView browser** with an address bar, navigation controls (back/forward/reload), and progress indicator, avoiding disruptive external browser switching.
6. **Rich Response & Code Rendering**:
   - Fenced code blocks render in a styled container with language badge, monospaced font, and a prominent "Copy Code" button.
   - Lists, blockquotes, bold text, and headers render cleanly according to Material Design 3 typography.

### Visual Identity & Theme
- **Color Palette**: Dark OLED theme with deep slate backgrounds (`#0B0F19`), surface containers (`#151C2C`), vibrant electric cyan/blue primary accents (`#2563EB` / `#38BDF8`), and neon purple accents (`#A855F7`).
- **Typography**: Clean Android system sans-serif hierarchy; monospaced font for code snippets (`Roboto Mono` / monospace).
- **Rhythm & Touch Targets**: Generous 48dp minimum interactive component touch targets, rounded pill shapes (`16.dp`–`24.dp`), and animated transitions.

---

## 3. Key Product Decisions & Trade-Offs

- **Multi-File Attachment Carousel**:
  - *Chosen Approach*: Horizontal `LazyRow` preview bar directly above the text input, preserving typed text while adding or removing attachments.
  - *Why*: Directly mirrors the proven UX of Claude and desktop AI clients, giving users visual confirmation and control over payload contents before sending.
- **Response Language via System Prompt Injection**:
  - *Chosen Approach*: Inject explicit, tailored linguistic directives into the system prompt for each provider based on the selected enum (`AUTOMATIC`, `ENGLISH`, `TAMIL`, `TANGLISH`, `MALAYALAM`, `HINDI`, `KANNADA`, `TELUGU`, `BENGALI`, `MARATHI`).
  - *Why*: Works uniformly across all three providers (Gemini, Claude, OpenAI) without requiring proprietary language parameters. For Tanglish, explicit phonetic script instructions ensure authentic colloquial responses.
- **Provider Connection State Management**:
  - *Chosen Approach*: Persist connection test outcomes (`ProviderConnectionStatus`) in `SettingsManager` with timestamp and verification state so users have clear visibility when entering Settings.
  - *Why*: Distinguishes between an untested saved key and a verified live connection, saving users from guessing why requests fail.
- **Integrated WebView Browser**:
  - *Chosen Approach*: Modal bottom sheet / screen containing an Android `WebView` with safe browsing defaults, JavaScript enabled, and navigation controls.
  - *Why*: Keeps the user immersed in their research and chat workflow without throwing them into Chrome or another browser app.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                              BYOKGPT UI                                │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                        Chat Screen Top Bar                       │  │
│  │   [☰ Menu]   [Model Chip]   [🌐 Language Chip]   [⚙️ Settings]    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                         Message Stream                           │  │
│  │   - User Bubble (Multi-attachment badges + User text)            │  │
│  │   - Assistant Bubble:                                            │  │
│  │     * Markdown Formatter (Headers, Lists, Blockquotes)           │  │
│  │     * Syntax Code Block + [📋 Copy Code] Button                  │  │
│  │     * Search Citations & Links (Opens In-App Browser)            │  │
│  │     * Inline Error Card: [⚠️ Details]  [🔄 Retry]  [🔀 Switch]     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                   Composer & Attachment Bar                      │  │
│  │   [Attachment Carousel: [🖼️ doc.png ✕] [📄 brief.pdf ✕] (<10MB)]  │  │
│  │   [📎 Attach]  [🔍 Web Search Toggle]  [Type a message...] [➤]   │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                      In-App Browser Sheet                        │  │
│  │   [← Back] [→ Fwd] [🔄 Reload] [https://www.google.com/...] [✕]  │  │
│  │   [Android WebView Component with progress bar]                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                           ViewModel                              │  │
│  │   - Instant UI Appending (<10ms)                                 │  │
│  │   - AttachmentValidator (Cumulative <= 10MB, Images & PDF)       │  │
│  │   - System Prompt Builder (Language + Search Grounding Directives│  │
│  │   - OkHttp Call Cancellation on Stop                             │  │
│  │   - Provider Health Ping (`testConnection`)                      │  │
│  └──────────────────┬───────────────────────────────┬───────────────┘  │
│                     │                               │                  │
│                     ▼                               ▼                  │
│  ┌─────────────────────────────────────┐  ┌─────────────────────────┐  │
│  │        AI Provider Layer            │  │   Encrypted Key Store   │  │
│  │  - GeminiClient (REST/SSE + Tools)  │  │  - Android Keystore     │  │
│  │  - ClaudeClient (Messages REST/SSE) │  │    AES-256 GCM          │  │
│  │  - OpenAiClient (Chat REST/SSE)     │  │  - Provider Health State│  │
│  │  - Shared OkHttp Connection Pool    │  │  - Zero Secret Logging  │  │
│  └─────────────────────────────────────┘  └─────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Component & State Mapping

1. **Launcher Icon & Branding**:
   - `file_00000000a4fc8208b423e83efa890d18_e7339cd5.png` processed via ImageMagick into:
     - `res/mipmap-*/ic_launcher.png` and `ic_launcher_round.png`
     - `res/drawable/app_logo.png`
     - Centered adaptive icon layers in `res/drawable/ic_launcher_foreground.xml` and `ic_launcher_background.xml`
     - Displayed in `ConversationDrawerContent` and `ChatScreen` empty state.
2. **Provider Connection Status**:
   - Model `ProviderConnectionStatus`: `NOT_CONFIGURED`, `CHECKING`, `CONNECTED`, `FAILED(error)`.
   - `SettingsScreen` and `ApiKeySetupDialog` reflect live status with colored badges and explicit test actions.
3. **Response Language Injection**:
   - Enum `ResponseLanguage`: `AUTOMATIC`, `ENGLISH`, `TAMIL`, `TANGLISH`, `MALAYALAM`, `HINDI`, `KANNADA`, `TELUGU`, `BENGALI`, `MARATHI`.
   - Persisted in `SettingsManager.responseLanguage`.
   - Injected into `ChatViewModel.buildSystemPrompt()` with strict script and grammatical guidelines.
4. **Google Web Search & Browser**:
   - `isWebSearchEnabled: Boolean` state in `ChatViewModel`.
   - `SimpleBrowserSheet.kt`: Compose bottom sheet embedding Android `WebView` with progress indicator, URL display, back/forward buttons, and secure configuration.
5. **Multi-Attachment Workflow**:
   - `pendingAttachments: StateFlow<List<Attachment>>` supporting multiple files up to 10MB total.
   - `AttachmentCarousel.kt`: Displays thumbnails, file names, size in KB/MB, and remove buttons.
6. **Rich Markdown & Code Rendering**:
   - `MarkdownText.kt`: Renders headings, bold, bullet points, blockquotes, and code blocks with syntax styling and a "Copy Code" button.

---

## 5. Verification Plan

- Run unit & Robolectric tests (`gradle :app:testDebugUnitTest`) verifying request formatting, attachment validation, and language prompt generation.
- Execute full app compilation (`compile_applet`) ensuring clean build without warnings.
- Package updated APK and generate `BYOKGPT-updated.zip`.
