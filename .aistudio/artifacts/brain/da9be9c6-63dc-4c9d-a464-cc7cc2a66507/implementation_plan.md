# BYOKGPT — Model Refinement, Security & Feature Enhancement Plan

1-2 concise sentences summarizing the overarching goal:
This plan upgrades the BYOKGPT Android client to use `gemini-3.8-flash` exclusively with verified endpoints and real-time temporal context, adds live web search grounding for current facts, activates complete message action toolbars (Copy, Like, Dislike, Share, Regenerate for AI; Copy, Edit, Delete for User) with clickable links, integrates App PIN and Biometric security (cold launch & background return), and delivers a multi-provider image generation suite supporting Pollinations (free), Gemini Imagen, and OpenAI DALL-E with graceful handling of unsupported providers.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following three key design decisions have been confirmed with the user in Phase 1 and are locked into this plan:

- **Confirmed Decision 1 (Security & App Lock Activation)**: The application will enforce PIN / Fingerprint biometric authentication on every cold launch and whenever returning from background, with configuration options neatly housed in Settings.
- **Confirmed Decision 2 (Image Generation Providers)**: Multi-provider image generation selector with free Pollinations.ai (no API key required), Google Gemini (Imagen 3), and OpenAI (DALL-E 3), with clear error messaging explaining that Anthropic Claude has no image generation endpoint.
- **Confirmed Decision 3 (Real-Time Search Grounding)**: Live web search is triggered automatically when queries reference current events, dates, live scores, weather, or real-time facts, as well as when Web Search mode is explicitly enabled.

---

## 1. Overview & Core Concept

- **What It Does**: BYOKGPT is a private, client-side AI chat client for Android. Users bring their own API keys (stored locally in AndroidKeyStore with AES-GCM-256). This update cleans up model clutter to focus on Google's flagship `gemini-3.8-flash`, ensures real-world date/time accuracy, empowers message management with working like/dislike, edit, delete, and copy actions, protects personal chats with biometric lock, and introduces multi-model image generation.
- **Developer Identity**: Consistently identifies as **BYOKGPT**, created by **DoraDev**.
- **Key Value**: Accurate real-time knowledge, rock-solid security, privacy, and full control over every chat interaction and image generation request.

---

## 2. User Experience & Visual Design

```
┌─────────────────────────────────────────────────────────────┐
│  [=] BYOKGPT  [Gemini 3.8 Flash v]               (:) [Lock] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                                    ┌──────────────────────┐ │
│                                    │ What is the date and │ │
│                                    │ latest space news?   │ │
│                                    └──────────────────────┘ │
│                                    [Copy] [Edit] [Delete]   │
│                                                             │
│  (*) BYOKGPT (Google Gemini)                                │
│  Today is Monday, October 26, 2026. Here is the latest      │
│  verified space exploration update from NASA and ESA...    │
│                                                             │
│  Sources:                                                   │
│  - https://www.nasa.gov/news                                │
│                                                             │
│  [Copy] [Like] [Dislike] [Share] [Regenerate]               │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  (+) [ Search or ask anything...               ] [Send]     │
└─────────────────────────────────────────────────────────────┘
```

### Visual Changes & User Flows
1. **Gemini Model Selector**:
   - For Google Gemini, the model picker displays only **Gemini 3.8 Flash** (`gemini-3.8-flash`). Legacy options (`gemini-1.5-flash`, etc.) are eliminated from the UI.
   - Any saved preferences pointing to older Gemini versions automatically migrate to `gemini-3.8-flash`.
2. **Interactive Message Actions**:
   - **Assistant Messages**: Action bar beneath each response provides:
     - `Copy`: Copies formatted response text to clipboard with toast confirmation.
     - `Like` / `Dislike`: Saves user feedback state to the message entity, highlighting the selected thumb icon.
     - `Share`: Launches Android system share sheet (`Intent.ACTION_SEND`) with message content.
     - `Regenerate`: Triggers regeneration of the assistant response using the current provider and model.
   - **User Messages**: Subtle action toolbar below the user bubble provides:
     - `Copy`: Copies user prompt text.
     - `Edit`: Populates composer with message text (or opens quick inline edit) and allows resending.
     - `Delete`: Deletes the user message (and subsequent assistant turn) from the Room database.
   - **Clickable Links**:
     - Both AI markdown and user message text render URLs as clickable links. Tapping a URL opens it inside the in-app BYOK Browser Mode.
