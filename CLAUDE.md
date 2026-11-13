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
| Language | Kotlin 2.4.21; builds, tests and runs on JDK 27 (Temurin, `~/.jdks/jdk-27+35`), bytecode target 26 until a stable Kotlin supports JVM_27 |
| Build | Gradle 9.9 (`build.gradle` Groovy DSL, `application` plugin); JUnit 6 + kotlin-test under `src/test/` |
| UI | JavaFX 27.0.1 (Stage/Scene/TabPane, CSS from `src/main/resources/styles/`) |
| DI | Koin 4.2.2 |
| HTTP | Ktor client 3.6.1 (CIO engine, content-negotiation, logging) |
| JSON | Moshi 1.15.2 (+ moshi-adapters, lenient model enums) + kotlinx.serialization 1.12.0 |
| LLM clients | langchain4j 1.23.0 — Google Gemini/Gemma, OpenAI, Anthropic Claude (all streaming); mid-tier models only; each request carries the system message (instructions) and the user message, with the model's parameters narrowed by `effectiveParameters` to what it accepts (`ModelParameterSupport` per model; the defaults keep reasoning minimal) |
| Translation methods | A chain of up to 3 on Settings → General (`TranslationMethod`, method 1 + 2 reserves), each on two levels (`Hearing`): how the speech is heard — a **voice** model that hears the WAV itself (Gemini 3.5 Flash-Lite, GPT-Audio-1.5) or a **recognizer** — and, for a recognizer only, the model that translates; failover in `utils/TranslationFailover.kt` |
| Voice recognition | The recognizer level of a text method (`VoiceRecognitionEngine`, offered: **Whisper batch** — faster-whisper one-shot subprocess, **Whisper streaming** — persistent Python `whisper_streaming_util.py`, **Sherpa-ONNX** 2.0.1 — in-process JNI, vendored jars in `libs/`; Speechmatics is not wired and not offered) |
| Logging | Logback 1.6.6 + SLF4J 2.0.20 |
| Python utils | `python/` scripts invoked as subprocesses (see `python/requirements.txt`); separate `.venv` under `python/.venv/` |

## Module layout (`src/main/kotlin/`)

