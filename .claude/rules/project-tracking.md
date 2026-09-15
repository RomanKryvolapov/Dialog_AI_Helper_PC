# Project tracking — bugs, tasks, work log

The repository carries its own bug list and task list under `management/`. They are plain markdown
files — no database and no validator. The only derived artifacts are the two index files, and the
same script that creates entries rebuilds them. **Read them before you work** (what prior sessions did,
what is still open) and keep them current as you go.

## The tracker holds the product and nothing else

**One test decides whether something belongs here at all: does it run at the customer's side?**
Everything that does is the product — the applications, the shared libraries they are built from,
the database schema, and the infrastructure that deploys and runs them. Work on that is tracked.

**Everything that exists only to help us build it is never an entry** — not a bug, not a task, no
matter who asked or how big the job was: this repository's documentation, the rules and the skills,
this tracker and its script, hooks, service and build scripts, CI pipelines, the folder layout, the
audit log. That work is simply done, and the commit plus the automatic log under `management/logs/`
is its complete record. Filing it as an entry buries the real product list under housekeeping —
which is exactly what the tracker exists to prevent.

**Tasks vs bugs — inside that scope, keep the two separate:**

- **Bugs** = defects in the product's own code — the application behaving wrong: backend, frontend,
  API, data, or the infrastructure it runs on. **QA always records findings as bugs**, never tasks,
  and any product defect noticed during other work goes here too. **Fixing a bug does NOT create a
  task.**
- **Tasks** = product work a developer asked for: new functionality and rework of existing
  functionality in that same code.

**Never an entry at all**, however broken it looks or however much work it was: a stale document, a
dead link, a wrong path in a rule or a skill, a flaw in this tracker or in a service script, a hook
that misreads its input, a CI misconfiguration, a renamed folder, a rule someone asked for. None of
it reaches the customer, so none of it is a bug — and turning it into a task is the same mistake
wearing a different label. **Do not file it. Do the work and let the commit speak.**

In doubt about a piece of work: could a user of the product notice it — as a defect they hit, or as
something delivered to them? **No → no entry.**

## Where things live

| What                                     | Where                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bugs (**one file per bug**)              | `management/bugs/<module>/BUG-XXXX-<slug>.md`; closed bugs move to `management/bugs/done/` with `git mv`. `<module>` is the area folder — see "Which folder an entry goes in".                                                                                                                                                                                                                                                                                            |
| Tasks (**one file per task**)            | `management/tasks/<module>/TASK-XXXX-<slug>.md`, the same shape as a bug; closed tasks move to `management/tasks/done/` with `git mv`. Related tasks may share a `batch:` label (the Jira key `CA-56`, or `<date>-<slug>`) — it groups them in the index and never puts two tasks in one file.                                                                                                                                                                            |
| Screenshots (bugs and tasks)             | `management/bugs/attachments/<MONTH>-<YEAR>/` and the same under `tasks/` — the month the shot was filed in, spelled like the index files (`JULY-2026`). See "Screenshots" below.                                                                                                                                                                                                                                                                                         |
| Audit log (automatic, hook-written)      | `management/logs/<author-slug>/<date>/<session8>.log` — one folder per developer, a folder per day, one file per session. Never edit it by hand.                                                                                                                                                                                                                                                                                                                          |
| Indexes (generated)                      | `management/bugs/INDEX-<MONTH>-<YEAR>.md` and the same under `tasks/` — one file per month, every entry of that month in one list oldest first. Rebuilt by `python management/new.py index`; never edited by hand.                                                                                                                                                                                                                                                        |
| Bug table (generated, **not committed**) | `qa-artifacts/bug-tracker.tsv` — every bug on one flat row, open first and worst severity first, with the symptom, the expected behaviour, the requirement id, the dates, the path to the full entry and the latest Log line. A QA-side convenience view for sorting and sharing outside the repository, so `qa-artifacts/` is ignored and the file is never committed: the same command rebuilds it from the entries on whichever machine runs it. Never edited by hand. |

