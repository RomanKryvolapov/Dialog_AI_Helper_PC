# Setting the project up on a new machine — instructions for Claude Code

This file is written for a Claude Code session that has just been given a fresh clone of this repository and asked
to get the app running. Work through the steps in order. Each step says what to check first, what to install only
if it is missing, and how to tell it worked. Do not skip the checks: most machines already have part of this.

What the app is and how it is built is in [README.md](../README.md) and [CLAUDE.md](../CLAUDE.md); read both before
changing any code. This file covers only the set-up.

## What you cannot do yourself

Stop and ask the person for these; everything else is yours to do.

- **API keys.** The person pastes them into the app (Settings → API keys) or into `.env` themselves. Never ask for
  a key in the chat, and never read `.env`: the project's settings deny it on purpose.
- **Which audio device the speech comes from** — a microphone, or a virtual cable carrying a call.
- **Installing system software that needs administrator rights** (a JDK installer, Python, NVIDIA drivers,
  VoiceMeeter): say what is missing and why, and let the person install it or agree to you doing it.

## 0. Before anything else

- The app is tested on **Windows 11 x64 only**. The vendored sherpa-onnx native library in `libs/` is the Windows
  x64 build, so the Sherpa-ONNX recogniser does not work elsewhere without replacing that jar.
- An NVIDIA GPU is optional. With one, Whisper runs on it; without one it falls back to the CPU by itself, slower.
- Read `.claude/settings.json` and tell the person two things about it before you rely on it: it sets
  `defaultMode` to `bypassPermissions` (tools run without asking), and it pins the model. Both are the owner's
  choices for their own machine; the person may want their own in `.claude/settings.local.json`.

## 1. Java

One JDK is enough: **JDK 27**. The app compiles and runs on it (the toolchain in `build.gradle`), and Gradle 9.9
itself runs on any JDK from 17 to 27.

1. Run `java -version`. If it is 17 or newer, do nothing more: Gradle starts on it, and when it is not 27, Gradle
   finds a JDK 27 in the usual places or downloads one by itself (the `foojay-resolver-convention` plugin in
   `settings.gradle`).
2. If it is older (Java 8 is still the default on many machines) or missing, look for a newer JDK already
   installed (`~/.jdks/`, `C:\Program Files\Java`, `C:\Program Files\Eclipse Adoptium`) and point `JAVA_HOME` at
   it for the Gradle commands, for example in Git Bash: `export JAVA_HOME="/c/Users/<user>/.jdks/jdk-27+35"`.
   Only if none exists, install JDK 27 (Eclipse Temurin).

Check: `./gradlew --version` prints Gradle 9.9.2 and a launcher JVM between 17 and 27.

## 2. Build and test

```bash
./gradlew --no-daemon build
```

It compiles, runs the unit tests and assembles. The first run needs the network: the libraries come from Maven
Central, the Gradle plugins from the Gradle Plugin Portal, and JDK 27 is downloaded if the machine has none. The
two sherpa-onnx jars are already in `libs/`. Read the output, not only the exit code: the build must end in
`BUILD SUCCESSFUL` and the reports under `build/test-results/test/` must show no failures.

## 3. Python, for the Whisper recognisers

Needed only for the Whisper batch and Whisper streaming recognisers. Sherpa-ONNX, Speechmatics and the voice
models work without it.

1. Check `py -0` (or `python --version`) for **Python 3.14**; the pinned packages are built for it.
2. Create the environment exactly where the app looks for it and install the pinned packages:

   ```bash
   cd python
   py -3.14 -m venv .venv
   ./.venv/Scripts/python.exe -m pip install --upgrade pip
   ./.venv/Scripts/python.exe -m pip install -r requirements.txt
   ```

   The download is several gigabytes (PyTorch with its CUDA libraries).
3. Do not "fix" the versions `requirements.txt` holds back. Its header says which ones and why; the short of it is
   that newer builds silently lose the GPU or break transcription.

Check, after step 4 has given you a model:

```bash
./.venv/Scripts/python.exe whisper_util.py <any speech .wav> ../models/faster-whisper-base.en en
```

It prints the text that was said. A line `Whisper: cuda failed: …` on stderr followed by the text means it worked
on the CPU: fine on a machine without an NVIDIA GPU, a problem to report on one that has it.

## 4. Speech models

Models are large and git-ignored; they go into `models/` in the project root (create it).

