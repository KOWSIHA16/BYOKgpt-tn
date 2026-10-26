# BYOKGPT Production Refinement & Feature Enhancement Plan

1-2 concise sentences summarizing the overarching goal:
This plan upgrades the existing BYOKGPT Android app into a production-grade, highly reliable AI assistant by removing unwanted UI clutter, eliminating the AI response bubble box for a modern borderless look, introducing persistent split-session Web Search without mandatory API keys, adding fully functional long-term memory management, expanding attachment parsing across documents and code, and integrating genuine image generation and editing.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All three key design decisions have been confirmed with the user and are locked into this plan:

- **Confirmed Decision 1 (Web Search Execution)**: When Web Search is toggled ON in Settings, queries sent in chat will execute live web grounding/search, synthesize answers with cited sources, and provide one-tap links to open relevant sources in the secure in-app browser.
- **Confirmed Decision 2 (AI Response Presentation)**: In response to the user's specific request (*"user type pannurathu bubble Iruku okay super, ana ai reply pannuratha bubble la illama plain ah bubble box ah mattum remove pannu"*), the AI message bubble card is removed. The assistant's markdown text will render borderless directly on the canvas alongside its provider avatar and action toolbar, while the user's message remains in its rounded colored bubble.
- **Confirmed Decision 3 (Memory Extraction)**: Long-term memory extraction operates automatically and silently in the background when Memory is enabled in Settings, saving key user preferences and facts while strictly ignoring sensitive credentials and API keys.

---

## 1. Overview & Core Concept

- **What It Does**: BYOKGPT (Bring Your Own Key GPT) is a multi-provider Android AI client supporting Google Gemini, OpenAI ChatGPT, and Anthropic Claude. Users bring their own API keys, securely stored in Android Keystore with zero cloud telemetry or third-party loggers.
- **Developer Identity**: When asked about itself, the assistant clearly and consistently identifies as **BYOKGPT**, developed by **DoraDev**.
- **Key Value**: A fast, private, multi-model AI assistant with native document comprehension, web search grounding, long-term memory, and local privacy.

---

## 2. User Experience & Visual Design

```
┌─────────────────────────────────────────────────────────────┐
│  [=] BYOKGPT  [Gemini 3.5 Flash v]               (:)        │
│      [Mode: Web Search Active]                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                                    ┌──────────────────────┐ │
│                                    │ User message bubble  │ │
│                                    │ with attachments     │ │
│                                    └──────────────────────┘ │
│                                                             │
│  (*) BYOKGPT (DoraDev)                                      │
│  Borderless plain AI markdown response flows                │
│  directly on the screen canvas without a bubble box.        │
│                                                             │
│  Sources:                                                   │
│  [1] Android Developers (developer.android.com)             │
│                                                             │
│  [Copy] [Retry] [Switch Provider] [Share]                   │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  (+) [ Search or ask anything...               ] [Send]     │
└─────────────────────────────────────────────────────────────┘
```

### Visual Changes & Polish
1. **Remove Duplicate Globe Icons & Language Selector**:
   - Remove the globe icon toggle from the composer row in `ChatScreen.kt`.
   - Remove the language selector globe chip from the `TopAppBar` in `ChatScreen.kt`. The language selector will reside exclusively in `SettingsScreen.kt`.
   - Add a subtle status bar chip under the top bar or inside the composer placeholder when Web Search is active (e.g. `[Web Search Active]`, placeholder: *"Search the web or enter URL..."*).
2. **Borderless AI Message Bubble**:
   - In `ChatMessageBubble.kt`: User message retains the rounded primary bubble container.
   - Assistant message removes `background(surfaceContainerHighest)`, clipping, and fixed width bounds, allowing rich markdown, code blocks, and source citations to flow directly on the chat background.
   - Assistant avatar remains at the top-left of the response, and the action toolbar (Copy, Retry, Switch Provider, Share) remains neatly anchored below the text.
3. **Attachment Carousel & Preview**:
   - Updated file picker supporting photos/images, PDF, TXT, DOC/DOCX, XLS/XLSX, CSV, PPT/PPTX, JSON, XML, source code (`.kt`, `.py`, `.js`, `.ts`, `.java`, `.cpp`, `.html`, `.css`), and ZIP archives.
   - Document badge with actual file type icon, file name, and formatted byte size.

---

## 3. Key Product Decisions & Trade-Offs

### 1. Web Search & Separate Conversation Histories
- **Approach**: Persistent setting in `SettingsManager` (`webSearchEnabled`).
- **Separate Histories**: Update `ConversationEntity` to include `sessionType: String` (`"NORMAL"` vs `"WEB_SEARCH"`). The drawer will display separate sections or filter tabs ("Chats" vs "Web Searches"), ensuring switching modes never overwrites or mingles normal AI conversations with web search queries.
- **No-Key Web Access**: When a user inputs a direct URL (e.g., `https://...` or `wikipedia.org`), it opens directly in `SimpleBrowserSheet` without requiring any AI API key. When a search query is submitted, if no API key is configured, it launches Google Search in `SimpleBrowserSheet` directly; if an API key is configured, it summarizes web search results and links sources.