- `app/` — `Main.kt` (entry point) and `DialogApplication.kt` (JavaFX `Application`, Koin bootstrap; top tabs = **Messages** + **Questions** + **Settings**, with a top bar right of the tab names: `MessagesTab.headerControls` (Start/Clear dialog, the tabs' size and spacing via `.header-button` and `-fx-tab-min/max-width`, disabled on Settings), mic/RAM meters and the dialog's token count (`TokenUsageTracker`))
- `ui/` — `MessagesTab`, `MethodChainSelector` (Settings → General: three numbered rows, first level `Hearing` → second level translating model, disabled for voice; rows 2–3 offer only `reserveHearings(method 1)`, a pick rebuilds the whole chain from the rows once the combos settle; green = translated the latest phrase, orange = resting), `SettingsTab`, `MainPromptTab` / `VoicePromptTab` (every prompt part, each a `PromptPartEditor` with Save / Reset to default, checked by `promptProblem`; the last requests as sent, from `LastRequests`), `ModelParametersPane` (Settings → LLM: per-model parameters, only those the model accepts), `PromptEditors.kt` (the shared editor pieces), `ConsoleTab` (plus `ui/base/`). Only **Messages** and **Settings** are top-level tabs; **Settings** is a master-detail screen (left sidebar of categories: General / Phrases / LLM / API keys / Whisper / Sherpa-ONNX / Speechmatics / Main prompt / Voice prompt / Console; the phrase-cutting values on Phrases serve Whisper, the voice models and, in part, Speechmatics (Sherpa-ONNX ends phrases itself), each value lives on one page only). Every Settings page is built from the pieces in `extensions/ViewExtensions.kt` (`settingsPage`, `addSection` / `addGroup`, `settingsButton`s of one width: Save green, Clear red, the rest blue) and the `settings-*` / `note-label` classes in `styles.css`: a left-aligned column, a title, a grey note, the control, a row of buttons under it. The Messages tab's status line (bottom) shows `AppStatus.statusFlow`.
- `di/` — Koin modules (`AllModules`, `RepositoryModule`, `UiModule`, `UtilsModule`, `MappersModule`)
- `repository/` — `CloudRepository`, `PreferencesRepository` + their `*Impl`. `PreferencesRepositoryImpl` **chunks** the JSON across multiple prefs keys (`java.util.prefs` caps a single value at 8 KB; the prompts blow past it). It also layers `.env` over the saved values via `ApplicationInfoEnvOverlay`: on read a setting the user has not filled in comes from `.env`; on write only values that differ from the `.env`/built-in fallbacks are stored, so an untouched setting stays unset and later `.env` edits keep taking effect.
- `models/` — `common/`, `domain/`, `network/…`. Key domain types: `ApplicationInfo` (`translationMethods`, method 1 first; method 1 also decides how audio comes in), `TranslationMethod` (model + `recognizer`, null for voice; saved as `voice:<id>` / `<recognizer slug>:<id>`, older `text:<id>` resolved on the saved or `.env` recognizer; `resolveMethodChain` keeps only reserves that `fitsAfter` method 1), `Hearing` / `allHearings` / `reserveHearings` / `textModels`, `VoiceRecognitionEngine` (`slug`, `offered`, `recordsPhrases`), `GoogleAiModelsEnum` / `OpenAiModelsEnum` / `ClaudeModelsEnum` (limits, titles, `hearsAudio` / `readsText`, `parameters`: the `ModelParameterSupport` each provider was found to accept), `ModelParameters` (the user's per-model choices; `effectiveParameters` = what is sent), `ModelPrompt` (system + user), `PromptSettings` (`previousPhrases`, `text` / `voice` `PromptParts` = system, user, previous-phrase format; `summary` = enabled, every N phrases, system, user), `WhisperModelConfig`, `SherpaModelConfig`. The old prompt library (`prompt`, `promptsMap`) is no longer read; saved settings that hold it load on the built-in prompts. The old `selectedModel` and the separate `voiceRecognitionEngine` are only read to migrate them (`PreferencesRepositoryImpl.migrated`, the mapper) and never written.
- `mappers/` — `base/`, `domain/`, `network/`
- `utils/` — `AudioChunker`, `WhisperUtil` (batch: `recognize` one phrase file → `WhisperResult` Heard / Silence / Failed; `transcribe` for a phrase in memory), `WhisperStreamingUtil` (streaming), `WhisperHallucinations` (`clean`: an utterance made only of what Whisper writes on silence and noise — "Thank you.", "you", "Thanks for watching!", subtitle credits — is null, a sentence looped 3+ times is kept once; applied by both Whisper utils), `SherpaOnnxRecognizer` (in-process), `TranslationRateLimiter`, `TranslationFailover` (`classifyFailure`, `MethodHealth`, `chooseMethod`), `TokenUsageTracker` (each finished answer's `tokenUsage()` recorded by `CloudRepositoryImpl.streamingFlow`, per model; reset by Clear dialog, answers to requests sent before it not counted), `ConversationContext`, `TranslationPrompts` (builds the text, voice and summary requests), `PromptTemplates` (`PromptVariables`, `PromptPart` with its allowed / required placeholders, one-pass `fillTemplate`, `promptProblem`), `LastRequests` (the last request of each kind as sent, and the summary, for the prompt pages), `DialogLogger`, `ChatLogger`, `AppStatus` (+ `*Impl`)
- `extensions/` — `CommonExtensions.kt`, `ViewExtensions.kt`
- `Consts.kt` — colours, the built-in prompt parts (`TEXT_INSTRUCTIONS` / `TEXT_MESSAGE`, `PREVIOUS_PHRASE_FORMAT`, `SUMMARY_INSTRUCTIONS` / `SUMMARY_MESSAGE`, `VOICE_INSTRUCTIONS` / `VOICE_MESSAGE`, `VOICE_AUDIO_MARK`), default `ApplicationInfo`, coroutine scopes
- `.env` (git-ignored) / `.env.example` — optional fallback values for every field of `ApplicationInfo` except the prompts and the model parameters (only the parts the user changed are stored, see `dropFallbackValues`); key names live in `ApplicationInfoEnvOverlay`, parsing in `EnvSettingsImpl`. Order: saved setting → OS environment variable → `.env` → built-in default.
- `libs/` — **vendored** sherpa-onnx desktop jars (API + win-x64 native libs), referenced by `build.gradle`

## How the app runs

1. `app.MainKt.main` → `Application.launch(DialogApplication::class.java)`
2. `DialogApplication.start`:
   - `startKoin { modules(allModules) }`
   - Loads `ApplicationInfo` from `PreferencesRepository`
   - Builds a `TabPane` with **Messages** + **Questions** + **Settings** tabs
3. `MessagesTab` drives the live loop: `AudioChunker` captures mic audio → the primary method's input (a recorded phrase, or the recognizer's text) → the **translation scheduler** picks a method from the chain → LLM call → streamed answer.

## Voice recognition & translation pipeline

Method 1 decides how the audio comes in: a **voice** method 1 needs no recognizer (`currentRecognizer == null`, `AudioChunker` cuts WAV phrases as in batch mode); a recognizer method 1 runs its recognizer (`primaryMethod.recognizer`). `MessagesTab.applyRecognitionEngine` wires `AudioChunker` + that recognizer and reacts to changes via `appInfoFlow`:

- `WHISPER_BATCH` (default) — `AudioChunker` VAD-segments utterances to WAV; `WhisperUtil.recognize` runs `python/whisper_util.py` (faster-whisper, one-shot subprocess per chunk); `onWhisperPhrase` adds the phrase's row and takes its conversation turn (`beginTurn("")`) first, and its own result fills that row and turn, so overlapping runs cannot swap rows or the order of turns (a silent phrase's row — no text, or only a phrase `WhisperHallucinations` drops — is hidden, not removed, keeping later rows' indexes, and its turn is discarded; a turn without text takes no place in `{{previous_phrases}}`; results from before Clear dialog are dropped by epoch). When the chain holds a voice method, `onWhisperPhrase` keeps the WAV bytes with the phrase so a voice reserve can hear it.
- `WHISPER_STREAMING` — `AudioChunker` streams PCM frames (VAD-gated) to a persistent `python/whisper_streaming_util.py`; it commits sentences by punctuation + min-chars and flushes the trailing phrase on silence. Partials shown live, finals translated; a final `WhisperHallucinations` drops arrives empty and its row is hidden. No recordings, so voice reserves are skipped.
- Voice primary (no recognizer) — `MessagesTab.onVoicePhrase` reads each WAV into memory (file deleted at once) and queues a `TranslationTask` with audio. Tasks still waiting for a transcript (`awaitsTranscript`) go oldest first, one at a time (`voiceSlot`), and the prompt is built after the slot is taken, so it carries the previous phrase's transcript. The voice prompt (`TranslationPrompts.voice`: its instructions as the system message, then summary + previous phrases + `{{audio}}`) goes via `CloudRepository.streamGoogleAIWithAudio` / `streamOpenAIWithAudio` (GPT-Audio gets no reasoning effort: it rejects it), which put the audio part at `VOICE_AUDIO_MARK`; the streamed answer is two tags (`<said>`, `<translated>`) parsed by `VoiceAnswer`, and the transcript fills the turn's source. A recorded phrase that falls through to a text method is transcribed first by `WhisperUtil.transcribe`. `ChatLogger` logs media parts by size only.
- `SHERPA_ONNX` — in-process JNI (`SherpaOnnxRecognizer`, jars in `libs/`, CPU). `AudioChunker` feeds **every** frame continuously (incl. silence) so sherpa's own endpointer works. A phrase is finalized by sherpa endpoint (pause) **OR** `splitMaxWords` **OR** the `maxHangMillis` timer — whichever first. Output is lowercased + capitalized (the model is all-caps); `int8` `.onnx` files are auto-picked from the model folder.

**Translation scheduler** (`MessagesTab`): phrases become `TranslationTask`s in a queue (cap 8, TTL 90 s; text newest first, phrases awaiting a voice transcript oldest first). For each, `chooseMethod` takes the first method in the chain not yet failed for it (`task.tried`), usable for it (key set, the input it needs), not resting (`MethodHealth`) and let through by its model's `TranslationRateLimiter` (one per model id; a wait ≤ 3 s is waited out, a longer one passes the phrase on); with nothing healthy, a softly resting method is used anyway. A failure — classified by `classifyFailure` from langchain4j's typed exceptions, the HTTP status and the provider body (429 per minute / per day / `insufficient_quota`, 401/403 and Google's `API_KEY_INVALID`, 404, other 4xx, 5xx, network), a stream silent for 30 s (`Flow.timeout`), an empty or malformed answer — rests the method (`MethodHealth`: retry-after or 60 s; per day: midnight Pacific for Google, OpenAI's retry-after; 15 min; until keys/chain change; 30 s doubling to 5 min, soft) and requeues the phrase for the next method; 3 attempts in all, then the row says why. Google's per-day 429 also calls `exhaustDay()` (its "retry in" hint is only the minute window); OpenAI's per-day one penalizes the limiter for its retry-after. When every method has failed a phrase, the pause before the next pass holds a voice phrase's slot (keeping phrases in order) but not a text phrase's. Phrases evicted or gone stale are marked "skipped", and a task carries the epoch it was queued in, so after Clear dialog a late retry is dropped. Each task carries a `ConversationContext.Snapshot` taken when its phrase arrived. Covered by `TranslationFailoverTest` / `MethodChainSettingsTest`.

**Conversation context** (`utils/ConversationContext`): every phrase becomes a `ConversationTurn`; a translation is written back as that turn's translation. Each request gets the last `previousPhrases` turns that have text (`Snapshot.previous`, read when the request is built, in the prompt's previous-phrase format) as `{{previous_phrases}}`, plus the running summary as `{{summary}}`. The summary is a request of its own (`Fold`, `TranslationPrompts.summary`, on the first chain model that answers text, has its key — the method in use's model first, through its rate limiter, 60 s cap including the quota wait): first as soon as a turn leaves the window, then each time `summary.everyPhrases` turns have left it since; it gets the summary so far and those turns, and `foldDone` swaps them for the new summary. Turns stop being folded at one still waiting for its text (order is kept). A failed summary is retried 5 phrases later, and the waiting turns are capped at 4 summaries' worth. With the summary off only the window is kept and `{{summary}}` is empty. Clear dialog resets it. Covered by `ConversationContextTest` and `TranslationPromptsTest`.

**Prompts** (Settings → Main prompt, Voice prompt; built-in texts in `Consts.kt`): one prompt per way of translating — a recognized phrase (`text`) and a recorded one a voice model hears (`voice`) — plus the summary request. Each is instructions (system message, no placeholders) + message (`{{summary}}`, `{{previous_phrases}}`, `{{phrase}}` / `{{audio}}`; the summary's `{{summary}}`, `{{new_phrases}}`) + previous-phrase format (`{{said}}`, `{{translated}}`). The screens refuse a part with an unknown placeholder or without its required one (`promptProblem`); a part left at (or reset to, or saved blank as) its built-in text is not stored. The voice answer's `<said>` / `<translated>` tags are what `VoiceAnswer` reads.

**Model parameters** (Settings → LLM): per model id, `ModelParameters` (temperature, top P, top K, max output tokens, thinking level / reasoning effort, verbosity); the page offers only what `ModelParameterSupport` lists and shows what is sent. Found by test requests on 2026-11-09: Flash-Lite takes thinking minimal–high; Gemma 4 only minimal (fixed); GPT-6.1 Terra reasoning none–xhigh, temperature / top P only at none, verbosity; GPT-Audio temperature only (top P makes the server fail); Claude Sonnet 5.5 (from Anthropic's docs, no key to test) thinking `between_tools` (off) / `adaptive`, no sampling parameters.

## Build and run

```bash
./gradlew run                      # launch the app (uses application plugin)
./gradlew build                    # compile + test + assemble
./gradlew test                     # unit tests only (src/test)
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
- Heavy work goes on coroutine scopes defined in `Consts.kt` (`backgroundThreadScope`, `defaultThreadScope`); UI updates go through `mainThreadScope`. All three are supervised (`SupervisorJob` + a logging handler): a coroutine that fails with nothing to catch it ends alone and is logged, instead of cancelling every other task in its scope.
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

## Changes of 2026-11-10 (supersede the older lines above where they disagree)

- **Method switching** (`utils/ActiveMethod`): one method in use per dialog — method 1 at Start dialog (and after a chain or key change); any failure of it (classified model failure, own rate limit wait > `SHORT_WAIT_MILLIS`, recogniser failure via `onFailureListener` of `WhisperStreamingUtil` / `SherpaOnnxRecognizer` / `SpeechmaticsRecognizer`, Whisper batch `Failed`) switches to the next for the rest of the dialog (`MessagesTab.switchAfterFailure`); the last method stays. `applyRecognitionEngine` wires the recogniser of the method in use, so any method can be a reserve (`reserveHearings` / `fitsAfter` / `MethodHealth` / `chooseMethod` are gone). The top bar shows the method in use; `MethodChainSelector` outlines it green and the left ones orange.
- **Speechmatics** (`utils/SpeechmaticsRecognizer(Impl)`, `SpeechmaticsConfig`, Settings → Speechmatics): realtime cloud recogniser over `java.net.http.WebSocket` (`StartRecognition`, binary audio while someone speaks, `ForceEndOfUtterance` at a pause, `AddTranscript` words split into phrases at a sentence end ≥ 20 chars or `EndOfUtterance`); with speaker labels each phrase carries "S1"/"S2" into `ConversationTurn.speaker`.
- **Questions mode** (`utils/QuestionsAssistant`, `utils/QuestionPrompts`, `QuestionsSettings`, `ui/QuestionsTab`, `ui/QuestionsPromptTab`): every turn that gets its text (`MessagesTab.phraseHeard`) is checked by the chosen model with the last N phrases, the summary and the Q&A history; the reply's `<status>` (answer / wait / answered / none) decides; one request in flight, phrases meanwhile coalesce; "wait" + 3 s without a phrase → one re-check with `<silence>`; an answer whose question overlaps an earlier one ≥ 0.65 is not shown again. The built-in prompt is the design tested on GPT-6.1 Terra (see `QuestionPrompts`). Covered by `QuestionPromptsTest`.
- **Whisper hallucinations** (`utils/WhisperHallucinations`): video-subtitle lines are cut anywhere; "thank you" / "you" and the like drop an utterance only when it holds nothing else.
- **Shared rate limiters** (`utils/RateLimiters`): one limiter per model for translations, summaries and questions.
