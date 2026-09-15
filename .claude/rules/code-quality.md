# Code quality — clean solutions only, and never a patch over a cause

Every change is judged the way an architect would judge it, not the way a ticket-closer would: not
"does it make the symptom go away" but **"is this what this code should look like now"**. A change
that works and leaves the codebase worse has failed.

Half an hour spent on a clean solution beats ten minutes spent on one that costs an hour of
bug-fixing afterwards. That trade is not close, and it is the trade this rule exists to force.

---

## Find the cause; never patch the symptom

**When something misbehaves, the first job is to find the mechanism that produces it** — the exact
place the wrong value, the wrong order or the wrong state comes from. Only then is a fix designed.

Forbidden as fixes, always:

- **A special case for the input that failed.** An `if` naming the value from the bug report, a
  branch for "when it comes from that screen", a guard added at the point where the crash happened
  rather than where the bad state was created.
- **A retry, a delay, a re-fetch or a "refresh twice" that makes a race look solved.**
- **Swallowing the error** — a catch that logs nothing, a default that hides a missing answer, a
  fallback that makes a failure indistinguishable from a success.
- **Widening a type or loosening a check** so the wrong value stops being wrong.
- **A second source of truth** kept in sync by hand because the first one was inconvenient.

Each of those makes the next defect harder to find and moves the real cause further out of sight.
Two or three of them stacked in one area is how a codebase becomes unmaintainable — and every one
of them was, at the time, the fast answer.

**If the clean fix is genuinely out of reach right now** — it needs a migration, a decision that is
not yours, work far outside the task — then say so explicitly: what the real cause is, what the
temporary measure does, and what it will take to remove it. Written down, at the place it sits, and
in the report. A temporary measure nobody knows about is a permanent one.

---

## Clean, and no more than that

Two failures, opposite in direction, equally bad:

**Under-designed** — the crutch above: layers of special cases, duplicated logic, names that lie,
state nobody owns, a function that grew a fourth responsibility because that was where the change
landed.

**Over-designed** — an abstraction with one implementation, an interface introduced "for later", a
configuration switch nobody asked for, a factory producing one thing, indirection that has to be
traced through four files to answer a simple question. Generality that no requirement calls for is
cost with no return, and it is _harder_ to remove later than a missing abstraction is to add.

The test for both: **can a competent developer who has never seen this code read the change and say
what it does, and why it is where it is?** If the answer needs you to explain, the code is not
finished.

---

## SOLID, applied as judgement rather than ritual

- **One reason to change.** A class, module or function does one thing. When a change forces you to
  touch something for a reason unrelated to its purpose, that is the design telling you where the
  seam should have been.
- **Extend rather than edit** where behaviour varies — but only where it actually varies. Do not
  build the extension point for a variation nobody has.
- **A subtype must be usable wherever its base is**, without the caller knowing which it got.
- **Narrow interfaces.** A consumer should not depend on methods it never calls.
- **Depend on the abstraction, not the detail** — especially across a boundary the project already
  draws (a port and its adapter, a domain layer and its infrastructure). Follow the boundaries this
  project already has; do not invent new ones for a single change.

And the ones SOLID does not name but that matter as much here: **no duplicated logic** (two places
that must be changed together will not be), **no dead code** left behind, **names that say what the
thing is**, and **failures that are loud** — an error that is swallowed is worse than one that
crashes.

---

## When it is wrong, rewrite it

If a piece of code cannot be made clean by editing — the structure is wrong, the responsibilities
are tangled, the change would be the fourth patch on the same spot — **rewrite it.** From scratch if
that is what it takes. Rewriting a function or a module is ordinary work, not a special event, and it
is usually cheaper than the third patch.

The limits: a rewrite must keep the behaviour that is depended on (find the consumers first — see
[architectural-forks](architectural-forks.md), sign 1), must be verified as thoroughly as new code
(see [finishing-work](finishing-work.md)), and must stay inside the task. Rewriting a subsystem the
task did not ask about is scope, not quality.

---

## When the design is unclear, decide from the user's side

Not every question has a technical answer. When two implementations are both defensible, the
tie-break is **what the person using this will experience** — and specifically:

- **Transparent** — the person can tell what happened and why. Nothing important happens silently.
- **Simple** — the fewest moving parts they have to hold in their head; no mode, flag or state they
  did not ask for.
- **Predictable** — the same action does the same thing every time, and the surprising case is the
  one that gets the explanation.

**Do not speculate about preferences** — what somebody "would probably like", what "feels modern",
what a competitor does. You do not know that, and guessing produces features nobody wanted. The
three properties above are the only assumptions you may make about a user, and they hold for
everyone.

---

## Checklist

- [ ] The **mechanism** behind the defect was found and named before any fix was written.
- [ ] The fix removes the cause. No special case for the failing input, no retry papering over a
      race, no swallowed error, no loosened check, no second source of truth.
- [ ] Any unavoidable temporary measure is written down where it sits **and** in the report, with
      the real cause and what removing it takes.
- [ ] Not under-designed: no duplicated logic, no lying names, no unowned state, no function that
      grew an unrelated responsibility.
- [ ] Not over-designed: no abstraction with one implementation, no extension point for a variation
      that does not exist, no indirection a reader must trace to answer a simple question.
- [ ] The change reads correctly to someone who has never seen this code, without you explaining it.
- [ ] Responsibilities, boundaries and dependencies follow the structure this project already has.
- [ ] Code that could not be made clean by editing was rewritten, with its consumers found first.
- [ ] Where the design was a judgement call, it was settled on transparency, simplicity and
      predictability — never on a guess about what someone would prefer.
