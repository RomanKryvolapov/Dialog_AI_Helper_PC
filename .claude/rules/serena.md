# Use Serena MCP for all code work

Serena provides LSP-powered semantic code navigation and editing.
**Prefer it over Read/Grep/Edit/Glob for code files** — it works on symbols, not raw text, so it is more precise and cheaper than reading or scanning whole files.

## Serena instead of standard tools

| Task                             | Use (Serena)                                   | Instead of              |
| -------------------------------- | ---------------------------------------------- | ----------------------- |
| Explore a code file's API        | `get_symbols_overview`                         | reading the whole file  |
| Find a class / function / method | `find_symbol`                                  | grepping for names      |
| Read a specific symbol body      | `find_symbol(include_body=true)`               | `Read` with offset      |
| Find who references a symbol     | `find_referencing_symbols`                     | grepping for the name   |
| Edit a symbol body               | `replace_symbol_body`                          | `Edit`                  |
| Add code before/after a symbol   | `insert_before_symbol` / `insert_after_symbol` | `Edit`                  |
| Rename across the codebase       | `rename_symbol`                                | many `Edit` calls       |
| Delete unused code safely        | `safe_delete_symbol`                           | manual `Edit`           |
| Regex search in code             | `search_for_pattern`                           | `Grep`                  |
| Find files by name / mask        | `find_file`                                    | `Glob`                  |
| List a directory                 | `list_dir`                                     | `Bash ls`               |

Exact parameters for each tool are in the MCP tool schemas — no need to duplicate them here.

## When standard tools ARE allowed

`Read` / `Edit` / `Grep` / `Glob` / `Bash` are fine for:

- Non-code files: `.yaml`, `.yml`, `.json`, `.md`, `.txt`, `.toml`, `.gradle`, `.properties`, `.css`, `.xml`.
- Directory listing / file finding and git operations.
- When Serena returns an error for a specific call — fall back for that one operation.

They are also faster for simple reads, small edits, and plain text searches — use judgement.

## Workflow

1. **Understand first** — `get_symbols_overview` on a new code file before reading it.
2. **Read only what you need** — `find_symbol(include_body=true)` for a specific method, not the whole file.
3. **Check impact before editing** — `find_referencing_symbols` / `search_for_pattern`.
4. **Edit semantically** — `replace_symbol_body`, `insert_*_symbol`, `rename_symbol`; never raw `Edit` on a symbol body.

Serena also has a project memory system (`write_memory` / `read_memory` / `list_memories` …) for saving and recalling project knowledge across sessions.

## Checklist

- [ ] Code exploration and edits went through Serena symbol tools, not blind `Read`/`Grep`/`Edit`.
- [ ] Standard tools used only for non-code files, git, simple text ops, or after a Serena error.
- [ ] Impact checked (`find_referencing_symbols`) before renames or symbol-body replacements.
