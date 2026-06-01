## About this file

**What’s inside:** XML tag semantics (role/context/task/rules), nesting, attributes, namespaces, and the universal XML prompt template.

**Hub:** [../SKILL.md](../SKILL.md)

---

## Table of contents

- Data presentation
- Attribution rules

---

## Data presentation

### XML tags and semantics

**When to apply:** Prompts with three or more logical blocks (context, task, rules, output, examples). Use semantic tags in English (fewer tokens, models expect them).

**Why it works:** Tagged structure improves parsing of instructions and reduces errors. Combination Role + Task + Output + Example in studies reaches ~98% accuracy on structured tasks. Anthropic: [Use XML tags to structure your prompts](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/use-xml-tags). XML for grounding reasoning: [HoT: Highlighted Chain of Thought](https://arxiv.org/abs/2503.02003).

XML tags create clear boundaries between types of information. The model sees at once: where context, where task, where rules.

#### Basic tags

| Component | Main tag     | Alternatives                      | When to use                      |
| --------- | ------------ | --------------------------------- | -------------------------------- |
| Role      | `<role>`     | `<persona>`, `<assistant>`        | always at prompt start           |
| Context   | `<context>`  | `<background>`, `<situation>`     | when background is needed        |
| Task      | `<task>`     | `<objective>`, `<goal>`           | required — core of prompt        |
| Rules     | `<rules>`    | `<constraints>`, `<requirements>` | constraints and quality criteria |
| Output    | `<output>`   | `<format>`, `<response>`          | when output format matters       |
| Example   | `<example>`  | `<sample>`, `<demo>`              | complex formats, few-shot        |
| Input     | `<input>`    | `<data>`, `<content>`             | input data to process            |
| Document  | `<document>` | `<doc>`, `<source>`               | cited sources                    |

Minimum prompt = 3 tags: `<context>` + `<task>` + `<rules>`

#### English vs other-language tags

| Option  | Tokens              | Standard          | Recommendation     |
| ------- | ------------------- | ----------------- | ------------------ |
| English | `context` = 1 token | industry standard | ✅ recommended     |
| Other   | 2–3 tokens          | less standard     | ⚠️ works but worse |

#### Nested XML tags

Tags can be nested — creates information hierarchy. 2–3 levels is enough.

Nesting example:

```
<document>
    <metadata>
        <title>Report Q3 2024</title>
        <author>Analytics team</author>
        <date>2024-10-15</date>
    </metadata>
    <content>
        Revenue grew 15%...
    </content>
</document>
```

#### Tag attributes

| Attribute                     | Purpose              | Example                                   |
| ----------------------------- | -------------------- | ----------------------------------------- |
| `source="..."`                | data source          | `<context source="McKinsey 2024">`        |
| `id="..."`                    | attribution marker   | `<doc id="report_q3">`                    |
| `lang="..."`                  | content language     | `<text lang="en">`                        |
| `do_not_copy_language="true"` | avoid language drift | `<en_source do_not_copy_language="true">` |

#### Namespace for grouping

When you have 5+ tags, use prefixes to group by function:

```
<input:article>
Article text for analysis...
</input:article>

<input:comments>
User comments...
</input:comments>

<rules:content>
• Use ONLY facts from input:article
</rules:content>

<output:format>
JSON: {"summary": "...", "facts": [...]}
</output:format>
```

#### Universal XML prompt template

```
<role>
You are [specialization]. Your task is [main function].
</role>

<context>
[Background, situation, constraints]
</context>

<task>
[Concrete task: what exactly to do]
</task>

<rules>
• [Constraint 1]
• [Constraint 2]
• [Output format]
</rules>

<output>
[Expected output structure]
</output>

<example>
Input: [example input]
Output: [example correct answer]
</example>
```
