import sys
import os
import torch
from faster_whisper import WhisperModel

def main():

    if len(sys.argv) < 4:
        print("Whisper: Arguments not found")
        sys.exit(1)

    audio_path = sys.argv[1]
    model_dir = sys.argv[2]
    language = sys.argv[3]

    if not os.path.isfile(audio_path):
        print("Whisper: Audio file not found:")
        sys.exit(1)

    if not os.path.isdir(model_dir):
        print("Whisper: Model directory not found:")
        sys.exit(1)

    if not language:
        print("Whisper: Language is empty", file=sys.stderr)
        sys.exit(1)

    device = "cuda" if torch.cuda.is_available() else "cpu"

    try:
        model = WhisperModel(
            model_size_or_path=model_dir,
            compute_type="int8",
            device=device
        )
        segments, _ = model.transcribe(
            audio_path,
            language=language
        )
        text = " ".join([seg.text for seg in segments])
        print(text)
    except Exception as e:
        print("Whisper: Exception ", str(e), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
