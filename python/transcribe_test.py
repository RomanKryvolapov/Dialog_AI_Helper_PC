import sys
import os
import traceback
from faster_whisper import WhisperModel
import torch

def main():
    print("Start")

    print(torch.__version__)
    print(torch.version.cuda)

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    audio_path = os.path.join(base_dir, "cache", "chunk_0.wav")

    if not os.path.isfile(audio_path):
        print(f"Audio file not found: {audio_path}", file=sys.stderr)
        exit(1)

    try:
        print("Loading model...")

        model_dir = os.path.join(base_dir, "models", "faster-whisper-base")
        if not os.path.isdir(model_dir):
            print(f"Model directory not found: {model_dir}", file=sys.stderr)
            exit(1)

        model = WhisperModel(model_dir, compute_type="int8")
        print("Model loaded")

        print(f"Transcribing file: {audio_path}")
        segments, _ = model.transcribe(audio_path)

        text = " ".join([seg.text for seg in segments])
        print(text)

    except Exception as e:
        print("Exception occurred:", file=sys.stderr)
        traceback.print_exc()
        exit(2)

if __name__ == "__main__":
    main()