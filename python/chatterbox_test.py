import os
import torch
import torchaudio
import traceback
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

    device = "cuda" if torch.cuda.is_available() else "cpu"

    if torch.cuda.is_available():
        print("Current device:", torch.cuda.current_device())
        print("Device name:", torch.cuda.get_device_name(torch.cuda.current_device()))

    if not os.path.isfile(speaker_wav):
        print(f"Voice sample not found: {speaker_wav}")
        return

    try:
        model = ChatterboxMultilingualTTS.from_pretrained(
            device=torch.device(device)
        )
        text = "Привет! Это тест с использованием Chatterbox TTS"
        wav = model.generate(
            text=text,
            language_id="ru",
            audio_prompt_path=speaker_wav,
            cfg_weight=0.7,
            exaggeration=0.8
        )
        torchaudio.save(output_path, wav, sample_rate=model.sr)
        print("Playing audio...")
        samplerate, data = read(output_path)
        sd.play(data, samplerate=samplerate)
        sd.wait()
        print("Done.")
    except Exception as e:
        print("Exception:", e)
        traceback.print_exc()

if __name__ == "__main__":
    main()
