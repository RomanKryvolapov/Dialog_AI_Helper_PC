---
name: serena
description: MANDATORY Serena MCP tools for semantic code navigation, editing, and refactoring. MUST be used instead of Read/Edit for all Kotlin and Python code files.
allowed-tools: mcp__serena__get_symbols_overview mcp__serena__find_symbol mcp__serena__find_referencing_symbols mcp__serena__replace_symbol_body mcp__serena__insert_before_symbol mcp__serena__insert_after_symbol mcp__serena__rename_symbol mcp__serena__safe_delete_symbol mcp__serena__list_memories mcp__serena__read_memory mcp__serena__write_memory mcp__serena__edit_memory mcp__serena__delete_memory mcp__serena__rename_memory mcp__serena__check_onboarding_performed mcp__serena__onboarding mcp__serena__initial_instructions
---

# Serena MCP — MANDATORY Semantic Code Tools

**CRITICAL: You MUST use Serena tools for ALL Kotlin and Python code tasks. Do NOT fall back to Read/Edit when Serena can handle it. This is not a suggestion — it is a hard requirement.**

Serena provides semantic code tools for all `.kt` and `.py` files in this project (Kotlin is the primary language, Python is used for voice-recognition/TTS utilities under `python/`).

## MANDATORY replacement rules

**You MUST follow these rules. Violations are unacceptable.**

| Task | MUST use (Serena) | FORBIDDEN alternative |
|------|-------------------|----------------------|
| Explore a new code file | `get_symbols_overview` | ~~`Read` entire file~~ |
| Find a class/method/function | `find_symbol` | ~~`Grep` for names~~ |
| Read a method/function body | `find_symbol(include_body=true)` | ~~`Read` with offset~~ |
| Find who calls a symbol | `find_referencing_symbols` | ~~`Grep` for symbol name~~ |
| Edit a method/function body | `replace_symbol_body` | ~~`Edit`~~ |
| Add code before/after a symbol | `insert_before_symbol` / `insert_after_symbol` | ~~`Edit`~~ |
| Rename a symbol across codebase | `rename_symbol` | ~~multiple `Edit` calls~~ |
| Delete unused code safely | `safe_delete_symbol` | ~~manual `Edit`~~ |

## When standard tools ARE allowed (exceptions)

Standard tools (Read, Edit, Grep, Glob, Bash) are ONLY allowed for:
- **Non-code files**: `.yaml`, `.yml`, `.json`, `.md`, `.txt`, `.env`, `.toml`, `.sql`, `.csv`
- **File/text search**: `Grep` and `Glob` — the `claude-code` context excludes `search_for_pattern`, `find_file`, `list_dir`
- **Directory listing**: `Bash ls` or `Glob`
- **Git operations**: `Bash` with git commands
- **Serena failure**: if Serena returns an error, you may fall back to standard tools for that specific operation

## MANDATORY workflow

### Step 1: Understand before acting
- **ALWAYS** call `get_symbols_overview(relative_path, depth=1)` first when working with a Kotlin or Python file
- This gives you class/method/function names without reading entire files
- NEVER `Read` an entire `.kt` or `.py` file to understand its structure

### Step 2: Read only what you need
- Use `find_symbol(name_path_pattern, include_body=true)` to read specific functions/methods
- Use `find_symbol(name_path_pattern, depth=1)` to see class members
- NEVER use `Read` with offset/limit to read a function from a `.kt` or `.py` file

### Step 3: Find before editing
- Use `find_symbol` to locate the symbol you want to edit
- Use `find_referencing_symbols` to check impact before changes
- For text pattern search: use `Grep` (Serena `search_for_pattern` is excluded in `claude-code` context)

### Step 4: Edit semantically
- Use `replace_symbol_body` to modify functions/methods/classes
- Use `insert_before_symbol` / `insert_after_symbol` to add new code
- Use `rename_symbol` for renames across the codebase
- NEVER use `Edit` to modify function bodies in `.kt` or `.py` files

## Tool reference

### get_symbols_overview

**ALWAYS call this first** when exploring any Kotlin or Python file.

```
relative_path: "src/main/kotlin/app/DialogApplication.kt"
depth: 0    # top-level symbols only (classes, interfaces, top-level functions/properties)
depth: 1    # include children (methods and properties inside classes)
```

Kotlin symbol kinds returned: `Class`, `Interface`, `Object` (for `companion object` and `object` declarations), `Constructor`, `Method`, `Property`, `Constant` (for top-level `const val`), `Function` (top-level functions).

### find_symbol

Find symbols by name path pattern. Use this instead of Grep for finding code entities.

