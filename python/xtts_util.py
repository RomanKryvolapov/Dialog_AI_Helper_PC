import sys
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

    if len(sys.argv) < 3:
        print("Usage: xtts_util.py <text> <speaker_wav>", file=sys.stderr)
        sys.exit(1)

    text = sys.argv[1]
    speaker_wav = sys.argv[2]

    if not os.path.isfile(speaker_wav):
        print(f"Audio file not found: {speaker_wav}", file=sys.stderr)
        sys.exit(1)

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    output_path = os.path.join(base_dir, "cache", "output.wav")

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
