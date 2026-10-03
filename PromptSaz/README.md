# PromptSaz — پرامپت‌ساز

**PromptSaz** turns a rough idea into a professional, precisely structured
prompt that you can copy into any AI tool (ChatGPT, Claude, Gemini, DeepSeek,
Midjourney, video generators…). The whole UI is Persian and fully RTL.

- **Offline by default** — a template/rule engine with per-domain knowledge
  bases builds prompts with zero internet and zero accounts.
- **Optional AI mode** — bring your own OpenAI-compatible API key (e.g.
  Codecraft) and the app will use an LLM to write even richer prompts. The key
  is stored only on your device, encrypted (EncryptedSharedPreferences).
- **Archive** — every generated prompt is auto-saved locally (Room) with
  search, filters, favorites, tags, edit, duplicate, share, and JSON
  export/import.

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug or newer (any recent version with AGP 9 support) |
| JDK | 17 or newer |
| Android SDK | Platform 36, Build-Tools 36.x |

## Build & run (Android Studio)

1. Open Android Studio → **File ▸ Open…** → select the `PromptSaz` folder.
2. Let Gradle sync finish (first sync downloads dependencies).
3. Press **Run ▸ Run 'app'** on an emulator or a phone with USB debugging.

## Build an APK

### Debug APK (fastest way to install)
```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (signed)
```bash
./gradlew assembleRelease
# unsigned: app/build/outputs/apk/release/app-release-unsigned.apk
```
To sign it, create a keystore and add a `signingConfigs` block in
`app/build.gradle.kts`, or use **Build ▸ Generate Signed Bundle / APK** in
Android Studio. Full walkthrough: [docs/APK_BUILD.md](docs/APK_BUILD.md).

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

## Project layout

```
app/src/main/kotlin/com/promptsaz/app/
├── domain/          # pure Kotlin: models, prompt engine, providers, use cases
│   ├── engine/      # clarify → assemble (10-part IR) → render → score
│   ├── provider/    # PromptProvider interface + AI-mode system prompt
│   └── repository/  # repository interfaces
├── data/            # Room, DataStore, EncryptedSharedPreferences, KB loader,
│                    # OpenAI-compatible provider, export DTOs
├── ui/              # Compose screens (Persian, RTL), theme, navigation
└── util/            # Persian digits, Jalali calendar, time-ago
app/src/main/assets/knowledge/   # editable JSON knowledge bases
```

See [docs/KNOWLEDGE_BASE_GUIDE.md](docs/KNOWLEDGE_BASE_GUIDE.md) for adding
domains without code changes, and [docs/PHASE_1.md](docs/PHASE_1.md) for the
full architecture.

## Security notes

- The app makes **no network calls** unless you enable AI mode in Settings.
- The API key is stored with EncryptedSharedPreferences, excluded from
  backups, never logged, never exported, and shown masked in the UI.
- No analytics, no accounts, no servers of our own.

## Fonts

Bundles [Vazirmatn](https://github.com/rastikerdar/vazirmatn) v33.003 under
the SIL Open Font License 1.1 (see [docs/FONT_LICENSE_OFL.txt](docs/FONT_LICENSE_OFL.txt)).
