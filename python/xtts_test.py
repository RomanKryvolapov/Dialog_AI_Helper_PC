import os
import torch
from TTS.api import TTS
from scipy.io.wavfile import read
import sounddevice as sd

from TTS.tts.configs.xtts_config import XttsConfig
from TTS.tts.models.xtts import XttsAudioConfig, XttsArgs
from TTS.config.shared_configs import BaseDatasetConfig

torch.serialization.add_safe_globals([
    XttsConfig,
    XttsAudioConfig,
    XttsArgs,
    BaseDatasetConfig
])

def main():

    text = "Тестовый текст"

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    speaker_wav = os.path.join(base_dir, "cache", "chunk_0.wav")
    output_path = os.path.join(base_dir, "cache", "output_0.wav")

    print(f"Synthesizing text: {text}")
    print(f"Using speaker: {speaker_wav}")

    tts = TTS(
        model_name="tts_models/multilingual/multi-dataset/xtts_v2",
        gpu=torch.cuda.is_available()
    )

    tts.tts_to_file(
        text=text,
        file_path=output_path,
        language="ru",
        speaker_wav=speaker_wav
    )

    print("Playing audio...")
    samplerate, data = read(output_path)
    sd.play(data, samplerate=samplerate)
    sd.wait()
    print("Done.")

if __name__ == "__main__":
    main()
