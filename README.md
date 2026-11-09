# Dialog AI Helper - PC Version

## What is this app for?

You can use this app to translate dialogue or to generate suggestions during a dialogue in real time.

The application's operation consists of 2 stages:

* **Voice recognition**: Recognize each sentence in a dialogue in a continuous mode

* **Sending to LLM**: Send the recognized text with a prompt to LLM models and receive streaming responses

## Translation methods and reserves

How each phrase becomes a translation is the **translation method**, chosen in **Settings → General** as three methods in order: method 1 translates, and when it fails a phrase the phrase goes to method 2, and when that fails too, to method 3 (2 and 3 can be left empty).

Each method is chosen on two levels. The first is **how the speech is heard**:

* **Voice** — a model that hears the audio itself, so no speech recognizer runs: each phrase, cut at pauses, goes to the model as audio together with the conversation notes and recent phrases, and the model writes both what was said and its translation, streamed into the window. Phrases go one at a time in the order they were said, so each prompt carries the transcript of the phrase before it. Clear speech is transcribed accurately; on hard-to-hear audio the model, like Whisper, can mishear or paraphrase, and it is told to mark words it cannot make out as [?]. There is no second level: the same model translates.
  * **Gemini 3.5 Flash-Lite** — free tier, needs the Google API key. A phrase costs the instructions and context (about 1,000 tokens) plus 32 tokens per second of audio, and one of the 500 requests a day (summaries included); the answer starts in 1–2 seconds.
  * **GPT-Audio-1.5** — OpenAI's model that hears audio; paid, about half a cent a phrase (2–4 dollars an hour of dense conversation), the answer starts in about a second. OpenAI's regular models, GPT-6.1 Terra included, do not accept audio.
