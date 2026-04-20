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
relative_path: "src/main/kotlin/app/MainApp.kt"
depth: 0    # top-level symbols only (functions, classes, constants)
depth: 1    # include children (methods of classes)
```

### find_symbol

Find symbols by name path pattern. Use this instead of Grep for finding code entities.

```
name_path_pattern: "MainViewModel"                 # any symbol with this name
name_path_pattern: "MainViewModel/onMessage"       # method inside class (relative)
name_path_pattern: "/MainViewModel"                # exact full path (absolute)
relative_path: "src/main/kotlin/ui"                # restrict search scope (ALWAYS set this)
include_body: true                                 # get source code
include_info: true                                 # get docstring/signature
depth: 1                                           # retrieve children
max_matches: 5                                     # limit results
substring_matching: true                           # "View" matches "MainViewModel"
```

**Name path patterns:**
- `"myFunction"` — any symbol named myFunction
- `"MyClass/myMethod"` — myMethod inside MyClass (relative)
- `"/MyClass/myMethod"` — exact full path (absolute)

### find_referencing_symbols

Find all code locations referencing a symbol. Use instead of Grep for finding usages.

```
name_path: "MainViewModel"
relative_path: "src/main/kotlin/ui/MainViewModel.kt"  # file where symbol is DEFINED
```

### replace_symbol_body

Replace the entire definition of a symbol. **MUST read the symbol first** with `find_symbol(include_body=true)`.

```
name_path: "onMessage"
relative_path: "src/main/kotlin/ui/MainViewModel.kt"
body: "fun onMessage(text: String) {\n    ...\n}"
```

**IMPORTANT:** Body includes the `fun`/`class` line but does NOT include preceding comments, KDoc, annotations placed on separate lines, or imports.

### insert_before_symbol / insert_after_symbol

Insert new code before or after a symbol definition.

```
name_path: "onMessage"
relative_path: "src/main/kotlin/ui/MainViewModel.kt"
body: "\nfun newHelper(): String = \"hello\"\n"
```

Use cases:
- Add a function after an existing one: `insert_after_symbol`
- Add imports before the first symbol: `insert_before_symbol`
- Add a method to a class after the last method

### rename_symbol

LSP-powered rename across the entire codebase.

```
name_path: "onMessage"
relative_path: "src/main/kotlin/ui/MainViewModel.kt"
new_name: "handleMessage"
```

**WARNING:** Modifies files across the codebase. Verify scope before using.

### safe_delete_symbol

Delete a symbol only if no references exist. Returns reference list if still used.

```
name_path_pattern: "oldUnusedFunction"
relative_path: "src/main/kotlin/utils/Helpers.kt"
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

### `replace_symbol_body` on multi-line top-level constants is broken

When a top-level constant is assigned to a multi-line literal (dict/list/tuple/long string), Jedi reports `body_location` as the NAME line only, not the full literal. `replace_symbol_body` then inserts the new body BEFORE the old literal, producing invalid syntax like:

```python
X = {              # new body
    "a": 1,
} = {              # ← old literal is still there, syntax error
    "b": 2,
}
```

**Workarounds:**
- For multi-line constants, use `Write` to rewrite the whole file, OR
- Wrap the constant in a function and `replace_symbol_body` on the function, OR
- `safe_delete_symbol` first, then `insert_before_symbol` / `insert_after_symbol` to add it fresh.

Single-line constants (`MAX_HISTORY = 20`) work fine.

### `replace_symbol_body` does NOT include preceding docstrings/comments

If the original has a docstring above the `def`, it is outside the symbol body and will survive the replacement. Don't duplicate it in your new body.

### `insert_before_symbol` with imports

Imports are not symbols. To add `import` lines, use `insert_before_symbol` targeting the first top-level symbol (constant/function) in the file, and include the imports in the inserted body with a trailing blank line. Don't try to `find_symbol` an import.

## Project-specific notes

- Primary language: Kotlin (JVM desktop app, Gradle-built)
- Secondary language: Python (voice-recognition / TTS utilities under `python/`)
- Language backend: JetBrains (Serena MCP is launched with `--language-backend JetBrains`)
- Key Kotlin directories under `src/main/kotlin/`:
  - `app/` — application entry point
  - `ui/` — Compose Desktop UI and view models
  - `di/` — dependency injection
  - `repository/` — data access layer
  - `models/` — data classes
  - `mappers/`, `extensions/`, `utils/` — helpers
- Python directory: `python/` — standalone scripts for Whisper / Chatterbox / XTTS experimentation
- Non-code files (use standard tools): `build.gradle`, `settings.gradle`, `gradle.properties`, `requirements.txt`, `README.md`, `.gitignore`, files under `src/main/resources/`
- Binary / generated directories to stay out of: `build/`, `.gradle/`, `.kotlin/`, `.venv/`, `models/`, `cache/`
