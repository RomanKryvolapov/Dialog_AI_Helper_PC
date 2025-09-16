import os
import sys
import torch
import torchaudio
import sounddevice as sd
from scipy.io.wavfile import read
from chatterbox.mtl_tts import ChatterboxMultilingualTTS

base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
speaker_wav = os.path.join(base_dir, "voice", "voice_sample_ru.wav")
output_path = os.path.join(base_dir, "cache", "output_chatterbox_test.wav")

def main():
    print("Torch version:", torch.__version__)
    print("CUDA version:", torch.version.cuda)
    print("CUDA available:", torch.cuda.is_available())

    if len(sys.argv) < 3:
        print("Сhatterbox: Arguments not found")
        sys.exit(1)

    text = sys.argv[1]
    language = sys.argv[2]

    if not os.path.isfile(speaker_wav):
        print("Сhatterbox: Voice sample audio file not found")
        sys.exit(1)

    if not text:
        print("Сhatterbox: Text is empty", file=sys.stderr)
        sys.exit(1)

    if not language:
        print("Сhatterbox: Language is empty", file=sys.stderr)
        sys.exit(1)

    device = "cuda" if torch.cuda.is_available() else "cpu"

    if torch.cuda.is_available():
        print("Current device:", torch.cuda.current_device())
        print("Device name:", torch.cuda.get_device_name(torch.cuda.current_device()))

    try:
        model = ChatterboxMultilingualTTS.from_pretrained(
            device=torch.device(device)
        )
        wav = model.generate(
            text=text,
            language_id=language,
            audio_prompt_path=speaker_wav,
            cfg_weight=0.7,
            exaggeration=0.8
        )
        torchaudio.save(output_path, wav, sample_rate=model.sr)
        samplerate, data = read(output_path)
        sd.play(data, samplerate=samplerate)
        sd.wait()
    except Exception as e:
        print("Сhatterbox: Exception ", str(e), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
