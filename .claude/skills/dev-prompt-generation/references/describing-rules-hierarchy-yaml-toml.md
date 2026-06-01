## About this file

**What’s inside:** Rule priority hierarchy (🔴🟡🟢), YAML and TOML as readable rule configs (with examples).

**Hub:** [../SKILL.md](../SKILL.md)

---

### Rule hierarchy

**When to apply:** Multiple constraints with different importance (must-have vs nice-to-have).

**Why it works:** Models comply better when constraints are ordered "hard → easy"; constraint order significantly affects behavior. Critical in the middle is lost (recency bias). Study: [Order Matters: Position Bias in Multi-constraint Instruction Following](https://arxiv.org/abs/2502.17204).

<rules>
Three priority levels: 🔴 Critical → 🟡 Important → 🟢 Desirable
Order: hard→easy → improves accuracy
Critical rules → at start OR end, not in the middle (recency bias)
</rules>

### YAML/TOML for readable rules

#### YAML config

YAML — when a human reads the rules (supports comments):

```
# Content generation settings
output:
  format: markdown
  max_length: 1500      # characters
  language: en

style:
  tone: friendly        # friendly | formal | casual
  emoji: true
  max_emoji: 3
  headers: true

constraints:
  forbidden_words:
    - unique
    - best
    - number one
    - no alternatives

  required_sections:
    - intro
    - body
    - cta

validation:
  min_paragraphs: 3
  max_paragraphs: 7
  links_allowed: false
```

#### TOML config

TOML — clear section split, not sensitive to indentation:

```
[meta]
name = "product_card_generator"
version = "2.1.0"
author = "marketing_team"

[output]
format = "html"
max_chars = 2000
language = "en"

[style]
tone = "professional"
emoji_allowed = true
max_emoji = 3

[forbidden]
words = ["best", "unique", "number one"]
phrases = ["market leader", "no alternatives"]

[required]
sections = ["title", "description", "specs", "cta"]
min_specs = 3
max_specs = 7

[validation]
check_length = true
check_forbidden = true
check_required = true
```
