## About this file

**What’s inside:** Pseudocode for conditional logic (`IF`/`ELSE`, `SWITCH`/`CASE`), defaults/fallbacks, and function-calling / docstring-style rule blocks.

**Hub:** [../SKILL.md](../SKILL.md)

---

## Table of contents

- Describing rules
- Processing algorithm
- Output format

---

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
