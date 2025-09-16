import sys
import os
import traceback
import torch
from faster_whisper import WhisperModel

def main():
    print("Torch version:", torch.__version__)
    print("Cuda version:", torch.version.cuda)
    print("CUDA available:", torch.cuda.is_available())
    print("Current device:", torch.cuda.current_device())
    print("Device name:", torch.cuda.get_device_name(0))

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    audio_path = os.path.join(base_dir, "cache", "chunk_0.wav")
    model_dir = os.path.join(base_dir, "models", "faster-whisper-base")


    try:
        if not os.path.isdir(model_dir):
            print(f"Model directory not found: {model_dir}", file=sys.stderr)
            sys.exit(1)
        if not os.path.isfile(audio_path):
            print(f"Audio file not found: {audio_path}", file=sys.stderr)
            sys.exit(1)
        model = WhisperModel(
            model_size_or_path=model_dir,
            compute_type="int8",
            device="cuda" if torch.cuda.is_available() else "cpu"
        )
        segments, _ = model.transcribe(audio_path)
        text = " ".join([seg.text for seg in segments])
        print(text)
    except Exception as e:
        print("Exception occurred:", file=sys.stderr)
        traceback.print_exc()
        sys.exit(2)


if __name__ == "__main__":
    main()
