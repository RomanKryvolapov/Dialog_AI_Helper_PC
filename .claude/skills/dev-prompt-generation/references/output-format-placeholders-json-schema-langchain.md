## About this file

**What’s inside:** Placeholders `{var}` vs `[model fills]`, JSON schema in prompts, LangChain/`{{` escaping notes, and intermediate-JSON strategy (complex formats via JSON first).

**Hub:** [../SKILL.md](../SKILL.md)

---

## Table of contents

- Output format control
- Input (you fill)
- Output format (model fills)
- SCHEMA
- EXAMPLES
- TASK
- Few-shot examples
- Examples (=== separates examples)
- Now process:
- Examples with reasoning

---

## Output format control

### Placeholders

Two types: model fills vs you supply.

| Syntax              | Who fills     | When to use             | Example                       |
| ------------------- | ------------- | ----------------------- | ----------------------------- |
| `[text]`            | Model         | Template for generation | Hello, `[client_name]`!       |
| `{variable}`        | You           | Your data slot          | Analyze: `{text}`             |
| `[a/b/c]`           | Model chooses | Limited choice          | `[positive/negative/neutral]` |
| `[1-5]`             | Model         | Numeric range           | Rating: `[1-5]`               |
| `[up to 100 words]` | Model         | Length limit            | Summary: `[up to 100 words]`  |

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
