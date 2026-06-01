## About this file

**What’s inside:** Minimal quick templates (task/context/rules/format), few-shot block, self-check snippet, key-value mini-config, **quality checklist**, and **verification vs practices** checklist.

**Hub:** [../SKILL.md](../SKILL.md)

---

## Quick templates

**Minimal structure:**

```markdown
## Task

[What to do]

---

## Context / Data

<context>
[Background or input]
</context>

---

## Rules

- [rule 1]
- [rule 2]

---

## Format

[Expected output shape or example]
```

**Few-shot block:**

```markdown
## Examples (=== separates examples)

Input: "[example 1]"
-> Output: [result 1]

===

Input: "[example 2]"
-> Output: [result 2]

---

## Task

[Actual input to process]
```

**Self-check block:**

```markdown
<self_check>
Before output, check:
[] [requirement 1]?
[] [requirement 2]?
[] [requirement 3]?

If any is not met -- fix BEFORE output.
</self_check>
```

**Key-value (short config):**

```
Role: [role]
Tone: friendly | formal | neutral
Length: ~500 chars
Format: JSON
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
- [ ] **If skill (SKILL.md):** name matches folder; description = what + when + keywords; layout matches **latest Cursor Skills docs** (required `SKILL.md` + optional folders only as documented); links from `SKILL.md` to any `references/` / `assets/` / `scripts/`

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
- [ ] **If rule/skill** — Rule (.mdc): applies to the right scope; Skill: matches **latest Cursor docs** (name, description, `SKILL.md`, optional folders per docs); **Cursor docs were consulted** for this edit (see **Cursor official documentation (mandatory)**).
- [ ] **If rule (.mdc) with agent-decides:** no `globs`; `description` is keyword-rich and task-oriented so the agent can select by relevance.
- [ ] **Single responsibility** — no inline specifics about topics owned by another canonical rule/skill; only "see X" references.

If any item is unchecked or the prompt still violates a practice — update the prompt and re-run this checklist before considering the task done.
