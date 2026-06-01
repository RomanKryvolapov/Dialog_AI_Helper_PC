## About this file

**What’s inside:** ASCII frames for immutable rules, glossary blocks, and visual markers / emoji-style section labels.

**Hub:** [../SKILL.md](../SKILL.md)

---

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
