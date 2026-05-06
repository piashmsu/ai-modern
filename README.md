# Priya — Vibe Modern AI Voice Assistant

Bangla-aware Android AI girlfriend / Jarvis assistant. Talks to you in real time
via voice, understands interruption (barge-in), can control your phone — open
apps, send SMS, analyze the current screen, and trigger gestures via
Accessibility Service. Bring your own LLM key.

> Designed by **Shorif Uddin Piash** — fb.com/piashmsuf

## Highlights

- **3 LLM providers, your key**: OpenAI-compatible (any base URL + model),
  Groq, Gemini. Switch in Settings; conversation history is preserved across
  swaps.
- **Bilingual personality** "Priya": girlfriend-style Bangla + English, mood
  matching, optional argumentative mode. Custom system prompt supported.
- **Voice in**: Android `SpeechRecognizer` (free, on-device-ish) with optional
  cloud Whisper hook (Groq `whisper-large-v3`).
- **Voice out**: Android `TextToSpeech` default, optional ElevenLabs or any
  OpenAI-compatible `/audio/speech` endpoint (configurable base URL, voice id,
  model).
- **Barge-in interruption**: when Priya is talking and you start speaking, TTS
  stops within ~200-400 ms and she switches to listening.
- **Persistent live mode**: foreground service keeps the mic warm in the
  background; floating bubble overlays every other app.
- **Phone control** (with permissions you grant in Settings):
  - SMS send & read via `SmsManager` / `ContentResolver`
  - App launcher (open Canva, CapCut, etc. by name)
  - Accessibility service for screen content + tap/swipe/type primitives
  - `su` shell helper for rooted devices
- **Encrypted secrets**: API keys live in AndroidX EncryptedSharedPreferences
  (AES-256-GCM, keystore-backed); never logged or backed up.

## Build

Requires JDK 17 and the Android SDK (API 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

For release builds, generate your own keystore and add a signing config to
`app/build.gradle.kts`.

CI builds a debug APK on every push and uploads it as the `Priya-debug`
artifact — see Actions tab.

## First run

1. Open the app → **Settings**.
2. Pick your LLM provider, paste the API key, set your preferred model.
3. (Optional) Configure cloud TTS — base URL + key + voice id.
4. Set your name and personality intensity.
5. Grant permissions: microphone, notifications, overlay, accessibility,
   SMS (only what you actually want her to use).
6. Open **Home** → **Activate Priya**. The bubble appears; voice loop starts.

## Architecture

| Layer | Stack |
|-------|-------|
| UI | Jetpack Compose, Material 3, Compose Navigation |
| State | `SettingsRepository` (plain prefs) + `SecureKeyStore` (encrypted) |
| LLM | `LlmProvider` interface · OpenAI / Groq / Gemini implementations |
| Voice | `SpeechRecognizerWrapper` + `AndroidTts` / `CloudTts` driven by `VoicePipeline` |
| Service | `PriyaForegroundService` (mic) · `OverlayService` (bubble) · `PriyaAccessibilityService` (screen) |
| Automation | `SmsController` · `AppLauncher` · `RootShell` |

Key files:

- `app/src/main/java/com/piash/priya/voice/VoicePipeline.kt`
- `app/src/main/java/com/piash/priya/ai/ChatEngine.kt`
- `app/src/main/java/com/piash/priya/services/PriyaForegroundService.kt`
- `app/src/main/java/com/piash/priya/ui/screens/SettingsScreen.kt`

## License

Personal project. All rights reserved unless stated otherwise.
