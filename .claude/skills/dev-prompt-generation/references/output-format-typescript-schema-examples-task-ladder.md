## About this file

**What’s inside:** TypeScript-style output contracts, markdown table comparisons, control ladder (request → schema → rules), and **Schema / Examples / Task** full template.

**Hub:** [../SKILL.md](../SKILL.md)

---

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

| Criterion   | Option A         | Option B | Option C |
| ----------- | ---------------- | -------- | -------- |
| Price       | [price]          | [price]  | [price]  |
| Speed       | [1-5]            | [1-5]    | [1-5]    |
| Quality     | [1-5]            | [1-5]    | [1-5]    |
| Support     | [yes/no/partial] | [...]    | [...]    |
| **Verdict** | [recommendation] | [...]    | [...]    |

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
