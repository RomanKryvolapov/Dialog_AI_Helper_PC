## About this file

**What’s inside:** Few-shot sizing, `===` delimiters, contrasting pairs, CoT inside examples, and example ordering rules.

**Hub:** [../SKILL.md](../SKILL.md)

---

## Few-shot examples

**When to apply:** New or non-obvious output format; classification with clear boundaries; when you want the model to copy both format and reasoning style.

**Why it works:** Scaling improves few-shot behavior without fine-tuning. CoT + few-shot in examples gives best effectiveness; showing reasoning in examples improves answer quality. Too many examples can hurt — optimal count depends on model and task. Last example is remembered best → make it the main or hardest. Contrasting pairs (good vs bad) clarify quality boundaries. Studies: [Language Models are Few-Shot Learners (GPT-3)](https://arxiv.org/abs/2005.14165); for CoT+few-shot in structured format see **Schema–Examples–Task**.

### Count and format

| Count             | When to use                        | Example task                         |
| ----------------- | ---------------------------------- | ------------------------------------ |
| **0 (zero-shot)** | Simple tasks, model knows format   | "Translate text to English"          |
| **1-2**           | Show specific output format        | "Extract data as JSON"               |
| **3-5**           | Complex classification, edge cases | "Classify tickets into 5 categories" |
| **5+**            | Rarely needed, uses context        | Very non-standard format             |

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

| Element                                      | Purpose                             |
| -------------------------------------------- | ----------------------------------- |
| Delimiters `===` between examples            | visually separate examples          |
| `---`                                        | separate examples section from task |
| Meta-instruction: "(=== separates examples)" | explain markup                      |
| Arrow `→`                                    | show input→output transformation    |

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