* **Recognition** — a [speech recognizer](#voice-recognition) turns the phrase into text, and the second level picks the [LLM model](#llm-models) that translates it.

Method 1 decides how the audio comes in, so methods 2 and 3 offer only what can work after it. After a voice method or Whisper (batch) the phrase is recorded: methods 2 and 3 can be a voice model, or Whisper (batch) with any translating model — after a voice method Whisper then transcribes the recording first, so it needs a Whisper model. After Whisper (streaming) or Sherpa-ONNX nothing is recorded, so methods 2 and 3 can only translate the same recognized text with another model. Changing method 1 drops a method 2 or 3 that no longer fits.

**What counts as a failure.** A phrase moves on to the next method when the request fails — a rate limit or used-up quota (HTTP 429), a rejected API key (401/403, or Google's 400 "API key not valid"), a missing model (404), any other rejected request (4xx), a server error or overload (5xx), a network error — when no answer has arrived, or the answer stops arriving, for 30 seconds, when the answer is empty or not in the expected shape, and when the method's own rate limit would keep the phrase waiting more than 3 seconds. A method whose API key is not set is skipped. "No speech" from a voice method is an answer, not a failure.

**Rest after a failure.** A method that failed rests for a while, so meanwhile the phrases go straight to the next method instead of each failing on it first; when the rest is over, the next phrase tries it again, and a success puts it back in front. It rests as long as the provider asked (or a minute) after a per-minute limit, after a used-up daily quota until midnight Pacific for Google and as long as OpenAI asks (its daily limit is a rolling window), 15 minutes when the OpenAI account is out of credit, until you change a key or the chain after a rejected key or a missing model, and 30 seconds — doubling with each failure in a row, up to 5 minutes — after a server, network or request error or no answer in time. After an unusable answer only that phrase moves on. When nothing else can take a phrase, a method resting after a passing error is tried anyway. A phrase gets three attempts in all; if none succeeds, its row says why.

On Settings → General the method that translated the latest phrase is outlined in green and a resting one in orange (its tooltip says why and until when); the status line under the messages says when method 2 or 3 is in use.

## Voice recognition

The recognizers a translation method can use on its first level (**Settings → General**):

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

Google's models are the ones that fit its free tier; for OpenAI and Claude only mid-tier models are listed — the flagships are slower and costlier than translation needs, the smallest tiers (Luna, Haiku) were weaker at recovering misrecognized words. Every model is asked to skip or minimise its reasoning, so the first words arrive quickly, and every answer streams into the Messages tab as it is generated.

* **Google AI** — the models that fit Google's free tier: Gemini 3.5 Flash-Lite (the default; text and voice) and Gemma 4 31B / Gemma 4 26B A4B (text).  
  Get an API key from [Google AI Studio](https://aistudio.google.com/app/apikey) (or enable the Gemini API in [Google Cloud Console](https://console.cloud.google.com/apis/api/generativelanguage.googleapis.com/) and use a [Cloud API key](https://cloud.google.com/docs/authentication/api-keys)).  
  Free-tier limits as AI Studio → Rate limit showed them in November 2026, which the app paces itself by (at 80% of the per-minute ones): Gemini 3.5 Flash-Lite — 15 requests/min, 500/day, about a second to the first word; Gemma 4 — 30 requests/min and 14,400/day but only 16,000 input tokens/min, which with the conversation context means roughly seven phrases a minute, and Gemma 4 31B took 10–25 seconds a phrase. Summary requests count against the same limits (about one per ten phrases). So the free tier covers about 12 phrases a minute and some 40 minutes of fast conversation a day on Flash-Lite; when speech outpaces the limit, or a day's quota is spent, the phrases go to a reserve method if you set one, and otherwise those that cannot be sent in time are marked "skipped" on screen, the status line saying why. Gemini 3.6 / 3.8 Flash are not listed: their free tier is 20 requests a day. A paid tier raises all of these limits.

* **OpenAI** — GPT-6.1 Terra (the mid tier of GPT-6, text), sent with reasoning effort `none`, and GPT-Audio-1.5 (voice only: it refuses a request without audio). GPT-6 Luna, the cheapest tier, is left out: in a test it translated misrecognized words literally where Terra recovered them; GPT-Audio Mini is cheaper but deprecated. Requires an [OpenAI API key](https://platform.openai.com/api-keys).

* **Anthropic Claude** — Claude Sonnet 5.5, with thinking switched off. Requires an [Anthropic API key](https://console.anthropic.com/settings/keys).

Paste the relevant API token into **Settings → LLM**. Which model translates is chosen as a [translation method](#translation-methods-and-reserves) on Settings → General; add a second or third method on another provider and a model that hits its limits hands over by itself. A method saved in your settings whose model is no longer offered moves to its successor or, without one, falls back to the default; your other settings are kept.

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

* **Translate text (conversation context)** — the default. Translates into natural spoken Russian — accurate but not word-for-word, consistent in names, terms and «ты»/«вы» — using the whole conversation: the latest phrases verbatim together with the translations already given, plus running notes on everything older (topic, people, how they address each other, the Russian chosen for names and terms, the tone). The notes are refreshed by a separate small background request every ten or so phrases (every three when phrases are very long), so with the default phrase length the prompt stays around 0.6–1.6k tokens (about 1.2k on average) however long the conversation runs. **Clear dialog** starts a new conversation.
* **Translate text** — translates each recognized phrase on its own
* **Translate text (+1 … +5 previous sentences)** — same, but also feeds the model the last 1–5 recognized sentences as context, so a mid-sentence fragment is translated as a seamless continuation of the ongoing speech
* **Answer the question** — answers a recognized question in Russian

Placeholders replaced at request time: `{{source_text}}` (the current recognized text); `{{previous_text_1}}` … `{{previous_text_5}}` (the preceding sentences, newest = `1`); `{{recent_turns}}` (the latest phrases with their translations) and `{{conversation_summary}}` (the running notes) — a prompt that contains `{{conversation_summary}}` switches the notes on.

## Tabs

There are two top-level tabs:

* **Messages** — the live loop: shows recognized speech and the model's streaming answer, with a status line underneath (recognition, translation, which method answers and why)
* **Settings** — a master-detail screen with a category sidebar:
  * **General** — translation methods 1–3 (how the speech is heard → which model translates), input device, question language
  * **LLM** — streaming-response toggle, API tokens
  * **Whisper batch** / **Whisper streaming** / **Sherpa-ONNX** — per-engine model path & tuning
  * **Prompts** — select and edit prompts (saved in the prompts map)
  * **Console** — captures `stdout` / `stderr` from the running process for debugging

Right of the tabs, the same size and spacing, are **Start dialog** and **Clear dialog** (shown always, active while Messages is open), then the microphone and memory meters and the tokens the current dialog has used.

## Use for conferences

If you need to translate dialogue from a conference, route the conference audio into the app and to your speakers at the same time using a virtual audio cable. [VB-Audio VoiceMeeter Banana](https://vb-audio.com/Voicemeeter/banana.htm) works well for this.

## Additional information

* Tested only on Windows.
* The `{{source_text}}` tag in a prompt is replaced with the recognized text.
* PyTorch 2.11 (CUDA 12.8) + CTranslate2 4.8.0 is the tested GPU combination for the Python side; `python/requirements.txt` says why those two are held back.

## Future features

* Function calls via LangChain
* Refine-answer button — for each LLM answer

<br>

![Screenshot_messages](Screenshot_messages.png)<br>
![Screenshot_prompt](Screenshot_prompt.png)<br>
![Screenshot_settings](Screenshot_settings.png)<br>
