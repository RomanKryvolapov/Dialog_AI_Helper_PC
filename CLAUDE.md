# Dialog AI Translator — Claude Code guide

A Kotlin/JavaFX desktop app that listens to a live conversation, recognizes speech, sends the text (plus a user-editable prompt) to an LLM, and returns a translated or assistant-style response. Useful for real-time translation during calls or conferences and for generating suggested replies.

## MANDATORY: use Serena MCP for all code work

**Before editing or reading any `.kt` or `.py` file, use the Serena MCP tools — not `Read`/`Edit`/`Grep`.**

The project has a dedicated Serena skill at `.claude/skills/serena/SKILL.md` with full tool reference, gotchas, and project-specific notes. Read it if you haven't already. Highlights:

- **Explore a file** → `mcp__serena__get_symbols_overview(relative_path, depth=1)`
- **Find a class/method** → `mcp__serena__find_symbol(name_path_pattern, relative_path, include_body=true)`
- **See who calls something** → `mcp__serena__find_referencing_symbols(name_path, relative_path)`
- **Edit a method body** → `mcp__serena__replace_symbol_body(...)` (read with `include_body=true` first)
- **Add code** → `mcp__serena__insert_before_symbol` / `mcp__serena__insert_after_symbol`
- **Rename across codebase** → `mcp__serena__rename_symbol`

Standard tools (`Read`, `Edit`, `Grep`, `Glob`, `Bash`) are only allowed for:
- Non-code files: `.yaml`, `.yml`, `.json`, `.md`, `.txt`, `.toml`, `.gradle`, `.properties`, `.css`, `.xml`
- Directory listing / file finding
- Git operations
- When Serena returns an error for a specific call

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.2.20 on JVM 24 |
| Build | Gradle (`build.gradle` Groovy DSL, `application` plugin) |
| UI | JavaFX 26-ea (Stage/Scene/TabPane, CSS from `src/main/resources/styles/`) |
| DI | Koin 4.1.1 |
| HTTP | Ktor client 3.3.0 (CIO engine, content-negotiation, logging) |
| JSON | Moshi 1.15.2 + kotlinx.serialization 1.9.0 |
| LLM clients | langchain4j 1.4.0 (Google Gemini, OpenAI-compatible, Ollama) |
| Voice recognition | Vosk 0.3.45 (primary, in-process); Whisper (faster-whisper via Python subprocess) |
| TTS | XTTS and Chatterbox (via Python subprocesses) |
| Logging | Logback 1.5.13 + SLF4J |
| Python utils | `python/` scripts invoked as subprocesses from Kotlin (see `python/requirements.txt`) |

## Module layout (`src/main/kotlin/`)

- `app/` — `Main.kt` (entry point) and `DialogApplication.kt` (JavaFX `Application`, Koin bootstrap, tab wiring)
- `ui/` — four tabs: `MessagesTab`, `PromptTab`, `SettingsTab`, `ConsoleTab` (plus `ui/base/` helpers)
- `di/` — Koin modules (`AllModules`, `RepositoryModule`, `UiModule`, `UtilsModule`, `MappersModule`)
- `repository/` — `CloudRepository`, `LocalNetworkRepository`, `PreferencesRepository` + their `*Impl`
- `models/` — split into `common/`, `domain/`, `network/{googleai,googletranslate,ollama,openai}`
- `mappers/` — `base/`, `domain/`, `network/`
- `utils/` — `VoskVoiceRecognizer`, `WhisperUtil`, `AudioChunker`, `ChatterboxUtil`, `XttsUtil`, `Assistant`, `ChatLogger` (+ `*Impl`)
- `extensions/` — `CommonExtensions.kt`, `ViewExtensions.kt`
- `Consts.kt` — colours, default prompts, default `ApplicationInfo`, coroutine scopes

## How the app runs

