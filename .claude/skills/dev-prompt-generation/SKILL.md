---
name: dev-prompt-generation
description: Prompts, Cursor Rules, and Agent Skills. When editing rules or skills, consult latest Cursor documentation at cursor.com/docs and migrate repo artifacts if the documented format changed. Structure, delimiters, XML, output format, few-shot, pseudocode. Use when drafting or reviewing any prompt, rule, or skill.
---

# Prompt, Cursor Rules and Skills generation (spec)

<context>
Rules and techniques for generating effective prompts and for generating rules (Cursor rules, spec files, MDC).
Methods are based on research and practical experience.
</context>

<task>
Apply these rules when creating prompts and when writing rules:
• Choose techniques to fit the task
• Use structure (delimiters, tags, headings)
• Control output format (schema, examples, self-check)
• Follow brevity and reformulation principles
</task>

<rules>
• Techniques by category: structure → data → rules → format
• Each technique includes examples
• Combine techniques for complex tasks
• Start with basics, add advanced as needed
</rules>

---

## Progressive disclosure (how skills load)

Cursor loads skills in **three stages**. Design `SKILL.md` and `references/` so agents spend tokens only on what they need.

| Level                | When it loads               | Rough token budget      | Contents                                         |
| -------------------- | --------------------------- | ----------------------- | ------------------------------------------------ |
| **1 — Metadata**     | Always (skill discovery)    | ~100                    | YAML frontmatter: `name`, `description`          |
| **2 — Instructions** | When the skill is activated | ~5,000                  | Body of `SKILL.md` (this file and peers)         |
| **3 — Resources**    | Only when explicitly needed | Only the opened file(s) | Files under `references/` linked from `SKILL.md` |

**Implications**

- Keep **`SKILL.md` as a hub** (~500 lines or fewer); put depth in **`references/`** (see **How to split a large skill into `references/`** below for file size, names, cut rules, and hub tables).
- Link from **`SKILL.md`** to **`references/`** with **one hop** only (no primary chains `references/a.md` → `references/b.md`).
- In **`SKILL.md`**, list topics with **explicit links** and a **Contents** column so the right file is obvious without opening every ref (Strapi-style index):

```markdown
## Topic index

- **Cursor docs & techniques** → [references/cursor-docs-rules-skills-technique-table.md](references/cursor-docs-rules-skills-technique-table.md)
- **Core principles (basics → size control)** → [references/core-principles-basics-language-brevity-through-size-control.md](references/core-principles-basics-language-brevity-through-size-control.md)
```

**Agent rule:** Read **only** the reference file(s) that match the user’s task. If the user asks about output schemas, open the output-format reference — not the whole set.

---

## How to split a large skill into `references/`

Use this when a **`SKILL.md` would exceed ~500 lines** or when a topic is large enough to load on its own (same rules apply to **workshop scripts**, **long rule packs**, etc.).

### 1. Size targets

| Layer                           | Target                                                                                                                                                                                          |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`SKILL.md`**                  | **≤ ~500 lines** — hub only: purpose, progressive disclosure, **index tables** linking to `references/`, short mandatory notes (e.g. “consult cursor.com/docs”), checklists pointing into refs. |
| **Each `references/*.md` file** | **~150 lines (±)** of body. If one topic is still too long, split into **two files with different semantic names**, not one oversized file.                                                     |

### 2. File names (must be semantic)

- **Pattern:** `short-topic-kebab-slug.md` or `{skill-theme}-{what-this-file-covers}.md`, all **English**, **kebab-case**, **stable** (do not rename lightly once linked).
- **Good:** `describing-rules-metglyph-symbolic-operators.md`, `output-format-self-check-verification-inot.md` — you can guess the content from the name.
- **Bad:** `part-01.md`, `chunk-2.md`, `ref-b.md` — the agent cannot choose the right file without opening it.

### 3. Safe cut boundaries (do not break structure)

Split **only** on boundaries that keep Markdown valid and readable:

