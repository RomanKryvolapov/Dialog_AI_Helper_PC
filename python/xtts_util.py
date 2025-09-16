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

base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
speaker_wav = os.path.join(base_dir, "voice", "voice_sample_ru.wav")
output_path = os.path.join(base_dir, "cache", "output_xtts.wav")

def main():

    if len(sys.argv) < 3:
        print("Xtts: Arguments not found")
        sys.exit(1)

    text = sys.argv[1]
    language = sys.argv[2]

    if not os.path.isfile(speaker_wav):
        print("Xtts: Voice sample audio file not found")
        sys.exit(1)

    if not text:
        print("Xtts: Text is empty", file=sys.stderr)
        sys.exit(1)

    if not language:
        print("Xtts: Language is empty", file=sys.stderr)
        sys.exit(1)

    try:
        tts = TTS(
            model_name="tts_models/multilingual/multi-dataset/xtts_v2",
            gpu=torch.cuda.is_available()
        )

        tts.tts_to_file(
            text=text,
            file_path=output_path,
            language=language,
            speaker_wav=speaker_wav
        )

        samplerate, data = read(output_path)
        sd.play(data, samplerate=samplerate)
        sd.wait()
    except Exception as e:
        print("Xtts: Exception ", str(e), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
