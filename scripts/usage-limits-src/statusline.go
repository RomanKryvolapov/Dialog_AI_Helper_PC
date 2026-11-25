// statusline.go — the status line: what Claude Code hands it, and how it is drawn.
//
// The status line is the one output a person reads rather than a model or a program, so it is the
// only one painted and the only one fitted to the terminal. It is also the only one Claude Code
// hands the real size of the context window and its own 5-hour and 7-day figures, which stand in
// for the usage API's, without a word, when those have gone stale. Beside the CONTEXT gauge it
// writes how long the prompt cache stays warm, and last, under the limits, how many tokens the
// session and the account have used, followed by the model and the effort the session runs on. Of
// the limits it draws the account's windows, each per-model weekly bucket above 0% as a line
// of its own inside the 7-day block.

package main

import (
	"encoding/json"
	"os"
	"strconv"
	"strings"
	"time"
)

// statusInput is the part of the status line's input this hook reads. Any field can be missing:
// Claude Code sends the limits only to a subscriber and only after the first reply, and an older
// Claude Code sends fewer fields altogether.
type statusInput struct {
	TranscriptPath string `json:"transcript_path"`
	Model          *struct {
		ID          string `json:"id"`
		DisplayName string `json:"display_name"` // "Opus 5.5"; a plugin is handed the id alone
	} `json:"model"`
	// Effort is the reasoning effort the session is set to. Claude Code sends none for a model that
	// takes no effort setting, and hands a plugin none at all; see renderPanel.
	Effort        *effortLevel `json:"effort"`
	ContextWindow *struct {
		TotalInputTokens  float64 `json:"total_input_tokens"`
		ContextWindowSize float64 `json:"context_window_size"`
		// Null before the session's first reply and again right after a compaction, which is when
		// the total beside it cannot be taken at its word.
		CurrentUsage json.RawMessage `json:"current_usage"`
	} `json:"context_window"`
	RateLimits *struct {
		FiveHour *liveWindow `json:"five_hour"`
		SevenDay *liveWindow `json:"seven_day"`
	} `json:"rate_limits"`
	PromptCache *promptCache `json:"prompt_cache"`
	// ContextEstimate is the panel plugin's alone: what /context estimates the context holds, sent
	// while no request has measured it — before a session's first reply; see renderPanel.
	ContextEstimate float64 `json:"context_estimate"`
}

// promptCache is how long the prompt cache stays warm, as Claude Code reports it to a status line —
// or, for the panel, which Claude Code hands no such reading, as the transcript shows it; see
// transcriptPromptCache.
type promptCache struct {
	Warm            bool     `json:"warm"`
	CachingObserved bool     `json:"caching_observed"`
	TTL             string   `json:"ttl"`        // the lifetime it was written with: "5m" or "1h"
	ExpiresAt       *float64 `json:"expires_at"` // Unix seconds; null once nothing is cached
}

type effortLevel struct {
	Level string `json:"level"` // low, medium, high, xhigh or max
}

// modelText is what the session runs on, as the status line writes it at the end, after the token
// counts: "MODEL Opus 5.5 EFFORT high". The name is Claude Code's own where the input carries it, and
// is otherwise worked out from the model's id; the effort is left out where there is none. Empty
// where the input names no model.
func (in *statusInput) modelText() string {
	name := ""
	if in.Model != nil {
		name = strings.TrimSpace(in.Model.DisplayName)
		if name == "" {
			name = modelName(in.Model.ID)
		}
	}
	effort := ""
	if in.Effort != nil {
		effort = strings.TrimSpace(in.Effort.Level)
	}
	switch {
	case name == "":
		return ""
	case effort == "":
		return "MODEL " + name
	}
	return "MODEL " + name + " EFFORT " + effort
}

