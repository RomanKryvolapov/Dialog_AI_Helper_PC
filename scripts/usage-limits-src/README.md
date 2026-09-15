# usage-limits — subscription budget awareness hook

A Claude Code hook that keeps the session aware of how much of the account's
Claude subscription has been used, so work is paced to the real remaining budget
instead of guessing.

## What it does

- **Reads live usage** from `https://api.anthropic.com/api/oauth/usage` using the
  local OAuth token that `claude login` already stored (no API key). Covers the 5-hour
  and 7-day windows plus the per-model weekly buckets. Data is account-global — it
  reflects every Claude session on the subscription.
- **Injects context** so the model sees the numbers: a `[session-budget]` block at
  session start and a `[limits]` block on every prompt — the same table as the status
  line, plus a `BURN` row (rate, forecast, pace) and a `ZONE` row (zone, binding
  limiter, what it allows); session start also names the active model.
- **Gates subagent / workflow spawns** (`PreToolUse`): denies them in the RED zone and
  asks once per session in ORANGE — it remembers that it asked, never the answer — so a hot budget
  can't be blown by a fan-out without one deliberate decision first.
  **One exemption, by name:** a `change-reviewer` spawn is always allowed, in every zone. It is
  the independent grading pass the finishing-work rule requires on every change — one agent per
  revision — and blocking it does not save budget, it removes the only reviewer who is not the
  author.
- **Powers the status line** and **exposes JSON** (`--mode json`) for planning.

## Modes

| Command                                  | Purpose                                  |
| ---------------------------------------- | ---------------------------------------- |
| `--mode inject --event SessionStart`     | `[session-budget]` block → model context |
| `--mode inject --event UserPromptSubmit` | `[limits]` block → model context         |
| `--mode gate`                            | PreToolUse gate for Agent/Task/Workflow  |
| `--mode json [--model <id>]`             | full computed state as JSON              |
| `--mode statusline`                      | status-line string (see below)           |

The status line is three lines. The first is this session's own context; the other two put
the limits side by side — the 5-hour window, the 7-day window, then every per-model weekly
bucket the API reports (whether or not that model is the active one), each as a pair of
stacked lines:

- **LIMIT** — how much of the budget is spent: the percentage, the gauge, and "RESET AT" plus
  the clock time the limit resets at (a weekday is added once the reset is not today). It is a
  point in time; the wait itself is on the line below.
- **TIME** — how much of the window has already run out: the same gauge again, and how long
  is left, clock-style (`3:49` below a day, `6 days 17:25` above one).

**The two are meant to be read against each other.** Both fill the same way — one cell per
started 5 %, so neither reads empty once something has happened — which makes the comparison
direct: a fuller LIMIT bar than TIME bar means the budget is running out faster than the clock
and will not last to the reset. When the TIME bar fills up, the limit resets. A limit whose
reset time the API did not return keeps its LIMIT line and simply has nothing below it.

The CONTEXT line on top is not a subscription limit: it is how many tokens the last request
carried — fresh input plus everything written to or read from the prompt cache — taken from
the session transcript Claude Code passes the hook, next to the window it is measured against
(1M by default, `context_window_tokens` in the config). Its gauge is one cell per percent, so it reads
straight as the number beside it, and it is drawn with a lighter block than the limit gauges
so the two are never confused. Subagent turns are skipped — they run in their own
context — and after a compaction the number simply drops. On the first prompt of a session
the transcript holds no reply yet, so the line is absent rather than reading zero.

```
CONTEXT  634k/1M   63 %  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
5 HOURS   33 % LIMIT ███████░░░░░░░░░░░░░ RESET AT 04:00      7 DAYS   13 % LIMIT ███░░░░░░░░░░░░░░░░░ RESET AT MON 18:00          FABLE    0 % LIMIT ░░░░░░░░░░░░░░░░░░░░
-         46 %  TIME ██████████░░░░░░░░░░ RESET AFTER 2:41              4 %  TIME █░░░░░░░░░░░░░░░░░░░ RESET AFTER 6 days 16:41
```

The dash in the first column of the last line is not decoration: Claude Code trims leading
whitespace off every status-line row, so without a non-blank character there the whole TIME
line would slide left and stop lining up with the LIMIT line above it.

The percentages keep a fixed field and the captions one width, so the gauges start in the same
column on both lines. The picture is wide — around 170 columns with three limits — so a
narrower terminal will wrap it.

Optional config: `.claude/usage-limits-config.json` (fetch TTL, gate on/off, zone
thresholds, `context_window_tokens`). The behaviour rule that consumes it is `.claude/rules/session-budget.md`.

## How it runs

`.claude/settings.json` calls the thin POSIX launcher `.claude/hooks/usage-limits`,
which `uname`-detects the OS and execs the matching prebuilt binary committed under
`scripts/` (`usage-limits-{darwin-arm64,linux-amd64,windows-amd64.exe}`). Because the
binaries are committed, end users need no Go toolchain — only the launcher. The hook
never breaks the session: on any error it exits 0 — the `inject` and `gate` modes stay
silent, `statusline` prints `LIMITS -> N/A` and `json` prints an error document.

## Rebuild

```sh
sh scripts/usage-limits-src/build.sh   # cross-compiles all three binaries (needs Go)
```
