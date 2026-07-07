# Dialog AI Translator — Claude Code guide

A Kotlin/JavaFX desktop app that listens to a live conversation, recognizes speech, sends the text (plus a user-editable prompt) to an LLM, and returns a translated or assistant-style response. Useful for real-time translation during calls or conferences and for generating suggested replies.

## Response style (mandatory)

**Language of communication = the user's language.** Reply strictly in the language of the user's latest message. This rule is mandatory and covers EVERYTHING: the internal reasoning / thinking blocks, the answers themselves, interim comments and status updates along the way, clarifying questions (including via interactive choice prompts), and any communication with me at all. The reasoning the user sees streamed must be in the same language as the answer — never think in English and reply in Russian. Asked in Russian — the whole answer in Russian; asked in English — in English. Only unavoidable technical tokens that can't be translated stay in their original form (node, file, field names, commands); the prose itself is always in the user's language.

**The user is a human reading the chat, not Claude Code. He does NOT have the source open in parallel and does NOT want to decode code identifiers, field names, file paths, or snippet quotes inside an answer. Talk to him like a colleague, not like a code review tool.**

Answers must be **short, plain, and human**. Plain prose, in the user's language, answering the literal question. No code snippets, no `file:line` citations, no field names, no enum values, no node-type identifiers, no big tables, no multi-section breakdowns, no ASCII diagrams **by default** — even when the topic is technical.

Forbidden by default in user-facing replies:

- Code identifiers of any kind — variable, class, function, field, node-type, constant, file, folder names. Anything that looks like `snake_case`, `camelCase`, `PascalCase`, `UPPER_CASE`, or a backticked code-y token.
- File paths, `file.json:42` citations, folder structure.
- Code snippets, inline backtick code, language-tagged values, raw JSON.
- Multi-row tables, ASCII diagrams, per-branch walkthroughs.
- Restating what was changed file-by-file or field-by-field after an implementation. The user trusts the change landed — no need to itemize it.

Allowed by default:

- 1–5 sentences of plain prose.
- A single short bullet list (≤5 items) when the user explicitly asks for a list or for options.
- Workflow and node names in human terms ("the Jira ticket workflow", "the Gmail trigger", "the Switch by stage") — these are product nouns, not code identifiers.

When code-level detail IS appropriate: only when the user explicitly asks for it ("look at the JSON and write it up", "show the node's code", "what are the coordinates"). Otherwise stay in plain prose.

End-of-task summary rule: one or two plain sentences. What now works differently, in human terms. Not a changelog of files touched or fields renamed.

If unsure whether short or long is wanted → answer short, then offer to expand on a specific part.

Length and technical density are costs, not virtues. Long, code-dense responses are treated as a defect.

## MANDATORY: use Serena MCP for all code work

**Before editing or reading any `.kt` or `.py` file, use the Serena MCP tools — not `Read`/`Edit`/`Grep`.**

The project has a dedicated Serena skill at `.claude/skills/dev-serena/SKILL.md` with full tool reference, gotchas, and project-specific notes. Read it if you haven't already. Highlights:

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
| Language | Kotlin 2.3.20 on JVM 24 |
| Build | Gradle (`build.gradle` Groovy DSL, `application` plugin) |
| UI | JavaFX 26 (Stage/Scene/TabPane, CSS from `src/main/resources/styles/`) |
| DI | Koin 4.2.1 |
| HTTP | Ktor client 3.4.2 (CIO engine, content-negotiation, logging) |
| JSON | Moshi 1.15.2 + kotlinx.serialization 1.11.0 |
| LLM clients | langchain4j 1.13.0 — Google Gemini/Gemma, OpenAI, Anthropic Claude (all streaming) |
| Voice recognition | Three selectable engines (`VoiceRecognitionEngine`): **Whisper batch** (faster-whisper one-shot subprocess), **Whisper streaming** (persistent Python `whisper_streaming_util.py`), **Sherpa-ONNX** (in-process JNI, vendored jars in `libs/`) |
| Logging | Logback 1.5.32 + SLF4J |
| Python utils | `python/` scripts invoked as subprocesses (see `python/requirements.txt`); separate `.venv` under `python/.venv/` |

## Module layout (`src/main/kotlin/`)

