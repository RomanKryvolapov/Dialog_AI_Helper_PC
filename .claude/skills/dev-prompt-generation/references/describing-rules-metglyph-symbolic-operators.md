## About this file

**What’s inside:** MetaGlyph-style symbolic logic, operator stability, formulas, examples, and ASCII alternatives to symbols.

**Hub:** [../SKILL.md](../SKILL.md)

---

### MetaGlyph for compact logic

**When to apply:** Filtering, set logic, conditional rules where wording is long; when token count matters.

**Why it works:** Symbolic notation gives 62–81% token savings; operator stability varies (∈, ⇒, ¬ are usually reliable; ∩ is often confused). On data-selection tasks, models at sufficient scale can reach ~100% accuracy with symbols vs ~90% with text. Use ASCII alternatives (&&, \|\|, !) if symbols are unavailable. Study: [Semantic Compression of LLM Instructions via Symbolic Metalanguages (MetaGlyph)](https://arxiv.org/abs/2601.07354).

Math symbols instead of verbose conditions. Saves 62–81% tokens.

#### MetaGlyph symbols

**Logic:**

- `∧` — AND: `A ∧ B`
- `∨` — OR: `A ∨ B`
- `¬` — NOT: `¬A`
- `→` — therefore: `A → B`
- `⇒` — if-then: `cond ⇒ action`
- `↔` — equivalent: `A ↔ B`

**Sets:**

- `∈` — belongs: `x ∈ {a,b,c}`
- `∉` — not in: `x ∉ {spam}`
- `⊂` — subset: `A ⊂ B`
- `∩` — intersection: `A ∩ B`
- `∪` — union: `A ∪ B`
- `∅` — empty: `result = ∅`

**Comparisons:**

- `>` — greater: `price > 1000`
- `<` — less: `length < 500`
- `≥` — greater or equal: `rating ≥ 4`
- `≤` — less or equal: `100 ≤ x ≤ 500`
- `≠` — not equal: `status ≠ deleted`
- `=` — equal: `status = active`

**Quantifiers:**

- `∀` — for all: `∀ items: check`
- `∃` — exists: `∃ x: condition`
- `|` — such that: `x | x > 0`

**Operations:**

- `◦` — composition: `filter ◦ sort`
- `↦` — mapping: `x ↦ f(x)`
- `⊆` — subset (eq): `A ⊆ B`
- `∑` — sum: `∑(values)`
- `±` — plus-minus: `100 ± 10`
- `≈` — approx: `≈ 100 words`

#### Operator stability

**Reliable:**

- `∈` (belongs) — up to 91.3% on GPT-5.2
- `⇒` (if-then) — up to 98.1% on Kimi K2
- `¬` (NOT) — works everywhere

**Unstable:**

- `∩` — max 21.4% (models confuse with "list")
- Fix: write with comma: `∈(A), ∈(B), ¬(C)`

**Do not work:**

- `→` as transformation — 0% on all models
- Use word "select" or "filter"

#### MetaGlyph formula

Base formula: `{data} → {action} where {conditions} → {format}`

#### Examples

**Filtering:**

```
products → filter where ∈(electronics), ¬(refurbished) → table
```

**Conditional rules:**

```
users → apply:
  ∈(admin) ⇒ access = full
  ∈(moderator) ⇒ access = limited
  ∈(user) ⇒ access = basic
```

**Complex logic:**

```
companies → select where (∈(tech), ¬(hardware)) ∪ ∈(AI) → JSON{name, revenue}
```

**Operation composition:**

```
data → (filter ∈(active)) ◦ (sort by date) ◦ (limit 10) → table
```

**Element mapping:**

```
names ↦ lowercase, prices ↦ round(2) → output
```

#### ASCII alternative

If symbols are unavailable:

- `&&` instead of `∧`
- `||` instead of `∨`
- `!` instead of `¬`
- Models understand both
