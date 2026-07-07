---
name: dev-serena
description: MANDATORY Serena MCP tools for semantic code navigation, editing, and refactoring. MUST be used instead of Read/Grep/Edit/Glob for all code files.
allowed-tools: mcp__serena__get_symbols_overview mcp__serena__find_symbol mcp__serena__find_referencing_symbols mcp__serena__replace_symbol_body mcp__serena__insert_before_symbol mcp__serena__insert_after_symbol mcp__serena__rename_symbol mcp__serena__safe_delete_symbol mcp__serena__search_for_pattern mcp__serena__find_file mcp__serena__list_dir mcp__serena__list_memories mcp__serena__read_memory mcp__serena__write_memory mcp__serena__edit_memory mcp__serena__delete_memory mcp__serena__rename_memory mcp__serena__check_onboarding_performed mcp__serena__onboarding mcp__serena__initial_instructions
---

# Serena MCP — MANDATORY Semantic Code Tools

**CRITICAL: You MUST use Serena tools for ALL code-related tasks. Do NOT fall back to Read/Grep/Edit/Glob when Serena can handle it. This is not a suggestion — it is a hard requirement.**

Serena provides LSP-powered semantic code tools for Kotlin, Java, and C++ files in this project.

## MANDATORY replacement rules

**You MUST follow these rules. Violations are unacceptable.**

| Task                            | MUST use (Serena)                              | FORBIDDEN alternative      |
| ------------------------------- | ---------------------------------------------- | -------------------------- |
| Explore a new code file         | `get_symbols_overview`                         | ~~`Read` entire file~~     |
| Find a class/method/function    | `find_symbol`                                  | ~~`Grep` for names~~       |
| Read a method/function body     | `find_symbol(include_body=true)`               | ~~`Read` with offset~~     |
| Find who calls a symbol         | `find_referencing_symbols`                     | ~~`Grep` for symbol name~~ |
| Edit a method/function body     | `replace_symbol_body`                          | ~~`Edit`~~                 |
| Add code before/after a symbol  | `insert_before_symbol` / `insert_after_symbol` | ~~`Edit`~~                 |
| Rename a symbol across codebase | `rename_symbol`                                | ~~multiple `Edit` calls~~  |
| Delete unused code safely       | `safe_delete_symbol`                           | ~~manual `Edit`~~          |
| Search text patterns in code    | `search_for_pattern`                           | ~~`Grep`~~                 |
| Find files by name/mask         | `find_file`                                    | ~~`Glob`~~                 |
| List directory contents         | `list_dir`                                     | ~~`Bash ls`~~              |

## When standard tools ARE allowed (exceptions)

Standard tools (Read, Edit, Grep, Glob) are ONLY allowed for:

- **Non-code files**: XML layouts, resources, `.gradle`, `.toml`, `.properties`, `.json`, `.yml`, `.md`
- **Git operations**: `Bash` with git commands
- **ADB/shell commands**: device interaction
- **Serena failure**: if Serena returns an error, you may fall back to standard tools for that specific operation

## MANDATORY workflow

### Step 1: Understand before acting

- **ALWAYS** call `get_symbols_overview(relative_path, depth=1)` first when working with a code file
- This gives you class/method/property names without reading entire files
- NEVER `Read` an entire `.kt`, `.java`, or `.cpp` file to understand its structure

### Step 2: Read only what you need

- Use `find_symbol(name_path_pattern, include_body=true)` to read specific methods
- Use `find_symbol(name_path_pattern, depth=1)` to see class members
- NEVER use `Read` with offset/limit to read a method from a code file

### Step 3: Find before editing

- Use `find_symbol` to locate the symbol you want to edit
- Use `find_referencing_symbols` to check impact before changes
- Use `search_for_pattern` to find text patterns across the codebase

### Step 4: Edit semantically

- Use `replace_symbol_body` to modify methods/functions/classes
- Use `insert_before_symbol` / `insert_after_symbol` to add new code
- Use `rename_symbol` for renames across the codebase
- NEVER use `Edit` to modify method bodies in code files

## Tool reference