- `app/` — `Main.kt` (entry point) and `DialogApplication.kt` (JavaFX `Application`, Koin bootstrap; top tabs = **Messages** + **Settings**; overwrites prompts from code on startup when `OVERWRITE_PROMPTS_FROM_CODE`)
- `ui/` — `MessagesTab`, `SettingsTab`, `PromptTab`, `ConsoleTab` (plus `ui/base/`). Only **Messages** and **Settings** are top-level tabs; **Settings** is a master-detail screen (left sidebar of categories: General / LLM / Whisper batch / Whisper streaming / Sherpa-ONNX / Prompts / Console) that embeds `PromptTab.content` and `ConsoleTab.content` as categories.
- `di/` — Koin modules (`AllModules`, `RepositoryModule`, `UiModule`, `UtilsModule`, `MappersModule`)
- `repository/` — `CloudRepository`, `PreferencesRepository` + their `*Impl`. `PreferencesRepositoryImpl` **chunks** the JSON across multiple prefs keys (`java.util.prefs` caps a single value at 8 KB; the prompts blow past it).
- `models/` — `common/`, `domain/`, `network/…`. Key domain types: `ApplicationInfo`, `VoiceRecognitionEngine`, `GoogleAiModelsEnum` (limits + `title` + `supportsStreaming`), `WhisperModelConfig`, `SherpaModelConfig`, `PromptModel`
- `mappers/` — `base/`, `domain/`, `network/`
- `utils/` — `AudioChunker`, `WhisperUtil` (batch), `WhisperStreamingUtil` (streaming), `SherpaOnnxRecognizer` (in-process), `TranslationRateLimiter`, `DialogLogger`, `ChatLogger`, `AppStatus` (+ `*Impl`)
- `extensions/` — `CommonExtensions.kt`, `ViewExtensions.kt`
- `Consts.kt` — colours, `OVERWRITE_PROMPTS_FROM_CODE` flag, the built-in prompts (`translateWithContextPrompt` + base/answer), default `ApplicationInfo`, coroutine scopes
- `libs/` — **vendored** sherpa-onnx desktop jars (API + win-x64 native libs), referenced by `build.gradle`

## How the app runs

1. `app.MainKt.main` → `Application.launch(DialogApplication::class.java)`
2. `DialogApplication.start`:
   - `startKoin { modules(allModules) }`
   - If `OVERWRITE_PROMPTS_FROM_CODE` (in `Consts.kt`), overwrites the saved prompts with the built-in ones
   - Loads `ApplicationInfo` from `PreferencesRepository`
   - Builds a `TabPane` with **Messages** + **Settings** tabs
3. `MessagesTab` drives the live loop: `AudioChunker` captures mic audio → the selected recognition engine produces text → text goes through the **translation scheduler** → LLM call → streamed answer.

## Voice recognition & translation pipeline

The recognition engine is chosen in Settings → General (`VoiceRecognitionEngine`); `MessagesTab.applyRecognitionEngine` wires `AudioChunker` + the active recognizer (and reacts to changes via `appInfoFlow`):

- `WHISPER_BATCH` (default) — `AudioChunker` VAD-segments utterances to WAV; `WhisperUtil` runs `python/whisper_util.py` (faster-whisper, one-shot subprocess per chunk).
- `WHISPER_STREAMING` — `AudioChunker` streams PCM frames (VAD-gated) to a persistent `python/whisper_streaming_util.py`; it commits sentences by punctuation + min-chars and flushes the trailing phrase on silence. Partials shown live, finals translated.
- `SHERPA_ONNX` — in-process JNI (`SherpaOnnxRecognizer`, jars in `libs/`, CPU). `AudioChunker` feeds **every** frame continuously (incl. silence) so sherpa's own endpointer works. A phrase is finalized by sherpa endpoint (pause) **OR** `splitMaxWords` **OR** the `maxHangMillis` timer — whichever first. Output is lowercased + capitalized (the model is all-caps); `int8` `.onnx` files are auto-picked from the model folder.

**Translation scheduler** (`MessagesTab`): recognized finals become `TranslationTask`s in a newest-first queue (cap 8, TTL 40 s). A single-consumer loop honours the model's rate limits (`TranslationRateLimiter`, fed from `GoogleAiModelsEnum`), is non-preemptive, and retries transient (5xx/429) errors ≤3×. Each task carries the previous recognized sentences; the selected prompt's `{{previous_text_N}}` placeholders (1–5) are filled for context.

**Prompts** (`Consts.kt`, selectable in Settings → Prompts): `Translate text`, `Translate text (+1…+5 previous sentences)` (context prompts built by `translateWithContextPrompt`), `Answer the question`. With `OVERWRITE_PROMPTS_FROM_CODE = true`, code is the source of truth (set `false` to keep edits made in the Prompts UI).

## Build and run

```bash
./gradlew run                      # launch the app (uses application plugin)
./gradlew build                    # compile + test + assemble
./gradlew runMainKt                # alt entry (JavaExec task, bypasses application plugin)
```

Python utilities (separate `.venv` under `python/.venv/`):

```bash
cd python
python whisper_util.py <audio_path> <model_dir> <language>                              # batch: one-shot transcribe
python whisper_streaming_util.py <model_dir> <language> <step_ms> <min_chars> <max_ms>  # streaming: persistent, JSON over stdio
```

## Files and directories to ignore when navigating

- `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `.venv/`, `cache/`
- `models/` (downloaded Whisper / sherpa-onnx models — very large)
- `dialogs/` (saved dialog logs), `app.log` (runtime log)
- `libs/` holds **vendored** sherpa-onnx jars — real build deps (referenced by `build.gradle`), not generated; don't delete.

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