// modelName is a model's name worked out from its id, the way Claude Code names its own models:
// "claude-opus-5-5" is Opus 5.5, "claude-opus-4-0" Opus 4, "claude-3-5-haiku" Haiku 3.5, and a dated
// "claude-sonnet-4-5-20250929" Sonnet 4.5. An id that asks for the million-token window,
// "claude-opus-5-5[1m]", is Opus 5.5 (1M context): Claude Code writes it so for the models its
// catalogue marks for it, and this, which cannot see that catalogue, for every model. Any other
// suffix in brackets is no part of the name. An id of any other shape, another provider's or an
// alias, is written as it is.
func modelName(id string) string {
	id = strings.TrimSpace(id)
	base, suffix := id, ""
	if i := strings.Index(id, "["); i > 0 {
		base = strings.TrimSpace(id[:i])
		if strings.HasSuffix(id, "[1m]") {
			suffix = " (1M context)"
		}
	}
	rest, ok := strings.CutPrefix(base, "claude-")
	if !ok {
		return id
	}
	parts := strings.Split(rest, "-")
	if last := parts[len(parts)-1]; len(parts) > 1 && len(last) == 8 && isDigits(last) {
		parts = parts[:len(parts)-1]
	}
	family, version := "", []string{}
	for _, part := range parts {
		switch {
		case family == "" && part != "" && strings.Trim(part, "abcdefghijklmnopqrstuvwxyz") == "":
			family = part
		case len(part) > 0 && len(part) <= 2 && isDigits(part):
			version = append(version, part)
		default:
			return id
		}
	}
	if family == "" || len(version) == 0 || len(version) > 2 {
		return id
	}
	if len(version) == 2 && version[1] == "0" {
		version = version[:1]
	}
	return strings.ToUpper(family[:1]) + family[1:] + " " + strings.Join(version, ".") + suffix
}

func isDigits(s string) bool {
	return s != "" && strings.Trim(s, "0123456789") == ""
}

type liveWindow struct {
	UsedPercentage *float64 `json:"used_percentage"`
	ResetsAt       *float64 `json:"resets_at"` // Unix seconds
}

// decodeStatusInput reads what it can. A field of an unexpected type costs that field and not the
// rest, and input that is not JSON at all leaves every field empty.
func decodeStatusInput(raw string) *statusInput {
	in := &statusInput{}
	if raw != "" {
		_ = json.Unmarshal([]byte(raw), in)
	}
	return in
}

// liveLimits turns the limits Claude Code handed over into the shape the usage API answers in. A
// window whose reset has already passed says nothing about the one running now, and is left out.
func (in *statusInput) liveLimits(now time.Time) *usageRaw {
	if in.RateLimits == nil {
		return nil
	}
	window := func(w *liveWindow) *usageWindow {
		if w == nil || w.UsedPercentage == nil || w.ResetsAt == nil {
			return nil
		}
		reset := time.Unix(int64(*w.ResetsAt), 0)
		if !reset.After(now) {
			return nil
		}
		return &usageWindow{Utilization: *w.UsedPercentage, ResetsAt: reset.UTC().Format(time.RFC3339)}
	}
	five, seven := window(in.RateLimits.FiveHour), window(in.RateLimits.SevenDay)
	if five == nil && seven == nil {
		return nil
	}
	return &usageRaw{FiveHour: five, SevenDay: seven}
}

// statusContext is the CONTEXT reading from Claude Code's own figures: the real size of the window
// the model has, which the configured size can only guess at, and the tokens the last request
// carried. Where Claude Code has no reading yet, the transcript is read instead, as the injected
// block does.
func (in *statusInput) statusContext() contextReading {
	r := contextReading{window: contextWindowTokens}
	if cw := in.ContextWindow; cw != nil {
		if cw.ContextWindowSize > 0 {
			r.window = cw.ContextWindowSize
		}
		if isJSONValue(cw.CurrentUsage) && cw.TotalInputTokens > 0 {
			r.tokens, r.ok = cw.TotalInputTokens, true
			return r
		}
	}
	r.tokens, r.ok = contextTokens(in.TranscriptPath)
	return r
}

// cacheText is how long the prompt cache stays warm, painted on the gauges' scale; see cacheReading.
// Nothing when Claude Code reports no cache: an older version, or no reply yet.
func (in *statusInput) cacheText(now time.Time, p painter) string {
	text, at, ok := in.cacheReading(now)
	if !ok {
		return ""
	}
	return "CACHE " + p.paint(text, p.gaugeColour(at))
}

// cacheReading is how long the prompt cache stays warm — a wait, COLD, or WARM where Claude Code
// gives no end — and where that stands on the gauges' scale: green with the whole cache lifetime
// ahead, red as it runs out, and a cold cache red. Past that, the next request writes the whole
// context into the cache again — the dearest request a long session makes. Not ok when Claude Code
// reports no cache.
func (in *statusInput) cacheReading(now time.Time) (text string, at float64, ok bool) {
	c := in.PromptCache
	if c == nil || !c.CachingObserved {
		return "", 0, false
	}
	if !c.Warm {
		return "COLD", 1, true
	}
	if c.ExpiresAt == nil {
		return "WARM", 0, true
	}
	left := time.Unix(int64(*c.ExpiresAt), 0).Sub(now).Minutes()
	if left <= 0 {
		return "COLD", 1, true
	}
	// The lifetime the cache was written with, "5m" or "1h"; an hour where Claude Code does not say.
	lifetime := 60.0
	if d, err := time.ParseDuration(c.TTL); err == nil && d > 0 {
		lifetime = d.Minutes()
	}
	return formatWait(left), 1 - left/lifetime, true
}

