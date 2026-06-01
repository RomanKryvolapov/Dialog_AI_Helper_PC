# Real-time распознавание речи (STT) — варианты для Dialog AI Translator

> Дата обзора: 2026-06-01. Цифры по latency/WER/ценам актуальны на середину 2026 и могут меняться — проверяй у источников (в конце).
> Контекст: десктоп на Kotlin/JVM + JavaFX, в стеке есть Ktor (с WebSocket-клиентом), Koin, langchain4j. Уже подключены **Vosk** (in-process, стримовый) и **Whisper** (faster-whisper через Python-сабпроцесс).
> Задача: уйти от не-realtime Whisper к потоковому распознаванию **английского** с **минимальной задержкой**. Машина с **NVIDIA GPU**.

---

## 1. Текущая реализация (Whisper / faster-whisper) — baseline

Как устроено сейчас (код: `utils/AudioChunkerImpl.kt`, `utils/WhisperUtil*.kt`, `python/whisper_util.py`):

- **Захват и сегментация** — `AudioChunkerImpl`: 16 kHz, 16-bit, mono; читает кадрами по 100 мс. Энергетический VAD (`silenceThresholdPercents`, `maxSilenceMilliseconds`, `maxChunkDurationMilliseconds`, мин. длина 500 мс) режет **целую реплику** по тишине (или по максимальной длительности) и сохраняет её в WAV (`saveChunk`).
- **Распознавание** — `python/whisper_util.py` запускается **разово на каждый WAV**: `faster-whisper` (`WhisperModel`, `compute_type="int8"`, `device="cuda"` если есть), `model.transcribe(audio_path, language=...)`, возвращает текст целиком.
- **Модели** — английские: `faster-whisper-base.en`, `medium.en` (+ `base`).

**Почему это не realtime:**
- Текст появляется **только после конца фразы** (нужно дождаться тишины для endpoint) **плюс время инференса** Whisper. По логам — инференс ~3-8 с на чанк, реплики ~5-8 с → **задержка несколько секунд**.
- **Нет partial-результатов** — Whisper по своей природе обрабатывает аудио целиком, а не инкрементально. Ничего не «печатается» по мере речи.
- Каждый чанк — новый процесс (старт Python + загрузка модели амортизируется, но это батч, не поток).

**Что хорошо:** высокая точность (Whisper-класс, ~5-6% WER на large-v3; `medium.en` близко на чистом английском), полностью **локально/офлайн/бесплатно**, GPU-ускорение (int8/CUDA), уже работает.

**Вывод:** проблема не в точности, а в **архитектуре (батч) и задержке**. Отсюда — переход на потоковый движок.

---

## 2. Ключевая мысль: realtime — это смена архитектуры, а не только модели

Любой настоящий realtime устроен так:
**непрерывный поток PCM → инкрементальные partial-гипотезы (обновляются по мере речи) → финализация по endpoint (VAD/turn-detection)**.

То есть `AudioChunker` должен не нарезать фиксированные WAV, а **кормить кадрами** стримовый распознаватель и получать partial/final. Это хорошо ложится на уже сделанный планировщик переводов: partial'ы показываем сразу, а на **финале фразы** ставим перевод в очередь (newest-first + rate limit).

Способы интеграции в JVM-приложение:

| Способ | Движки | Заметка |
|---|---|---|
| **In-process** (нативная либа, без IPC) | Vosk (есть), **sherpa-onnx** (Java/Kotlin биндинги) | кормишь PCM-кадры → partial/final, как Vosk сейчас |
| **Постоянный Python-сабпроцесс** (стрим через stdin/сокет) | NeMo/Nemotron, Moonshine, Kyutai | ложится на текущий паттерн «шелл в `whisper_util.py`», но процесс долгоживущий |
| **Локальный gRPC-сервер** | NVIDIA Riva | Java gRPC-клиент, продакшн-грейд |
| **Облачный WebSocket** | Deepgram, AssemblyAI, OpenAI Realtime | в стеке уже есть Ktor WebSocket-клиент |

---

## 3. Локальные варианты (есть NVIDIA GPU)

| Движок | Задержка | English WER | GPU | Интеграция в этот app | Эффорт |
|---|---|---|---|---|---|
| **sherpa-onnx** streaming Zipformer | 80–320 мс | ~7–9% | не нужен (realtime даже на ARM CPU) | **In-process, есть Kotlin/Java биндинги** | низкий-средний |
| **NVIDIA Nemotron-speech-streaming-en-0.6b** (2026) | 80 / 160 / 560 / 1120 мс (через `att_context_size`) | 6.93% @1.12с … 8.43% @80мс | да (Ampere+) | NeMo (постоянный сабпроцесс) или Riva gRPC | средний-высокий |
| **Moonshine v2** (2026) | Medium **258 мс** / Small 148 / Tiny 50 | **6.65%** (Medium, лучше Whisper-L-v3) | CPU/GPU, лёгкая | Python/ONNX-сабпроцесс | средний |
| **Kyutai STT** stt-1b-en_fr | ~0.5 с/слово, 1 с после старта | 6.4% | да | self-host сервер → WebSocket | средний-высокий |
| **NVIDIA Riva** (Parakeet/Nemotron RNNT) | чанки 100 / 160 / 320 мс | как Nemotron/Parakeet | да | Docker-сервер + Java gRPC | высокий, но «продакшн» |
| **Streaming Whisper** (WhisperLiveKit / SimulStreaming) | **1–3.3 с** | как Whisper (~5–6%) | да | свой Python-сервер/сабпроцесс | низкий (минимум миграции) |
| **Vosk** (уже подключён) | мгновенные partial'ы | ~10%+ | нет | **уже в проекте, in-process** | нулевой |

