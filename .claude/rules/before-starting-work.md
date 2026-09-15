# Before starting work

**Every piece of work that reads or changes this repository opens the same way**: bring the branch
up to date, then check that what the project stands on is still current. Both happen _before_
planning, editing or reviewing — not after, and not "if there is time".

Skipping either has the same shape of cost, and it is never visible on the day. A stale branch means
work built on a base that no longer exists. A stale dependency means work built on a version whose
vendor has stopped supporting it — which shows up as a surcharge, a security hole, or a migration
three times harder than it would have been.

---

## 1. Pull the branch you will land on

1. **Fetch, then look.** `git fetch` and see how far behind the branch is and what is coming in.
   A quiet fetch costs nothing; it also tells you whether anything relevant to your task moved.
2. **Fast-forward.** With a clean tree, `git pull --ff-only` on the working branch — the one
   CLAUDE.md names under "Branch and runtime", unless the task says otherwise.
3. **Read what arrived** if it touches your area: the incoming file list, and the tracker entries
   among them. Someone may have already done part of the task, or numbered an entry you were about
   to number.

The same pull happens again at the other end of the work — before any turn that changed files ends, and
again if a commit is asked for — so that the state reviewed is the state that lands. That half
belongs to [finishing-work](finishing-work.md).

### When the tree is not clean

Uncommitted work does not excuse skipping the pull — it changes how you do it.

- **Fetch first and compare** the incoming file list against your own changed files. No overlap →
  fast-forward proceeds cleanly with the changes still in the tree.
- **Overlap on generated files** (index files, a regenerated client, a lock file) → restore them to
  the committed state, pull, then regenerate. Never resolve a generated file by hand.
- **Overlap on real work** → stop and tell the developer what collides, in plain language, before
  touching anything. Do not force, do not reset, do not discard their changes to make the pull go
  through.

### When the fetch itself fails

A fetch that fails is not permission to carry on without one — say so and stop, the same as any
other blocked step.

One failure reads as something it is not: **"repository not found" on a private repository almost
always means the wrong account, not a missing repository.** The host hides the existence of a
private repository from anyone who cannot see it, so a failed authentication and a deleted
repository give the identical message. Where several accounts are signed in, only one is active at a
time and the git credential helper uses that one — an account switch elsewhere silently breaks every
fetch here. Check which account is active and whether it can see the repository before concluding
anything, and tell the developer what you found rather than switching their accounts around on a
guess.

---

## 2. Check what the project stands on

Version currency is part of starting work, not a separate project. Two duties, one mandatory and
one proportional.

### The platform you deploy on is never left behind — this one is not optional

A runtime past its vendor's support window is the one kind of staleness that costs money and
options rather than comfort: it is billed at a penalty rate where the vendor charges for it, stops
receiving security patches, and is eventually upgraded on the vendor's schedule rather than yours.
What counts as "the runtime" differs by project — a managed cluster, a language line, a base image, a
database engine, a hosting platform — and each of those has its own vendor clock.

- **Check it every time**: what is deployed, and what the newest supported version is. Compare them.
  CLAUDE.md names this project's runtime, and what "supported" means for it, under "Branch and
  runtime" — a managed cluster, a language line, a base image and a database engine each have
  their own vendor clock.
- **If it is behind, that is a finding you act on, not one you note.** Bring the repository's
  declarations current in the same change — every place the version is written, so local and
  deployed stay on the same line.
- **Moving a running environment is announced first.** It is an operation on a live system, it goes
  one major version at a time, and the pieces around it do not follow on their own. Say what it will
  take and get the developer's go-ahead, then do it. What is never acceptable is leaving it behind
  in silence.

### Everything else: update what is quick, report what is not

Dependencies, base images, build actions, language and tool versions.

- **Quick — just do it, as part of the task.** A version bump that is a number change plus green
  gates: patch and minor releases, and a major whose only effect is a rename the tooling itself
  migrates. Do it, run the gates, mention it in one line.
- **Not quick — tell the developer and offer.** A bump needing code changes, a configuration
  rewrite, a data migration, or one whose result only a human eye can judge (anything that changes
  how the product looks). Name what is out, what it would take, and what standing still costs.
  Then let them decide.
- **A ceiling the project set on purpose is not stale.** Where a version is deliberately held back
  and the reason is written down, respect it and raise it as a question rather than lifting it.
- **Check what is actually published, not what is advertised.** A registry's "latest" and "beta"
  labels are a curated pointer, not the list of releases — a stable line can sit unlabelled behind
  a newer major. Read the published versions before concluding a version does not exist.

**Never skip silently.** Either the update is done, or the developer is told it is available. A
version left behind with nobody informed is the one outcome this section exists to prevent.

---

## When this does not apply

A question answered from the conversation, an explanation, anything that touches no file — no pull
and no version check needed. This rule is about work that reads or changes the repository.

## Checklist

- [ ] Branch fetched and fast-forwarded **before** planning, editing or reviewing.
- [ ] Incoming changes that touch the task's area actually read, tracker entries among them.
- [ ] Dirty tree handled by comparing file lists first; generated files regenerated, never hand-merged.
- [ ] A collision with the developer's own uncommitted work reported, not forced through.
- [ ] A failed fetch reported, not worked around; "repository not found" on a private repository
      treated as the wrong account until proven otherwise.
- [ ] The deployed runtime checked against its vendor's supported versions; if behind, the
      repository brought current and the live move proposed — never left behind in silence.
- [ ] Other versions: the quick ones updated with the gates re-run, the slow ones named to the
      developer with what they would take.
- [ ] Deliberate version ceilings respected and raised as questions, not lifted.
- [ ] Version claims made from the list of published releases, not from a registry's labels.