- Prefer **after** a complete **`##` / `###` section**.
- Prefer **after** a **closed fenced code block** (the closing ` ``` ` line is included; the next file starts on a new section or paragraph).
- Do **not** split **inside** a fenced block, inside a single table row, or mid-sentence in a normative rule list.
- If a single `##` section is > ~150 lines, subdivide by **`###` subsections** into multiple reference files (each file name reflects that subsection).

### 4. Header every reference file: `## About this file`

At the **top** of each `references/*.md` (after any optional repo comment), use:

```markdown
## About this file

**What’s inside:** One or two sentences: topics, techniques, or section numbers covered.

**Hub:** [../SKILL.md](../SKILL.md)
```

Optional: a short **inner** `##` / `###` mini-TOC for that file only — do not duplicate the whole skill index here.

### 5. Hub index in `SKILL.md` (required)

- Use a **table** with at least **Contents** (what’s in the file) and **File** (link).
- **Contents** must be specific enough that the agent picks **one** file without opening all of them.
- **Every** file under `references/` that belongs to the skill must appear **once** in this index (no orphan refs).

### 6. Navigation rules

- **One hop:** `SKILL.md` → `references/foo.md`. Do **not** make `references/a.md` → `references/b.md` the primary navigation path; deep chains waste tokens and behave inconsistently.
- **No numbering as meaning:** do not use `part-01` / `part-02` as the **only** signal of order; order is optional and secondary to **topic**.

### 7. After splitting

- Remove or avoid leaving **duplicate monolith** copies of the same text next to the new files (single source of truth).
- If your repo tracks **`CLAUDE.md`** / **`dev-project.mdc`**, update them when the skill’s layout or discoverability changes.

---

## Cursor official documentation (mandatory) — summary

When **creating, editing, or reviewing** any Cursor **Rule** (`.mdc`) or **Agent Skill** (`SKILL.md`):

1. Load **current** official docs from **https://cursor.com/docs** (Rules, Agent Skills, Context); use web access so behavior matches the shipping product.
2. Treat official documentation as the **contract** for paths, frontmatter, activation (`alwaysApply`, `globs`, description-based selection), and optional folders such as `references/`.
3. **Resolve conflicts** in favor of official documentation; update this skill if it drifts.
4. **Migrate** repo rules/skills that no longer match documented structure in the same change set.
5. After substantive rule/skill work, sync **`CLAUDE.md`** and **`dev-project.mdc`** (and any other index your repo requires).

Full wording, rules/skills sections, and the **when to use which technique** table → [references/cursor-docs-rules-skills-technique-table.md](references/cursor-docs-rules-skills-technique-table.md).

---

## Reference index (read on demand)

Split large topics into **~150-line (±) files** with **descriptive names** (see **Progressive disclosure**). Open **only** the row that matches the task.

### Cursor docs & technique table (single file)

| Contents                                                                             | File                                                                                                             |
| ------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| Official docs rule, prompts in code, `.mdc`, `SKILL.md`, when to use which technique | [references/cursor-docs-rules-skills-technique-table.md](references/cursor-docs-rules-skills-technique-table.md) |

### Core principles

| Contents                                                                                                     | File                                                                                                                                                     |
| ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Core principle, language, brevity, reformulation, structure, wording, examples, parameters, **size control** | [references/core-principles-basics-language-brevity-through-size-control.md](references/core-principles-basics-language-brevity-through-size-control.md) |
| Single responsibility, organizing structure, delimiters/headings, markdown, **tables**                       | [references/core-principles-single-responsibility-organizing-and-tables.md](references/core-principles-single-responsibility-organizing-and-tables.md)   |
| Task block patterns, table-filter example, **token separation**, CAPS, quotes                                | [references/core-principles-task-markdown-tokens-caps.md](references/core-principles-task-markdown-tokens-caps.md)                                       |
| **ASCII frames**, glossary, visual markers / icons                                                           | [references/core-principles-ascii-frames-glossary-visual-icons.md](references/core-principles-ascii-frames-glossary-visual-icons.md)                     |

### Data presentation (XML, JSON, sources)

| Contents                                                                       | File                                                                                                                                                 |
| ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| XML tags, templates, universal XML prompt                                      | [references/data-presentation-xml-tags-and-universal-template.md](references/data-presentation-xml-tags-and-universal-template.md)                   |
| XML mistakes, namespaces, **source marking**, attribution, counting / chunking | [references/data-presentation-xml-mistakes-namespaces-sources-counting.md](references/data-presentation-xml-mistakes-namespaces-sources-counting.md) |
| **JSON** input structure, benchmarks / anchors                                 | [references/data-presentation-json-input-and-anchors.md](references/data-presentation-json-input-and-anchors.md)                                     |

### Describing rules (pseudocode, MetaGlyph, YAML, decorators)

| Contents                                                       | File                                                                                                                                     |
| -------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| **Pseudocode** `IF`/`ELSE`, `SWITCH`, function-style rules     | [references/describing-rules-pseudocode-if-switch-function-style.md](references/describing-rules-pseudocode-if-switch-function-style.md) |
| **MetaGlyph** symbolic operators, examples, ASCII alternatives | [references/describing-rules-metglyph-symbolic-operators.md](references/describing-rules-metglyph-symbolic-operators.md)                 |
| Rule **priority hierarchy**, **YAML** & **TOML** configs       | [references/describing-rules-hierarchy-yaml-toml.md](references/describing-rules-hierarchy-yaml-toml.md)                                 |
| **Prompt decorators** (`+++…`), stacking                       | [references/describing-rules-prompt-decorators.md](references/describing-rules-prompt-decorators.md)                                     |

### Output format (schema, few-shot, self-check)

| Contents                                                                           | File                                                                                                                                     |
| ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Placeholders, JSON schema, LangChain escaping, intermediate JSON                   | [references/output-format-placeholders-json-schema-langchain.md](references/output-format-placeholders-json-schema-langchain.md)         |
| TypeScript-style contract, comparison tables, ladder, **Schema / Examples / Task** | [references/output-format-typescript-schema-examples-task-ladder.md](references/output-format-typescript-schema-examples-task-ladder.md) |
| Code fences, **self-check**, verification-first, **INoT**                          | [references/output-format-self-check-verification-inot.md](references/output-format-self-check-verification-inot.md)                     |
| **Few-shot** patterns, delimiters, ordering, contrasting pairs                     | [references/output-format-few-shot-examples-and-order.md](references/output-format-few-shot-examples-and-order.md)                       |

### Universal template & checklists

| Contents                                                                     | File                                                                                                                           |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Full universal template (role/context/task/rules/output/examples/self-check) | [references/universal-prompt-template-full-example.md](references/universal-prompt-template-full-example.md)                   |
| Quick templates + **quality** + **verification** checklists                  | [references/universal-prompt-quick-templates-and-checklists.md](references/universal-prompt-quick-templates-and-checklists.md) |

---

## When to use this skill

- Drafting or reviewing **LLM prompts** (system, user, tool, RAG).
- Creating or editing **Cursor rules** (`.mdc`) or **agent skills** (`SKILL.md`).
- Choosing **structure, delimiters, data format, few-shot, and self-check** patterns for a task.

---

## Checklist (this skill hub)

- [ ] **Progressive disclosure:** `SKILL.md` stays lean; depth lives under `references/` with explicit links from this file.
- [ ] **Splitting (when adding a large skill):** Follow **How to split a large skill into `references/`** — semantic filenames, ~150 lines per ref, safe cut boundaries, `## About this file`, hub table with **Contents** column.
- [ ] **One hop:** Primary navigation is `SKILL.md` → `references/*.md`; avoid deep reference-only chains.
- [ ] **Reference size & naming:** Each `references/*.md` file stays **~150 lines (±)**; **filename + `## About this file`** explain the contents without reading the whole file.
- [ ] **Cursor docs:** Confirmed against **cursor.com/docs** before changing rule/skill conventions.
- [ ] **Repo sync:** After substantive edits, **`CLAUDE.md`** / **`dev-project.mdc`** updated if your process requires it.
- [ ] **Full checklists** for prompts and rules: [references/universal-prompt-quick-templates-and-checklists.md](references/universal-prompt-quick-templates-and-checklists.md); full template body: [references/universal-prompt-template-full-example.md](references/universal-prompt-template-full-example.md).
