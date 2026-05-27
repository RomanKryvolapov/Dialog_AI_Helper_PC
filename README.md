# Dialog AI Helper - PC Version

## What is this app for?

You can use this app to translate dialogue or to generate suggestions during a dialogue in real time.

The application's operation consists of 2 stages:

* **Voice recognition**: Recognize each sentence in a dialogue in a continuous mode

* **Sending to LLM**: Send the recognized text with a prompt to LLM models and receive streaming responses

## Voice recognition

The app uses [faster-whisper](https://github.com/SYSTRAN/faster-whisper) (a re-implementation of OpenAI's Whisper) for speech-to-text. The Kotlin side captures audio from the selected input device, splits it into chunks by silence detection, and spawns a Python subprocess per chunk to transcribe it.

To make it work:

1. Install the Python dependencies from `python/requirements.txt` into `python/.venv/` (CUDA-enabled PyTorch and `faster_whisper`).
2. Download a Whisper model (for example from [Hugging Face: faster-whisper](https://huggingface.co/Systran)) and unpack it to a folder.
3. In the **Settings** tab, set **Whisper model path** to that folder.
4. Select an input audio device and the question language code (e.g. `en`, `ru`).

If a CUDA-capable GPU is available, transcription runs on GPU automatically; otherwise it falls back to CPU.

### Chunking parameters (Settings tab)

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

* **OpenAI** — GPT-5.4 mini / GPT-5.4 nano. Requires an [OpenAI API key](https://platform.openai.com/api-keys).

* **Anthropic Claude** — Claude Haiku 4.5. Requires an [Anthropic API key](https://console.anthropic.com/settings/keys).

Paste the relevant API token into the **Settings** tab. If a model hits its limits, just switch to another one in the model dropdown.

## Prompts

Multiple named prompts are stored and can be switched between in the **Prompt** tab. Two are built in by default:

* **Translate text** — translates the recognized text from English to Russian
* **Answer the question** — answers a recognized question in Russian

When editing a prompt, the `{{source_text}}` placeholder is replaced with the recognized text at request time.

## Tabs

* **Messages** — the live loop: shows recognized speech, voice-recognition status, the model's streaming answer, and a microphone toggle button
* **Prompt** — select and edit prompts (saved in the prompts map)
* **Settings** — input device, Whisper model path + chunking config, selected LLM model, API tokens
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
