#!/usr/bin/env node
// Cross-platform notification sound for Claude Code hooks (Notification, StopFailure).
// Plays the bundled notify.wav on Windows, macOS, and Linux.
// Invoked from .claude/settings.json:  node "${CLAUDE_PROJECT_DIR}/.claude/sounds/play-sound.cjs"
const { spawnSync } = require("child_process");
const path = require("path");

const sound = path.join(__dirname, "notify.wav");

function run(cmd, args) {
  try {
    const r = spawnSync(cmd, args, { stdio: "ignore" });
    return !r.error && r.status === 0;
  } catch {
    return false;
  }
}

if (process.platform === "win32") {
  // Built-in .NET sound player, no extra tools required.
  run("powershell", [
    "-NoProfile",
    "-Command",
    `(New-Object Media.SoundPlayer '${sound}').PlaySync()`,
  ]);
} else if (process.platform === "darwin") {
  run("afplay", [sound]);
} else {
  // Linux / other: try the common players in order, stop at the first that works.
  const players = [
    ["pw-play", [sound]],                                             // PipeWire
    ["paplay", [sound]],                                              // PulseAudio / PipeWire
    ["aplay", ["-q", sound]],                                         // ALSA
    ["play", ["-q", sound]],                                          // SoX
    ["ffplay", ["-nodisp", "-autoexit", "-loglevel", "quiet", sound]], // FFmpeg
    ["canberra-gtk-play", ["-f", sound]],                            // libcanberra
  ];
  for (const [cmd, args] of players) {
    if (run(cmd, args)) break;
  }
}
