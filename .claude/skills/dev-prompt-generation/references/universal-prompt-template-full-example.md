## About this file

**What’s inside:** Full “universal” prompt/rules template (Glossary, Role, Context, Task, Rules with priorities, Output JSON, Examples, self-check).

**Hub:** [../SKILL.md](../SKILL.md)

---

## Table of contents

- Universal prompt (and rules) template
- GLOSSARY
- Role / Context / Task / Rules / Output format / Examples (template sections)
- Quick templates
- Quality checklist (before shipping any prompt)
- Verification checklist: practices vs prompt (for agent)

---

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
