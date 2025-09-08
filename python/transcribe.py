import sys
import os
import traceback
from faster_whisper import WhisperModel
import torch


def main():
    # print("Torch version:", torch.__version__)
    # print("Torch CUDA version:", torch.version.cuda)
    # print("CUDA available:", torch.cuda.is_available())
    if len(sys.argv) < 2:
        print("No audio path provided", file=sys.stderr)
        sys.exit(1)
    audio_path = sys.argv[1]
    model_dir = sys.argv[2]
    if not os.path.isfile(audio_path):
        print(f"Audio file not found: {audio_path}", file=sys.stderr)
        sys.exit(1)
    if not os.path.isdir(model_dir):
        print(f"Model directory not found: {model_dir}", file=sys.stderr)
        sys.exit(1)
    try:
        # base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        # model_dir = os.path.join(base_dir, "models", "faster-whisper-base.en")
        # if not os.path.isdir(model_dir):
        #     print(f"Model directory not found: {model_dir}", file=sys.stderr)
        #     sys.exit(1)
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
