## About this file

**What’s inside:** Task block patterns (incl. table-filter example), token separation, CAPS/emphasis rules, and quotes for exact forbidden values.

**Hub:** [../SKILL.md](../SKILL.md)

---

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

| Symbol         | Example                  | When to use                             |
| -------------- | ------------------------ | --------------------------------------- |
| `,` (comma)    | `a, b, c, d`             | element lists, enumerations             |
| `\|` (pipe)    | `name \| age \| city`    | tabular data, record fields             |
| `\n` (newline) | each element on new line | long lists, structured data             |
| `---` (line)   | Block 1---Block 2        | semantic section boundaries             |
| space          | `s t r a w b e r r y`    | character-level analysis (letter count) |

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

| Task           | Format              | Example                      |
| -------------- | ------------------- | ---------------------------- |
| High precision | scientific notation | `+3.14e+02` instead of `314` |
| Simple tasks   | no separators       | `1250` instead of `1,250.00` |

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

| Format                         | Result                                     |
| ------------------------------ | ------------------------------------------ |
| `Do not use the word unique`   | model may interpret differently            |
| `Do not use the word "unique"` | model understands: this is the EXACT value |
