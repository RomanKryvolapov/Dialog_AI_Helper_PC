## About this file

**What’s inside:** Backticks/code fences for “code-only” answers, self-check blocks (□ lists), verification-first and quit instructions, INoT (solver/critic pattern).

**Hub:** [../SKILL.md](../SKILL.md)

---

### Backticks for code

Open code block → model closes it with code, not chatter.

<example type="bad">
"Sure! I'll help you write a sort function. 
Here is a Python example that uses the algorithm..."
</example>

<example type="good">
Write a sort function in Python

```python
</example>

### Self-check

**When to apply:** When output correctness or format is critical (e.g. JSON, structured reports, factual answers).

**Why it works:** Iterative self-check before output improves accuracy; in experiments the model finds errors in its own reasoning and multi-solution voting increases final accuracy. Step-level check further improves error detection and correction. Model does not self-check by default — you must ask (e.g. checklist with □). Studies: [SelfCheck: Using LLMs to Zero-Shot Check Their Own Step-by-Step Reasoning](https://arxiv.org/abs/2308.00436); [Learning to Check: Step-Level Self-Correction](https://arxiv.org/abs/2402.13035).

Model checks itself before output. Model does not self-check by default. If you ASK — it will.

#### Control levels

| Level | Description | Accuracy |
|-------|-------------|----------|
| No control | just answer | ~60% |
| Ask to check | check yourself | ~75% |
| Checklist | "□ schema fields" | ~85% |
| Validation | JSON schema | ~92% |
| Iterative correction | 3 passes | ~97% |

#### Self-check block format

```

<self_check>
Before output, check:

□ All required fields filled?
□ Types match schema?
□ No forbidden words?
□ Length within limit?
□ Output language correct?

If any item fails — fix BEFORE output.
</self_check>

```

#### Self-check for text

```

<self_check>
Before finalizing:

□ Main thesis covered?
□ Concrete examples/numbers?
□ No repetition or fluff?
□ Word limit respected?
□ CTA at the end?
□ Tone matches TA?

If not — revise.
</self_check>

```

#### Verification-first approach

Instead of "think step by step and find the answer" → "here is an answer [any], verify it, then give the correct one". Verification before generation improves accuracy.

#### Quit instructions

Teach the model to stop and ask instead of acting under uncertainty:
```

If unsure — write 'Need clarification: ...' instead of guessing.

```

#### INoT (Introspective Negotiation of Thought)

**When to apply:** High-stakes or critical decisions; when a single answer must be checked before output. Model "debates" with itself (solver + critic) then revises.

**Why it works:** INoT gives ~7.95% accuracy gain and ~58% token reduction vs iterative user dialogue "answer → user critique → new answer". Fewer rounds with user, higher accuracy on critical choices. Study: [Introspection of Thought Helps AI Agents (INoT)](https://arxiv.org/abs/2507.08664).

Example pattern:
```

<AGENT_1 role="Solver">
→ Propose a solution to the task
</AGENT_1>

<AGENT_2 role="Critic">
→ Find weaknesses in AGENT_1's solution
→ List specific issues
</AGENT_2>

<AGENT_1>
→ Revise the solution given the criticism
</AGENT_1>

REPEAT 2-3 rounds UNTIL consensus
OUTPUT final_solution

```

```