**Коротко:**
- **sherpa-onnx** — лучший баланс под этот стек: работает **прямо в JVM** (как Vosk, но точнее и с низкой задержкой), GPU не требует, ONNX. Минимум IPC.
- **Nemotron-speech-streaming-en-0.6b** — **лучшая точность английского среди стримовых локальных**, cache-aware, задержку крутишь до 80–160 мс, использует твою видеокарту.
- **Moonshine v2** — самый «лёгкий и быстрый» (258 мс, точнее Whisper-large-v3).
- **Streaming Whisper** — единственный, где задержка остаётся 1–3 с; берётся только ради минимума переделок.

> Важно: «обычный» **Parakeet TDT 0.6b v2/v3** и **Canary Qwen 2.5B** дают топ-точность (5.6–6.3% WER), но это **батч**-модели — наивный чанкинг роняет качество (Parakeet v3: 6.32% → 9.22% WER). Для потока нужны именно **cache-aware** версии (Nemotron-streaming / FastConformer-streaming).

---

## 4. Облачные варианты (минимальная задержка + топ-точность, платно/онлайн)

| API | Задержка (partial) | Цена (стрим) | English | Интеграция |
|---|---|---|---|---|
| **AssemblyAI Universal-Streaming** | ~300 мс (P50) | **$0.15/час** (дёшево) | заточен под EN, keyterm-prompting | Ktor WebSocket |
| **Deepgram Nova-3** (+ **Flux** для диалогов) | **< 300 мс** | $0.0077/мин (~$0.46/час) | отличный | Ktor WebSocket |
| **OpenAI Realtime** (`gpt-realtime-whisper` / `gpt-4o-transcribe`) | 200–400 мс | $0.017/мин стрим (4o-transcribe 4.1% WER) | топ-точность | WebSocket/WebRTC |
| Google STT v2/Chirp, Azure, Amazon Transcribe, Speechmatics | ~300 мс | сопоставимо | хорошо | gRPC/WebSocket |

В стеке **уже есть Ktor с WebSocket-клиентом**, поэтому облачный стрим — это «открыть WS, слать PCM, читать partial'ы». Самый дешёвый и заточенный под английский — **AssemblyAI**; лучший для диалогов с детекцией конца реплики — **Deepgram Flux**; максимальная точность — **OpenAI gpt-4o-transcribe**.

---

## 5. Сравнение с текущей реализацией (Whisper) — главное

Все варианты на одних осях, baseline — текущий Whisper-пайплайн:

| Решение | Тип | Задержка до результата | Partial'ы | English WER | Офлайн | Стоимость | GPU | Эффорт интеграции |
|---|---|---|---|---|---|---|---|---|
| **Whisper (faster-whisper) — сейчас** | батч / по реплике | **несколько секунд** (тишина + инференс 3-8 с) | **нет** | ~5–6% (`medium.en`) | да | бесплатно | да (int8/CUDA) | — (уже есть) |
| Streaming Whisper (SimulStreaming) | поток | 1–3.3 с | да (refine) | ~5–6% | да | бесплатно | да | низкий |
| Vosk (есть) | поток | мгновенно | да | ~10%+ | да | бесплатно | нет | нулевой |
| **sherpa-onnx Zipformer** | поток | **80–320 мс** | да | ~7–9% | да | бесплатно | нет | низкий-средний |
| **Nemotron-streaming-en-0.6b** | поток | **80–1120 мс** | да | **6.9%** | да | бесплатно | да | средний-высокий |
| Moonshine v2 Medium | поток | **258 мс** | да | **6.65%** | да | бесплатно | CPU/GPU | средний |
| Kyutai STT | поток | ~0.5–1 с | да | 6.4% | да | бесплатно | да | средний-высокий |
| NVIDIA Riva | поток | 100–320 мс | да | 6–7% | да | бесплатно* | да | высокий |
| AssemblyAI Universal-Streaming | поток (cloud) | ~300 мс | да | топ (4–5%) | нет | $0.15/час | — | низкий-средний |
| Deepgram Nova-3 | поток (cloud) | < 300 мс | да | топ | нет | ~$0.46/час | — | низкий-средний |
| OpenAI Realtime / gpt-4o-transcribe | поток (cloud) | 200–400 мс | да | **топ (4.1%)** | нет | $0.017/мин | — | средний |