**The entry is the original; everything else is a view of it.** QA writes the bug once, as the entry
file, in the form a developer works from — symptom, expected, steps, where to look, fix checklist. The
monthly index and the flat table are generated from those files by the script, and are overwritten on
the next run. So there is no hand-off step and nothing to copy anywhere: a bug filed with
`python management/new.py bug --found=app` is already in the developers' folder and already in the index — both
committed, and all a developer needs. The flat table is the same data in one row per bug, rebuilt
locally by whoever wants to sort it outside the repository. Editing either changes nothing — the next
run of the script erases the edit.

## Which folder an entry goes in

Bugs and tasks share the same folders, and a folder is named after **the requirement file that owns
the area** — the `req-*` skill without its prefix: `req-uc-ai-assistant` → `ai-assistant`,
`req-uc-prompt-library` → `prompt-library`, `req-uc-brand-voice` → `brand-voice`. One requirement
file, one folder, the same name on both sides of the tracker.

- **A new requirement file gets its folder** the first time an entry needs one.
- **A renamed requirement takes its folder with it** — `git mv` in the same change, so the two never
  drift apart.
- **Never a second spelling** for an area that already has a folder. Look at what exists before
  inventing a name, and keep the existing spelling even where it is shorter than the requirement's.
- **Product work no requirement owns** — the infrastructure the applications run on, cross-cutting
  technical work — goes in `platform/`. It is the only folder with no requirement behind it, and it
  takes both sides: a deployment defect is a bug like any other. It is **not** a home for repository
  housekeeping — that is not an entry at all, see the scope at the top of this rule.

## Creating an entry

One script, standard-library Python, no install and no Node — QA can run it too:

```
python management/new.py bug --found=app --module=auth --severity=P1 --title="Sign-in loops on expired token"
python management/new.py task --module=auth --priority=P1 --title="One password for both apps"
```

It makes the entry's id, fills the template, writes the file and rebuilds the two index
files. That is all it does: nothing validates or cross-checks the entries afterwards. **An id is four characters of digits and capital
letters, drawn at random** — `BUG-2W7F`, `TASK-G45D` — never the next number in line. A counter is
what makes two people collide: each reads the same highest number on their own branch, each adds
one, and git merges both files happily because their names differ. Ids are global and permanent
across all modules: never renumbered, never reused. Entries raised before this keep the sequential
numbers they were given.
Copy the template by hand if the script is unavailable.

A task takes an optional `--batch` when it belongs with others (`--batch=CA-56`,
`--batch=2026-07-28-admin-invites`). The label is written into the task's front matter and shown in
the index — it groups related tasks, it never merges them into one file.

**Severity:** `P0` crash / data loss / release blocker · `P1` feature broken · `P2` inconvenience / edge case · `P3` cosmetic.

**Bug status — one value, and it says where the bug stands.**

_How it was found_ — set by whoever files it, QA or developer alike:
`FOUND_IN_CODE` · `FOUND_IN_APP` · `FOUND_IN_CODE_AND_APP`.

_How the developer confirmed it_ — set by the developer, and one of the three before a single line
of code is changed: `CONFIRMED_IN_CODE` · `CONFIRMED_IN_APP` · `CONFIRMED_IN_CODE_AND_APP`.

_While the fix happens:_ `IN_PROGRESS` (being written) · `IN_REVIEW` (up for review, not merged) ·
`BLOCKED` (the entry says what it waits on) · `RESOLVED` (fixed and checked by the developer,
waiting for QA) · `REOPENED` (QA: the fix did not hold, or the bug came back).

_Closed_ — each needs a `closed:` date and a move to `done/`: `CLOSED_FIXED` · `CLOSED_NOT_A_BUG` ·
`CLOSED_WONT_FIX` · `CLOSED_CANNOT_REPRODUCE` · `CLOSED_DUPLICATE`.

The important half of that list is `RESOLVED`: it is **not** a closed state. A bug leaves the list
when QA has seen the fix, not when a developer says it is done — `CLOSED_FIXED` is QA's word and
nobody else's. The three `CONFIRMED_` values are the developer's alone, and one of them is set
before any code is touched: a bug the developer has not established is a bug being fixed by guess.
A deliberate decision not to fix is `CLOSED_WONT_FIX` with the reason and the decider in the Log,
and a defect nobody can establish either way is `CLOSED_CANNOT_REPRODUCE` with what was tried
written down.