```
name_path_pattern: "DialogApplication"                   # any symbol with this name
name_path_pattern: "DialogApplication/start"             # method inside class (relative)
name_path_pattern: "/DialogApplication"                  # exact full path (absolute)
relative_path: "src/main/kotlin/app"                     # restrict search scope (ALWAYS set this)
include_body: true                                       # get source code
include_info: true                                       # get signature/KDoc (Kotlin) or docstring (Python)
depth: 1                                                 # retrieve children
max_matches: 5                                           # limit results
substring_matching: true                                 # "Dialog" matches "DialogApplication"
```

**Name path patterns:**
- `"startListening"` — any symbol named startListening
- `"MessagesTab/startListening"` — startListening inside MessagesTab (relative)
- `"/MessagesTab/startListening"` — exact full path (absolute)

**Multiple matches are common.** E.g., `find_symbol("main")` returns one hit per `*_util.py` and `*_test.py` plus `Main.kt`. Always pass `relative_path` to narrow the scope, or use `max_matches=1` when you want exactly one.

### find_referencing_symbols

Find all code locations referencing a symbol. Use instead of Grep for finding usages.

```
name_path: "DialogApplication"
relative_path: "src/main/kotlin/app/DialogApplication.kt"  # file where symbol is DEFINED
```

**Kotlin note:** references to an *interface method* may return an empty list — the JetBrains backend tracks call sites against concrete symbols. To find who calls a method in practice, look up the *implementation* (e.g., `CloudRepositoryImpl/generateAnswerByGoogleAI` rather than `CloudRepository/generateAnswerByGoogleAI`).

**Path note:** results from the JetBrains backend on Windows often contain backslashes (`src\\main\\kotlin\\...`). This is cosmetic — forward slashes always work as input to any Serena tool.

### replace_symbol_body

Replace the entire definition of a symbol. **MUST read the symbol first** with `find_symbol(include_body=true)`.

```
name_path: "DialogApplication/start"
relative_path: "src/main/kotlin/app/DialogApplication.kt"
body: "override fun start(primaryStage: Stage) {\n    ...\n}"
```

**IMPORTANT:** Body includes the `fun`/`class` line but does NOT include preceding comments, KDoc, or annotations placed on separate lines above the declaration. Re-reading those with `find_symbol(include_body=true)` shows what's inside the body vs. outside.

### insert_before_symbol / insert_after_symbol

Insert new code before or after a symbol definition.

```
name_path: "DialogApplication/start"
relative_path: "src/main/kotlin/app/DialogApplication.kt"
body: "\nprivate fun newHelper(): String = \"hello\"\n"
```

Use cases:
- Add a function after an existing one: `insert_after_symbol`
- Add imports before the first symbol: `insert_before_symbol`
- Add a method to a class after the last method

### rename_symbol

LSP-powered rename across the entire codebase.

```
name_path: "DialogApplication/start"
relative_path: "src/main/kotlin/app/DialogApplication.kt"
new_name: "launch"
```

**WARNING:** Modifies files across the codebase. Verify scope before using.

### safe_delete_symbol

Delete a symbol only if no references exist. Returns reference list if still used.

```
name_path_pattern: "oldUnusedFunction"
relative_path: "src/main/kotlin/utils/CommonExtensions.kt"
```

**WARNING:** If it returns "OK", the code is already deleted.

## Memory tools

Serena's project memory system (separate from Claude Code memory):

- `write_memory(memory_name, content)` — save knowledge (use `/` for topics: `"arch/agent_flow"`)
- `read_memory(memory_name)` — recall saved knowledge
- `edit_memory(memory_name, needle, repl, mode)` — edit (`"literal"` or `"regex"`)
- `list_memories(topic)` — list all or by topic
- `rename_memory(old_name, new_name)` — reorganize
- `delete_memory(memory_name)` — remove

## Setup / onboarding tools

### check_onboarding_performed

Check if the project has been onboarded. Call at the start of a conversation before working on the project.

```
# No parameters
```

### onboarding

Run project onboarding if `check_onboarding_performed` shows it hasn't been done. Creates project structure overview and essential info. Call at most once per conversation.

```
# No parameters
```

### initial_instructions

Load the Serena Instructions Manual. Call once if you haven't read it in this conversation. Returns usage guidelines, available tools, active modes, and project info.

```
# No parameters
```

## Gotchas (learned the hard way)

### Parameter naming is inconsistent across tools
- `find_symbol` uses `name_path_pattern`
- `replace_symbol_body`, `insert_before_symbol`, `insert_after_symbol`, `find_referencing_symbols`, `rename_symbol` use `name_path`
- `safe_delete_symbol` uses `name_path_pattern`

Double-check the parameter name before each call. Calling `find_symbol` with `name_path=...` fails with a pydantic validation error.