\* Riva self-hosted под лицензией NVIDIA AI Enterprise.

**Что меняется относительно Whisper:**
- **Задержка**: с «несколько секунд после конца фразы» → до **80–300 мс** (локальные стримовые / облако) с partial'ами, которые видно во время речи.
- **Точность английского**: у Whisper она и так хорошая. Стримовые локальные (Nemotron 6.9%, Moonshine 6.65%) — сопоставимо/чуть хуже; облако (4–5%) — лучше. Главный выигрыш — **не точность, а отзывчивость**.
- **Архитектура**: уходит «нарезка WAV + разовый сабпроцесс» → приходит «поток кадров + partial/final». `AudioChunker` упрощается (VAD остаётся для endpoint/финализации, но WAV-файлы не нужны).
- **Офлайн/цена**: локальные варианты сохраняют офлайн-бесплатность Whisper; облако даёт минимум кода и лучшую точность ценой денег и онлайна.

---

## 6. Рекомендация под этот кейс (min latency, NVIDIA, и local, и cloud)

1. **Самый быстрый realtime, полностью локально, минимум интеграции → `sherpa-onnx` streaming Zipformer (in-process Kotlin).** ~150–300 мс, GPU не нужен, в JVM как Vosk. «Дешёвая победа».
2. **Лучший английский локально на видеокарте → `nemotron-speech-streaming-en-0.6b`** через **NVIDIA Riva** (gRPC из JVM, продакшн) или постоянный NeMo-сабпроцесс. Задержка до 80–160 мс.
3. **Абсолютный минимум задержки + лучшая точность, минимум кода (платно/онлайн) → AssemblyAI Universal-Streaming или Deepgram Nova-3** через Ktor WS.

**Прагматичный план:** сделать общий интерфейс `StreamingRecognizer` (по аналогии с выбором LLM-движка) и за ним 2-3 бэкенда — `sherpa-onnx` (локальный дефолт) + один облачный (AssemblyAI/Deepgram) для «максимум качества», оставив Whisper-батч как fallback. Тогда переключение — просто настройка, как уже сделано для LLM-движков.

---

## 7. Привязка к коду этого приложения

- `utils/AudioChunkerImpl.kt` — источник PCM. Для потока: вместо `saveChunk` → WAV отдавать кадры (16 kHz/16-bit/mono, по 100 мс уже есть) напрямую в `StreamingRecognizer`. VAD (`calculateVoiceLevel`) переиспользуется как **endpoint/turn-detection** для финализации.
- Новый `utils/StreamingRecognizer` (интерфейс) + бэкенды: `SherpaOnnxRecognizer` (in-process), `CloudWsRecognizer` (Ktor WS), `WhisperBatchRecognizer` (текущий, fallback). Выбор — через `PreferencesRepository`/Settings, как у LLM.
- Связка с уже сделанным планировщиком переводов в `ui/MessagesTab.kt`: **partial** → обновлять строку оригинала (без перевода), **final** → класть задачу в `translationQueue` (newest-first + rate limit). Это снимает текущую нарезку и даёт «живой» оригинал + перевод по факту завершения фразы.

---

## Источники

- Open ASR Leaderboard: [HF Space](https://huggingface.co/spaces/hf-audio/open_asr_leaderboard) · [инсайты/тренды](https://huggingface.co/blog/open-asr-leaderboard) · [лучшие open-source STT 2026 (Northflank)](https://northflank.com/blog/best-open-source-speech-to-text-stt-model-in-2026-benchmarks)
- NVIDIA: [nemotron-speech-streaming-en-0.6b](https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b) · [stt_en_fastconformer_hybrid_large_streaming_multi](https://huggingface.co/nvidia/stt_en_fastconformer_hybrid_large_streaming_multi) · [Parakeet TDT 0.6b v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) · [Riva ASR performance](https://docs.nvidia.com/deeplearning/riva/user-guide/docs/asr/asr-performance.html) · [On-device streaming ASR paper](https://arxiv.org/html/2604.14493v2)
- sherpa-onnx: [GitHub (Kotlin/Java биндинги)](https://github.com/k2-fsa/sherpa-onnx) · [docs](https://k2-fsa.github.io/sherpa/onnx/index.html)
- Moonshine: [GitHub](https://github.com/moonshine-ai/moonshine) · [Moonshine v2 paper](https://arxiv.org/html/2602.12241v1) · [Local STT 2026: Moonshine vs Parakeet vs Whisper](https://www.onresonant.com/resources/local-stt-models-2026)
- Kyutai STT: [блог kyutai](https://kyutai.org/blog)
- Streaming Whisper: [WhisperLiveKit (SimulStreaming)](https://github.com/QuentinFuxa/WhisperLiveKit) · [whisper_streaming](https://github.com/ufal/whisper_streaming)
- Cloud: [Deepgram pricing](https://deepgram.com/pricing) · [AssemblyAI Universal-Streaming](https://www.assemblyai.com/universal-streaming) · [OpenAI realtime transcription](https://developers.openai.com/api/docs/guides/realtime-transcription)