**Assignee:** the person who **wrote the code** that produced the defect — the author of the change and
of its pull request. Not whoever reviewed it, not whoever merged it, not whoever happens to be free. It
is filled from the history of the feature the bug sits in: find the change that implemented that
behaviour and take its author. Leave it empty rather than guess; an entry assigned to the wrong person
costs more than an unassigned one. For a defect that belongs to no single change — a gap in the
environment, a requirement never implemented — leave it empty and say so in the Log.
**Priority:** `P0` critical · `P1` high · `P2` normal · `P3` low.
**Task status:** `TODO` · `IN_PROGRESS` · `IN_REVIEW` · `BLOCKED` · `RESOLVED` (done and checked by the developer, waiting for QA) · `REOPENED` (open) · `CLOSED_DONE` · `CLOSED_CANCELLED` (closed — needs a `closed:` date and a move to `done/`).

## The language of an entry

**An entry is written in Latin script — English, unless the developer said otherwise.** The id, the
title, the file name, the front matter, the section headings and the Log lines are read by people who
do not share a language, and the file name is built from the title by a slug that keeps only `a-z0-9`:
a title in another script collapses to `BUG-004-entry.md`, leaving the id as the only thing the name
carries.

**Only the description may be in another script, and only when the developer asked for it** — a
report is sometimes worth keeping in the words it arrived in. Never switch on your own initiative:
with no such request the entry is English end to end. Even with it, the title, the front matter and
the headings stay Latin, so the index and the file name still read.

## Screenshots

A screenshot is part of the entry, so it lives in the repository next to it — no external service and
no credentials, and an agent working on the entry can open the image itself. Bugs and tasks work the
same way:

```
python management/new.py attach BUG-001 shot.png --note="sign-in loops right after the fix"
```

The script does the filing, so nobody has to remember the shape:

- **Folder** — `bugs/attachments/<MONTH>-<YEAR>/`, or the same under `tasks/`: the month the shot was
  **filed** in — the command stamps the moment it runs, not the moment the picture was taken —
  spelled the way the index files are (`JULY-2026`).
- **Name** — `BUG-001_30-JULY-2026_18-12_<slug>.png`: the entry id first, then the day, month, year
  and time it was filed, then a few words from the note. A file that turns up on its own is still
  traceable, and inside a month folder the shots of one entry line up in order.
- **Line in the entry** — appended to its `## Screenshots` section, carrying the same moment in words,
  the note, and the image. **Append only:** a bug that changed over three weeks holds a shot per
  state, and an old line has to keep showing how it was then. Never rewrite or delete one.

Only images (png, jpg, gif, webp), only under 4 MB, cropped to what matters — git keeps every byte
forever, and the attach command refuses anything bigger or anything that is not an image. Nothing
checks a file dropped into the folder by hand, so use the command. A screen recording is not
committed: put it wherever the team keeps large files and link it from the entry.

Two consequences of tying the folder to when the shot was filed. Shots of one entry can sit in
different month folders — that is intended; the entry's own Screenshots section is the timeline, and
the id at the front of the file name finds every shot of one entry across months. And the attachments
folder sits beside the module folders, so the relative link keeps working after the entry is moved
into `done/` — there is nothing to fix on close.

The image is **not** staged for you: `git add` the picture together with the entry, or the committed
link points at nothing.

## Link the requirement, never copy it

Product behavior is specified once, in the `req-*` skills: each use case carries numbered acceptance
criteria. **Before filing a bug or a task, look there.** If a criterion already describes the
behavior in question, reference it by id in the entry's `req:` front-matter field — both sides carry
it — and do not restate the rule in the entry.

Criterion ids read `AC-<UC-ID>-<NNN>` (`AC-UC-AUTH-002-001`, `AC-UC-BRANDVOICE-001-007`); a whole use
case is `UC-AUTH-002`. Write the id only; the wording stays in the skill, so the two can never drift.

- A bug's **Expected** describes the concrete observable behavior in that case; it does not re-derive the rule.
- A task's **Done criteria** covers the delivery side only (migration applied, API client regenerated, tests added).
- An empty `req:` — or a short `none — <why>` in its place — is a valid answer for infrastructure and
  refactor work, which no requirement owns.
- **The requirement wins.** If an entry and a `req-*` skill disagree, the skill is right.
- **Behavior with no criterion?** Add or update the criterion in the `req-*` skill **first**, then
  reference the new id. Do not smuggle new product behavior into a task description.

