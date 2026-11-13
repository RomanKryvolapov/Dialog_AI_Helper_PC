# Before starting work — other sessions, pull, read what arrived, check versions

Every message from the developer that you act on opens the same way: find out what the other sessions
in this repository are doing, bring the branch up to date, read what arrived and let all of it change
the work in hand; for work that reads or changes the repository, also check that what the project
stands on is still current. All of it before planning, editing or reviewing — two sessions on one
thing, a stale branch, an unread commit or an unsupported runtime costs nothing visible on the day and
a great deal later.

- The trigger is the developer's message, not the size of the work: before planning it, before reading
  code for it, before answering a question about a tracker entry. Once per message, not once per step.
- Skip it only when no repository is in play — a question answered without opening anything here.
- A message from another Claude session is not a trigger: answer it (section 1) and run none of this.

## 1. Ask the other sessions working here

Sessions that do not know about each other overwrite each other's edits and commit, pull or switch the
branch under each other. Before the pull:

- **Find them**: `claude agents --json` lists every live session on this machine with its folder (`cwd`)
  and the name `SendMessage` addresses (`name`); `ListAgents` shows no folders. This repository's
  sessions work in this checkout, in one of its worktrees (`git worktree list`) or in another clone of
  it; yours is the entry whose `sessionId` equals the `CLAUDE_CODE_SESSION_ID` environment variable.
  None → nothing to ask.
- **Ask each**, one `SendMessage` per session, all at once: say what you are about to do, and ask for its
  task, the files and areas it changes or will change, its checkout and branch, what it holds
  uncommitted or staged, what it keeps running, and any commit, pull, merge or branch switch it has
  coming. A session that has already told you in this conversation what it is doing is asked again
  only while it is busy.
- **Edit nothing until they answer**, the pull included. Answers arrive on their own between your tool
  calls, so fetch and read the incoming commits meanwhile. A session with no answer once you have read
  them — silent, or reported undeliverable — is not a yes: keep off whatever its checkout shows
  uncommitted, and say it did not answer.
- **Overlap** — the same files, the same contract or schema, the same running service or the same
  task → stop and tell the developer what collides before touching it.
- **A checkout shared with another session**: never switch the branch, stash, reset, clean or rebase
  there, and commit only your own paths, by path (`git commit -- <paths>`) — the index is shared, and a
  plain `git commit` takes whatever the other session staged.
- **Asked by another session**: answer at once — it waits on you before editing — with the same facts,
  and say whether its plan collides with yours.

## 2. Pull before acting on the message

### Which branch to bring in

1. `git fetch`, and look at how far behind you are and what is coming in.
2. On the working branch CLAUDE.md names under "Branch and runtime": `git pull --ff-only` on a clean
   tree, and pull nothing else. Branches downstream of it carry release merges — downstream is not a
   parent.
3. On a branch cut from it: also merge the parent's tip into your branch — the working branch, and first
   any side branch yours was cut from. Never rebase anything already pushed. This merge goes into your
   own branch only; merging your branch into the parent publishes your work and needs the developer's
   words (rule `finishing-work`). Three preconditions, each a stop rather than something to work around:
   - a clean tree;
   - a parent you can name — git records nothing about where a branch was cut from, so where the branch
     name, the task or CLAUDE.md does not make it plain, ask;
   - a branch to merge into — on a detached HEAD, say so and stop.

   A conflict is handled by rule `merge-conflicts`.

### Read what arrived

A pull nobody read did not happen. Before writing anything, go through the incoming commits and answer:

- **What moved under the task** — the files it touches, the contract, schema or migration it assumes, the
  neighbouring feature it calls. Part of it may already be done, moved, or made wrong.
- **What arrived in the trackers** — new bugs and tasks in the area, entries whose status moved. A new bug
  may be the defect you are about to fix; a new task may already own your change.
- **What arrived in the requirements** — wherever the project keeps them: its `req-*` skills, a requirements
  document, or the skill that defines what a correct run is. A changed requirement changes what correct
  means and outranks any older tracker entry about the same behaviour.

Then say in one line what the other sessions are doing, what came in, and what it changes for the work
— "nothing relevant" included. In a work report that line belongs in its first part, and it is where a
tracker id belongs in a reply.

### When the tree is not clean

Fetch first and compare the incoming files with your changed files:

- No overlap → fast-forward with the changes in place.
- Overlap on generated files (indexes, a generated client, a lock file) → restore them to the committed
  state, pull, regenerate. Never resolve a generated file by hand.
- Overlap on real work → stop and tell the developer what collides before touching anything. No force, no
  reset, nothing of theirs discarded.

### When the fetch fails

A failed fetch is a stop, not permission to continue. "Repository not found" on a private repository
almost always means the wrong account is active, not a missing repository: check which account the
credential helper uses and whether it can see the repository, and report what you found rather than
switching accounts on a guess.

## 3. Check what the project stands on

On every piece of work that reads or changes the repository — not on every message.

### The deployed runtime is never left behind

- Compare what is deployed with the newest version its vendor supports; CLAUDE.md names the runtime and
  its vendor clocks under "Branch and runtime". A runtime past its support window costs money, security
  patches and options.
- Behind → bring every place the version is declared current in the same change, so local and deployed
  stay on one line.
- Moving a live environment is announced first: say what it takes, get the go-ahead, move one major
  version at a time. Leaving it behind in silence is never acceptable.

### Everything else: update what is quick, report what is not

- Quick — a patch or minor bump, or a major whose only effect is a rename the tooling migrates: do it as
  part of the task, run the gates, mention it in one line.
- Not quick — code changes, a configuration rewrite, a data migration, or a result only a human eye can
  judge: name what is out, what it would take and what standing still costs, and let the developer decide.
- A version held back on purpose, with the reason written down, is not stale: raise it as a question.
- Read the list of published versions, not a registry's "latest" label.
- Never skip silently: either the update is done or the developer is told it is available.

## Checklist

- [ ] The repository's other sessions found with `claude agents --json` and asked before the pull, told what you are about to do.
- [ ] Nothing edited, the pull included, before they answered; one that did not answer kept clear of and reported.
- [ ] An overlap told to the developer before touching it; in a shared checkout no branch switch, stash, reset, clean or rebase, and commits by path only.
- [ ] A question from another session answered at once, without running this opening for it.
- [ ] Branch brought up to date before acting on the message; once per message.
- [ ] On a cut branch, the parent merged in — into your own branch, on a clean tree, never a rebase of pushed work.
- [ ] A parent you could not name, or a detached HEAD, asked about rather than guessed.
- [ ] Incoming commits read for the task's files, the tracker entries in its area and the requirements that moved.
- [ ] What the other sessions are doing, what came in, and what it changes, said in one line.
- [ ] A dirty tree handled by comparing file lists; generated files regenerated; collisions with the developer's work reported.
- [ ] A failed fetch reported; "repository not found" treated as the wrong account first.
- [ ] The deployed runtime checked against its vendor's support; if behind, declarations updated and the live move proposed.
- [ ] Quick bumps done with the gates re-run, slow ones named, deliberate ceilings respected, published versions checked.
