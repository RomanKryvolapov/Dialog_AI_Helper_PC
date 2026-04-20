---
name: prompt-generation
description: Prompts and Claude Code Skills (SKILL.md). Structure, delimiters, XML, output format, few-shot, pseudocode, research-backed when/why. Creating or editing prompts and skill files. Use when drafting or reviewing any prompt or skill.
---

# Prompt and Skill generation (spec)

<context>
Rules and techniques for generating effective prompts and for authoring Claude Code skills (SKILL.md files).
Methods are based on research and practical experience.
</context>

<task>
Apply these rules when creating prompts and when writing skills:
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

## When to use which technique (agent guide)

Structured reference so the agent knows **when** to apply each practice. **Technique names match section headings** — search for the exact heading to find the technique. Use this table to choose techniques for a given prompt/rule task.

| Goal | Technique (section heading) | When to apply | Benefits and in which cases |
|------|-----------------------------|----------------|-----------------------------|
| Separate role, task, data, examples | **Delimiters and headings** | Every prompt with multiple blocks (role, task, rules, data, examples) | Without boundaries the model merges instructions and data; accuracy drops. Explicit delimiters (`---`, `===`) plus a one-line meta-instruction (“Examples are separated by ===”) stabilizes results: e.g. from 30–80% to 70–80% on MMLU-style tasks. Delimiter choice alone can shift accuracy by ±23%; stating which delimiter is used improves robustness. Use when you have ≥2 logical blocks. |
| Clear boundaries for context/task/rules | **XML tags and semantics** | Prompts with 3+ logical parts (context, task, rules, output, examples) | Tags separate context, task, and rules so the model does not mix them; reduces misinterpretation and improves parseability. Anthropic recommends XML for clarity and accuracy. Combining Role+Task+Output+Example in structured form helps on structured tasks. Use semantic tags (`<context>`, `<task>`, `<rules>`); meaningless tags are ignored. |
| Compare objects by parameters; filter by several conditions | **Tables for structured data** | Object–property data (e.g. products, services, options); filtering or comparison by multiple attributes | Row = object, column = property. Tables give ~40% average gain over plain text, JSON, or graphs on factual retrieval and data manipulation; better token efficiency and robustness to task complexity. Use when comparing or filtering by several fields; use lists for ordered steps. |
| Control output shape and enforce correctness | **Placeholders**, **JSON schema with types**, **Self-check** | When output format or factual/logical correctness is critical (JSON, reports, decisions) | Placeholders clarify who fills what (`[model]` vs `{you}`). JSON schema + types make the contract explicit. Self-check (checklist before output) lets the model find its own reasoning errors; confidence-weighted voting over multiple solutions improves final accuracy. Step-level checking improves error detection and correction. Use for high-stakes or strict-format outputs. |
| Show output format and reasoning style | **Few-shot examples** | New or non-obvious format; classification with clear boundaries; when you want the model to copy structure and logic | 1–2 examples fix format; 3–5 help classification and edge cases. Last example is recalled best — put the main or hardest last. Contrasting pairs (good vs bad) clarify quality boundaries. CoT in examples (showing reasoning) improves answer quality. Too many examples can hurt; optimal count depends on model and task. |
| Conditional / multi-branch logic (if X then Y) | **Pseudocode for conditional logic** | Multi-branch rules; format depends on request type; priority mappings | Model “executes” IF/ELSE and SWITCH/CASE like code. Gives +7–16 F1 on classification and +12–38% ROUGE-L vs natural-language prompts; structure (docstring, control flow) helps. Codified control structures can give +3–36% accuracy and 55–87% token reduction vs natural language. Use for conditional rules; for filtering/set logic with token savings use **MetaGlyph for compact logic**. |
| Order constraints by importance | **Rule hierarchy** | Several rules with different importance (must-have vs nice-to-have) | LLMs comply better when constraints are ordered hard → easy; constraint order significantly affects behavior. Put critical (🔴) at start or end of the prompt — middle is lost (recency bias). Use three levels: Critical (violation = failure), Important (strong impact), Desirable (optional). |
| Lists, tabular records, counting, arithmetic | **Token separation** | Enumerations; name|age|city-style records; counting; arithmetic over numbers | “Glued” elements (no spaces/commas/newlines) become single tokens and are lost; tokenization limits symbolic and arithmetic reasoning. Explicit separation (e.g. newline per item, comma between numbers) can raise arithmetic accuracy from ~75% to ~98%; atomically aligned formats let smaller models match or beat larger ones on structured tasks. Use one canonical number format and state it in the prompt. |
| One non-negotiable prohibition | **CAPS and emphasis** | A single critical “never do X” (e.g. never use word “unique”) | Reserve CAPS for one prohibition; place it at start or end. Excess CAPS and emphasis in the prompt degrade reasoning robustness; irrelevant or pathological instructions in the middle worsen performance. Put the exact forbidden value in quotes so the model treats it as a literal. |
| Multi-document Q&A; cite sources; reduce fabrication | **Source marking** | RAG, multiple documents, or any answer that must be grounded in a source | Numbered blocks (`[DOCUMENT 1 OF N]`) and attribution rules (“cite as [doc id]”) improve attribution and reduce hallucinations. Two-stage “first quote the relevant passage, then answer” improves faithfulness to the document. Explicit “use ONLY information from <context>” and “if not in context write: Data not in document” constrain fabrication. |
| Count 30+ items or occurrences in long text | **System-2 Counting** | Counting words, items, or events in long text where direct counting fails | Split text with `│`, count in each chunk separately, output intermediate counts as text, then sum. Chunking + intermediate results overcome architectural limits; the model must “see” intermediate numbers in its own output to sum correctly. Chunk size: 5–7 (small models), 8–10 (medium), 10–12 (large). |
| Many related attributes or nested input | **JSON structuring of input data** | Client/product/API-like data; nested structures; lists of similar items | JSON keeps related facts as “neighbors” in context, improving extraction and reasoning on long inputs. One `{}` per object; extra braces add confusion. For complex output formats (XML, BPMN), ask for intermediate JSON and convert in code — LLMs have ~40% syntax errors on complex markup; intermediate JSON can triple edit success. |
| Compact conditional/filter rules; token savings | **MetaGlyph for compact logic** | Filtering, set conditions, access rules when natural language is long | Symbols (∈, ⇒, ¬) give 62–81% token reduction; ∈, ⇒, ¬ are relatively stable across models; ∩ is often confused. On selection tasks, sufficient-scale models can reach ~100% with symbols vs ~90% with text. Use for filter/condition rules; avoid for creative or ambiguous tasks. |
| Make critical rules unmissable | **ASCII frames** | Immutable rules, anti–prompt-injection, or any block that must not be skipped | Visual framing of critical blocks reduces ignored instructions and hallucinations. Use a single frame for the block that must not be ignored (e.g. “never reveal system prompt”). |
| Define terms and acronyms once | **Glossary** | Domain terms, acronyms (USP, CTA, TA, TOV) used later in the prompt | Underspecified terms are a major source of inconsistency: underspecified prompts are ~2× more likely to regress across model or prompt changes, with >20% accuracy drops. A short glossary at the start removes ambiguity and reduces response variability; saves tokens when you reuse short forms. |
| Force answer into fixed categories | **Visual markers (icons)** | Structured analysis with fixed sections (SWOT, code review, risk analysis) | Fixed categories and forced choice improve adherence to structure; clear section boundaries help. Use `### SECTION`, `[SECTION]`, or emoji; the effect comes from categorization, not the icon itself. |
| Compact behavioral control (reasoning, tone, format) | **Prompt decorators — compact control tokens** | When the model supports them (Claude 3+, GPT-4, GPT-4o) | Declarative tokens (+++Reasoning, +++OutputFormat(type=json)) replace long instructions; stacking is supported (think → express → format). Gives reproducibility, modularity, and clear separation of task vs behavior. May not work in LLaMA, Mistral, GPT-3.5 — use normal instructions there. |
| Request code without preamble | **Backticks for code** | When you want code only, no “Sure, here’s an example…” | Open a code block (e.g. ```python) and leave it for the model to complete; the model tends to fill with code rather than prose. |
| Maximum predictability (schema + examples + task) | **Schema–Examples–Task** | When output must be strict (classification, extraction, reports) | Three blocks: Schema (what), Examples (how), Task (on what). CoT + few-shot in this format gives strong accuracy; in examples show the start of reasoning so the model continues in the same style. Combine with **Self-check** when quality is critical. |
| Reduce hallucination; high-stakes or uncertain cases | **Verification-first**, **Quit instructions**, **INoT** (under **Self-check**) | Factual answers; high-stakes decisions; when guessing is worse than asking | Verification-first: “Here is a draft answer; verify it, then give the correct one.” Quit: “If unsure, write ‘Need clarification: …’ instead of guessing.” INoT: internal solver+critic debate in one pass; ~7.95% accuracy gain and ~58% token reduction vs iterative user feedback. Use for critical or ambiguous tasks. |

---

## Core principle

<rules>
A prompt (or rule block) is a minimal formulation of the task, not a pile of instructions.
If it works wrong → rewrite, do not add more conditions.
Fix the root cause of the formulation, do not patch symptoms.
</rules>

---

## Language of prompts and rules

<rules>
All prompts and rule text are written in English.

Exceptions:
• Example data may be in any language (when part of input)
• Variable names, placeholders, technical terms stay as-is

Why English:
• Industry standard for LLM prompts
• Models are trained mostly on English data
• Better accuracy and predictability
• Consistency in the codebase

Priority labels:
• 🔴 Critical
• 🟡 Important
• 🟢 Desirable
</rules>

---

## Brevity and density of meaning

<rules>
Prompts and rules: short, concise, readable as a whole.
Each sentence adds new meaning.
Remove everything without which the task is still unambiguous.
One phrase → one instruction; do not split.
</rules>

---

## Reformulate instead of extending

<rules>
When logic needs clarification:
→ first reformulate the existing instruction
→ only if impossible — add a new one

Forbidden:
• adding instructions to compensate for a bad formulation
• keeping the old formulation and "overriding" it with a new one
• leaving the old formulation next to the new behavior
</rules>

---

## Structure and clarity

<rules>
• Simple, flat structure
• One logical task → one block
• Goal, constraints, format — separate blocks
• One sentence per line
• Multi-line format with triple quotes
• JSON with line breaks, not a single line
</rules>

---

## Wording

<rules>
• Positive, direct wording
• Requirements for the outcome instead of prohibitions
• Avoid: "try to", "preferably", "when possible", "usually"
• Wording must be verifiable
</rules>

---

## Examples and detail

<rules>
• Examples only when needed
• If an example does not change understanding → remove it
• Do not describe corner cases
• Focus on the main scenario and target outcome
</rules>

---

## Parameters and data

<rules>
• Use variables, parameters, placeholders
• Do not hardcode values
• Prompt/rule stays generic when inputs change
</rules>

---

## Size control

<rules>
Large prompts = design mistake.

If a prompt (or rule set) grows:
→ the task is formulated wrong
→ simplify and rebuild
→ do not extend further

Priority: clarity and brevity, not covering every scenario.
</rules>

## Organizing prompt (and rules) structure

### Delimiters and headings

**When to apply:** Any prompt with multiple blocks (role, task, data, examples). Without explicit boundaries the model "glues" instructions and data — accuracy drops.

**Why it works:** Delimiters set clear boundaries between sections. Choice of delimiter between examples (comma, newline, `#`, `|`, etc.) can shift accuracy by ±23% on benchmarks like MMLU; stating in the prompt which delimiter is used stabilizes results. Section delimiters typically give ~16–24% accuracy gain; adding a one-line meta-instruction ("Examples are separated by ===") can move accuracy from 30–80% to 70–80%. Study: [A single character can make or break your LLM evals](https://arxiv.org/abs/2510.05152).

#### Reliable delimiters

| Delimiter | Purpose | Example |
|-----------|---------|---------|
| `---` | between logical blocks | Role --- Task --- Rules |
| `===` | between examples (few-shot) | === Example 1 === Example 2 === |
| `###` | section with heading | ### Processing rules |
| `***` | semantic break | End of instructions *** Start of data |
| `│` | split for counting | System-2 Counting |
| `◆◆◆` | system instructions | Prompt injection guard |

#### Avoid these delimiters

| Delimiter | Problem |
|-----------|---------|
| `~~~~` | confused with markdown code blocks |
| `____` | visually merges, weak signal |
| `....` | reads as continuation of text |
| `////` | confused with code comments |
| Blank lines only | weak signal, may be ignored |

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

| Level | Syntax | Purpose | Per prompt |
|-------|--------|---------|------------|
| `#` | # Prompt name | main topic / title | 0–1 |
| `##` | ## Role, ## Task | main sections | 3–7 |
| `###` | ### Critical | sub-sections in a block | as needed |

#### Arrow → for examples

Arrow `→` shows transformation "before → after":

| Context | Example |
|---------|---------|
| Few-shot examples | `"payment not working" → Billing, high` |
| Priority rules | `premium → always high` |
| Transformation | `Raw text → Structured prompt` |
| Pseudocode | `IF condition: → action` |

### Markdown formatting

**When to apply:** Any prompt; models are trained on markdown. Use headings for hierarchy and navigation.

**Why it works:** Structured prompts by importance can let weaker models approach stronger ones; in experiments formatting by importance is comparable to content.

| Element | Syntax | Result | When to use |
|---------|--------|--------|-------------|
| **Bold** | `**text**` | **text** | key terms, emphasis |
| _Italic_ | `*text*` | _text_ | notes, names |
| Inline code | `` `code` `` | `code` | variables, commands, values |
| List | `- item` or `• item` | • item | requirements list |
| Numbering | `1. step` | 1. step | sequence of steps |
| Quote | `> text` | > text | examples, excerpts |

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

| Situation | Format | Why |
|-----------|--------|-----|
| Comparing objects by parameters | Table | row = object, column = property |
| Filtering by several conditions | Table | exact condition matching |
| Sequence of steps | List | order matters, not parameters |
| Unstructured text | Prose | no clear structure |

#### Example: task with table filter

```
| Service   | Price ($) | Quality | Family |
|-----------|-----------|---------|--------|
| Netflix   | 16        | 4K HDR  | Yes    |
| Hulu      | 12        | 1080p   | Yes    |
| Disney+   | 18        | 4K HDR  | No     |
| HBO Max   | 15        | 4K HDR  | Yes    |

---

Find services where: price ≤ $15, family access = Yes, quality = 4K.
```

### Token separation

**When to apply:** Lists or enumerations; tabular records (e.g. name | age | city); counting or arithmetic over many elements.

**Why it works:** Tokenization strongly affects arithmetic and symbolic reasoning: "glued" elements become one token and are lost. Explicit separation (e.g. comma for digit grouping) can raise accuracy from ~75% to ~98% on arithmetic; atomic alignment of elements improves reasoning and can let smaller models match or beat larger ones on structured tasks. Studies: [Tokenization counts: impact on arithmetic in frontier LLMs](https://arxiv.org/abs/2402.14903); [Tokenization Constraints in LLMs: Symbolic and Arithmetic Reasoning](https://arxiv.org/abs/2505.14178).

LLM works with tokens, not individual characters. Elements "glue" into one token → model does not see separate letters. Solution: force separation of elements.

#### Separation symbols

| Symbol | Example | When to use |
|--------|--------|-------------|
| `,` (comma) | `a, b, c, d` | element lists, enumerations |
| `\|` (pipe) | `name \| age \| city` | tabular data, record fields |
| `\n` (newline) | each element on new line | long lists, structured data |
| `---` (line) | Block 1---Block 2 | semantic section boundaries |
| space | `s t r a w b e r r y` | character-level analysis (letter count) |

#### Examples

<example type="bad">
Analyze: apple,pear,banana,orange
</example>
Tokenizer may glue several words → loss of elements

<example type="good">
Analyze:
- apple
- pear
- banana
- orange
</example>
Each element = separate token → accurate count

#### Pipe delimiter for tabular data

```
John | 25 | Moscow | premium
Mary | 32 | NYC | basic
Alex | 28 | London | premium
```

#### Number canonicalization

Use a single number format before putting into the prompt:

| Task | Format | Example |
|------|--------|---------|
| High precision | scientific notation | `+3.14e+02` instead of `314` |
| Simple tasks | no separators | `1250` instead of `1,250.00` |

Add to prompt: "All numeric values are in format [description]"

### CAPS and emphasis

**When to apply:** A single non-negotiable prohibition (e.g. "NEVER use the word \"unique\""). Place at start or end of prompt, not in the middle.

**Why it works:** Excess CAPS and emphasis in the prompt reduce reasoning accuracy. One critical prohibition in CAPS stands out; multiple CAPS cancel out. Study: [Exploring LLM Reasoning Through Controlled Prompt Variations](https://arxiv.org/abs/2504.02111).

#### CAPS rule

Use CAPS for only ONE critical prohibition in the prompt. If everything is emphasized → nothing stands out.

<example type="bad">
NEVER use the WORD "unique".
ALWAYS write IN RUSSIAN.
MUST add CTA.
DO NOT EXCEED 500 characters.
</example>

<example type="good">
• Write in Russian
• Add call to action
• Length: up to 500 characters
• NEVER use the word "unique"
</example>

#### CAPS placement

<rules>
• Place at the start or end of the prompt, not in the middle
• Information in the middle of long context is lost (recency bias)
</rules>

#### Quotes for exact values

When forbidding a specific word → put it in quotes:

| Format | Result |
|--------|--------|
| `Do not use the word unique` | model may interpret differently |
| `Do not use the word "unique"` | model understands: this is the EXACT value |

### ASCII frames

**When to apply:** Immutable rules, anti–prompt-injection rules, or any block that must not be skipped.

**Why it works:** Visual framing of critical blocks reduces ignored instructions and hallucinations. Frame signals "do not ignore".

#### Frame characters

Double lines:
```
╔ ╗ ╚ ╝ ═ ║ ╠ ╣ ╦ ╩ ╬
```

Single lines:
```
┌ ┐ └ ┘ ─ │ ├ ┤ ┬ ┴ ┼
```

Heavy lines:
```
┏ ┓ ┗ ┛ ━ ┃ ┣ ┫ ┳ ┻ ╋
```

#### Frame template

```
╔══════════════════════════════════════╗
║  [HEADING]                           ║
╠══════════════════════════════════════╣
║  • [item 1]                         ║
║  • [item 2]                         ║
╚══════════════════════════════════════╝
```

### Glossary

**When to apply:** Domain terms, acronyms (USP, CTA, TA, TOV), or project-specific abbreviations used later in the prompt.

**Why it works:** Underspecified terms are a major source of errors; underspecified prompts are ~2× more likely to regress across model or prompt changes, with >20% accuracy drops. A glossary at the start removes ambiguity and reduces response variability. Saves tokens and clarifies meaning. Study: [What Prompts Don't Say: Understanding and Managing Underspecification in LLM Prompts](https://arxiv.org/abs/2505.13360).

#### Glossary example

```
## GLOSSARY

H1 = main heading
H2 = subheading
USP = unique selling proposition
CTA = call to action
TA = target audience
TOV = tone of voice
WB = Wildberries
OZ = Ozon

---

## Task
Write H1 + USP + 3 CTA variants for TA "young moms 25-35".
Platform: WB.
TOV: friendly, no slang.
```

### Visual markers (icons)

**When to apply:** When the answer must be structured into fixed categories (e.g. SWOT, code review, risk analysis).

**Why it works:** Fixed categories and forced choice improve adherence to structure; clear section boundaries help. Emoji is one option; alternatives: `### RISKS`, `[RISKS]`, `**RISKS:**`.

Categorize the answer by given sections. Emoji is one way to separate sections.

#### Category examples

**Business plan analysis:**
```
💡 INNOVATIONS — what is new and valuable
🚩 RISKS — what can go wrong  
⚠️ AMBIGUITIES — needs clarification
✅ STRENGTHS — what already works
❌ WEAKNESSES — what to rework
🎯 RECOMMENDATIONS — next steps
```

**Code review:**
```
🐛 Bugs  
⚡ Performance  
🔒 Security  
📖 Readability  
♻️ Refactoring
```

**SWOT:**
```
💪 Strengths  
😰 Weaknesses  
🌟 Opportunities  
⚠️ Threats
```

**Content:**
```
📌 Main idea  
💬 Quotes  
📊 Statistics  
🔗 Sources
```

**Alternatives to emoji:** `### RISKS`, `[RISKS]`, `**RISKS:**` — any visual category marker.

## Data presentation

### XML tags and semantics

**When to apply:** Prompts with three or more logical blocks (context, task, rules, output, examples). Use semantic tags in English (fewer tokens, models expect them).

**Why it works:** Tagged structure improves parsing of instructions and reduces errors. Combination Role + Task + Output + Example in studies reaches ~98% accuracy on structured tasks. Anthropic: [Use XML tags to structure your prompts](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/use-xml-tags). XML for grounding reasoning: [HoT: Highlighted Chain of Thought](https://arxiv.org/abs/2503.02003).

XML tags create clear boundaries between types of information. The model sees at once: where context, where task, where rules.

#### Basic tags

| Component | Main tag | Alternatives | When to use |
|-----------|----------|--------------|-------------|
| Role | `<role>` | `<persona>`, `<assistant>` | always at prompt start |
| Context | `<context>` | `<background>`, `<situation>` | when background is needed |
| Task | `<task>` | `<objective>`, `<goal>` | required — core of prompt |
| Rules | `<rules>` | `<constraints>`, `<requirements>` | constraints and quality criteria |
| Output | `<output>` | `<format>`, `<response>` | when output format matters |
| Example | `<example>` | `<sample>`, `<demo>` | complex formats, few-shot |
| Input | `<input>` | `<data>`, `<content>` | input data to process |
| Document | `<document>` | `<doc>`, `<source>` | cited sources |

Minimum prompt = 3 tags: `<context>` + `<task>` + `<rules>`

#### English vs other-language tags

| Option | Tokens | Standard | Recommendation |
|--------|--------|----------|----------------|
| English | `context` = 1 token | industry standard | ✅ recommended |
| Other | 2–3 tokens | less standard | ⚠️ works but worse |

#### Nested XML tags

Tags can be nested — creates information hierarchy. 2–3 levels is enough.

Nesting example:
```
<document>
    <metadata>
        <title>Report Q3 2024</title>
        <author>Analytics team</author>
        <date>2024-10-15</date>
    </metadata>
    <content>
        Revenue grew 15%...
    </content>
</document>
```

#### Tag attributes

| Attribute | Purpose | Example |
|-----------|---------|---------|
| `source="..."` | data source | `<context source="McKinsey 2024">` |
| `id="..."` | attribution marker | `<doc id="report_q3">` |
| `lang="..."` | content language | `<text lang="en">` |
| `do_not_copy_language="true"` | avoid language drift | `<en_source do_not_copy_language="true">` |

#### Namespace for grouping

When you have 5+ tags, use prefixes to group by function:

```
<input:article>
Article text for analysis...
</input:article>

<input:comments>
User comments...
</input:comments>

<rules:content>
• Use ONLY facts from input:article
</rules:content>

<output:format>
JSON: {"summary": "...", "facts": [...]}
</output:format>
```

#### Universal XML prompt template

```
<role>
You are [specialization]. Your task is [main function].
</role>

<context>
[Background, situation, constraints]
</context>

<task>
[Concrete task: what exactly to do]
</task>

<rules>
• [Constraint 1]
• [Constraint 2]
• [Output format]
</rules>

<output>
[Expected output structure]
</output>

<example>
Input: [example input]
Output: [example correct answer]
</example>
```

#### Common XML tag mistakes

<rules>
❌ Meaningless tags do NOT work:
• `<xyz>`, `<aaa>`, `<block1>` → model ignores them
• Only semantic tags work: `<context>`, `<draft>`, `<solution>`

❌ Unclosed tags:
• Every opening `<tag>` must have closing `</tag>`

❌ Too deep nesting:
• 3+ levels hurt understanding
• Prefer JSON inside one tag over 5 levels of XML
</rules>

### Namespace for grouping

- When 5+ tags, use prefixes: `input:`, `rules:`, `output:`
- Groups tags by function; model sees hierarchy

### Source marking

**When to apply:** Multiple documents or RAG; answers must be grounded in sources; attribution required.

**Why it works:** Structured context and explicit source marking improve attribution and reduce hallucinations. Two-stage "first quote the relevant passage, then answer" improves document adherence. Numbered document blocks help the model know boundaries and total count. Prevents mixing contexts; model knows who said what. For two-stage RAG/grounding, see retrieval-then-reason and quote-then-answer practices in RAG literature.

#### Numbered documents

```
[DOCUMENT 1 OF 3]
first document text
[END DOCUMENT 1]

[DOCUMENT 2 OF 3]
second document text
[END DOCUMENT 2]

[DOCUMENT 3 OF 3]
third document text
[END DOCUMENT 3]
```

Template `[DOCUMENT N OF M]...[END DOCUMENT N]` tells the model total count and boundaries of each.

#### Marking with attributes

```
<doc id="smith" author="John Smith" source="Forbes 2024">
"AI market will triple by 2027. We see exponential growth."
</doc>

<doc id="jones" author="Jane Jones" source="Analytics Report">
"Do not overestimate AI growth. There are infrastructure limits."
</doc>

---

## Attribution rules

When citing, ALWAYS use [doc_id].
Format: "quote" — [author, source]

NEVER attribute words from one document to another author.
```

#### Context grounding

To avoid fabrication — constrain the model to the source:

```
<context source="Report Q3 2024">
[document text]
</context>

---

RULES:
• Use ONLY information from <context>
• Do not add external knowledge
• If not in context — write: "Data not in document"
• When citing use: [from context]
```

#### Two-stage prompt

For strict document adherence: first ask to quote the relevant passage, then answer based on it. Two-stage approach reduces hallucinations.

### System-2 Counting

**When to apply:** Counting words, items, or occurrences in long text (30+ elements). Model accuracy drops to near zero when counting "in head".

**Why it works:** Split text with delimiter `│`, count in each part separately, output intermediate results as text, then sum. Chunking with step-by-step counting and explicit intermediate results is empirically validated; latent counts are stored per part and aggregated via attention. Model must "see" intermediate numbers in its own output or summation fails. Study: [Mechanistic Interpretability of Large-Scale Counting in LLMs through a System-2 Strategy](https://arxiv.org/abs/2601.02989).

The model cannot accurately count 30+ elements. Split into chunks — accuracy goes from ~5% to ~95%.

#### Why models count poorly

Transformers have no "counter variable". Information is spread across layers:

| Element count | Accuracy |
|---------------|----------|
| Up to 10 | accurate |
| 15–20 | errors |
| 30+ | near zero accuracy |

#### Recommended chunk size

| Model size | Accurate count up to | Recommended chunk size |
|------------|---------------------|------------------------|
| Small (7B params) | ~8 elements | 5–7 |
| Medium (13B–70B) | ~12 elements | 8–10 |
| Large (GPT-4, Claude) | ~15 elements | 10–12 |

#### Split template with │

```
Text below is split by │

Instructions:
1. Count the word "type" IN EACH PART separately
2. Write intermediate results
3. Sum at the end

Output format:
Part 1: [number]
Part 2: [number]
Part 3: [number]
---
Total: [sum]

Text:
[first 10 sentences] │ [next 10] │ [next 10]
```

#### Key point

Output intermediate results as TEXT. Without this the model cannot sum correctly — it must "see" the numbers in its own answer.

### JSON structuring of input data

**When to apply:** Many related attributes; nested structures; lists of same-type items; data from API/DB.

**Why it works:** JSON keeps related facts "neighbors" in context, improving extraction and reasoning; structured input with clear instructions improves accuracy on long contexts. One pair `{}` per object; extra braces add tokens and confusion. For complex output formats (XML, BPMN), request intermediate JSON and convert in code — edit success can grow ~3×.

For related object attributes use JSON instead of plain text. JSON groups attributes together — model processes more accurately.

<rules>
• Use only one pair of braces `{}` for a JSON object
• Extra braces `{{{{...}}}}` cause confusion and more tokens
• JSON schema in the prompt is a structure description, not a multi-brace template
</rules>

#### Example: plain text vs JSON

❌ Bad: plain text context
```
Analyze the client. Name: Alex Smith, age 34,
city New York, position Senior Developer at TechCorp,
salary 350000, married, two kids, interests: skiing
and programming, last purchase Jan 15 —
MacBook Pro for 250000...
```

❌ Bad: excess braces
```
{{{{"answer": "string", "summary": "string"}}}}
```

✅ Good: JSON structure
```
Analyze the client:

{
  "profile": {
    "name": "Alex Smith",
    "age": 34,
    "city": "New York"
  },
  "work": {
    "position": "Senior Developer",
    "company": "TechCorp",
    "salary_rub": 350000
  },
  "family": {
    "status": "married",
    "children": 2
  },
  "interests": ["skiing", "programming"],
  "purchases": [
    {"date": "2026-01-15", "item": "MacBook Pro", "price": 250000},
    {"date": "2025-11-20", "item": "iPhone 16", "price": 120000}
  ]
}
```

#### When to use JSON

| Situation | Format |
|------------|--------|
| Many related attributes | JSON |
| Nested structures (object in object) | JSON |
| Lists of same-type elements | JSON |
| Data from API/DB | JSON |

#### Reference points: JSON as comparison anchors

Adding reference data to JSON (market averages, benchmarks, history) lets the model make more accurate comparative conclusions:

```
{
  "current": {"revenue": 1200000, "margin": 15},
  "benchmarks": {
    "industry_avg": {"revenue": 800000, "margin": 12},
    "top_10_percent": {"revenue": 2500000, "margin": 22}
  },
  "history": [
    {"year": 2024, "revenue": 900000, "margin": 11},
    {"year": 2025, "revenue": 1100000, "margin": 14}
  ]
}
```

## Describing rules

### Pseudocode for conditional logic

**When to apply:** Conditional rules (if X then Y); multi-branch logic; priority or format rules that depend on request type.

**Why it works:** Model "executes" logic like code. Pseudocode instructions give +7–16 F1 on classification and +12–38% ROUGE-L vs natural-language prompts; structure (comments, docstring, control flow) helps. IF/ELSE or SWITCH formulation can give ~+3–36% accuracy with 55–87% token reduction vs natural language. Studies: [Prompting with Pseudo-Code Instructions](https://arxiv.org/abs/2305.11790); [CodeAgents: Token-Efficient Codified Multi-Agent Reasoning](https://arxiv.org/abs/2507.03254).

Use `IF/ELSE` and `SWITCH/CASE` instead of prose. Pseudocode gives +36% accuracy, -87% tokens. Model "executes" logic like code instead of interpreting text.

#### IF/ELSE conditions

```
## Processing algorithm

IF length(text) > 500 words:
    1. Extract 3-5 key theses
    2. Brief analysis per thesis
    3. Summary at the end
ELSE:
    1. Analyze text as a whole
    2. One paragraph of conclusions

IF language(input) != "Russian":
    1. Detect source language
    2. Translate key terms
    3. Answer — STRICTLY in Russian
```

#### SWITCH/CASE

```
## Output format

SWITCH request_type:
    CASE "question":
        → Short answer (1-2 sentences)
        → Detailed explanation
        
    CASE "task":
        → Step-by-step solution
        → Final answer in a frame
        
    CASE "analysis":
        → Structure: thesis → arguments → conclusion
        → Table if comparison
        
    CASE "code":
        → Code only, no explanation
        → Comments inside code
        
    DEFAULT:
        → Ask user to clarify request type
```

#### DEFAULT and FALLBACK

- `DEFAULT` — default branch in SWITCH/CASE
- `FALLBACK` — fallback when data is missing:
  ```
  tone: FALLBACK "neutral"
  price: FALLBACK "on request"
  author: FALLBACK "unspecified"
  ```

#### Function-calling style

Frame the task as a Python function with types and docstring:

```
def classify_ticket(
    text: str,
    categories: list[str] = ["Technical", "Billing", "Account"]
) -> dict:
    """
    Classify a support ticket.

    Args:
        text: Customer message
        categories: Allowed categories

    Returns:
        {
            "category": str,      # one of categories
            "confidence": float,  # 0.0-1.0
            "reasoning": str      # why this category
        }

    Constraints:
        - confidence < 0.7 → category = "Unknown"
        - reasoning ≤ 50 words
    """
```

Model is trained on millions of Python functions and treats docstring as contract. NOT for creative tasks — rigid structure kills variation.

### MetaGlyph for compact logic

**When to apply:** Filtering, set logic, conditional rules where wording is long; when token count matters.

**Why it works:** Symbolic notation gives 62–81% token savings; operator stability varies (∈, ⇒, ¬ are usually reliable; ∩ is often confused). On data-selection tasks, models at sufficient scale can reach ~100% accuracy with symbols vs ~90% with text. Use ASCII alternatives (&&, \|\|, !) if symbols are unavailable. Study: [Semantic Compression of LLM Instructions via Symbolic Metalanguages (MetaGlyph)](https://arxiv.org/abs/2601.07354).

Math symbols instead of verbose conditions. Saves 62–81% tokens.

#### MetaGlyph symbols

**Logic:**
- `∧` — AND: `A ∧ B`
- `∨` — OR: `A ∨ B`
- `¬` — NOT: `¬A`
- `→` — therefore: `A → B`
- `⇒` — if-then: `cond ⇒ action`
- `↔` — equivalent: `A ↔ B`

**Sets:**
- `∈` — belongs: `x ∈ {a,b,c}`
- `∉` — not in: `x ∉ {spam}`
- `⊂` — subset: `A ⊂ B`
- `∩` — intersection: `A ∩ B`
- `∪` — union: `A ∪ B`
- `∅` — empty: `result = ∅`

**Comparisons:**
- `>` — greater: `price > 1000`
- `<` — less: `length < 500`
- `≥` — greater or equal: `rating ≥ 4`
- `≤` — less or equal: `100 ≤ x ≤ 500`
- `≠` — not equal: `status ≠ deleted`
- `=` — equal: `status = active`

**Quantifiers:**
- `∀` — for all: `∀ items: check`
- `∃` — exists: `∃ x: condition`
- `|` — such that: `x | x > 0`

**Operations:**
- `◦` — composition: `filter ◦ sort`
- `↦` — mapping: `x ↦ f(x)`
- `⊆` — subset (eq): `A ⊆ B`
- `∑` — sum: `∑(values)`
- `±` — plus-minus: `100 ± 10`
- `≈` — approx: `≈ 100 words`

#### Operator stability

**Reliable:**
- `∈` (belongs) — up to 91.3% on GPT-5.2
- `⇒` (if-then) — up to 98.1% on Kimi K2
- `¬` (NOT) — works everywhere

**Unstable:**
- `∩` — max 21.4% (models confuse with "list")
- Fix: write with comma: `∈(A), ∈(B), ¬(C)`

**Do not work:**
- `→` as transformation — 0% on all models
- Use word "select" or "filter"

#### MetaGlyph formula

Base formula: `{data} → {action} where {conditions} → {format}`

#### Examples

**Filtering:**
```
products → filter where ∈(electronics), ¬(refurbished) → table
```

**Conditional rules:**
```
users → apply:
  ∈(admin) ⇒ access = full
  ∈(moderator) ⇒ access = limited
  ∈(user) ⇒ access = basic
```

**Complex logic:**
```
companies → select where (∈(tech), ¬(hardware)) ∪ ∈(AI) → JSON{name, revenue}
```

**Operation composition:**
```
data → (filter ∈(active)) ◦ (sort by date) ◦ (limit 10) → table
```

**Element mapping:**
```
names ↦ lowercase, prices ↦ round(2) → output
```

#### ASCII alternative

If symbols are unavailable:
- `&&` instead of `∧`
- `||` instead of `∨`
- `!` instead of `¬`
- Models understand both

### Rule hierarchy

**When to apply:** Multiple constraints with different importance (must-have vs nice-to-have).

**Why it works:** Models comply better when constraints are ordered "hard → easy"; constraint order significantly affects behavior. Critical in the middle is lost (recency bias). Study: [Order Matters: Position Bias in Multi-constraint Instruction Following](https://arxiv.org/abs/2502.17204).

<rules>
Three priority levels: 🔴 Critical → 🟡 Important → 🟢 Desirable
Order: hard→easy → improves accuracy
Critical rules → at start OR end, not in the middle (recency bias)
</rules>

### YAML/TOML for readable rules

#### YAML config

YAML — when a human reads the rules (supports comments):

```
# Content generation settings
output:
  format: markdown
  max_length: 1500      # characters
  language: en

style:
  tone: friendly        # friendly | formal | casual
  emoji: true
  max_emoji: 3
  headers: true

constraints:
  forbidden_words:
    - unique
    - best  
    - number one
    - no alternatives
  
  required_sections:
    - intro
    - body
    - cta

validation:
  min_paragraphs: 3
  max_paragraphs: 7
  links_allowed: false
```

#### TOML config

TOML — clear section split, not sensitive to indentation:

```
[meta]
name = "product_card_generator"
version = "2.1.0"
author = "marketing_team"

[output]
format = "html"
max_chars = 2000
language = "en"

[style]
tone = "professional"
emoji_allowed = true
max_emoji = 3

[forbidden]
words = ["best", "unique", "number one"]
phrases = ["market leader", "no alternatives"]

[required]
sections = ["title", "description", "specs", "cta"]
min_specs = 3
max_specs = 7

[validation]
check_length = true
check_forbidden = true
check_required = true
```

### Prompt decorators — compact control tokens

**When to apply:** When the model supports them (Claude 3+, GPT-4, GPT-4o). Use for reasoning, tone, format, debate instead of long prose. May not work in LLaMA, Mistral, GPT-3.5 — then use normal instructions.

**Why it works:** Compact tokens like +++Reasoning, +++OutputFormat act as meta-instructions and control behavior with less text; stacking decorators gives predictable, auditable composition. Order: how to think → how to express → how to format. Specification: [Prompt Decorators (Synapti AI)](https://arxiv.org/abs/2510.19850) / [Prompt Decorators Specification](https://synaptiai.github.io/prompt-decorators/prompt-decorators-specification-v1.0/).

Declarative syntax to control model behavior. Long instructions get lost in the prompt. Decorators are a compact alternative.

#### Families

**Cognitive & generative (how to think):** +++Reasoning (depth=basic|moderate|comprehensive), +++Refine(iterations=1-5), +++Debate (perspectives=2-4 or roles=[…]), +++Import(domain=…|topic=…), +++Verify(criteria=…), +++Hypothesize(count=3-5), +++Synthesize.

**Expressive & systemic (how to output):** +++Tone(style=formal|casual|technical|friendly), +++OutputFormat(type=json|markdown|list|table, sections=[…]), +++Length(target=short|medium|long, max_words=N), +++Priority(order=…), +++Audience(level=…), +++Language(lang=…), +++Confidence(show=…).

#### Basic decorators

❌ Verbose instruction:
```
Please show your reasoning step by step. Use formal business tone. 
Consider the problem from different angles. Output the result as JSON.
```

✅ Decorators:
```
+++Reasoning
+++Tone(style=formal)
+++Debate
+++OutputFormat(type=json)

[Your question here]
```

#### Decorators and their role

| Decorator | Replaces instruction | Family |
|-----------|----------------------|--------|
| +++Reasoning | "show reasoning step by step" | Cognitive |
| +++Tone(style=formal) | "use formal/business tone" | Expressive |
| +++OutputFormat(type=json) | "output result as JSON" | Systemic |
| +++Debate | "consider from different angles" | Cognitive |
| +++Refine(iterations=3) | "improve result iteratively" | Generative |
| +++Import(topic="X") | "use knowledge from domain X" | Systemic |

#### Stacking decorators

Decorators combine as layers. Order matters — model processes top to bottom:

```
+++Debate
+++Reasoning
+++Refine(iterations=2)
+++OutputFormat(type=markdown)

Evaluate a startup's market entry strategy.
```

Stacking logic: `how to think → how to express → how to format`

#### Advanced: explicit roles

```
+++Debate(
    roles=[
        "Advocate: argues FOR, seeks evidence",
        "Skeptic: finds weaknesses, demands evidence"
    ],
    rounds=3,
    respond_to_opponent=true,
    early_stop_on_consensus=true,
    show_process=true
)
+++OutputFormat(type=markdown, sections=["Round N", "Verdict"])

Should we deploy an AI assistant for support instead of hiring more staff?
```

⚠️ Experimental: works on Claude 3+, GPT-4, GPT-4o. May not work on LLaMA, Mistral, GPT-3.5.

## Output format control

### Placeholders

Two types: model fills vs you supply.

| Syntax | Who fills | When to use | Example |
|--------|-----------|-------------|---------|
| `[text]` | Model | Template for generation | Hello, `[client_name]`! |
| `{variable}` | You | Your data slot | Analyze: `{text}` |
| `[a/b/c]` | Model chooses | Limited choice | `[positive/negative/neutral]` |
| `[1-5]` | Model | Numeric range | Rating: `[1-5]` |
| `[up to 100 words]` | Model | Length limit | Summary: `[up to 100 words]` |

#### Template with both types

```
## Input (you fill)

Product: {product_name}
Category: {category}
Price: {price}
Features: {features}

---

## Output format (model fills)

# [Sales headline — up to 60 chars]

[Emotional description — 2-3 sentences]

**Features:**
• [feature 1]
• [feature 2]  
• [feature 3]

**Price:** [price]

[Call to action — 1 sentence]
```

### JSON schema with types

Strict contract: fields, types, allowed values. JSON schema is a contract. Model either follows or breaks it. No middle ground.

<rules>
• Use only one pair of braces `{}` for JSON structure description
• Avoid excess braces: `{{{{...}}}}` — `{...}` is enough
• JSON schema describes output structure, does not need multiple wrappers

⚠️ Exception for LangChain prompts:
• In LangChain templates `{}` are template variables
• To escape JSON use double braces `{{` and `}}`
• Docs: `{{` escapes one `{`, `{{{{` escapes `{{`

**Double escaping for f-strings:**
• In Python f-string (`f"""`) use double escaping
• In f-string: `{{{{"key": "value"}}}}` → Python → `{{"key": "value"}}` → LangChain → `{"key": "value"}` for model
• In plain string: `{{"key": "value"}}` → LangChain → `{"key": "value"}` for model

• f-string example: `{{{{"answer": "string"}}}}` in f-string → model sees `{"answer": "string"}`
• Plain string example: `{{"answer": "string"}}` → model sees `{"answer": "string"}`
• Source: https://docs.langchain.com/oss/python/langchain/errors/INVALID_PROMPT_INPUT
</rules>

#### Schema format

```
Return result as JSON:

{
  "sentiment": "positive" | "negative" | "neutral",
  "confidence": number 0.0 to 1.0,
  "score": integer 1 to 5,
  "keywords": array of strings (max 5),
  "summary": string (up to 100 words),
  "issues": array of strings | null (if no issues)
}

Rules:
- sentiment: ONLY one of the three values
- confidence: decimal, one digit after point
- issues: array OR null, NOT empty array []
```

#### Example: complex structure with object array

```
Return result as JSON:

{
  "problems": [
    {
      "category": "delivery" | "product" | "service",
      "priority": "high" | "medium" | "low",
      "count": number,
      "examples": ["quote 1", "quote 2"]
    }
  ],
  "summary": "brief conclusion in 1-2 sentences"
}
```

**Rules:**
- One pair `{}` for main object
- Array `[]` contains objects with fields
- Union types via `|` for limited values
- Nested structures allowed, no excess braces

#### Intermediate JSON instead of complex formats

**Problem:** LLMs are very bad at generating complex formats (XML, HTML, BPMN) — ~40% syntax errors.

**Solution:** Ask for intermediate simplified JSON, not the final complex format.

❌ Bad: "Generate BPMN diagram in XML"
✅ Good: "Return JSON: {nodes: [{id, label, type}], edges: [{from, to}]}" → Convert to XML in code

**Result:** Data edit success rate grows ~3x.

### TypeScript typing

Maximum strictness: union types, optional fields, generics. Model understands TypeScript types.

#### TypeScript schema example

```
type ReviewAnalysis = {
  sentiment: "positive" | "negative" | "neutral";
  score: 1 | 2 | 3 | 4 | 5;
  confidence: number;        // 0.0-1.0
  
  keywords: string[];        // max 5 items
  pros: string[];
  cons: string[];
  
  issues?: string[];         // only if score < 3
  
  recommendation: {
    action: "buy" | "wait" | "skip";
    reason: string;          // up to 50 words
  };
}

// Analyze the review and return object of type ReviewAnalysis
```

### Markdown tables for comparison

<rules>
Forces concreteness → every cell needs a value
Model cannot "pad with fluff"
</rules>

<example>
Compare three options. Answer ONLY as a table:

| Criterion | Option A | Option B | Option C |
|-----------|----------|----------|----------|
| Price | [price] | [price] | [price] |
| Speed | [1-5] | [1-5] | [1-5] |
| Quality | [1-5] | [1-5] | [1-5] |
| Support | [yes/no/partial] | [...] | [...] |
| **Verdict** | [recommendation] | [...] | [...] |

After the table — one paragraph conclusion (up to 50 words).
</example>

### Control ladder

6 levels: from chaos to guarantee. Each step adds predictability.

1. **Request** — "Analyze the review" → Chaos
2. **+Example** — "Here is a good analysis: ..." → Hint
3. **+Template** — "Fill: Sentiment: `[...]`, Score: `[...]`" → Structure
4. **+Schema** — `{"sentiment": "...", "score": ...}` → Fields
5. **+Types** — `"positive" | "negative" | "neutral"` → Validation
6. **+Rules** — `IF score < 3 THEN issues required` → Guarantee

### Schema–Examples–Task

**When to apply:** When output must be highly predictable and format is strict (classification, extraction, reports).

**Why it works:** CoT + few-shot in this three-block format (Schema — WHAT, Examples — HOW, Task — ON WHAT) gives best accuracy in experiments. In examples, show the start of reasoning so the model continues in the same style.

Three blocks. Always three. Schema — WHAT. Examples — HOW. Task — ON WHAT.

#### Full template

```
## SCHEMA

{
  "category": "string — product category",
  "sentiment": "positive" | "negative" | "mixed",
  "score": 1-5,
  "issues": ["string"] | null
}

---

## EXAMPLES

Examples separated by "===":

===
Review: "Great vacuum! Recommend to everyone."
→ {"category": "vacuum", "sentiment": "positive", "score": 5, "issues": null}
===
Review: "Camera good but battery is weak."
→ {"category": "camera", "sentiment": "mixed", "score": 3, "issues": ["battery"]}
===

---

## TASK

Review: "Phone is ok but heats up when gaming"
→
```

#### CoT + Few-Shot = maximum

Best results from Chain-of-Thought (ask to reason step by step) plus Few-Shot (2–3 examples). Schema–Examples–Task is that combination.

#### Help with first steps

Biggest quality gain: help the model take the first steps on the current task, not show examples of other tasks. In examples show the start of reasoning.

### Backticks for code

Open code block → model closes it with code, not chatter.

<example type="bad">
"Sure! I'll help you write a sort function. 
Here is a Python example that uses the algorithm..."
</example>

<example type="good">
Write a sort function in Python

```python
</example>

### Self-check

**When to apply:** When output correctness or format is critical (e.g. JSON, structured reports, factual answers).

**Why it works:** Iterative self-check before output improves accuracy; in experiments the model finds errors in its own reasoning and multi-solution voting increases final accuracy. Step-level check further improves error detection and correction. Model does not self-check by default — you must ask (e.g. checklist with □). Studies: [SelfCheck: Using LLMs to Zero-Shot Check Their Own Step-by-Step Reasoning](https://arxiv.org/abs/2308.00436); [Learning to Check: Step-Level Self-Correction](https://arxiv.org/abs/2402.13035).

Model checks itself before output. Model does not self-check by default. If you ASK — it will.

#### Control levels

| Level | Description | Accuracy |
|-------|-------------|----------|
| No control | just answer | ~60% |
| Ask to check | check yourself | ~75% |
| Checklist | "□ schema fields" | ~85% |
| Validation | JSON schema | ~92% |
| Iterative correction | 3 passes | ~97% |

#### Self-check block format

```
<self_check>
Before output, check:

□ All required fields filled?
□ Types match schema?
□ No forbidden words?
□ Length within limit?
□ Output language correct?

If any item fails — fix BEFORE output.
</self_check>
```

#### Self-check for text

```
<self_check>
Before finalizing:

□ Main thesis covered?
□ Concrete examples/numbers?
□ No repetition or fluff?
□ Word limit respected?
□ CTA at the end?
□ Tone matches TA?

If not — revise.
</self_check>
```

#### Verification-first approach

Instead of "think step by step and find the answer" → "here is an answer [any], verify it, then give the correct one". Verification before generation improves accuracy.

#### Quit instructions

Teach the model to stop and ask instead of acting under uncertainty:
```
If unsure — write 'Need clarification: ...' instead of guessing.
```

#### INoT (Introspective Negotiation of Thought)

**When to apply:** High-stakes or critical decisions; when a single answer must be checked before output. Model "debates" with itself (solver + critic) then revises.

**Why it works:** INoT gives ~7.95% accuracy gain and ~58% token reduction vs iterative user dialogue "answer → user critique → new answer". Fewer rounds with user, higher accuracy on critical choices. Study: [Introspection of Thought Helps AI Agents (INoT)](https://arxiv.org/abs/2507.08664).

Example pattern:
```
<AGENT_1 role="Solver">
    → Propose a solution to the task
</AGENT_1>

<AGENT_2 role="Critic">
    → Find weaknesses in AGENT_1's solution
    → List specific issues
</AGENT_2>

<AGENT_1>
    → Revise the solution given the criticism
</AGENT_1>

REPEAT 2-3 rounds UNTIL consensus
OUTPUT final_solution
```

## Few-shot examples

**When to apply:** New or non-obvious output format; classification with clear boundaries; when you want the model to copy both format and reasoning style.

**Why it works:** Scaling improves few-shot behavior without fine-tuning. CoT + few-shot in examples gives best effectiveness; showing reasoning in examples improves answer quality. Too many examples can hurt — optimal count depends on model and task. Last example is remembered best → make it the main or hardest. Contrasting pairs (good vs bad) clarify quality boundaries. Studies: [Language Models are Few-Shot Learners (GPT-3)](https://arxiv.org/abs/2005.14165); for CoT+few-shot in structured format see **Schema–Examples–Task**.

### Count and format

| Count | When to use | Example task |
|-------|-------------|--------------|
| **0 (zero-shot)** | Simple tasks, model knows format | "Translate text to English" |
| **1-2** | Show specific output format | "Extract data as JSON" |
| **3-5** | Complex classification, edge cases | "Classify tickets into 5 categories" |
| **5+** | Rarely needed, uses context | Very non-standard format |

### Example structure

#### Example format: input → output

```
## Examples (=== separates examples)

Input: "Card payment not working"
→ Category: Billing
→ Priority: high
→ Reason: payment mentioned

===

Input: "App crashes on launch"
→ Category: Technical
→ Priority: medium
→ Reason: bug/error

===

Input: "Want to change email in profile"
→ Category: Account
→ Priority: low
→ Reason: account settings

---

## Now process:
"Double charge for subscription"
```

#### Key elements

| Element | Purpose |
|--------|---------|
| Delimiters `===` between examples | visually separate examples |
| `---` | separate examples section from task |
| Meta-instruction: "(=== separates examples)" | explain markup |
| Arrow `→` | show input→output transformation |

#### CoT in examples

Show not only the result but the reasoning in the example. Model learns to apply the same logic:

```
## Examples with reasoning

Input: "Can't pay by card, shows error"
Reasoning: Payment + error mentioned. Payment → Billing.
            Error could be Technical, but context is payment.
            Priority high — blocks purchase.
→ Category: Billing
→ Priority: high

===

Input: "Want to delete my account"
Reasoning: About account → Account. Not urgent, not a bug.
            Priority low.
→ Category: Account
→ Priority: low
```

#### Contrastive pair (good vs bad)

One input, two outcomes — bad and good — with a short reason. Clarifies quality boundaries better than positive-only examples.

<example>
Input: "Write a product description: Sony WH-1000XM5 wireless headphones"

❌ Bad: "Great headphones, recommend buying." (too short, no specs)

✅ Good: "Sony WH-1000XM5 wireless headphones with active noise cancellation. Up to 30 hours battery, quick charge (3 min = 3 hours music). LDAC for Hi-Res Audio." (concrete specs, objective)
</example>

### Example order

<rules>
• Last example = main (model remembers end better)
• Contrasting pairs (+ and −) → improve boundary understanding
• Variety of formats → helps model adapt
</rules>

### Example format > content

One good example is worth a page of instructions.
Order: last = main.
Contrasting pairs (+ and −) → show category boundaries.

## Universal prompt (and rules) template

Full template using all techniques:

```
# ═══════════════════════════════════════════════════════
# PROMPT / RULES: [name]
# VERSION: 1.0.0
# ═══════════════════════════════════════════════════════

## GLOSSARY

H1 = main heading
CTA = call to action
TA = target audience

---

## Role

<role>
You are [role with experience and specialization].
</role>

---

## Context

<context source="[source]" id="doc1">
{data_to_process}
</context>

---

## Task

<task>
[Clear description of what to do]
</task>

---

## Rules

### 🔴 Critical
NEVER [prohibition 1]
NEVER [prohibition 2]

### 🟡 Important
• [requirement 1]
• [requirement 2]

### 🟢 Desirable
• [recommendation]

---

## Output format

<output>
{
  "field1": "type | allowed values",
  "field2": number 1-10,
  "field3": ["array of strings"] | null
}
</output>

---

## Examples

Examples separated by "===":

===
Input: [example 1]
Output: [result 1]
===
Input: [example 2]
Output: [result 2]
===

---

<self_check>
Before answering, check:
□ All fields filled?
□ Types match schema?
□ Prohibitions followed?
□ Format correct?

If any item fails — fix BEFORE output.
</self_check>
```

---

## Quality checklist (before shipping any prompt)

- [ ] Sections and delimiters (`---`, `###`) between blocks
- [ ] Context/task/rules separated; critical rules at start or end
- [ ] No wall of text; key terms in **bold** or `backticks`
- [ ] Data in tags or JSON/table; sources numbered if multiple
- [ ] Rules in IF/ELSE or priority (🔴🟡🟢) when conditional
- [ ] Output format explicit (schema, example, or template)
- [ ] Self-check when quality is critical
- [ ] No duplicate or compensating instructions; one formulation per requirement
- [ ] **If skill (SKILL.md):** name matches folder; description = what + when + keywords; no reference.md / references/ — all in SKILL.md

Good prompt: readable in one pass, primary vs secondary clear, solves by formulation, explicit format (schema + examples + self-check where needed).

---

## Verification checklist: practices vs prompt (for agent)

Use this **after** drafting a prompt, rule, or skill text. Go through every practice category below; for each, decide if it applies to this prompt and verify alignment. If a practice applies but the prompt violates it — fix the prompt. If the prompt contains something that contradicts any practice — fix or remove it.

**How to use:** For each block, answer: (1) Is this practice relevant to this prompt? (2) If yes — is the prompt aligned? (3) Is there anything in the prompt that breaks this practice?

- [ ] **Core principle** — Task is minimal formulation; no pile of compensating instructions. If something works wrong, root cause fixed, not patched.
- [ ] **Language** — Prompt/rule text in English (except allowed examples/variables). Priority labels 🔴🟡🟢 used where priorities exist.
- [ ] **Brevity** — Short, dense; each sentence adds meaning; nothing redundant for unambiguity.
- [ ] **Reformulate vs extend** — No "add instruction to fix bad formulation"; no old + new conflicting wording; no duplicate formulations.
- [ ] **Structure** — Flat structure; one task → one block; goal/constraints/format separate; one sentence per line where it helps.
- [ ] **Wording** — Positive, direct, verifiable; outcome requirements, not vague "try to" / "preferably".
- [ ] **Examples** — Only when needed; no example that doesn’t change understanding; no corner-case overload; focus on main scenario.
- [ ] **Parameters** — Variables/placeholders, no hardcoding; prompt stays generic when inputs change.
- [ ] **Size control** — No unnecessary growth; if long, task reformulated/simplified, not extended.
- [ ] **Delimiters** — Reliable delimiters (`---`, `===`, `###`, etc.); avoid `~~~~`, `____`, `....`, `////`; delimiter purpose stated where it matters.
- [ ] **Headings** — Clear hierarchy (# main, ## sections, ### subs); arrow `→` for transformations where useful.
- [ ] **Tables** — "Object — properties" in tables, not long prose lists where comparison matters.
- [ ] **Token separation** — Lists/enumerations separated (e.g. newline, comma, pipe) so elements don’t glue; numbers in one canonical form if specified.
- [ ] **CAPS** — At most one critical prohibition in CAPS; placed at start or end, not buried in the middle; exact forbidden values in quotes.
- [ ] **XML/semantic tags** — Where used: meaningful tags (`<context>`, `<task>`, `<rules>`, etc.); all closed; nesting ≤ 2–3 levels; 5+ tags consider namespace/prefix.
- [ ] **Source marking** — Multiple documents/sources clearly separated and attributed; context grounding stated when model must not add external knowledge.
- [ ] **JSON** — One pair `{}` for structure; no `{{{{...}}}}`; LangChain/f-string escaping only where applicable.
- [ ] **Conditional logic** — IF/ELSE or SWITCH/CASE (or MetaGlyph where stable) instead of long prose where it helps.
- [ ] **Rule hierarchy** — Critical → Important → Desirable; critical at start or end.
- [ ] **Output format** — Schema and/or template and/or examples; types/constraints explicit where strictness needed; self-check when quality is critical.
- [ ] **Few-shot** — Count appropriate (0–2–3–5); examples separated (e.g. `===`); last example = main; contrasting pairs if boundaries matter.
- [ ] **Self-check** — If quality is critical: explicit checklist (□ …) before output.
- [ ] **No contradictions** — Full read-through: nothing in the prompt contradicts any of the practices above; no forbidden patterns (meaningless tags, unclosed tags, vague wording, duplicate/compensating rules).
- [ ] **Technique fit** — For the prompt’s goal (structure, format, grounding, logic, etc.), the "When to use which technique" table was used; applied techniques match the task (e.g. delimiters for multi-block, tables for object comparison, self-check when quality is critical).
- [ ] **If rule/skill** — Rule (.mdc): applies to the right scope; Skill (SKILL.md): name matches folder, description = what + when + keywords; all content in one file (no reference.md / references/).

If any item is unchecked or the prompt still violates a practice — update the prompt and re-run this checklist before considering the task done.
