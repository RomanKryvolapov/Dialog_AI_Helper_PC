import sys
import os
import glob
import sysconfig
import json
import base64
import threading
import time

import numpy as np
from faster_whisper import WhisperModel


def _enable_cuda_dlls():
    """Make CUDA DLLs discoverable so the GPU can be used on Windows.

    torch ships a complete, self-consistent set of CUDA libraries (cudart/cublas/cudnn)
    in torch/lib; we add that folder (and any standalone nvidia/* packages) to the DLL
    search path so CTranslate2 can load them.
    """
    try:
        site = sysconfig.get_paths()["purelib"]
        candidates = [os.path.join(site, "torch", "lib")]
        candidates += glob.glob(os.path.join(site, "nvidia", "*", "bin"))
        for dll_dir in candidates:
            if os.path.isdir(dll_dir):
                os.add_dll_directory(dll_dir)
    except Exception as e:
        sys.stderr.write(f"whisper streaming: could not register CUDA DLL dirs: {e}\n")
        sys.stderr.flush()

# Streaming Whisper helper with sentence-based segmentation.
#
# Protocol (newline-delimited JSON on stdin):
#   {"cmd": "audio", "pcm": "<base64 of 16 kHz, 16-bit, mono PCM>"}
#   {"cmd": "flush"}   finalize whatever is buffered (e.g. on a real pause)
#   {"cmd": "reset"}   drop the current buffer without emitting
#   {"cmd": "exit"}    stop the process
#
# Emits (newline-delimited JSON on stdout):
#   {"type": "ready"}
#   {"type": "partial", "text": "..."}   the in-progress (not yet finished) phrase
#   {"type": "final",   "text": "..."}   a completed phrase (one message)
#   {"type": "error",   "text": "..."}
#
# Segmentation: the buffer is re-transcribed every `step_ms`. We commit a phrase
# when the transcribed text ends a sentence (.!?) AND has reached `min_chars`,
# but only once a LATER segment exists (so the boundary is stable). Committed
# audio is trimmed from the buffer; the remainder stays as the live partial.
# Safety: if the buffer grows past `max_sec` (or MAX_CHARS) with no sentence end,
# it is force-committed so we never get one endless message.

SAMPLE_RATE = 16000
BYTES_PER_SEC = SAMPLE_RATE * 2
MAX_CHARS = 220
SENTENCE_ENDINGS = ".!?…"


def main():
    if len(sys.argv) < 3:
        print(json.dumps({"type": "error", "text": "args"}), flush=True)
        sys.exit(1)

    model_dir = sys.argv[1]
    language = sys.argv[2]
    step_ms = int(sys.argv[3]) if len(sys.argv) > 3 else 700
    min_chars = int(sys.argv[4]) if len(sys.argv) > 4 else 20
    max_sec = (int(sys.argv[5]) / 1000.0) if len(sys.argv) > 5 else 8.0

    if not os.path.isdir(model_dir):
        print(json.dumps({"type": "error", "text": "model dir not found"}), flush=True)
        sys.exit(1)

    _enable_cuda_dlls()
    model = None
    for dev in ("cuda", "cpu"):
        try:
            m = WhisperModel(model_size_or_path=model_dir, compute_type="int8", device=dev)
            # Warm-up on a short silent buffer to surface missing CUDA libs before real audio arrives.
            list(m.transcribe(np.zeros(16000, dtype=np.float32), language=language)[0])
            model = m
            sys.stderr.write(f"whisper streaming: using {dev}\n")
            sys.stderr.flush()
            break
        except Exception as e:
            sys.stderr.write(f"whisper streaming: {dev} unavailable: {e}\n")
            sys.stderr.flush()
    if model is None:
        print(json.dumps({"type": "error", "text": "model load failed on any device (cuda, cpu)"}), flush=True)
        sys.exit(1)

    lock = threading.Lock()
    buffer = bytearray()
    flush_event = threading.Event()
    reset_event = threading.Event()
    stop_event = threading.Event()

    def transcribe(pcm_bytes):
        audio = np.frombuffer(bytes(pcm_bytes), dtype=np.int16).astype(np.float32) / 32768.0
        segments, _ = model.transcribe(audio, language=language)
        return [(float(s.start), float(s.end), s.text.strip()) for s in segments]

    def emit(kind, text):
        print(json.dumps({"type": kind, "text": text}), flush=True)

    def trim_front(n):
        with lock:
            del buffer[:min(max(n, 0), len(buffer))]

    def worker():
        last_len = 0
        last_partial = 0.0
        while not stop_event.is_set():
            if reset_event.is_set():
                reset_event.clear()
                with lock:
                    buffer.clear()
                last_len = 0
                continue

            do_flush = flush_event.is_set()
            if do_flush:
                flush_event.clear()

            now = time.monotonic()
            with lock:
                cur_len = len(buffer)
            if not do_flush and (cur_len == last_len or (now - last_partial) * 1000.0 < step_ms):
                time.sleep(0.02)
                continue
            last_len = cur_len
            last_partial = now

            with lock:
                snapshot = bytes(buffer)
            if len(snapshot) < 2:
                continue

            try:
                segs = transcribe(snapshot)
            except Exception as e:
                emit("error", "transcribe: " + str(e))
                continue
            if not segs:
                continue

            if do_flush:
                text = " ".join(t for (_, _, t) in segs).strip()
                if text:
                    emit("final", text)
                trim_front(len(snapshot))
                continue

            # Commit completed sentences (all but the in-progress last segment).
            commit_idx = -1
            acc = ""
            for i in range(len(segs) - 1):
                acc = (acc + " " + segs[i][2]).strip()
                if acc and acc[-1] in SENTENCE_ENDINGS and len(acc) >= min_chars:
                    emit("final", acc)
                    acc = ""
                    commit_idx = i

            if commit_idx >= 0:
                cut = int(segs[commit_idx][1] * BYTES_PER_SEC)
                cut -= cut % 2
                trim_front(cut)
                partial = (acc + " " + segs[-1][2]).strip()
                if partial:
                    emit("partial", partial)
            else:
                text = " ".join(t for (_, _, t) in segs).strip()
                buffer_sec = len(snapshot) / BYTES_PER_SEC
                if buffer_sec >= max_sec or len(text) >= MAX_CHARS:
                    if text:
                        emit("final", text)
                    trim_front(len(snapshot))
                elif text:
                    emit("partial", text)

    threading.Thread(target=worker, daemon=True).start()
    emit("ready", "")

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception:
            continue
        cmd = msg.get("cmd")
        if cmd == "audio":
            try:
                pcm = base64.b64decode(msg.get("pcm", ""))
            except Exception:
                continue
            with lock:
                buffer.extend(pcm)
        elif cmd == "flush":
            flush_event.set()
        elif cmd == "reset":
            reset_event.set()
        elif cmd == "exit":
            break

    stop_event.set()


if __name__ == "__main__":
    main()
