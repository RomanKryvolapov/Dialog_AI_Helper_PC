# Dialog AI Helper - PC Version

## What is this app for?

You can use this app to translate dialogue or to generate suggestions during a dialogue in real time.

The application's operation consists of 2 stages:

* **Voice recognition**: Recognize each sentence in a dialogue in a continuous mode

* **Sending to LLM**: Send the recognized text with a prompt to LLM models and receive streaming responses

## Voice recognition

The recognition engine is selectable in **Settings → General**. Three engines are available:

| Engine | Where it runs | Notes |
|--------|---------------|-------|
| **Whisper batch** (default) | [faster-whisper](https://github.com/SYSTRAN/faster-whisper), one Python subprocess per chunk | Most accurate. Audio is split into chunks by silence detection and each chunk is transcribed one-shot. Needs a Whisper model + the Python `.venv`. |
| **Whisper streaming** | faster-whisper, one persistent Python process | Lower latency: audio is streamed in and sentences are committed while you speak. Same model + `.venv` as batch. |
| **Sherpa-ONNX** | in-process (no Python), CPU | Lowest latency, fully streaming, no subprocess. Uses a sherpa-onnx streaming model folder. |

### Whisper (batch & streaming) setup

1. Install the Python dependencies from `python/requirements.txt` into `python/.venv/` (CUDA-enabled PyTorch and `faster_whisper`).
2. Download a Whisper model (for example from [Hugging Face: faster-whisper](https://huggingface.co/Systran)) and unpack it to a folder.
3. In **Settings → Whisper batch** (or **Whisper streaming**), set the **Whisper model path** to that folder.
4. In **Settings → General**, select an input audio device and the question language code (e.g. `en`, `ru`).

If a CUDA-capable GPU is available, transcription runs on GPU automatically; otherwise it falls back to CPU.

### Sherpa-ONNX setup

1. Download a sherpa-onnx **streaming** model (e.g. `sherpa-onnx-streaming-zipformer-en-…` from the [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases)) and unpack it.
2. In **Settings → Sherpa-ONNX**, set the model folder (it must contain the `encoder` / `decoder` / `joiner` `.onnx` files and `tokens.txt`).
3. Runs on CPU in-process — no Python needed. Tune segmentation with **Max words per message** and **Force-translate after ms** (force-finalize a phrase that has been hanging too long).

### Chunking parameters (Settings → Whisper batch / Whisper streaming)

| Parameter | Default | What it does |
|-----------|---------|--------------|
| Silence threshold % | 10 | Volume level below which audio is considered silence |
| Max silence ms | 500 | How long a silent gap closes the current chunk |
| Max chunk duration ms | 5000 | Hard upper bound on a single chunk's length |
| Max concurrent Whisper processes | 5 | How many transcription subprocesses can run in parallel |

## LLM models

The app supports three streaming LLM backends via [langchain4j](https://docs.langchain4j.dev/):

* **Google AI** — Gemma 3 / Gemma 3n / Gemma 4 / Gemini 2.0 / Gemini 2.5 family.  
  Get an API key from [Google AI Studio](https://aistudio.google.com/app/apikey) (or enable the Gemini API in [Google Cloud Console](https://console.cloud.google.com/apis/api/generativelanguage.googleapis.com/) and use a [Cloud API key](https://cloud.google.com/docs/authentication/api-keys)).  
  Different models have different [free usage limits](https://ai.google.dev/gemini-api/docs/rate-limits) — the model list in the app shows requests/min, requests/day and tokens/min for each one. The default model is `gemma-3-27b-it`, which gives 14,400 free requests per day.

* **OpenAI** — GPT-5.6 Terra / GPT-5.6 Luna (the two smaller tiers of the 5.6 family) and GPT-5.4 mini / GPT-5.4 nano. Requires an [OpenAI API key](https://platform.openai.com/api-keys).

* **Anthropic Claude** — Claude Sonnet 5 / Claude Haiku 4.5 (the two cheapest current models). Requires an [Anthropic API key](https://console.anthropic.com/settings/keys).

Paste the relevant API token into the **Settings** tab. If a model hits its limits, just switch to another one in the model dropdown.

## Settings from a `.env` file

Every field of the **Settings** screen can also be filled from a `.env` file in the project root —
handy for keeping API tokens out of the app's saved settings and out of git.

Copy `.env.example` to `.env` and fill in what you need. Each setting is resolved in this order:

1. the value saved in the app — wins whenever it is filled in
2. an OS environment variable of the same name
3. the `.env` file
4. the built-in default

So `.env` is a fallback, not an override: it supplies a setting while the corresponding field in
the app is still empty. The app only stores a setting you actually changed away from the `.env` /
built-in value, so editing `.env` and restarting takes effect for everything you have not chosen
yourself in the app. A setting you did choose keeps winning — to hand it back to `.env`, clear that
field in Settings and save. Whenever a `.env` key is overridden that way, the Console category says
so. `.env` is git-ignored; `.env.example` lists every key.

## Prompts

Multiple named prompts are stored and can be switched between in **Settings → Prompts**. Built in by default:

* **Translate text** — translates the recognized English text to Russian
* **Translate text (+1 … +5 previous sentences)** — same, but also feeds the model the last 1–5 recognized sentences as context, so a mid-sentence fragment is translated as a seamless continuation of the ongoing speech
* **Answer the question** — answers a recognized question in Russian

Placeholders replaced at request time: `{{source_text}}` (the current recognized text) and, for the context prompts, `{{previous_text_1}}` … `{{previous_text_5}}` (the preceding sentences, newest = `1`).

## Tabs

There are two top-level tabs:

* **Messages** — the live loop: shows recognized speech, voice-recognition status, the model's streaming answer, and a microphone toggle button
* **Settings** — a master-detail screen with a category sidebar:
  * **General** — recognition engine, input device, question language
  * **LLM** — selected model, streaming-response toggle, API tokens
  * **Whisper batch** / **Whisper streaming** / **Sherpa-ONNX** — per-engine model path & tuning
  * **Prompts** — select and edit prompts (saved in the prompts map)
  * **Console** — captures `stdout` / `stderr` from the running process for debugging

## Use for conferences

If you need to translate dialogue from a conference, route the conference audio into the app and to your speakers at the same time using a virtual audio cable. [VB-Audio VoiceMeeter Banana](https://vb-audio.com/Voicemeeter/banana.htm) works well for this.

## Additional information

* Tested only on Windows.
* The `{{source_text}}` tag in a prompt is replaced with the recognized text.
* CUDA 12.1 + PyTorch 2.5.1 is the tested combination for the Python side (see `python/requirements.txt`).

## Future features

* Function calls via LangChain
* Refine-answer button — for each LLM answer

<br>

![Screenshot_messages](Screenshot_messages.png)<br>
![Screenshot_prompt](Screenshot_prompt.png)<br>
![Screenshot_settings](Screenshot_settings.png)<br>