### 2. Functional Long-Term Memory Summary
- **Approach**: Dedicated `MemoryEntity` in Room database (`id, key, content, source, timestamp`).
- **Automatic Extraction**: When a turn completes, a lightweight background heuristic/prompt extracts key user profile facts (e.g. name, role, tech stack, preferences) and saves them, skipping any sensitive data (keys, passwords, tokens).
- **Manual Management in Settings**: `SettingsScreen.kt` provides a full Memory Management screen with:
  - Toggle Memory ON/OFF.
  - View all stored memories as editable cards.
  - Add manual memory ("Remember that I prefer Kotlin coroutines over RxJava").
  - Edit or delete individual memories or clear all memories.
- **Prompt Injection**: Active memories are formatted as `[User Long-Term Memories]` and injected into the system prompt.

### 3. Comprehensive File Parsing & Multimodal Vision
- **Approach**: Implement `DocumentParser` utility to read actual content from URI:
  - Plain text, JSON, XML, CSV, code files: Read UTF-8 text directly.
  - PDF documents: Extract text using Android's native `PdfRenderer` / text extraction.
  - Images: Scale and compress into base64, passing directly to Gemini (`inlineData`) and OpenAI (`image_url`).
  - Text contents are prepended into the message content (`--- Attached Document: filename.txt --- \n [content]`), allowing the AI to analyze the actual file contents.

### 4. Image Generation & Editing
- **Approach**:
  - Support image generation via OpenAI `dall-e-3` / `dall-e-2` and Google Gemini Imagen endpoints when image generation is requested.
  - Detect intent (e.g., "generate image of...", "draw a...", "edit this image") or provide an Image Generation mode toggle.
  - Display actual generated bitmap/URL in an `ImageMessageCard` with download/share actions.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    BYOKGPT UI Layer                         │
│  ChatScreen (Composer, Indicator, Drawer)                   │
│  ChatMessageBubble (User Bubble | Plain Borderless AI)      │
│  SettingsScreen (Web Search Toggle, Memory Manager)         │
│  SimpleBrowserSheet (SafeBrowserWebView, onRenderGone)      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    ChatViewModel                            │
│  - Mode Management: Normal Chat vs Web Search               │
│  - Immediate UI render (<10ms) & Background IO Streaming    │
│  - Memory Extraction Engine & Document Parser               │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│      Room AppDatabase        │ │      AiClient Layer        │
│  - ConversationEntity        │ │  - GeminiClient            │
│    (sessionType: CHAT | WEB) │ │  - OpenAiClient            │
│  - MessageEntity             │ │  - ClaudeClient            │
│  - MemoryEntity              │ │  - WebSearchClient / Engine│
│  - SettingsManager           │ │  - SecureKeyStorage        │
└──────────────────────────────┘ └────────────────────────────┘
```

### Migration & Database Schema Changes
- Increment Room version to `3` in `AppDatabase.kt` with clean fallback migration:
  - `ConversationEntity`: add `sessionType TEXT NOT NULL DEFAULT 'NORMAL'`.
  - Add `MemoryEntity` table with `id`, `key`, `content`, `source`, `createdAt`, `updatedAt`.
  - Create `MemoryDao` with `getAllMemoriesFlow()`, `insertMemory()`, `updateMemory()`, `deleteMemory()`, `clearAll()`.

---

## 5. Implementation Phases & Verification Steps

1. **Phase 1: UI Cleanup & AI Bubble Removal**
   - In `ChatScreen.kt`: Remove composer globe button and TopAppBar language chip. Add subtle mode indicator when Web Search is active.
   - In `ChatMessageBubble.kt`: Remove card container and background for `!isUser` messages, letting markdown render plain and borderless.
2. **Phase 2: Web Search & Persistent Split Histories**
   - Update `ConversationEntity` and `ChatDao` to filter by `sessionType` (`NORMAL` vs `WEB_SEARCH`).
   - Wire web search toggle in `SettingsScreen` and connect search querying & browser linking.
3. **Phase 3: Real Long-term Memory System**
   - Create `MemoryEntity`, `MemoryDao`, and background extraction in `ChatViewModel`.
   - Build Memory Management UI in `SettingsScreen.kt` (list, add, edit, delete).
   - Inject memories into system prompt.
4. **Phase 4: Document Parsing & Multimodal Vision**
   - Implement `DocumentParser.kt` to extract text from PDF, CSV, JSON, TXT, XML, and code files.
   - Feed parsed contents and image data to Gemini and OpenAI vision APIs.
5. **Phase 5: Image Generation & Chat Reliability**
   - Implement image generation client and message rendering.
   - Test end-to-end compilation with `compile_applet` and test suite with `gradle :app:testDebugUnitTest`.
