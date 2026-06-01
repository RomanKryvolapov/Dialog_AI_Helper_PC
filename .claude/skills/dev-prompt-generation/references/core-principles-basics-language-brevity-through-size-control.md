## About this file

**What’s inside:** Core principle, language of prompts/rules, brevity, reformulation, structure, wording, examples, parameters, and size control (through **Size control**).

**Hub:** [../SKILL.md](../SKILL.md)

---

## Table of contents

- Core principle
- Language of prompts and rules
- Brevity and density of meaning
- Reformulate instead of extending
- Structure and clarity
- Wording
- Examples and detail
- Parameters and data
- Size control
- Single responsibility for rules and skills
- Organizing prompt (and rules) structure
- Data format in this prompt
- Examples
- Task (blocks and patterns; appears twice in this file)
- GLOSSARY

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

---