## Name a document in the body — make it a link

A document named in an entry — a `req-*` or `dev-*` skill, a rule, a file of this repository,
another entry — is a **markdown link to that document**:
`[req-uc-auth](../../../.claude/skills/req-uc-auth/SKILL.md)`. A relative path to the file, and
nothing more.

- **A link to the document, never to a place inside it.** No `#` fragments, and **never an HTML
  `<a>` tag** — not in an entry, not in the document it points at. The tracker does not add anchors
  to anything.
- **One link per document per sentence.** The first mention carries it; everything else from the
  same document stays plain — `UC-PROMPT-002` linked once, its `BR-007` and `M-03` as bare ids after
  that. Eight links to the same file in one sentence help nobody.
- **Ids are not documents.** `BR-007`, `M-03`, `EC-04` mean nothing on their own — a dozen use cases
  have a `BR-007`. Name the owning use case in the sentence and let it carry the link.
- **Relative paths only** — no absolute paths and no repository URLs, so the links work in the
  editor, on the hosting side and in any clone.
- **Front matter stays plain.** `req:` and the other YAML fields carry bare ids: markdown is not
  rendered there.
- **A historical id is not a reference.** An id that was re-issued, or an old per-batch number, points
  at nothing — leave it as plain text. An entry never links to itself either.
- **A screenshot is the one embed** — `![note](../attachments/JULY-2026/…png)`, written by the attach
  command and not by hand. Everything else stays a plain link.

## Fixing a bug — confirm it, then fix it

A report is somebody's reading of what they saw. It can be right about the screen and wrong about
the cause, right about the cause and stale, or describe behavior that turns out to be correct. So a
fix never starts from the report. **Three steps, always in this order:**

1. **Confirm it in the code.** Find the place the wrong behavior actually comes from, and be able to
   say in one sentence why that code produces what was reported. A symptom you cannot trace to a
   mechanism is **not confirmed** — keep looking. If the code turns out to be right, the entry ends
   as `CLOSED_NOT_A_BUG` with the reasoning in its Log; it is never "fixed" anyway.
2. **Confirm it on the running application, whenever that is possible.** Reproduce it against a
   running environment — through the interface for anything a user sees, through the API for
   anything underneath. This is the step that catches a bug already fixed, one that only happens
   with a particular account or piece of data, and one whose real cause sits somewhere else
   entirely. Where a reproduction is genuinely out of reach — it needs a provider that is not
   configured, a state nobody can stage, a race that will not fire on demand — say so in the
   entry's Log, name what blocked it, and go on with the code evidence alone. "It was faster not
   to" is not out of reach.
3. **Only then fix it.** On the way through, set the status to what was actually established —
   `CONFIRMED_IN_CODE_AND_APP`, or `CONFIRMED_IN_CODE` / `CONFIRMED_IN_APP` when only one of the two
   was possible — so the entry records that the defect was proven real, and by what evidence,
   **before** a fix existed for it.

Then verify the finished fix the same way round: in the code, and on the running application
wherever step 2 could reach it. That verification is what earns `RESOLVED`, and if it could not be run
the Log says so instead of implying it passed. `RESOLVED` is still open work — the entry closes only
when QA re-checks it and sets `CLOSED_FIXED`.

The same three steps apply to a defect you found yourself while doing other work, and to one a
developer reports in chat. The only thing that changes is who wrote the first sentence of it down.

## Workflow

1. **Before starting** — read the open entries under `management/bugs/` and `management/tasks/`, and
   skim the recent `management/logs/` audit, so you continue prior work rather than repeat it.
2. **A developer asks for a feature or rework of the product** → create a task entry, fill its
   requirement ids, Goal and Done criteria, then do it.
3. **A developer asks for a change to the repository itself** — a rule, a skill, the documentation,
   this tracker, a hook, a script, CI → **create nothing.** Do the work; the commit and the audit
   log are its record.
4. **A developer asks to fix a bug** → **do NOT create a task.** Look for the bug file; if it does
   not exist, create it first. Then work the three steps above: confirm it in the code, confirm it
   on the running application where that is possible, and only then fix it.
5. **QA** → always files findings as bugs, one file per finding, with a screenshot attached whenever
   the defect is something you can see.