3. **App PIN & Biometric Lock Flow**:
   - Modern lock overlay rendered over the app when locked.
   - Prompts for device fingerprint/biometric unlock via Jetpack `BiometricPrompt` if supported, with a numeric PIN pad fallback.
   - Settings offers toggle for App Lock, PIN setup/change, and Biometric unlock toggle.
4. **Image Generation Experience**:
   - Dedicated prompt detection ("generate image...", "draw...", "create a picture...") and bottom bar shortcut.
   - Model/Provider selector for image generation:
     - **Pollinations.ai (Free)**: Generates high-quality images via Flux without requiring an API key.
     - **Google Gemini (Imagen 3)**: High-resolution image generation via `imagen-3.0-generate-002` with user's Gemini key.
     - **OpenAI (DALL-E 3 / DALL-E 2 edits)**: Generates or edits images with user's OpenAI key.
     - **Anthropic Claude**: Clear informational prompt explaining Anthropic's lack of image generation API, directing user to switch to Gemini, OpenAI, or Pollinations.
   - Images display with full-bleed cards, save-to-gallery, share, and expand-to-fullscreen actions.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Gemini Endpoint & Date/Time Grounding
- **Chosen Approach**:
  - Direct endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse&key=...` (and `:generateContent?key=...` for single calls).
  - Explicit system prompt injection of current real-world date, time, and timezone on every turn (`Locale` and `TimeZone` aware).
- **Why**: Eliminates 404 model errors and prevents the model from stating inaccurate or outdated temporal information.

### Decision 2: Real-Time Live Search Grounding
- **Chosen Approach**:
  - Automatic intent heuristic detects temporal and factual keywords ("today", "yesterday", "current", "latest", "price of", "score", "who won", "news", "2026").
  - Queries search engine (DuckDuckGo instant answers & Google Search HTML extraction) in background, injects authoritative snippets and source URLs into the conversation context, and instructs the model to ground its response and cite source links.
- **Why**: Delivers live, verified information without hallucinating or requiring paid search API subscriptions.

### Decision 3: App Lock Architecture (Lifecycle & Background Transition)
- **Chosen Approach**:
  - `AppLockManager` backed by `ProcessLifecycleOwner` (or activity `onPause`/`onStop` with timestamp).
  - Encrypted PIN storage in `SecureKeyStorage` using SHA-256 with random salt.
  - Native `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
- **Why**: Protects user conversations from unauthorized access when the device is handed to someone else or resumed from the recents tray.

### Decision 4: Image Generation Multi-Provider Architecture
- **Chosen Approach**:
  - Dedicated `ImageGenerationManager` supporting Pollinations (`image.pollinations.ai`), Gemini Imagen (`imagen-3.0-generate-002:predict`), and OpenAI (`/v1/images/generations` and `/v1/images/edits`).
  - Transparent error handling when Claude is selected, avoiding silent failures.
- **Why**: Provides a free out-of-the-box option (Pollinations) while supporting user-supplied keys for premium generation.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        App Navigation & Root UI                        │
│ ┌──────────────────────────────────┐ ┌───────────────────────────────┐ │
│ │ AppLockScreen (PIN / Biometrics) │ │ SettingsScreen (Security,Keys)│ │
│ └──────────────────────────────────┘ └───────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ ChatScreen                                                         │ │
│ │ - TopAppBar: Gemini 3.8 Flash / Claude / OpenAI / Provider Modal   │ │
│ │ - ChatList: ChatMessageBubble (Clickable links in User & AI)       │ │
│ │   - User Actions: [Copy] [Edit] [Delete]                           │ │
│ │   - AI Actions: [Copy] [Like] [Dislike] [Share] [Regenerate]       │ │
│ │ - Composer: Text, File Attachment, Image Generation Shortcut       │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
└────────────────────────────────────┼───────────────────────────────────┘
                                     │
                     ┌───────────────▼──────────────┐
                     │        ChatViewModel         │
                     │  - Message Orchestration     │
                     │  - Real-time Search Grounding│
                     │  - Temporal Date Injection   │
                     │  - Image Generation Router   │
                     └───────────────┬──────────────┘
                                     │
       ┌─────────────────────────────┼─────────────────────────────┐
       ▼                             ▼                             ▼