### get_symbols_overview

**ALWAYS call this first** when exploring any code file.

```
relative_path: "app/src/main/java/.../MyClass.kt"
depth: 0    # top-level symbols only
depth: 1    # include children (methods, properties of classes)
```

### find_symbol

Find symbols by name path pattern. Use this instead of Grep for finding code entities.

```
name_path_pattern: "MyClass/myMethod"   # relative path in symbol tree
relative_path: "app/src/main/java/..."  # restrict search scope (ALWAYS set this)
include_body: true                       # get source code
include_info: true                       # get docstring/signature
depth: 1                                 # retrieve children
max_matches: 5                           # limit results
substring_matching: true                 # "get" matches "getValue", "getData"
```

**Name path patterns:**

- `"myMethod"` — any symbol named myMethod
- `"MyClass/myMethod"` — myMethod inside MyClass (relative)
- `"/MyClass/myMethod"` — exact full path (absolute)
- `"MyClass/myMethod[0]"` — specific overload (Java)

### find_referencing_symbols

Find all code locations referencing a symbol. Use instead of Grep for finding usages.

```
name_path: "AiEngineUtil"
relative_path: "app/src/main/java/.../AiEngineUtil.kt"  # file where symbol is DEFINED
```

Note: C++ requires `compile_commands.json` (CMake with `CMAKE_EXPORT_COMPILE_COMMANDS ON`).

### replace_symbol_body

Replace the entire definition of a symbol. **MUST read the symbol first** with `find_symbol(include_body=true)`.

```
name_path: "MyClass/myMethod"
relative_path: "app/src/main/java/.../MyClass.kt"
body: "fun myMethod() {\n    // new implementation\n}"
```

**IMPORTANT:** Body includes the signature line but does NOT include preceding comments, docstrings, or imports.

### insert_before_symbol / insert_after_symbol

Insert new code before or after a symbol definition.

```
name_path: "MyClass/lastMethod"
relative_path: "app/src/main/java/.../MyClass.kt"
body: "\n    fun newMethod() {\n        // implementation\n    }"
```

Use cases:

- Add a method to a class: `insert_after_symbol` on the last method
- Add imports: `insert_before_symbol` on the first symbol in file
- Add a new class after an existing one

### rename_symbol

LSP-powered rename across the entire codebase.

```
name_path: "MyClass/oldName"
relative_path: "app/src/main/java/.../MyClass.kt"
new_name: "newName"
```

**WARNING:** Modifies files across the codebase. Verify scope before using.

### safe_delete_symbol

Delete a symbol only if no references exist. Returns reference list if still used.

```
name_path_pattern: "unusedMethod"
relative_path: "app/src/main/java/.../MyClass.kt"
```

**WARNING:** If it returns "OK", the code is already deleted. Use with caution.

### search_for_pattern

Regex search across files. Use for text patterns, not symbol names (use `find_symbol` for those).

```
substring_pattern: "TODO|FIXME"
relative_path: "app/src/main/java"          # scope (ALWAYS set this)
restrict_search_to_code_files: true          # only code files
paths_include_glob: "**/*.kt"                # filter by extension
paths_exclude_glob: "*test*"                 # exclude patterns
context_lines_before: 2
context_lines_after: 2
```

### find_file

Find files by name or mask. Use instead of Glob.

```
file_mask: "*.kt"           # supports * and ? wildcards
relative_path: "app/src"    # search scope
```

### list_dir

List directory contents. Use instead of Bash ls.

```
relative_path: "app/src/main/java"
recursive: false             # true for deep scan
skip_ignored_files: true     # respect .gitignore
```

### Memory tools

Serena's project memory system:

- `write_memory(memory_name, content)` — save knowledge (use `/` for topics: `"arch/di"`)
- `read_memory(memory_name)` — recall saved knowledge
- `edit_memory(memory_name, needle, repl, mode)` — edit (`"literal"` or `"regex"`)
- `list_memories(topic)` — list all or by topic
- `rename_memory(old_name, new_name)` — reorganize
- `delete_memory(memory_name)` — remove