1. `app.MainKt.main` → `Application.launch(DialogApplication::class.java)`
2. `DialogApplication.start`:
   - `startKoin { modules(allModules) }`
   - Loads `ApplicationInfo` from `PreferencesRepository`
   - Builds a `TabPane` with Messages / Prompt / Settings / Console tabs
   - Remembers last selected tab index
   - Initializes `VoskVoiceRecognizer` if Vosk is the selected engine and a model path is set
3. The Messages tab drives the live loop: voice recognizer → text buffer → LLM call → displayed answer.

## Build and run

```bash
./gradlew run                      # launch the app (uses application plugin)
./gradlew build                    # compile + test + assemble
./gradlew runMainKt                # alt entry (JavaExec task, bypasses application plugin)
```

Python utilities (separate `.venv` under `python/.venv/`):

```bash
cd python
python whisper_util.py <audio_path> <model_dir> <language>
python xtts_util.py <text> <speaker_wav> <output_path> <language>
python chatterbox_util.py <text> <speaker_wav> <output_path>
```

## Files and directories to ignore when navigating

- `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `.venv/`, `cache/`
- `models/` (downloaded Vosk/Whisper/TTS models — very large)
- `app.log` (runtime log)

## Working rules

- Keep edits minimal — don't refactor surrounding code unless asked.
- Don't add comments to code unless they capture non-obvious *why*.
- JavaFX, not Compose Desktop — don't suggest Compose APIs for UI changes.
- Heavy work goes on coroutine scopes defined in `Consts.kt` (`backgroundThreadScope`, `defaultThreadScope`); UI updates go through `mainThreadScope`.
- Preferences live behind `PreferencesRepository` — always round-trip through it, don't touch storage directly.
- **After every batch of edits:** stop any currently running `./gradlew run` instance and relaunch it in the background. A stale JVM holds the mic device and blocks the new instance from opening it. See **How to restart the app** below.

## How to restart the app

1. If a previous `./gradlew run` was launched via the `Bash` tool with `run_in_background: true`, you have its task id. Call `TaskStop <task_id>` — this kills the gradle wrapper.
2. **On Windows**, `TaskStop` alone does **not** kill the child JVM that holds the JavaFX window and the mic line. You must additionally kill every `app.MainKt` process. **Do NOT use `tasklist //V`** — its command-line column is truncated at ~72 chars so `grep app.MainKt` finds nothing even when 4 zombies are alive. Use `wmic` with a server-side `CommandLine like` filter:
   ```bash
   for pid in $(wmic process where "name='java.exe' and CommandLine like '%%app.MainKt%%'" get ProcessId /format:value 2>&1 \
                | tr -d '\r' | grep -oE '[0-9]+'); do
       taskkill //F //PID $pid
   done
   # verify:
   wmic process where "name='java.exe' and CommandLine like '%%app.MainKt%%'" get ProcessId /format:value 2>&1 \
     | tr -d '\r' | grep -oE '[0-9]+' | wc -l
   # should print 0
   ```
   Double `%%` is required because bash eats single `%`. Filter on the exact `app.MainKt` main-class token — never match by `Dialog_AI_Translator` (sweeps up IntelliJ Kotlin/Serena language servers).
   Also kill any orphaned gradle wrapper for this project:
   ```bash
   for pid in $(wmic process where "name='java.exe' and CommandLine like '%%GradleWrapperMain%%' and CommandLine like '%%Dialog_AI_Translator%%'" get ProcessId /format:value 2>&1 \
                | tr -d '\r' | grep -oE '[0-9]+'); do
       taskkill //F //PID $pid
   done
   ```
3. Launch fresh:
   ```bash
   ./gradlew --no-daemon run
   ```
   with `run_in_background: true`. Record the new task id for the next restart cycle.
4. Verify the launch reached `> Task :run` in the output file, then report back. The gradle task will hang there until the user closes the window or you `TaskStop` it next round — this is expected for a GUI app.

## Pointers

- Full tool reference and gotchas: `.claude/skills/serena/SKILL.md`
- User-facing app description: `README.md`
- Serena project config: `.serena/project.yml`
