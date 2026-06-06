<p align="center">
  <img src=".github/agora.png" alt="Agora" width="720"/>
</p>

# Agora

An on-device AI debate app for Android. Ask any question and watch two AI personas — **Socrates** and **Plato** — debate it from opposing angles before delivering a final synthesised advisory. All inference runs locally on-device using Gemma via LiteRT-LM. No internet required. No data leaves your phone.

---

## How it works

1. You ask a question
2. **Socrates** forms an initial position
3. **Plato** challenges it
4. They go back and forth (up to 10 rounds) until they reach consensus or exhaust their turns
5. The app delivers a structured **Agora Advisory** — conclusion, key considerations, main counterpoint, and confidence level

---

## Architecture

```
app/src/main/java/com/example/agora/
├── data/
│   └── Models.kt              # ChatMessage, DebateTurn, AgoraDebateResult, ChatRole
├── debate/
│   ├── AgoraDebateEngine.kt   # Orchestrates the Socrates ↔ Plato debate loop
│   ├── PromptTemplates.kt     # Prompt builders for each debate turn and final advisory
│   └── TranscriptFormatter.kt # Formats debate transcript for display
├── llm/
│   ├── LocalLlm.kt            # Interface: suspend fun generate(prompt: String): String
│   ├── GemmaLocalLlm.kt       # LiteRT-LM implementation using Gemma on-device
│   └── FakeLocalLlm.kt        # Configurable fake for unit tests
├── ui/
│   ├── ChatScreen.kt          # Jetpack Compose chat UI
│   └── theme/                 # Material3 theme
├── viewmodel/
│   └── AgoraViewModel.kt      # AndroidViewModel — state, model loading, debate orchestration
└── MainActivity.kt
```

**Stack:** Kotlin · Jetpack Compose · MVVM · LiteRT-LM (`com.google.ai.edge.litertlm`) · Coroutines/StateFlow

---

## Device requirements

- Android 8.0+ (API 26)
- `arm64-v8a` device (tested on Pixel 9 Pro)
- ~2.5 GB free storage for the model file

---

## Model setup

Agora uses **Gemma 4 E2B** in `.litertlm` format. The model is not bundled in the APK — you must place it on-device manually.

1. Download `gemma-4-E2B-it.litertlm` from [Google AI Edge](https://ai.google.dev/edge)
2. Install the app on your device
3. Push the model into the app's private files directory:

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Download/
adb shell
run-as com.example.agora
cp /sdcard/Download/gemma-4-E2B-it.litertlm /data/data/com.example.agora/files/
```

The app looks for the model at `context.filesDir/gemma-4-E2B-it.litertlm`.

---

## GPU acceleration

The app uses `Backend.GPU()` for hardware-accelerated inference. The `AndroidManifest.xml` declares the required native libraries:

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false" />
<uses-native-library android:name="libOpenCL.so" android:required="false" />
```

If GPU initialisation fails on your device, the fallback is CPU (slower but functional).

---

## Web search setup (optional)

Web search uses the [Brave Search API](https://brave.com/search/api/) (free tier: 2,000 queries/month).

1. Sign up at brave.com/search/api and get a free API key
2. Add it to `local.properties` (this file is gitignored):
   ```
   BRAVE_SEARCH_API_KEY=your_key_here
   ```
3. Rebuild the app

Once set, a 🔍 icon appears in the input row. Tap it to enable web search for a question — Agora will fetch the top 5 results and feed them as context to Socrates before the debate starts.

---

## Building

```bash
git clone https://github.com/somewisecrack/agora.git
cd agora
# Open in Android Studio and sync Gradle
```

Requires Android Studio Ladybug or newer, AGP 9.x, Kotlin 2.2.

---

## Roadmap

- [x] Collapsible debate transcript (show advisory only by default)
- [x] Persistent chat history with delete support (swipe-to-delete + clear all)
- [x] Share advisory via WhatsApp / system share sheet
- [x] Web search integration for post-training knowledge (Brave Search API, RAG)