6. **On completion** — append a dated **Log** line and update the status. Both sides work the same:
   - Developer done, bug or task alike → `status: RESOLVED` and a Log line saying what the fix or
     the work was. **Stop there:** no `closed:` date, no move — it is still open work until QA has
     seen it.
   - Re-checked by QA and holding → `CLOSED_FIXED` for a bug, `CLOSED_DONE` for a task, a `closed:`
     date, a Log line saying what was verified and where, `git mv` the file into
     `management/bugs/done/` or `management/tasks/done/`. This is the only way either list is left,
     apart from closing without a fix.
   - Fix did not hold, or the bug came back → `status: REOPENED`, clear `closed:`, `git mv` the file
     back out of `done/`, Log line saying what still fails.
   - Closed without a fix → `CLOSED_NOT_A_BUG` / `CLOSED_WONT_FIX` / `CLOSED_CANNOT_REPRODUCE` /
     `CLOSED_DUPLICATE` for a bug, `CLOSED_CANCELLED` for a task, each with its reason in the Log.
   - The move changes the entry's folder, so every relative link **into** it from a sibling and every
     link **out of** it to one has to be repointed in the same change — otherwise closing a task
     silently breaks the links its neighbours carry.
7. **Rebuild the indexes** after any change made by hand — a status edit, a `git mv` into `done/`:
   `python management/new.py index`. Creating an entry with the script does it on its own, and the
   pre-commit hook rebuilds them when `management/` files are part of the commit.
8. **The course of the work is logged automatically** by the hook. There is no hand-written session
   summary; the audit log is the record.
9. **Entries are written in Latin script**, English by default — see "The language of an entry".

## Checklist

- [ ] Before starting: read the open bug and task files and the recent `management/logs/` audit — continue, don't repeat.
- [ ] The entry is about the product — something that runs at the customer's side. Work on the repository itself (docs, rules, skills, this tracker, hooks, scripts, CI, layout) got **no entry at all**, however large: it was just done, and the commit plus `management/logs/` is its record.
- [ ] Product feature / rework → a task entry. **Fixing a bug creates no task.** **QA findings are always bugs.**
- [ ] Every bug fix ran the three steps in order: confirmed in the code, confirmed on the running application (or the Log says what made that impossible), and only then fixed — with the fix itself verified the same way before it was handed to QA.
- [ ] Entry created with `python management/new.py` (or the template copied by hand); its id is a random four-character one, not the next number in line, and no id was ever renumbered or reused.
- [ ] Entry filed in the folder named after the requirement that owns the area — `platform/` only for product work no requirement owns (never for repository housekeeping), and never a second spelling for an area that already has a folder.
- [ ] `req-*` skills checked first: an existing acceptance criterion is **linked by id**, never copied; new product behavior went into the skill first.
- [ ] Screenshots filed with `python management/new.py attach <id> <image> --note="…"` — never copied in by hand, never a line rewritten, image `git add`ed together with the entry, nothing over 4 MB and no screen recordings.
- [ ] Every document named in the body is a **markdown link to that document** — relative path, no `#` fragment, no HTML anchors anywhere; one link per document per sentence; front matter left as bare ids.
- [ ] Bug status came from the one vocabulary — a `FOUND_` value when filed, a `CONFIRMED_` value before any code was touched, then `IN_PROGRESS` / `IN_REVIEW` / `BLOCKED` / `RESOLVED`; `RESOLVED` left the entry **open**, and only QA's `CLOSED_` value closed it.
- [ ] `assignee:` names whoever **wrote the code** behind the defect — the author of the change and its pull request, not its reviewer or merger — or was left empty rather than guessed.
- [ ] On completion: the developer stopped at `RESOLVED`; only QA set a `CLOSED_*` status **plus** a `closed:` date and moved the file with `git mv` into `tasks/done/` or `bugs/done/`, with the relative links into and out of the moved entry repointed.
- [ ] The monthly `INDEX-<MONTH>-<YEAR>.md` files rebuilt and committed (`python management/new.py index`) — never edited by hand; the same run refreshes the local, uncommitted `qa-artifacts/bug-tracker.tsv`.
- [ ] Entry written in Latin script, English by default; another script appears only in the description, only because the developer asked for it, and never in the title, the front matter or a heading.
- [ ] `management/logs/` is hook-written — never edited by hand.
