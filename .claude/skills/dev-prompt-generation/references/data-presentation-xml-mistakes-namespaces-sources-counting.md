## About this file

**What’s inside:** Common XML mistakes, grouping/namespaces, source marking (numbered docs), attribution/grounding, two-stage prompts, and System-2 style counting with chunking.

**Hub:** [../SKILL.md](../SKILL.md)

---

#### Common XML tag mistakes

<rules>
❌ Meaningless tags do NOT work:
• `<xyz>`, `<aaa>`, `<block1>` → model ignores them
• Only semantic tags work: `<context>`, `<draft>`, `<solution>`

❌ Unclosed tags:
• Every opening `<tag>` must have closing `</tag>`

❌ Too deep nesting:
• 3+ levels hurt understanding
• Prefer JSON inside one tag over 5 levels of XML
</rules>

### Namespace for grouping

- When 5+ tags, use prefixes: `input:`, `rules:`, `output:`
- Groups tags by function; model sees hierarchy

### Source marking

**When to apply:** Multiple documents or RAG; answers must be grounded in sources; attribution required.

**Why it works:** Structured context and explicit source marking improve attribution and reduce hallucinations. Two-stage "first quote the relevant passage, then answer" improves document adherence. Numbered document blocks help the model know boundaries and total count. Prevents mixing contexts; model knows who said what. For two-stage RAG/grounding, see retrieval-then-reason and quote-then-answer practices in RAG literature.

#### Numbered documents

```
[DOCUMENT 1 OF 3]
first document text
[END DOCUMENT 1]

[DOCUMENT 2 OF 3]
second document text
[END DOCUMENT 2]

[DOCUMENT 3 OF 3]
third document text
[END DOCUMENT 3]
```

Template `[DOCUMENT N OF M]...[END DOCUMENT N]` tells the model total count and boundaries of each.

#### Marking with attributes

```
<doc id="smith" author="John Smith" source="Forbes 2024">
"AI market will triple by 2027. We see exponential growth."
</doc>

<doc id="jones" author="Jane Jones" source="Analytics Report">
"Do not overestimate AI growth. There are infrastructure limits."
</doc>

---

## Attribution rules

When citing, ALWAYS use [doc_id].
Format: "quote" — [author, source]

NEVER attribute words from one document to another author.
```

#### Context grounding

To avoid fabrication — constrain the model to the source:

```
<context source="Report Q3 2024">
[document text]
</context>

---

RULES:
• Use ONLY information from <context>
• Do not add external knowledge
• If not in context — write: "Data not in document"
• When citing use: [from context]
```

#### Two-stage prompt

For strict document adherence: first ask to quote the relevant passage, then answer based on it. Two-stage approach reduces hallucinations.

### System-2 Counting

**When to apply:** Counting words, items, or occurrences in long text (30+ elements). Model accuracy drops to near zero when counting "in head".

**Why it works:** Split text with delimiter `│`, count in each part separately, output intermediate results as text, then sum. Chunking with step-by-step counting and explicit intermediate results is empirically validated; latent counts are stored per part and aggregated via attention. Model must "see" intermediate numbers in its own output or summation fails. Study: [Mechanistic Interpretability of Large-Scale Counting in LLMs through a System-2 Strategy](https://arxiv.org/abs/2601.02989).

The model cannot accurately count 30+ elements. Split into chunks — accuracy goes from ~5% to ~95%.

#### Why models count poorly

Transformers have no "counter variable". Information is spread across layers:

| Element count | Accuracy           |
| ------------- | ------------------ |
| Up to 10      | accurate           |
| 15–20         | errors             |
| 30+           | near zero accuracy |

#### Recommended chunk size

| Model size            | Accurate count up to | Recommended chunk size |
| --------------------- | -------------------- | ---------------------- |
| Small (7B params)     | ~8 elements          | 5–7                    |
| Medium (13B–70B)      | ~12 elements         | 8–10                   |
| Large (GPT-4, Claude) | ~15 elements         | 10–12                  |

#### Split template with │

```
Text below is split by │

Instructions:
1. Count the word "type" IN EACH PART separately
2. Write intermediate results
3. Sum at the end

Output format:
Part 1: [number]
Part 2: [number]
Part 3: [number]
---
Total: [sum]

Text:
[first 10 sentences] │ [next 10] │ [next 10]
```

#### Key point

Output intermediate results as TEXT. Without this the model cannot sum correctly — it must "see" the numbers in its own answer.
