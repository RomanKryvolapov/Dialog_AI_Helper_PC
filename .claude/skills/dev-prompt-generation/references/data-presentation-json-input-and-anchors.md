## About this file

**What’s inside:** JSON as structured input (rules, examples), comparison anchors / benchmarks in JSON.

**Hub:** [../SKILL.md](../SKILL.md)

---

### JSON structuring of input data

**When to apply:** Many related attributes; nested structures; lists of same-type items; data from API/DB.

**Why it works:** JSON keeps related facts "neighbors" in context, improving extraction and reasoning; structured input with clear instructions improves accuracy on long contexts. One pair `{}` per object; extra braces add tokens and confusion. For complex output formats (XML, BPMN), request intermediate JSON and convert in code — edit success can grow ~3×.

For related object attributes use JSON instead of plain text. JSON groups attributes together — model processes more accurately.

<rules>
• Use only one pair of braces `{}` for a JSON object
• Extra braces `{{{{...}}}}` cause confusion and more tokens
• JSON schema in the prompt is a structure description, not a multi-brace template
</rules>

#### Example: plain text vs JSON

❌ Bad: plain text context

```
Analyze the client. Name: Alex Smith, age 34,
city New York, position Senior Developer at TechCorp,
salary 350000, married, two kids, interests: skiing
and programming, last purchase Jan 15 —
MacBook Pro for 250000...
```

❌ Bad: excess braces

```
{{{{"answer": "string", "summary": "string"}}}}
```

✅ Good: JSON structure

```
Analyze the client:

{
  "profile": {
    "name": "Alex Smith",
    "age": 34,
    "city": "New York"
  },
  "work": {
    "position": "Senior Developer",
    "company": "TechCorp",
    "salary_rub": 350000
  },
  "family": {
    "status": "married",
    "children": 2
  },
  "interests": ["skiing", "programming"],
  "purchases": [
    {"date": "2026-01-15", "item": "MacBook Pro", "price": 250000},
    {"date": "2025-11-20", "item": "iPhone 16", "price": 120000}
  ]
}
```

#### When to use JSON

| Situation                            | Format |
| ------------------------------------ | ------ |
| Many related attributes              | JSON   |
| Nested structures (object in object) | JSON   |
| Lists of same-type elements          | JSON   |
| Data from API/DB                     | JSON   |

#### Reference points: JSON as comparison anchors

Adding reference data to JSON (market averages, benchmarks, history) lets the model make more accurate comparative conclusions:

```
{
  "current": {"revenue": 1200000, "margin": 15},
  "benchmarks": {
    "industry_avg": {"revenue": 800000, "margin": 12},
    "top_10_percent": {"revenue": 2500000, "margin": 22}
  },
  "history": [
    {"year": 2024, "revenue": 900000, "margin": 11},
    {"year": 2025, "revenue": 1100000, "margin": 14}
  ]
}
```