┌──────────────┐             ┌──────────────┐              ┌──────────────┐
│GeminiClient  │             │OpenAiClient  │              │Pollinations  │
│gemini-3.8-   │             │DALL-E 3 &    │              │Image Client  │
│flash / Imagen│             │GPT-4o Mini   │              │(Free Flux)   │
└──────────────┘             └──────────────┘              └──────────────┘
```

### Data Model Enhancements
- `MessageEntity` in Room:
  - Add `userFeedback: String?` (`"LIKE"`, `"DISLIKE"`, or `null`).
  - Ensure `generatedImageUrl: String?` is persisted for image responses.
- `SettingsManager` / `SecureKeyStorage`:
  - `isAppLockEnabled: Boolean`
  - `appPinHash: String`
  - `appPinSalt: String`
  - `isBiometricEnabled: Boolean`
  - `imageGenerationProvider: String` (`"POLLINATIONS"`, `"GEMINI"`, `"OPENAI"`)

---

## 5. Implementation Phases & Verification Steps

1. **Phase 1: Gemini 3.8 Flash Exclusive Migration & Date Grounding**
   - In `Models.kt`: Retain only `gemini-3.8-flash` for Gemini. Remove `gemini-1.5-flash`, `gemini-2.5-flash`, and `gemini-3.5-flash`.
   - In `GeminiClient.kt`: Update `resolveModel` to return `gemini-3.8-flash`. Update endpoint URLs and fallbacks.
   - In `ChatViewModel.kt`: Inject current real-world date, time, and timezone string into `buildSystemPrompt()`.
   - In `SecureKeyStorage.kt`: Auto-migrate any stored Gemini model to `gemini-3.8-flash`.

2. **Phase 2: Real-Time Web Search Grounding**
   - Implement `RealTimeSearchGrounder.kt` using real HTTP search query retrieval.
   - Automatically detect temporal/news queries or active web search toggle in `ChatViewModel.kt`.
   - Fetch real snippets and URLs, and ground model context before streaming response.

3. **Phase 3: Interactive Chat Actions & Clickable Links**
   - Update `ChatMessageBubble.kt`:
     - Add user action bar: Copy, Edit, Delete.
     - Add assistant action bar: Copy, Like, Dislike, Share, Regenerate.
     - Wire user message text to render clickable URLs with `MarkdownContent` or AnnotatedString URL clicks.
   - Connect handlers in `ChatViewModel.kt` (regeneration, like/dislike update in Room, delete turn, edit prompt in composer).

4. **Phase 4: App PIN & Biometric Security**
   - Add biometric and PIN verification logic in `AppLockManager.kt` and `SecureKeyStorage.kt`.
   - Build `AppLockScreen.kt` with numeric PIN entry and biometric prompt invocation.
   - Add App Lock settings in `SettingsScreen.kt` (Enable PIN, Change PIN, Toggle Biometrics).
   - Wire cold launch and app resume lifecycle triggers in `MainActivity.kt`.

5. **Phase 5: Multi-Provider Image Generation**
   - Implement `PollinationsImageClient.kt` for free text-to-image without API keys.
   - Enhance `OpenAiClient.kt` to support image generation (`dall-e-3`) and editing with attachments (`dall-e-2`).
   - Verify `GeminiClient.kt` Imagen 3 generation (`imagen-3.0-generate-002:predict`).
   - Add explicit guidance when Claude is selected.
   - Expose image generation provider selection in Settings and composer.

6. **Phase 6: Verification & Compilation**
   - Run `compile_applet` to ensure zero compilation or syntax errors.
   - Run unit test suite with `gradle :app:testDebugUnitTest`.
   - Verify model endpoint, chat actions, search grounding, and security state transitions.
