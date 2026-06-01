## About this file

**What’s inside:** Prompt decorators (`+++Reasoning`, `+++Tone`, stacking, advanced role debates) — compact control tokens vs long prose.

**Hub:** [../SKILL.md](../SKILL.md)

---

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

| Decorator                  | Replaces instruction             | Family     |
| -------------------------- | -------------------------------- | ---------- |
| +++Reasoning               | "show reasoning step by step"    | Cognitive  |
| +++Tone(style=formal)      | "use formal/business tone"       | Expressive |
| +++OutputFormat(type=json) | "output result as JSON"          | Systemic   |
| +++Debate                  | "consider from different angles" | Cognitive  |
| +++Refine(iterations=3)    | "improve result iteratively"     | Generative |
| +++Import(topic="X")       | "use knowledge from domain X"    | Systemic   |

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