- **Whisper** — a faster-whisper model folder from [Hugging Face: Systran](https://huggingface.co/Systran), for
  example `Systran/faster-whisper-base.en` (small and quick, English only) or `Systran/faster-whisper-base`
  (multilingual). The folder must hold `model.bin`, `config.json`, `tokenizer.json` and the vocabulary file.
- **Sherpa-ONNX** — a *streaming* model from the
  [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases) (the `asr-models` release), for example
  `sherpa-onnx-streaming-zipformer-en-2023-06-26`, unpacked. The folder must hold the `encoder`, `decoder` and
  `joiner` `.onnx` files and `tokens.txt`; it also brings `test_wavs/` with real speech, handy for the check above.

Ask the person which languages they need before choosing; download only what the method they will use needs.

## 5. Settings and keys

1. Copy `.env.example` to `.env` in the project root. It lists every key with a comment; `.env` is git-ignored.
2. Fill in what is not secret yourself by telling the person the lines to add — you cannot read or edit `.env`:
   the model folders (`DIALOG_WHISPER_MODEL_PATH`, `DIALOG_SHERPA_MODEL_PATH`) and the speech language
   (`DIALOG_QUESTION_LANGUAGE`, a code such as `en`).
3. The person adds at least one API key, in `.env` or in the app: `DIALOG_GOOGLE_API_KEY` (Gemini has a free
   tier and is the default translation model), `DIALOG_OPENAI_API_KEY`, `DIALOG_CLAUDE_API_KEY`,
   `DIALOG_SPEECHMATICS_API_KEY`. Where to get each is in README, "LLM models".

Everything in `.env` can also be set in the app's Settings screen, where a saved value wins over `.env`.

## 6. Run it

```bash
./gradlew --no-daemon run
```

Run it in the background: the task stays at `> Task :run` for as long as the window is open. Then, with the person:

1. **Settings → General**: choose the audio device and the translation method (how the speech is heard → which
   model translates). Whisper batch with Gemini 3.5 Flash-Lite is the default.
2. On a small screen, **Settings → General → Interface size** makes everything smaller.
3. **Start dialog** in the top bar. If a method cannot start, a message says what it lacks (a key, a model folder).
4. The person says a phrase: the Mic meter moves, the phrase appears on **Messages** and its translation under it.

To translate a call rather than a microphone, the call's sound has to reach an input device: a virtual audio
cable such as [VB-Audio VoiceMeeter Banana](https://vb-audio.com/Voicemeeter/banana.htm), chosen as the audio
device. That is the person's to install and route.

**Restarting.** A running instance holds the audio device, so stop it before starting another. Stopping the Gradle
task does not stop the app's own JVM on Windows; find and stop it by its main class (PowerShell):

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*app.MainKt*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

## 7. Claude Code's own set-up for this repository

The repository carries its instructions for Claude Code; a few things are per machine and are not in it.

- **Already in the repository, nothing to install:** `CLAUDE.md`, the rules in `.claude/rules/`, the skills in
  `.claude/skills/`, and the hooks in `.claude/hooks/` (the usage-limits status line and a sound when a turn
  ends). The hooks are small launchers for prebuilt programs in `scripts/` (Windows x64, Linux x64, macOS arm64),
  so they need nothing but Git Bash on Windows.
- **Serena** (code navigation, which `CLAUDE.md` requires for `.kt` and `.py` files) is a per-machine MCP server.
  It needs [uv](https://docs.astral.sh/uv/); check `uvx --version` first. Add it for this project, with the real
  path of the clone:

  ```bash
  claude mcp add --scope local serena -- uvx --python 3.13 --from git+https://github.com/oraios/serena serena start-mcp-server --context claude-code --language-backend LSP --project <path to the clone>
  ```

  Python 3.13 is named because Serena does not support 3.14; `uvx` fetches it by itself. The tools appear only
  after the Claude Code session is restarted.
- **Context7** (library documentation) is optional and also per machine. The person gets a key with
  `npx ctx7 login` and adds the server in their own terminal; never put the key in a prompt or in a file of the
  repository, and never run `ctx7 setup` here. The exact command is in `.claude/rules/web-search-when-in-doubt.md`.

## Checklist

- [ ] Windows 11 x64; the person told about `bypassPermissions` and the pinned model in `.claude/settings.json`.
- [ ] A JDK between 17 and 27 runs Gradle (`./gradlew --version`); a missing JDK 27 left to Gradle to fetch.
- [ ] `./gradlew --no-daemon build` ends in `BUILD SUCCESSFUL`, no failed tests.
- [ ] For Whisper: Python 3.14, `python/.venv` created, `requirements.txt` installed unchanged.
- [ ] Models for the chosen recogniser in `models/`; a real speech file transcribed with `whisper_util.py`.
- [ ] `.env` copied from `.env.example`; model folders and language set; at least one API key added by the person.
- [ ] The app started, the audio device and the translation method chosen, a spoken phrase heard and translated.
- [ ] Serena added for this clone and the session restarted; Context7 only if the person wants it.
- [ ] Nothing of `.env`, `models/`, `cache/`, `voice/` or `dialogs/` staged for a commit.
- [ ] Anything that could not be done or checked reported as such, with what it waits on.
