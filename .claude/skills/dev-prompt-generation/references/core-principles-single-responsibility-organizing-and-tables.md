## About this file

**What’s inside:** Single responsibility for rules/skills, organizing prompt structure, delimiters/headings, markdown in prompts, and tables (including “when to use tables”).

**Hub:** [../SKILL.md](../SKILL.md)

---

## Single responsibility for rules and skills

<rules>
Each rule or skill owns exactly one topic. If a canonical rule exists for a topic (e.g. `dev-backend-error-contract.mdc` for error schema, `dev-observability.mdc` for logging), other rules and skills must **reference** it, not duplicate its content.

Allowed in other files:
• A brief "see `<canonical-rule>.mdc`" reference (blockquote or inline parenthetical)
• A one-line summary that names the topic without restating specifics

Forbidden in other files:
• Listing specific fields, thresholds, patterns, or steps that the canonical rule already defines
• Rephrasing the same rules in different words ("shadow copy")
• Providing an alternative or extended version of the canonical content

When the canonical rule is missing something that another file needs:
→ add the missing content to the canonical rule
→ reference it from the other file

This prevents drift: one source of truth is updated, all consumers stay in sync.
</rules>

---

## Organizing prompt (and rules) structure

### Delimiters and headings

**When to apply:** Any prompt with multiple blocks (role, task, data, examples). Without explicit boundaries the model "glues" instructions and data — accuracy drops.

**Why it works:** Delimiters set clear boundaries between sections. Choice of delimiter between examples (comma, newline, `#`, `|`, etc.) can shift accuracy by ±23% on benchmarks like MMLU; stating in the prompt which delimiter is used stabilizes results. Section delimiters typically give ~16–24% accuracy gain; adding a one-line meta-instruction ("Examples are separated by ===") can move accuracy from 30–80% to 70–80%. Study: [A single character can make or break your LLM evals](https://arxiv.org/abs/2510.05152).

#### Reliable delimiters

| Delimiter | Purpose                     | Example                                  |
| --------- | --------------------------- | ---------------------------------------- |
| `---`     | between logical blocks      | Role --- Task --- Rules                  |
| `===`     | between examples (few-shot) | === Example 1 === Example 2 ===          |
| `###`     | section with heading        | ### Processing rules                     |
| `***`     | semantic break              | End of instructions \*\*\* Start of data |
| `│`       | split for counting          | System-2 Counting                        |
| `◆◆◆`     | system instructions         | Prompt injection guard                   |

#### Avoid these delimiters

| Delimiter        | Problem                            |
| ---------------- | ---------------------------------- |
| `~~~~`           | confused with markdown code blocks |
| `____`           | visually merges, weak signal       |
| `....`           | reads as continuation of text      |
| `////`           | confused with code comments        |
| Blank lines only | weak signal, may be ignored        |

#### Explicit delimiter rule

State delimiter purpose explicitly: "(=== separates examples)" → stabilizes accuracy 30–80% → 70–80%

<example>
## Data format in this prompt

- Examples are separated by "==="
- Sections are separated by "---"
- User data is wrapped in triple quotes """

---

## Examples

===
Input: "Great product!"
Output: {"sentiment": "positive"}
===
Input: "Terrible quality"
Output: {"sentiment": "negative"}
===

---

## Task

Process the user data.
</example>

#### Headings and hierarchy

| Level | Syntax           | Purpose                 | Per prompt |
| ----- | ---------------- | ----------------------- | ---------- |
| `#`   | # Prompt name    | main topic / title      | 0–1        |
| `##`  | ## Role, ## Task | main sections           | 3–7        |
| `###` | ### Critical     | sub-sections in a block | as needed  |

#### Arrow → for examples

Arrow `→` shows transformation "before → after":

| Context           | Example                                 |
| ----------------- | --------------------------------------- |
| Few-shot examples | `"payment not working" → Billing, high` |
| Priority rules    | `premium → always high`                 |
| Transformation    | `Raw text → Structured prompt`          |
| Pseudocode        | `IF condition: → action`                |

### Markdown formatting

**When to apply:** Any prompt; models are trained on markdown. Use headings for hierarchy and navigation.

**Why it works:** Structured prompts by importance can let weaker models approach stronger ones; in experiments formatting by importance is comparable to content.

| Element     | Syntax               | Result   | When to use                 |
| ----------- | -------------------- | -------- | --------------------------- |
| **Bold**    | `**text**`           | **text** | key terms, emphasis         |
| _Italic_    | `*text*`             | _text_   | notes, names                |
| Inline code | `` `code` ``         | `code`   | variables, commands, values |
| List        | `- item` or `• item` | • item   | requirements list           |
| Numbering   | `1. step`            | 1. step  | sequence of steps           |
| Quote       | `> text`             | > text   | examples, excerpts          |

### Tables for structured data

**When to apply:** Comparing objects by parameters; filtering by several conditions. Not for ordered steps (use a list).

**Why it works:** Row = one object, column = one property; tables improve localization of relevant information by the model. Table format gives on average ~+40% accuracy vs plain text, JSON, or graphs on factual data and queries. Study: [Talking with Tables for Better LLM Factual Data Interactions](https://arxiv.org/abs/2412.17189).

"Object — properties" data → use tables instead of lists.

#### Example: list vs table

<example type="bad">
We have three services. Netflix is $16, 4K HDR, has family plan. 
Hulu is $12, 1080p, has family plan. 
Disney+ is $18, 4K HDR, no family plan.
</example>

<example type="good">
| Service  | Price | Quality | Family |
|----------|-------|--------|--------|
| Netflix  | $16   | 4K HDR | Yes    |
| Hulu     | $12   | 1080p  | Yes    |
| Disney+  | $18   | 4K HDR | No     |
</example>

#### When to use tables

| Situation                       | Format | Why                             |
| ------------------------------- | ------ | ------------------------------- |
| Comparing objects by parameters | Table  | row = object, column = property |
| Filtering by several conditions | Table  | exact condition matching        |
| Sequence of steps               | List   | order matters, not parameters   |
| Unstructured text               | Prose  | no clear structure              |