// isJSONValue reports whether a field was present and not null.
func isJSONValue(raw json.RawMessage) bool {
	s := strings.TrimSpace(string(raw))
	return s != "" && s != "null"
}

// painter colours text for a terminal. The zero value paints nothing, which is what every output
// but the status line uses. rgb says the terminal draws 24-bit colour; without it the gauges fall
// back to the 256-colour palette.
type painter struct{ on, rgb bool }

// statusPainter is the status line's painter: on, unless NO_COLOR says otherwise, and in 24-bit
// colour everywhere but Apple's Terminal; see gaugePalette256.
func statusPainter() painter {
	return painter{on: os.Getenv("NO_COLOR") == "", rgb: os.Getenv("TERM_PROGRAM") != "Apple_Terminal"}
}

func (p painter) paint(s, code string) string {
	if !p.on || code == "" || s == "" {
		return s
	}
	return "\x1b[" + code + "m" + s + "\x1b[0m"
}

// statusWidth is how many columns the status line may use, from the terminal size Claude Code
// passes in COLUMNS; zero when it passes none.
func statusWidth() int {
	n, err := strconv.Atoi(strings.TrimSpace(os.Getenv("COLUMNS")))
	if err != nil || n <= 0 {
		return 0
	}
	return max(n-statusMarginCols, 1)
}

// limitWindows is where the limits' own windows opened, counted back from their resets: the account's
// tokens are counted in them. Both are zero without a weekly reset to count back from, and the
// account's tokens are then left out; fiveStart alone is zero where there is no running 5-hour
// window, and the 5-hour count is then left out.
func limitWindows(st *state) (fiveStart, weekFrom time.Time) {
	if st == nil || st.r7Local.IsZero() {
		return time.Time{}, time.Time{}
	}
	if st.hasSession && !st.r5Local.IsZero() {
		fiveStart = st.r5Local.Add(-time.Duration(sessionWindowMin) * time.Minute)
	}
	return fiveStart, st.r7Local.Add(-time.Duration(weeklyWindowMin) * time.Minute)
}

// limitsState is what the limits are now, for the status line and the panel: for the model Claude
// Code names, or the one worked out from the session where it names none, with Claude Code's own
// figures standing in for stale ones of the usage API's. Nil without any figures.
func (in *statusInput) limitsState(args argSet, hookInput map[string]any, now time.Time) *state {
	activeModel := ""
	if in.Model != nil && in.Model.ID != "" {
		activeModel = cleanModelString(in.Model.ID)
	} else {
		activeModel = getActiveModel(args, hookInput)
	}
	return getState(args, activeModel, in.liveLimits(now))
}

// renderStatusline draws the whole status line.
func renderStatusline(args argSet, rawInput string, hookInput map[string]any, now time.Time) string {
	in := decodeStatusInput(rawInput)
	st := in.limitsState(args, hookInput, now)
	fiveStart, weekFrom := limitWindows(st)
	p := statusPainter()
	// The CONTEXT line carries how long the cache stays warm and, two spaces on, the token counts,
	// where the terminal has room for them; see renderOpts.tokens.
	o := renderOpts{p: p, width: statusWidth(), contextTail: in.cacheText(now, p),
		tokens: tokensText(in.TranscriptPath, in.modelText(), fiveStart, weekFrom, now)}

	var lines []string
	if st == nil {
		// No limits to line the CONTEXT gauge up with: all of it beside the gauge, which shortens to fit.
		ctx := in.statusContext()
		o.contextTail = o.withTokens()
		if top := topLine(ctx, o, headsFor(nil, ctx), ctxBarCells); top != "" {
			lines = append(lines, top)
		}
		lines = append(lines, "LIMITS -> N/A")
	} else {
		lines = append(lines, renderLimits(statusRows(st, now), in.statusContext(), o))
	}
	if rows := configRows(); len(rows) > 0 {
		lines = append(lines, renderTextRows(rows))
	}
	return strings.Join(lines, "\n")
}