### `replace_symbol_body` on multi-line top-level constants (Python/Jedi only)

This gotcha is **Python-Jedi specific**; the JetBrains backend handles Kotlin `const val` correctly.

In Python, when a top-level constant is assigned to a multi-line literal (dict/list/tuple/long string), Jedi reports `body_location` as the NAME line only, not the full literal. `replace_symbol_body` then inserts the new body BEFORE the old literal, producing invalid syntax like:

```python
X = {              # new body
    "a": 1,
} = {              # ← old literal is still there, syntax error
    "b": 2,
}
```

**Workarounds:**
- For multi-line Python constants, use `Write` to rewrite the whole file, OR
- Wrap the constant in a function and `replace_symbol_body` on the function, OR
- `safe_delete_symbol` first, then `insert_before_symbol` / `insert_after_symbol` to add it fresh.

Single-line constants (`MAX_HISTORY = 20`) work fine in both languages.

### `replace_symbol_body` does NOT include preceding docstrings/KDoc/annotations

If the original has a docstring above a Python `def`, or KDoc / separate-line annotation above a Kotlin `fun`/`class`, those lines sit *outside* the symbol body and will survive the replacement. Don't duplicate them in your new body.

### `insert_before_symbol` with imports

Imports are not symbols. To add `import` lines, use `insert_before_symbol` targeting the first top-level symbol (constant/function/class) in the file, and include the imports in the inserted body with a trailing blank line. Don't try to `find_symbol` an import.

### `find_symbol` needs enough context

Calling `find_symbol("main")` with no `relative_path` and no `max_matches` returns matches across every `*_util.py`, `*_test.py`, and `Main.kt`. Always scope: `relative_path="python/whisper_util.py"` or `relative_path="src/main/kotlin/app"`. Use `substring_matching=true` sparingly — Kotlin class names (`MessagesTab`, `SettingsTab`, `PromptTab`, `ConsoleTab`) collide easily.

### Companion objects and Kotlin `object`

Singletons declared as `companion object { ... }` or `object Foo { ... }` show up with kind `Object` in `get_symbols_overview`. To reach a member, use `ClassName/Companion/member` or `Foo/member` as the name path.

## Project-specific notes

- Primary language: **Kotlin** (JVM 24, Gradle, `application` plugin, main class `app.MainKt`)
- UI framework: **JavaFX** (not Compose Desktop) — `Stage`, `Scene`, `TabPane`, `.css` stylesheets from `src/main/resources/styles/`
- Secondary language: Python — standalone scripts under `python/` (Whisper / Chatterbox / XTTS experiments, invoked as subprocesses from Kotlin)
- Serena backend: JetBrains (`claude mcp list` shows `--language-backend JetBrains`)
- Key Kotlin directories under `src/main/kotlin/`:
  - `app/` — `Main.kt` entry, `DialogApplication.kt` JavaFX application
  - `ui/` — tab screens: `MessagesTab.kt`, `PromptTab.kt`, `SettingsTab.kt`, `ConsoleTab.kt` (+ `base/`)
  - `di/` — Koin modules: `AllModules.kt`, `RepositoryModule.kt`, `UiModule.kt`, `UtilsModule.kt`, `MappersModule.kt`
  - `repository/` — `CloudRepository`, `LocalNetworkRepository`, `PreferencesRepository` (+ impls)
  - `models/common`, `models/domain`, `models/network/{googleai,googletranslate,ollama,openai}`
  - `mappers/{base,domain,network}`
  - `utils/` — `Assistant.kt`, `AudioChunker.kt`, `ChatLogger.kt`, `ChatterboxUtil.kt`, `VoskVoiceRecognizer.kt`, `WhisperUtil.kt`, `XttsUtil.kt` (+ impls)
  - `extensions/` — `CommonExtensions.kt`, `ViewExtensions.kt`
  - `Consts.kt` — colours, prompts, default `ApplicationInfo`, coroutine scopes
- Python directory: `python/` — `whisper_util.py`, `chatterbox_util.py`, `xtts_util.py` (plus `_test.py` counterparts), each exposes a `main()` that reads CLI args
- Libraries in play (helpful when reading code): Koin (DI), Ktor client (HTTP), Moshi + kotlinx.serialization (JSON), langchain4j (Gemini / OpenAI / Ollama), Vosk (voice recognition), Logback (logging)
- Non-code files (use standard tools): `build.gradle`, `settings.gradle`, `gradle.properties`, `requirements.txt`, `README.md`, `.gitignore`, files under `src/main/resources/`
- Stay out of: `build/`, `.gradle/`, `.kotlin/`, `.venv/`, `.idea/`, `models/`, `cache/`
